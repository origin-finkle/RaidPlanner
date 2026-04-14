package com.origin.service.discord;

import com.origin.dto.JoueurDTO;
import com.origin.dto.PersonnageDTO;
import com.origin.dto.RaidDiagnosticDTO;
import com.origin.dto.RaidDTO;
import com.origin.dto.RaidDayResponse;
import com.origin.dto.RaidMessageDiagnosticDTO;
import com.origin.dto.RaidSignupDiagnosticDTO;
import com.origin.entity.Inscription;
import com.origin.entity.Joueur;
import com.origin.entity.Personnage;
import com.origin.entity.Raid;
import com.origin.enumOrigin.StatutParticipation;
import com.origin.repository.InscriptionRepository;
import com.origin.repository.PersonnageRepository;
import com.origin.repository.RaidRepository;
import com.origin.service.JoueurService;
import com.origin.service.PersonnageService;
import com.origin.util.ParsedEmoji;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

@Slf4j
@Service
@RequiredArgsConstructor
public class RaidQueryService {
    private static final String MANUAL_SIGNUP_COMMENT = "MANUAL_OFFICER_ADD";

    private static final Pattern EMOJI_PATTERN = Pattern.compile("<:([\\w]+):(\\d+)>");
    private static final Pattern STATUS_NAME_PATTERN = Pattern.compile("\\*\\*(.+?)\\*\\*");

    private final JDA jda;
    private final RaidRepository raidRepository;
    private final InscriptionRepository inscriptionRepository;
    private final PersonnageRepository personnageRepository;
    private final PersonnageService personnageService;
    private final RaidHelperParserService parserService;
    private final JoueurService joueurService;

    public List<RaidDayResponse> getRaidsGroupedByDay() {
        List<Raid> allRaids = deduplicateRaidsByDay(
                raidRepository.findByDateGreaterThanEqualOrderByDateAsc(getCurrentResetWeekStart())
        );
        List<RaidDTO> raidDTOList = new ArrayList<>();

        for (Raid raid : allRaids) {
            List<JoueurDTO> joueurDTOList = getInscriptionsFromRaidHelper(raid);
            raidDTOList.add(toRaidDTO(raid, joueurDTOList));
        }

        Map<LocalDate, List<RaidDTO>> grouped = raidDTOList.stream()
                .collect(Collectors.groupingBy(raid -> raid.getHeure().toLocalDate()));

        return grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new RaidDayResponse(entry.getKey().toString(), entry.getValue()))
                .collect(Collectors.toList());
    }

    public List<Raid> getBestRaidsInRange(LocalDateTime start, LocalDateTime endExclusive) {
        return deduplicateRaidsByDay(
                raidRepository.findByDateGreaterThanEqualAndDateLessThanOrderByDateAsc(start, endExclusive)
        );
    }

    private LocalDateTime getCurrentResetWeekStart() {
        LocalDate today = LocalDate.now();
        LocalDate resetStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.WEDNESDAY));
        return LocalDateTime.of(resetStart, LocalTime.MIN);
    }

    public List<Map<String, Object>> debugChannelMessages(String channelId, int limit) {
        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            return List.of(Map.of("error", "channel_not_found", "channelId", channelId));
        }

        List<Message> messages = channel.getHistory().retrievePast(Math.max(1, Math.min(limit, 30))).complete();
        List<Map<String, Object>> debug = new ArrayList<>();

        for (Message message : messages) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("messageId", message.getId());
            item.put("createdAt", message.getTimeCreated().toString());
            item.put("author", message.getAuthor().getName());
            item.put("bot", message.getAuthor().isBot());
            item.put("contentRaw", message.getContentRaw());
            item.put("buttonUrls", message.getButtons().stream().map(Button::getUrl).collect(Collectors.toList()));

            if (message.getEmbeds().isEmpty()) {
                item.put("hasEmbed", false);
                debug.add(item);
                continue;
            }

            MessageEmbed embed = message.getEmbeds().get(0);
            item.put("hasEmbed", true);
            item.put("title", embed.getTitle());
            item.put("description", embed.getDescription());
            item.put("fieldCount", embed.getFields().size());
            item.put("parsedAsRaidHelper", parserService.isRaidHelperEmbed(message));
            item.put("isCompositionTool", parserService.isCompositionToolEmbed(embed));
            item.put("extractedNom", parserService.extractNom(embed));
            item.put("extractedDate", parserService.extractDateFromEmbed(embed));
            item.put("raidHelperId", parserService.extractRaidHelperId(message).orElse(null));
            item.put("embedDiagnostic", parserService.describeEmbed(embed));
            debug.add(item);
        }

        return debug;
    }

    public RaidDiagnosticDTO getRaidDiagnostic(Long raidId) {
        Raid raid = raidRepository.findById(raidId)
                .orElseThrow(() -> new IllegalArgumentException("Raid introuvable : " + raidId));

        ResolvedSignupSource resolvedSource = resolveSignupSource(
                raid.getChannelId(),
                raid.getDiscordMessageId(),
                raid.getNom(),
                raid.getDate()
        );

        List<RaidSignupDiagnosticDTO> liveSignups = extractSignupsFromMessage(
                        resolvedSource.channelId(),
                        resolvedSource.messageId(),
                        resolvedSource.message()
                ).stream()
                .map(this::toSignupDiagnosticDto)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<RaidSignupDiagnosticDTO> snapshotSignups = loadPersistedSignups(raid).stream()
                .map(this::toSignupDiagnosticDto)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return RaidDiagnosticDTO.builder()
                .raidId(raid.getId())
                .nom(raid.getNom())
                .date(raid.getDate())
                .storedChannelId(raid.getChannelId())
                .storedMessageId(raid.getDiscordMessageId())
                .storedRaidHelperId(raid.getRaidHelperId())
                .publishedChannelId(raid.getPublishedChannelId())
                .publishedMessageId(raid.getPublishedMessageId())
                .storedMessage(toMessageDiagnostic(loadMessage(raid.getChannelId(), raid.getDiscordMessageId())))
                .resolvedMessage(toMessageDiagnostic(resolvedSource.message()))
                .sourceChanged(!Objects.equals(raid.getChannelId(), resolvedSource.channelId())
                        || !Objects.equals(raid.getDiscordMessageId(), resolvedSource.messageId()))
                .liveSignups(liveSignups)
                .snapshotSignups(snapshotSignups)
                .liveOnlyPlayers(computeSignupDifference(liveSignups, snapshotSignups))
                .snapshotOnlyPlayers(computeSignupDifference(snapshotSignups, liveSignups))
                .build();
    }

    private List<Raid> deduplicateRaidsByDay(List<Raid> raids) {
        Map<LocalDate, Raid> bestRaidByDay = new HashMap<>();

        for (Raid raid : raids) {
            LocalDate day = raid.getDate().toLocalDate();
            Raid current = bestRaidByDay.get(day);
            if (current == null || compareRaidPriority(raid, current) < 0) {
                bestRaidByDay.put(day, raid);
            }
        }

        return bestRaidByDay.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }

    private int compareRaidPriority(Raid left, Raid right) {
        int eventComparison = Boolean.compare(isCanonicalWeeklyRaidTitle(right.getNom()), isCanonicalWeeklyRaidTitle(left.getNom()));
        if (eventComparison != 0) {
            return eventComparison;
        }

        int messageComparison = compareNullableLongDesc(left.getDiscordMessageId(), right.getDiscordMessageId());
        if (messageComparison != 0) {
            return messageComparison;
        }

        return compareNullableLongDesc(left.getId(), right.getId());
    }

    private int compareNullableLongDesc(Long left, Long right) {
        long safeLeft = left != null ? left : Long.MIN_VALUE;
        long safeRight = right != null ? right : Long.MIN_VALUE;
        return Long.compare(safeRight, safeLeft);
    }

    private boolean isCanonicalWeeklyRaidTitle(String nom) {
        if (nom == null) {
            return false;
        }

        String normalized = nom.strip().toLowerCase(Locale.ROOT);
        return normalized.startsWith("raid du ")
                || normalized.startsWith("raid de ")
                || normalized.startsWith("raid d'");
    }

    private RaidDTO toRaidDTO(Raid raid, List<JoueurDTO> joueurDTOList) {
        return new RaidDTO(
                raid.getId(),
                raid.getNom(),
                raid.getDate(),
                joueurDTOList,
                raid.getGroup1().stream().map(personnageService::toDTO).collect(Collectors.toList()),
                raid.getGroup2().stream().map(personnageService::toDTO).collect(Collectors.toList()),
                raid.getCompositionStatus(),
                raid.isCompositionLocked(),
                raid.getLastPublishedAt()
        );
    }

    public List<JoueurDTO> getInscriptionsFromRaidHelper(String channelId, Long messageId) {
        return getInscriptionsFromRaidHelper(channelId, messageId, null, null);
    }

    @Transactional
    public List<JoueurDTO> getInscriptionsFromRaidHelper(Raid raid) {
        if (raid == null) {
            return List.of();
        }

        String originalChannelId = raid.getChannelId();
        Long originalMessageId = raid.getDiscordMessageId();

        ResolvedSignupSource resolvedSource = resolveSignupSource(
                raid.getChannelId(),
                raid.getDiscordMessageId(),
                raid.getNom(),
                raid.getDate()
        );

        boolean sourceChanged = !Objects.equals(originalChannelId, resolvedSource.channelId())
                || !Objects.equals(originalMessageId, resolvedSource.messageId());

        if (canWriteInCurrentTransaction()) {
            persistResolvedSignupSource(raid, resolvedSource);
        }
        List<JoueurDTO> signups = extractSignupsFromMessage(raid.getChannelId(), raid.getDiscordMessageId(), resolvedSource.message());
        if (!signups.isEmpty()) {
            if (canWriteInCurrentTransaction()) {
                persistSignupSnapshot(raid, signups);
            }
            return mergeWithManualSignups(raid, signups);
        }

        if (sourceChanged) {
            log.warn("Source Discord mise a jour pour le raid {} mais aucun inscrit n'a pu etre extrait depuis le message {}. Snapshot precedent ignore.",
                    raid.getId(),
                    resolvedSource.messageId());
            if (canWriteInCurrentTransaction()) {
                inscriptionRepository.deleteByRaidId(raid.getId());
            }
            return List.of();
        }

        return loadPersistedSignups(raid);
    }

    private List<JoueurDTO> mergeWithManualSignups(Raid raid, List<JoueurDTO> liveSignups) {
        if (raid == null || raid.getId() == null) {
            return liveSignups;
        }

        Map<Long, JoueurDTO> mergedByPlayerId = new LinkedHashMap<>();
        for (JoueurDTO signup : liveSignups) {
            if (signup != null && signup.getId() != null) {
                mergedByPlayerId.put(signup.getId(), signup);
            }
        }

        inscriptionRepository.findDetailedByRaidIdOrderByIdAsc(raid.getId()).stream()
                .filter(this::isManualSignup)
                .map(this::toSignupDto)
                .filter(Objects::nonNull)
                .forEach(signup -> {
                    if (signup.getId() != null) {
                        mergedByPlayerId.putIfAbsent(signup.getId(), signup);
                    }
                });

        return new ArrayList<>(mergedByPlayerId.values());
    }

    private List<JoueurDTO> getInscriptionsFromRaidHelper(String channelId,
                                                          Long messageId,
                                                          String expectedNom,
                                                          LocalDateTime expectedDate) {
        return extractSignupsFromMessage(channelId, messageId, resolveSignupSource(channelId, messageId, expectedNom, expectedDate).message());
    }

    private List<JoueurDTO> extractSignupsFromMessage(String channelId, Long messageId, Message message) {
        if (messageId == null) {
            log.warn("Aucun message Discord source pour le salon {}", channelId);
            return List.of();
        }

        if (message == null) {
            return List.of();
        }
        if (message.getEmbeds().isEmpty()) {
            log.warn("No embed found in message {}", messageId);
            return List.of();
        }

        MessageEmbed embed = message.getEmbeds().get(0);
        List<MessageEmbed.Field> fields = embed.getFields();

        Map<String, StatutParticipation> pseudoToStatus = new HashMap<>();
        for (MessageEmbed.Field field : fields) {
            String raw = field.getValue();
            if (raw == null) {
                continue;
            }

            String rawLower = raw.toLowerCase(Locale.ROOT);
            if (rawLower.startsWith("<:tentative:")) {
                extractStatus(pseudoToStatus, rawLower, StatutParticipation.TENTATIVE);
            } else if (rawLower.startsWith("<:bench:")) {
                extractStatus(pseudoToStatus, rawLower, StatutParticipation.BENCH);
            } else if (rawLower.startsWith("<:late:")) {
                extractStatus(pseudoToStatus, rawLower, StatutParticipation.LATE);
            }
        }

        List<JoueurDTO> joueurDTOList = new ArrayList<>();
        Set<String> addedPlayers = new HashSet<>();

        for (MessageEmbed.Field field : fields) {
            String value = field.getValue();
            if (value == null) {
                continue;
            }

            String[] lines = value.split("\\n");
            for (String line : lines) {
                Optional<JoueurDTO> joueurDTO = extractJoueurFromLine(line, pseudoToStatus);
                if (joueurDTO.isEmpty()) {
                    continue;
                }

                String dedupeKey = cleanServerPseudo(joueurDTO.get().getServerPseudo());
                if (!addedPlayers.add(dedupeKey)) {
                    continue;
                }

                joueurDTOList.add(joueurDTO.get());
            }
        }

        for (Map.Entry<String, StatutParticipation> entry : pseudoToStatus.entrySet()) {
            String cleaned = cleanServerPseudo(entry.getKey());
            if (cleaned == null || addedPlayers.contains(cleaned)) {
                continue;
            }

            JoueurDTO joueurDTO = extractJoueurStatus(entry.getKey(), entry.getValue());
            if (joueurDTO != null) {
                joueurDTOList.add(joueurDTO);
                addedPlayers.add(cleaned);
            }
        }

        return joueurDTOList;
    }

    private void persistSignupSnapshot(Raid raid, List<JoueurDTO> signups) {
        if (raid.getId() == null) {
            return;
        }

        List<Inscription> preservedManualSignups = inscriptionRepository.findDetailedByRaidIdOrderByIdAsc(raid.getId()).stream()
                .filter(this::isManualSignup)
                .collect(Collectors.toList());

        inscriptionRepository.deleteByRaidId(raid.getId());

        List<Inscription> snapshot = signups.stream()
                .map(this::toSnapshotInscription)
                .filter(Objects::nonNull)
                .map(inscription -> {
                    inscription.setRaid(raid);
                    return inscription;
                })
                .collect(Collectors.toList());

        if (!snapshot.isEmpty()) {
            inscriptionRepository.saveAll(snapshot);
        }

        if (!preservedManualSignups.isEmpty()) {
            Set<Long> livePlayerIds = snapshot.stream()
                    .map(Inscription::getPersonnage)
                    .filter(Objects::nonNull)
                    .map(Personnage::getJoueur)
                    .filter(Objects::nonNull)
                    .map(Joueur::getId)
                    .collect(Collectors.toSet());

            List<Inscription> manualToRestore = preservedManualSignups.stream()
                    .filter(inscription -> inscription.getPersonnage() != null && inscription.getPersonnage().getJoueur() != null)
                    .filter(inscription -> !livePlayerIds.contains(inscription.getPersonnage().getJoueur().getId()))
                    .map(inscription -> Inscription.builder()
                            .raid(raid)
                            .personnage(inscription.getPersonnage())
                            .statut(Optional.ofNullable(inscription.getStatut()).orElse(StatutParticipation.TITULAIRE.name()))
                            .commentaire(MANUAL_SIGNUP_COMMENT)
                            .build())
                    .collect(Collectors.toList());

            if (!manualToRestore.isEmpty()) {
                inscriptionRepository.saveAll(manualToRestore);
            }
        }
    }

    private Inscription toSnapshotInscription(JoueurDTO joueurDTO) {
        PersonnageDTO personnageDTO = joueurDTO.getPersonnageMain();
        if (personnageDTO == null || personnageDTO.getId() == null) {
            return null;
        }

        Optional<Personnage> personnageOpt = personnageRepository.findById(personnageDTO.getId());
        if (personnageOpt.isEmpty()) {
            return null;
        }

        return Inscription.builder()
                .personnage(personnageOpt.get())
                .statut(Optional.ofNullable(joueurDTO.getStatutParticipation())
                        .orElse(StatutParticipation.TITULAIRE)
                        .name())
                .commentaire(joueurDTO.getCommentaireInscription())
                .build();
    }

    private List<JoueurDTO> loadPersistedSignups(Raid raid) {
        if (raid.getId() == null) {
            return List.of();
        }

        return inscriptionRepository.findDetailedByRaidIdOrderByIdAsc(raid.getId()).stream()
                .map(this::toSignupDto)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private boolean isManualSignup(Inscription inscription) {
        return inscription != null && MANUAL_SIGNUP_COMMENT.equals(inscription.getCommentaire());
    }

    private RaidSignupDiagnosticDTO toSignupDiagnosticDto(JoueurDTO joueurDTO) {
        if (joueurDTO == null || joueurDTO.getPersonnageMain() == null) {
            return null;
        }

        PersonnageDTO personnage = joueurDTO.getPersonnageMain();
        return RaidSignupDiagnosticDTO.builder()
                .joueurId(joueurDTO.getId())
                .pseudo(joueurDTO.getPseudo())
                .pseudoIhm(joueurDTO.getPseudoIhm())
                .serverPseudo(joueurDTO.getServerPseudo())
                .personnageId(personnage.getId())
                .personnageNom(personnage.getNom())
                .classe(personnage.getClasse())
                .specialisation(personnage.getSpecialisation())
                .role(personnage.getRole())
                .main(personnage.isMain())
                .statutParticipation(joueurDTO.getStatutParticipation())
                .build();
    }

    private List<String> computeSignupDifference(List<RaidSignupDiagnosticDTO> base, List<RaidSignupDiagnosticDTO> comparison) {
        Set<String> comparisonKeys = comparison.stream()
                .map(this::signupDisplayKey)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return base.stream()
                .map(this::signupDisplayKey)
                .filter(Objects::nonNull)
                .filter(key -> !comparisonKeys.contains(key))
                .distinct()
                .collect(Collectors.toList());
    }

    private String signupDisplayKey(RaidSignupDiagnosticDTO signup) {
        if (signup == null) {
            return null;
        }

        String characterName = Optional.ofNullable(signup.getPersonnageNom()).orElse("").trim();
        String fallback = Optional.ofNullable(signup.getPseudoIhm()).orElse(signup.getServerPseudo());
        String displayName = !characterName.isBlank() ? characterName : fallback;
        if (displayName == null || displayName.isBlank()) {
            return null;
        }

        return displayName;
    }

    private RaidMessageDiagnosticDTO toMessageDiagnostic(Message message) {
        if (message == null) {
            return null;
        }

        MessageEmbed embed = message.getEmbeds().isEmpty() ? null : message.getEmbeds().get(0);
        Message channelMessage = message;
        String channelId = channelMessage.getChannel().getId();
        String channelName = channelMessage.getChannel().getName();
        String guildId = channelMessage.getGuild() != null ? channelMessage.getGuild().getId() : null;
        Optional<RaidHelperParserService.DiscordMessageLink> linkedMessage = parserService.extractLinkedDiscordMessage(message);

        return RaidMessageDiagnosticDTO.builder()
                .channelId(channelId)
                .channelName(channelName)
                .guildId(guildId)
                .messageId(channelMessage.getIdLong())
                .url(buildDiscordMessageUrl(guildId, channelId, channelMessage.getIdLong()))
                .author(channelMessage.getAuthor().getName())
                .bot(channelMessage.getAuthor().isBot())
                .createdAt(channelMessage.getTimeCreated().toLocalDateTime())
                .title(embed != null ? embed.getTitle() : null)
                .description(embed != null ? embed.getDescription() : null)
                .parsedAsRaidHelper(parserService.isRaidHelperEmbed(message))
                .compositionTool(embed != null && parserService.isCompositionToolEmbed(embed))
                .placeholderSignup(embed != null && parserService.isPlaceholderSignupEmbed(embed))
                .extractedNom(embed != null ? parserService.extractNom(embed) : null)
                .extractedDate(embed != null ? parserService.extractDateFromEmbed(embed) : null)
                .raidHelperId(parserService.extractRaidHelperId(message).orElse(null))
                .signupLineCount(embed != null ? countLikelySignupLines(embed) : 0)
                .linkedChannelId(linkedMessage.map(RaidHelperParserService.DiscordMessageLink::getChannelId).orElse(null))
                .linkedMessageId(linkedMessage.map(link -> Long.valueOf(link.getMessageId())).orElse(null))
                .build();
    }

    private String buildDiscordMessageUrl(String guildId, String channelId, Long messageId) {
        if (guildId == null || channelId == null || messageId == null) {
            return null;
        }

        return "https://discord.com/channels/" + guildId + "/" + channelId + "/" + messageId;
    }

    private Message loadMessage(String channelId, Long messageId) {
        if (channelId == null || messageId == null) {
            return null;
        }

        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            return null;
        }

        try {
            return channel.retrieveMessageById(messageId).complete();
        } catch (Exception exception) {
            log.debug("Impossible de charger le message {} du salon {}: {}", messageId, channelId, exception.getMessage());
            return null;
        }
    }

    private JoueurDTO toSignupDto(Inscription inscription) {
        if (inscription.getPersonnage() == null || inscription.getPersonnage().getJoueur() == null) {
            return null;
        }

        JoueurDTO joueurDTO = joueurService.toDTO(inscription.getPersonnage().getJoueur());
        StatutParticipation statut = parseStatut(inscription.getStatut());
        return new JoueurDTO(
                joueurDTO.getId(),
                joueurDTO.getPseudo(),
                joueurDTO.getPseudoIhm(),
                joueurDTO.getServerPseudo(),
                joueurDTO.getPersonnageMain(),
                joueurDTO.getRerolls(),
                joueurDTO.isRaider(),
                statut,
                inscription.getCommentaire()
        );
    }

    private StatutParticipation parseStatut(String value) {
        if (value == null || value.isBlank()) {
            return StatutParticipation.TITULAIRE;
        }

        try {
            return StatutParticipation.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return StatutParticipation.TITULAIRE;
        }
    }

    private ResolvedSignupSource resolveSignupSource(String channelId,
                                                     Long messageId,
                                                     String expectedNom,
                                                     LocalDateTime expectedDate) {
        if (messageId == null) {
            return new ResolvedSignupSource(channelId, messageId, null);
        }

        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            log.warn("Channel not found: {}", channelId);
            return new ResolvedSignupSource(channelId, messageId, null);
        }

        Message message;
        try {
            message = channel.retrieveMessageById(messageId).complete();
        } catch (Exception exception) {
            logMessageRetrievalFailure(channelId, messageId, exception);
            return new ResolvedSignupSource(channelId, messageId, null);
        }

        if (message == null) {
            return new ResolvedSignupSource(channelId, messageId, null);
        }

        Message resolvedMessage = resolveBestSignupSourceMessage(message, expectedNom, expectedDate);
        String resolvedChannelId = resolvedMessage.getChannel().getId();
        Long resolvedMessageId = resolvedMessage.getIdLong();
        return new ResolvedSignupSource(resolvedChannelId, resolvedMessageId, resolvedMessage);
    }

    private void persistResolvedSignupSource(Raid raid, ResolvedSignupSource resolvedSource) {
        if (raid == null || resolvedSource == null || resolvedSource.message() == null) {
            return;
        }

        boolean changed = false;
        if (!Objects.equals(raid.getChannelId(), resolvedSource.channelId())) {
            raid.setChannelId(resolvedSource.channelId());
            changed = true;
        }
        if (!Objects.equals(raid.getDiscordMessageId(), resolvedSource.messageId())) {
            raid.setDiscordMessageId(resolvedSource.messageId());
            changed = true;
        }

        String resolvedRaidHelperId = parserService.extractRaidHelperId(resolvedSource.message()).orElse(null);
        if (resolvedRaidHelperId != null && !Objects.equals(raid.getRaidHelperId(), resolvedRaidHelperId)) {
            Optional<Raid> owner = raidRepository.findByRaidHelperId(resolvedRaidHelperId);
            if (owner.isEmpty() || Objects.equals(owner.get().getId(), raid.getId())) {
                raid.setRaidHelperId(resolvedRaidHelperId);
                changed = true;
            } else {
                log.debug(
                        "RaidHelperId {} deja attache au raid {}, on conserve seulement le cache message/channel pour le raid {}",
                        resolvedRaidHelperId,
                        owner.get().getId(),
                        raid.getId()
                );
            }
        }

        if (changed) {
            raidRepository.save(raid);
        }
    }

    private Message resolveBestSignupSourceMessage(Message initialMessage,
                                                   String expectedNom,
                                                   LocalDateTime expectedDate) {
        if (initialMessage.getEmbeds().isEmpty()) {
            return initialMessage;
        }

        MessageEmbed initialEmbed = initialMessage.getEmbeds().get(0);
        String targetNom = expectedNom != null ? expectedNom : parserService.extractNom(initialEmbed);
        LocalDateTime targetDate = expectedDate != null ? expectedDate : parserService.extractDateFromEmbed(initialEmbed);
        Optional<Message> linkedMessage = resolveLinkedDiscordMessage(initialMessage);

        List<TextChannel> candidateChannels = new ArrayList<>();
        TextChannel initialChannel = initialMessage.getChannel().asTextChannel();
        if (initialChannel != null) {
            candidateChannels.add(initialChannel);
        }
        linkedMessage.map(message -> message.getChannel().asTextChannel())
                .filter(Objects::nonNull)
                .filter(channel -> candidateChannels.stream().noneMatch(existing -> existing.getId().equals(channel.getId())))
                .ifPresent(candidateChannels::add);

        List<Message> candidates = new ArrayList<>();
        candidates.add(initialMessage);
        linkedMessage.ifPresent(candidates::add);

        for (TextChannel candidateChannel : candidateChannels) {
            try {
                candidates.addAll(candidateChannel.getHistory().retrievePast(100).complete());
            } catch (Exception exception) {
                log.debug("Impossible de charger l'historique du salon {}: {}", candidateChannel.getId(), exception.getMessage());
            }
        }

        return candidates.stream()
                .filter(Objects::nonNull)
                .filter(candidate -> !candidate.getEmbeds().isEmpty())
                .filter(parserService::isRaidHelperEmbed)
                .filter(candidate -> matchesExpectedRaid(candidate, targetNom, targetDate))
                .max(Comparator
                        .comparingInt(this::scoreSignupSourceMessage)
                        .thenComparingLong(Message::getIdLong))
                .orElse(initialMessage);
    }

    private Optional<Message> resolveLinkedDiscordMessage(Message message) {
        return parserService.extractLinkedDiscordMessage(message)
                .flatMap(link -> {
                    TextChannel targetChannel = jda.getTextChannelById(link.getChannelId());
                    if (targetChannel == null) {
                        return Optional.empty();
                    }

                    try {
                        return Optional.ofNullable(targetChannel.retrieveMessageById(link.getMessageId()).complete());
                    } catch (Exception exception) {
                        log.debug("Impossible de suivre le lien Discord vers {} dans le salon {}: {}",
                                link.getMessageId(),
                                link.getChannelId(),
                                exception.getMessage());
                        return Optional.empty();
                    }
                });
    }

    private void logMessageRetrievalFailure(String channelId, Long messageId, Exception exception) {
        String error = exception.getMessage() == null ? "" : exception.getMessage();
        String context = "message " + messageId + " in channel " + channelId;

        if (error.contains("10008") || error.toLowerCase(Locale.ROOT).contains("unknown message")) {
            if (canWriteInCurrentTransaction()) {
                cleanupUnknownMessageReferences(channelId, messageId);
            }
            log.debug("Message Discord introuvable pour {}.", context);
            return;
        }

        if (error.contains("503")) {
            log.warn("Discord indisponible temporairement pour {}: {}", context, error);
            return;
        }

        log.error("Impossible de recuperer {}: {}", context, error);
    }

    private boolean canWriteInCurrentTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            return true;
        }
        return !TransactionSynchronizationManager.isCurrentTransactionReadOnly();
    }

    @Transactional
    protected void cleanupUnknownMessageReferences(String channelId, Long messageId) {
        boolean changed = false;

        for (Raid raid : raidRepository.findAllByDiscordMessageId(messageId)) {
            if (Objects.equals(raid.getChannelId(), channelId) && Objects.equals(raid.getDiscordMessageId(), messageId)) {
                raid.setDiscordMessageId(null);
                if (Objects.equals(raid.getLastMissingPingSourceMessageId(), messageId)) {
                    raid.setLastMissingPingSourceMessageId(null);
                }
                if (raid.getRaidHelperId() != null) {
                    raid.setRaidHelperId(null);
                }
                raidRepository.save(raid);
                changed = true;
            }
        }

        for (Raid raid : raidRepository.findAllByPublishedMessageId(messageId)) {
            if (Objects.equals(raid.getPublishedChannelId(), channelId) && Objects.equals(raid.getPublishedMessageId(), messageId)) {
                raid.setPublishedMessageId(null);
                raid.setPublishedChannelId(null);
                raidRepository.save(raid);
                changed = true;
            }
        }

        for (Raid raid : raidRepository.findAllByLastMissingPingSourceMessageId(messageId)) {
            if (Objects.equals(raid.getLastMissingPingSourceMessageId(), messageId)) {
                raid.setLastMissingPingSourceMessageId(null);
                raidRepository.save(raid);
                changed = true;
            }
        }

        if (changed) {
            log.info("References Discord obsoletes nettoyees pour le message {} du salon {}", messageId, channelId);
        }
    }

    private boolean matchesExpectedRaid(Message message, String expectedNom, LocalDateTime expectedDate) {
        if (message.getEmbeds().isEmpty()) {
            return false;
        }

        MessageEmbed embed = message.getEmbeds().get(0);
        String extractedNom = parserService.extractNom(embed);
        if (expectedNom != null && !normalizeCharacterKey(extractedNom).equals(normalizeCharacterKey(expectedNom))) {
            return false;
        }

        if (expectedDate == null) {
            return true;
        }

        LocalDateTime explicitDate = parserService.extractDateFromEmbed(embed);
        if (explicitDate != null) {
            return explicitDate.toLocalDate().equals(expectedDate.toLocalDate());
        }

        return !parserService.isPlaceholderSignupEmbed(embed);
    }

    private int scoreSignupSourceMessage(Message message) {
        if (message.getEmbeds().isEmpty()) {
            return 0;
        }

        MessageEmbed embed = message.getEmbeds().get(0);
        if (parserService.isPlaceholderSignupEmbed(embed)) {
            return 0;
        }

        int score = 0;
        if (parserService.extractDateFromEmbed(embed) != null) {
            score += 100;
        }
        if (parserService.isCompositionToolEmbed(embed)) {
            score += 20;
        }

        score += Math.min(50, countLikelySignupLines(embed) * 5);
        return score;
    }

    private int countLikelySignupLines(MessageEmbed embed) {
        int count = 0;

        for (MessageEmbed.Field field : embed.getFields()) {
            String value = field.getValue();
            if (value == null) {
                continue;
            }

            for (String line : value.split("\\n")) {
                if (extractPseudoFromLine(line) != null && extractPlayableEmojiId(line).isPresent()) {
                    count++;
                }
            }
        }

        return count;
    }

    private static class ResolvedSignupSource {
        private final String channelId;
        private final Long messageId;
        private final Message message;

        private ResolvedSignupSource(String channelId, Long messageId, Message message) {
            this.channelId = channelId;
            this.messageId = messageId;
            this.message = message;
        }

        private String channelId() {
            return channelId;
        }

        private Long messageId() {
            return messageId;
        }

        private Message message() {
            return message;
        }
    }

    public String cleanServerPseudo(String input) {
        if (input == null) {
            return null;
        }
        return input.replaceAll("\\s+", "").replaceAll("[^\\p{ASCII}]", "").trim();
    }

    private Optional<JoueurDTO> extractJoueurFromLine(String line, Map<String, StatutParticipation> pseudoToStatus) {
        String pseudo = extractPseudoFromLine(line);
        if (pseudo == null) {
            return Optional.empty();
        }

        String cleaned = cleanServerPseudo(pseudo);
        if (cleaned == null || cleaned.isBlank()) {
            return Optional.empty();
        }

        Joueur joueur = joueurService.findByServerPseudo(cleaned);
        Optional<String> emojiIdOpt = extractPlayableEmojiId(line);
        ParsedEmoji parsedEmoji = emojiIdOpt.map(id -> new ParsedEmoji().parseEmoji(id)).orElse(null);
        List<Personnage> existingCharacters = joueur != null ? personnageRepository.findByJoueurId(joueur.getId()) : List.of();
        Personnage matchingCharacter = findMatchingExistingCharacter(existingCharacters, parsedEmoji, pseudo, cleaned);

        if (joueur == null && parsedEmoji == null) {
            return Optional.empty();
        }

        if (joueur == null) {
            log.info("pseudo qui pose pb : {}", cleaned);
            return Optional.empty();
        }

        if (joueur.getMainCharacter() == null && matchingCharacter != null) {
            joueur = joueurService.createWithMainCharacter(joueur, matchingCharacter);
        }

        if (joueur.getMainCharacter() == null) {
            if (parsedEmoji == null || parsedEmoji.classe == null || "Inconnue".equalsIgnoreCase(parsedEmoji.classe)) {
                return Optional.empty();
            }

            Personnage p = Personnage.builder()
                    .nom(cleaned)
                    .classe(parsedEmoji.classe)
                    .role(parsedEmoji.role)
                    .specialisation(parsedEmoji.specialisation)
                    .main(true)
                    .joueur(joueur)
                    .build();
            p = personnageService.save(p);
            joueur = joueurService.createWithMainCharacter(joueur, p);

            log.info("Joueur auto-cree : {} [{} - {} - {}]", pseudo, parsedEmoji.classe, parsedEmoji.role, parsedEmoji.specialisation);
        }

        StatutParticipation statut = pseudoToStatus.getOrDefault(cleaned, StatutParticipation.TITULAIRE);
        JoueurDTO joueurDTO = joueurService.toDTO(joueur);

        return Optional.of(new JoueurDTO(
                joueurDTO.getId(),
                joueurDTO.getPseudo(),
                joueurDTO.getPseudoIhm(),
                joueurDTO.getServerPseudo(),
                joueurDTO.getPersonnageMain(),
                joueurDTO.getRerolls(),
                joueurDTO.isRaider(),
                statut,
                null
        ));
    }

    private Optional<String> extractPlayableEmojiId(String line) {
        Matcher matcher = EMOJI_PATTERN.matcher(line);
        ParsedEmoji parser = new ParsedEmoji();

        while (matcher.find()) {
            String emojiId = matcher.group(2);
            ParsedEmoji parsedEmoji = parser.parseEmoji(emojiId);
            if (!"Inconnue".equalsIgnoreCase(parsedEmoji.classe)) {
                return Optional.of(emojiId);
            }
        }

        return Optional.empty();
    }

    private String extractPseudoFromLine(String line) {
        String sanitized = line
                .replaceAll("<:[^:]+:\\d+>", " ")
                .replace("**", " ")
                .replace("`", " ")
                .replace("\u2022", " ")
                .trim();

        sanitized = sanitized.replaceAll("\\s+", " ").trim();
        sanitized = sanitized.replaceAll("^[\\p{So}\\p{Cntrl}\\s-]+", "").trim();
        sanitized = sanitized.replaceAll("^\\d+[.)-]?\\s*", "").trim();

        if (sanitized.isBlank()) {
            return null;
        }

        String lower = sanitized.toLowerCase(Locale.ROOT);
        if (lower.startsWith("group ")
                || lower.startsWith("sent by ")
                || lower.startsWith("absence")
                || lower.contains(",")
                || lower.contains(" melee ")
                || lower.contains(" ranged ")
                || lower.contains(" healers ")
                || "-".equals(sanitized)) {
            return null;
        }

        return sanitized;
    }

    private Personnage findMatchingExistingCharacter(List<Personnage> characters,
                                                     ParsedEmoji parsedEmoji,
                                                     String rawPseudo,
                                                     String cleanedPseudo) {
        if (characters == null || characters.isEmpty()) {
            return null;
        }

        String normalizedRawPseudo = normalizeCharacterKey(rawPseudo);
        String normalizedCleanedPseudo = normalizeCharacterKey(cleanedPseudo);

        Optional<Personnage> byName = characters.stream()
                .filter(character -> {
                    String normalizedCharacterName = normalizeCharacterKey(character.getNom());
                    return normalizedCharacterName.equals(normalizedRawPseudo)
                            || normalizedCharacterName.equals(normalizedCleanedPseudo);
                })
                .findFirst();
        if (byName.isPresent()) {
            return byName.get();
        }

        if (parsedEmoji == null) {
            return null;
        }

        String normalizedClass = normalizeCharacterKey(parsedEmoji.classe);
        String normalizedSpec = normalizeCharacterKey(parsedEmoji.specialisation);

        return characters.stream()
                .filter(character -> normalizeCharacterKey(character.getClasse()).equals(normalizedClass))
                .filter(character -> normalizeCharacterKey(character.getSpecialisation()).equals(normalizedSpec))
                .findFirst()
                .orElse(null);
    }

    private String normalizeCharacterKey(String value) {
        if (value == null) {
            return "";
        }

        return value
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace("é", "e")
                .replace("è", "e")
                .replace("ê", "e")
                .replace("à", "a")
                .replace("ù", "u")
                .replace("î", "i")
                .replace("ï", "i")
                .replace("ô", "o")
                .replace("ö", "o")
                .replace("û", "u")
                .replace("ü", "u")
                .replaceAll("[^a-z0-9]", "");
    }

    private Optional<String> extractEmojiId(String line) {
        Matcher matcher = EMOJI_PATTERN.matcher(line);
        if (matcher.find()) {
            return Optional.of(matcher.group(2));
        }
        return Optional.empty();
    }

    private void extractStatus(Map<String, StatutParticipation> mapStatus, String raidHelperString, StatutParticipation statutParticipation) {
        int indexAbsence = raidHelperString.indexOf("absence");
        if (indexAbsence != -1) {
            raidHelperString = raidHelperString.substring(0, indexAbsence);
        }

        Matcher matcher = STATUS_NAME_PATTERN.matcher(raidHelperString);
        while (matcher.find()) {
            mapStatus.put(cleanServerPseudo(matcher.group(1).trim()), statutParticipation);
        }
    }

    private JoueurDTO extractJoueurStatus(String pseudo, StatutParticipation statutParticipation) {
        String cleaned = cleanServerPseudo(pseudo);
        Joueur joueur = joueurService.findByServerPseudo(cleaned);
        if (joueur == null) {
            return null;
        }

        List<Personnage> rerolls = personnageService.gerRerolls(joueur.getId());

        return new JoueurDTO(
                joueur.getId(),
                joueur.getPseudo(),
                joueur.getPseudoIhm(),
                joueur.getServerPseudo(),
                personnageService.toDTO(joueur.getMainCharacter()),
                rerolls.stream().map(personnageService::toDTO).collect(Collectors.toList()),
                Boolean.TRUE.equals(joueur.getIsRaider()),
                statutParticipation,
                null
        );
    }
}
