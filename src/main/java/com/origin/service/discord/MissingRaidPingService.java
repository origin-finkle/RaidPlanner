package com.origin.service.discord;

import com.origin.dto.JoueurDTO;
import com.origin.dto.MissingRaidPingDTO;
import com.origin.entity.Joueur;
import com.origin.entity.Raid;
import com.origin.repository.JoueurRepository;
import com.origin.repository.RaidRepository;
import com.origin.service.JoueurService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MissingRaidPingService {

    private static final Pattern BOLD_NAME_PATTERN = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final String TEST_CHANNEL_ID = "1355602641748496394";

    private final RaidRepository raidRepository;
    private final RaidQueryService raidQueryService;
    private final JoueurRepository joueurRepository;
    private final JoueurService joueurService;
    private final JDA jda;

    public MissingRaidPingDTO buildMissingPing(Long raidId) {
        Raid raid = raidRepository.findById(raidId)
                .orElseThrow(() -> new IllegalArgumentException("Raid introuvable : " + raidId));

        Set<Long> respondedPlayerIds = new LinkedHashSet<>();
        List<JoueurDTO> signups = raidQueryService.getInscriptionsFromRaidHelper(raid);
        signups.stream()
                .map(JoueurDTO::getId)
                .forEach(respondedPlayerIds::add);

        respondedPlayerIds.addAll(extractAbsencePlayerIds(raid));

        List<Joueur> missingPlayers = joueurRepository.findAll().stream()
                .filter(joueur -> Boolean.TRUE.equals(joueur.getIsRaider()))
                .filter(joueur -> joueur.getDiscordId() != null && !joueur.getDiscordId().isBlank())
                .filter(joueur -> !respondedPlayerIds.contains(joueur.getId()))
                .sorted(Comparator.comparing(
                        joueur -> preferredDisplayName(joueur).toLowerCase(Locale.ROOT)
                ))
                .collect(Collectors.toList());

        if (missingPlayers.isEmpty()) {
            return new MissingRaidPingDTO(
                    "Tout le monde a deja repondu pour " + raid.getNom() + ".",
                    0,
                    List.of()
            );
        }

        String mentions = missingPlayers.stream()
                .map(joueur -> "<@" + joueur.getDiscordId() + ">")
                .collect(Collectors.joining(" "));

        String message = "Relance inscription " + raid.getNom() + " : " + mentions;
        List<String> displayNames = missingPlayers.stream()
                .map(this::preferredDisplayName)
                .collect(Collectors.toList());

        return new MissingRaidPingDTO(message, missingPlayers.size(), displayNames);
    }

    public MissingRaidPingDTO sendMissingPingToTestChannel(Long raidId) {
        MissingRaidPingDTO dto = buildMissingPing(raidId);
        if (dto.getMissingCount() <= 0) {
            return dto;
        }

        TextChannel channel = jda.getTextChannelById(TEST_CHANNEL_ID);
        if (channel == null) {
            throw new IllegalStateException("Salon de test introuvable : " + TEST_CHANNEL_ID);
        }

        channel.sendMessage(dto.getMessage()).complete();
        return new MissingRaidPingDTO(
                "Ping envoye dans le salon de test. " + dto.getMessage(),
                dto.getMissingCount(),
                dto.getMissingPlayers()
        );
    }

    public boolean sendMissingPingToRaidChannelIfNeeded(Long raidId) {
        Raid raid = raidRepository.findById(raidId)
                .orElseThrow(() -> new IllegalArgumentException("Raid introuvable : " + raidId));

        if (raid.getDiscordMessageId() == null) {
            log.info("Relance ignoree pour le raid {}: aucun message Discord source", raid.getId());
            return false;
        }

        if (raid.getDiscordMessageId().equals(raid.getLastMissingPingSourceMessageId())) {
            log.info(
                    "Relance deja envoyee pour le raid {} sur le message source {}",
                    raid.getId(),
                    raid.getDiscordMessageId()
            );
            return false;
        }

        MissingRaidPingDTO dto = buildMissingPing(raid.getId());
        if (dto.getMissingCount() <= 0) {
            log.info("Aucun non-inscrit a relancer pour le raid {}", raid.getId());
            return false;
        }

        TextChannel channel = jda.getTextChannelById(raid.getChannelId());
        if (channel == null) {
            throw new IllegalStateException("Salon du raid introuvable : " + raid.getChannelId());
        }

        channel.sendMessage(dto.getMessage()).complete();
        raid.setLastMissingPingSourceMessageId(raid.getDiscordMessageId());
        raid.setLastMissingPingAt(LocalDateTime.now());
        raidRepository.save(raid);

        log.info(
                "Relance auto envoyee pour le raid {} dans le salon {} ({} joueur(s) manquant(s))",
                raid.getId(),
                raid.getChannelId(),
                dto.getMissingCount()
        );
        return true;
    }

    private Set<Long> extractAbsencePlayerIds(Raid raid) {
        Set<Long> ids = new LinkedHashSet<>();
        if (raid.getDiscordMessageId() == null) {
            return ids;
        }

        TextChannel channel = jda.getTextChannelById(raid.getChannelId());
        if (channel == null) {
            return ids;
        }

        Message message;
        try {
            message = channel.retrieveMessageById(raid.getDiscordMessageId()).complete();
        } catch (Exception exception) {
            log.warn("Impossible de recuperer le message Discord source {} pour le raid {}", raid.getDiscordMessageId(), raid.getId());
            return ids;
        }

        if (message == null || message.getEmbeds().isEmpty()) {
            return ids;
        }

        MessageEmbed embed = message.getEmbeds().get(0);
        for (MessageEmbed.Field field : embed.getFields()) {
            String value = field.getValue();
            if (value == null) {
                continue;
            }

            if (!value.toLowerCase(Locale.ROOT).contains("absence")) {
                continue;
            }

            Matcher matcher = BOLD_NAME_PATTERN.matcher(value);
            while (matcher.find()) {
                String pseudo = raidQueryService.cleanServerPseudo(matcher.group(1));
                if (pseudo == null || pseudo.isBlank()) {
                    continue;
                }

                Joueur joueur = joueurService.findByServerPseudo(pseudo);
                if (joueur != null && joueur.getId() != null) {
                    ids.add(joueur.getId());
                }
            }
        }

        return ids;
    }

    private String preferredDisplayName(Joueur joueur) {
        if (joueur.getPseudoIhm() != null && !joueur.getPseudoIhm().isBlank()) {
            return joueur.getPseudoIhm();
        }
        if (joueur.getServerPseudo() != null && !joueur.getServerPseudo().isBlank()) {
            return joueur.getServerPseudo();
        }
        return joueur.getPseudo();
    }
}
