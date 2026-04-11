package com.origin.service.discord;

import com.origin.entity.Raid;
import com.origin.repository.RaidRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.time.Duration;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RaidDiscordScannerService {

    private JDA jda;
    private final RaidRepository raidRepository;
    private final RaidHelperParserService parserService;
    private final Environment environment;
    private final Set<String> loggedFailedEmbeds = ConcurrentHashMap.newKeySet();
    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{M}+");

    @Autowired
    public void setJda(@Lazy JDA jda) {
        this.jda = jda;
    }

    public int scanConfiguredRaidHelperChannels() {
        List<String> channelIds = getConfiguredRaidHelperChannelIds();

        if (channelIds.isEmpty()) {
            log.warn("Aucun salon discord.raidhelper.channel.* configure pour le scan");
            return 0;
        }

        log.info("Scan des salons RaidHelper configures: {}", channelIds);
        return scanAndImportRaids(channelIds);
    }

    public List<String> getConfiguredRaidHelperChannelIds() {
        return Binder.get(environment)
                .bind("discord.raidhelper.channel", Bindable.mapOf(String.class, String.class))
                .orElse(Collections.emptyMap())
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }

    public int scanAndImportRaids(List<String> channelIds) {
        int importedCount = 0;
        int candidateCount = 0;
        int missingDataCount = 0;
        int pastRaidCount = 0;
        int duplicateMessageCount = 0;
        int duplicateNomDateCount = 0;
        int duplicateRaidHelperCount = 0;

        for (String channelId : channelIds) {
            TextChannel channel = jda.getTextChannelById(channelId);
            if (channel == null) {
                log.warn("Salon Discord introuvable: {}", channelId);
                continue;
            }

            List<Message> messages = retrieveMessages(channel, 200);
            log.info("Analyse de {} messages dans le salon {}", messages.size(), channel.getName());

            for (Message message : messages) {
                if (!message.getAuthor().isBot()) {
                    continue;
                }
                if (message.getEmbeds().isEmpty()) {
                    continue;
                }

                Message sourceMessage = resolveSourceMessage(message);
                if (sourceMessage.getEmbeds().isEmpty()) {
                    continue;
                }

                MessageEmbed embed = sourceMessage.getEmbeds().get(0);
                if (!parserService.isRaidHelperEmbed(message) && !parserService.isRaidHelperEmbed(sourceMessage)) {
                    log.debug("Embed ignore: {}", embed.getTitle());
                    continue;
                }

                candidateCount++;
                String nom = parserService.extractNom(embed);
                TextChannel sourceChannel = resolveSourceChannel(sourceMessage, channel);
                String resolvedChannelId = sourceChannel.getId();
                LocalDateTime date = resolveRaidDate(sourceMessage, sourceChannel, embed);
                Optional<String> idOpt = parserService.extractRaidHelperId(sourceMessage);
                if (idOpt.isEmpty()) {
                    idOpt = parserService.extractRaidHelperId(message);
                }

                if (nom == null || date == null) {
                    missingDataCount++;
                    if (parserService.isCompositionToolEmbed(embed)) {
                        log.warn("Embed Composition Tool sans date exploitable: {}", embed.getTitle());
                    }
                    log.warn("Impossible d'extraire nom/date depuis l'embed : {}", embed.getTitle());
                    logFailedEmbedOnce(channelId, message.getId(), embed);
                    continue;
                }

                if (date.isBefore(LocalDateTime.now().minusHours(1))) {
                    pastRaidCount++;
                    log.debug("Raid ignore car deja passe - nom: {}, date: {}", nom, date);
                    continue;
                }

                if (raidRepository.existsByDiscordMessageId(sourceMessage.getIdLong())) {
                    duplicateMessageCount++;
                    log.debug("Raid deja existant (discordMessageId)");
                    continue;
                }

                Optional<Raid> existingRaid = findExistingRaid(idOpt, nom, date);
                if (existingRaid.isPresent()) {
                    Raid raid = existingRaid.get();
                    boolean updated = refreshImportedRaid(raid, resolvedChannelId, nom, date, idOpt.orElse(null), sourceMessage);

                    if (raid.getRaidHelperId() != null && idOpt.isPresent() && Objects.equals(raid.getRaidHelperId(), idOpt.get())) {
                        duplicateRaidHelperCount++;
                    } else {
                        duplicateNomDateCount++;
                    }

                    if (updated) {
                        log.info("Raid mis a jour - nom: {}, date: {}, messageId: {}", raid.getNom(), raid.getDate(), raid.getDiscordMessageId());
                    } else {
                        log.debug("Raid deja existant (nom/date ou raidHelperId)");
                    }
                    continue;
                }

                Raid raid = Raid.builder()
                        .nom(nom)
                        .date(date)
                        .channelId(resolvedChannelId)
                        .raidHelperId(idOpt.orElse(null))
                        .discordMessageId(sourceMessage.getIdLong())
                        .build();

                log.info("Raid detecte - nom: {}, date: {}, messageId: {}", raid.getNom(), raid.getDate(), raid.getDiscordMessageId());
                raidRepository.save(raid);
                importedCount++;
            }
        }

        log.info(
                "Scan termine - {} raid(s) importe(s) | candidats={} | sansNomOuDate={} | passes={} | dejaMessage={} | dejaNomDate={} | dejaRaidHelper={}",
                importedCount,
                candidateCount,
                missingDataCount,
                pastRaidCount,
                duplicateMessageCount,
                duplicateNomDateCount,
                duplicateRaidHelperCount
        );
        return importedCount;
    }

    public int rescanRaid(Long raidId) {
        Raid raid = raidRepository.findById(raidId)
                .orElseThrow(() -> new IllegalArgumentException("Raid introuvable : " + raidId));

        List<String> channels = new ArrayList<>();
        if (StringUtils.hasText(raid.getChannelId())) {
            channels.add(raid.getChannelId());
        }

        return scanAndImportRaids(channels);
    }

    private Message resolveSourceMessage(Message message) {
        return parserService.extractLinkedDiscordMessage(message)
                .flatMap(link -> {
                    TextChannel linkedChannel = jda.getTextChannelById(link.getChannelId());
                    if (linkedChannel == null) {
                        return Optional.empty();
                    }

                    try {
                        return Optional.ofNullable(linkedChannel.retrieveMessageById(link.getMessageId()).complete());
                    } catch (Exception exception) {
                        log.debug("Impossible de recharger le message source {} du salon {}: {}",
                                link.getMessageId(),
                                link.getChannelId(),
                                exception.getMessage());
                        return Optional.empty();
                    }
                })
                .orElse(message);
    }

    private TextChannel resolveSourceChannel(Message sourceMessage, TextChannel fallback) {
        TextChannel sourceChannel = sourceMessage.getChannel().asTextChannel();
        return sourceChannel != null ? sourceChannel : fallback;
    }

    private Optional<Raid> findExistingRaid(Optional<String> raidHelperId, String nom, LocalDateTime date) {
        if (raidHelperId.isPresent()) {
            Optional<Raid> byRaidHelperId = raidRepository.findByRaidHelperId(raidHelperId.get());
            if (byRaidHelperId.isPresent()) {
                return byRaidHelperId;
            }
        }

        return raidRepository.findByNomAndDate(nom, date);
    }

    private boolean refreshImportedRaid(Raid raid,
                                        String channelId,
                                        String nom,
                                        LocalDateTime date,
                                        String raidHelperId,
                                        Message incomingMessage) {
        Long discordMessageId = incomingMessage.getIdLong();
        if (!shouldUseIncomingMessage(raid, incomingMessage)) {
            return false;
        }

        boolean changed = false;

        if (!Objects.equals(raid.getChannelId(), channelId)) {
            raid.setChannelId(channelId);
            changed = true;
        }

        if (!Objects.equals(raid.getNom(), nom)) {
            raid.setNom(nom);
            changed = true;
        }

        if (!Objects.equals(raid.getDate(), date)) {
            raid.setDate(date);
            changed = true;
        }

        if (!Objects.equals(raid.getRaidHelperId(), raidHelperId) && StringUtils.hasText(raidHelperId)) {
            raid.setRaidHelperId(raidHelperId);
            changed = true;
        }

        if (!Objects.equals(raid.getDiscordMessageId(), discordMessageId)) {
            raid.setDiscordMessageId(discordMessageId);
            changed = true;
        }

        if (changed) {
            raidRepository.save(raid);
        }

        return changed;
    }

    private boolean shouldUseIncomingMessage(Raid raid, Message incomingMessage) {
        Long currentMessageId = raid.getDiscordMessageId();
        if (currentMessageId == null) {
            return true;
        }

        Message currentMessage = loadMessage(raid.getChannelId(), currentMessageId);
        if (currentMessage == null || currentMessage.getEmbeds().isEmpty()) {
            return true;
        }

        int currentScore = scoreMessage(currentMessage);
        int incomingScore = scoreMessage(incomingMessage);
        if (incomingScore != currentScore) {
            return incomingScore > currentScore;
        }

        return incomingMessage.getIdLong() > currentMessageId;
    }

    private Message loadMessage(String channelId, Long messageId) {
        if (messageId == null) {
            return null;
        }

        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            return null;
        }

        try {
            return channel.retrieveMessageById(messageId).complete();
        } catch (Exception exception) {
            log.debug("Impossible de recharger le message {} pour comparaison: {}", messageId, exception.getMessage());
            return null;
        }
    }

    private int scoreMessage(Message message) {
        if (message == null || message.getEmbeds().isEmpty()) {
            return 0;
        }

        MessageEmbed embed = message.getEmbeds().get(0);
        if (parserService.isPlaceholderSignupEmbed(embed)) {
            return 1;
        }
        if (parserService.extractDateFromEmbed(embed) != null) {
            return 4;
        }
        if (parserService.isCompositionToolEmbed(embed)) {
            return 3;
        }
        if (parserService.isRaidHelperEmbed(message)) {
            return 2;
        }
        return 0;
    }

    private List<Message> retrieveMessages(TextChannel channel, int maxMessages) {
        List<Message> messages = new ArrayList<>();
        MessageHistory history = channel.getHistory();

        while (messages.size() < maxMessages) {
            int batchSize = Math.min(100, maxMessages - messages.size());
            List<Message> batch = history.retrievePast(batchSize).complete();

            if (batch.isEmpty()) {
                break;
            }

            messages.addAll(batch);

            if (batch.size() < batchSize) {
                break;
            }
        }

        return messages;
    }

    private LocalDateTime resolveRaidDate(Message message, TextChannel channel, MessageEmbed embed) {
        Optional<LocalDateTime> inferredWeeklyDate = inferWeeklyRaidDate(message, channel, embed);
        LocalDateTime explicitDate = parserService.extractDateFromEmbed(embed);

        if (explicitDate != null && !shouldPreferWeeklyInference(message, explicitDate, inferredWeeklyDate)) {
            return explicitDate;
        }

        if (inferredWeeklyDate.isPresent()) {
            return inferredWeeklyDate.get();
        }

        return explicitDate;
    }

    private Optional<LocalDateTime> inferWeeklyRaidDate(Message message, TextChannel channel, MessageEmbed embed) {
        Optional<DayOfWeek> dayOfWeek = detectRaidDay(embed.getTitle(), true);
        if (dayOfWeek.isEmpty() && !StringUtils.hasText(embed.getTitle())) {
            dayOfWeek = detectRaidDay(channel.getName(), false);
        }

        if (dayOfWeek.isEmpty()) {
            return Optional.empty();
        }

        LocalTime defaultRaidTime = getDefaultRaidTime();
        LocalDateTime now = LocalDateTime.now();
        LocalDate raidDate = nextOrSame(now.toLocalDate(), dayOfWeek.get());
        LocalDateTime inferredDate = LocalDateTime.of(raidDate, defaultRaidTime);

        if (inferredDate.isBefore(now.minusHours(1))) {
            inferredDate = inferredDate.plusWeeks(1);
        }

        return Optional.of(inferredDate);
    }

    private boolean shouldPreferWeeklyInference(Message message,
                                                LocalDateTime explicitDate,
                                                Optional<LocalDateTime> inferredWeeklyDate) {
        if (inferredWeeklyDate.isEmpty()) {
            return false;
        }

        if (parserService.isCompositionToolEmbed(message.getEmbeds().get(0))) {
            return true;
        }

        LocalDateTime messageCreatedAt = message.getTimeCreated()
                .atZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime();

        if (Duration.between(messageCreatedAt, explicitDate).abs().toMinutes() <= 5) {
            return true;
        }

        return explicitDate.isBefore(LocalDateTime.now().minusHours(1))
                && inferredWeeklyDate.get().isAfter(LocalDateTime.now().minusHours(1));
    }

    private Optional<DayOfWeek> detectRaidDay(String text, boolean requireRaidKeyword) {
        if (!StringUtils.hasText(text)) {
            return Optional.empty();
        }

        String compact = compactNormalize(text);

        if (requireRaidKeyword && !compact.contains("raid")) {
            return Optional.empty();
        }

        if (compact.contains("lundi")) {
            return Optional.of(DayOfWeek.MONDAY);
        }
        if (compact.contains("mardi")) {
            return Optional.of(DayOfWeek.TUESDAY);
        }
        if (compact.contains("mercredi")) {
            return Optional.of(DayOfWeek.WEDNESDAY);
        }
        if (compact.contains("jeudi")) {
            return Optional.of(DayOfWeek.THURSDAY);
        }
        if (compact.contains("vendredi")) {
            return Optional.of(DayOfWeek.FRIDAY);
        }
        if (compact.contains("samedi")) {
            return Optional.of(DayOfWeek.SATURDAY);
        }
        if (compact.contains("dimanche")) {
            return Optional.of(DayOfWeek.SUNDAY);
        }

        return Optional.empty();
    }

    private LocalDate nextOrSame(LocalDate referenceDate, DayOfWeek dayOfWeek) {
        int delta = dayOfWeek.getValue() - referenceDate.getDayOfWeek().getValue();
        if (delta < 0) {
            delta += 7;
        }
        return referenceDate.plusDays(delta);
    }

    private LocalTime getDefaultRaidTime() {
        String configuredTime = environment.getProperty("discord.raidhelper.default-time", "20:45");
        try {
            return LocalTime.parse(configuredTime);
        } catch (Exception exception) {
            log.warn("Heure discord.raidhelper.default-time invalide ({}), fallback 20:45", configuredTime);
            return LocalTime.of(20, 45);
        }
    }

    private String normalize(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        normalized = DIACRITICS_PATTERN.matcher(normalized).replaceAll("");
        return normalized.toLowerCase(Locale.ROOT);
    }

    private String compactNormalize(String text) {
        return normalize(text).replaceAll("[^a-z]", "");
    }

    private void logFailedEmbedOnce(String channelId, String messageId, MessageEmbed embed) {
        String key = channelId + ":" + messageId;
        if (!loggedFailedEmbeds.add(key)) {
            return;
        }

        log.warn("Diagnostic embed non parse - {}", parserService.describeEmbed(embed));
    }
}
