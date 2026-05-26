package com.origin.service.discord;

import com.origin.entity.Inscription;
import com.origin.entity.Joueur;
import com.origin.entity.Personnage;
import com.origin.entity.Raid;
import com.origin.entity.RaidInscription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordOfficerAuditService {

    private static final DateTimeFormatter RAID_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("EEEE d MMMM 'a' HH:mm", Locale.FRANCE);

    private final JDA jda;

    @Value("${discord.officer-audit.channel-id:}")
    private String officerAuditChannelId;

    public String getConfiguredChannelId() {
        return officerAuditChannelId == null ? "" : officerAuditChannelId.trim();
    }

    public String sendTestMessage() {
        String channelId = getConfiguredChannelId();
        if (channelId.isBlank()) {
            throw new IllegalStateException("Salon d'audit officier non configure.");
        }

        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            throw new IllegalStateException("Salon Discord d'audit officier introuvable: " + channelId);
        }

        channel.sendMessage("**Raid planner** - test audit officier OK.").complete();
        return "Message de test envoye dans #" + channel.getName() + ".";
    }

    public void notifySignupChange(Raid raid,
                                   Joueur joueur,
                                   Personnage personnage,
                                   DiscordCustomSignupService.SignupStatus status,
                                   String commentaire,
                                   Inscription previousSignup) {
        String previousStatus = Optional.ofNullable(previousSignup)
                .map(Inscription::getStatut)
                .map(value -> DiscordCustomSignupService.SignupStatus.fromKey(value).getKey())
                .orElse(null);

        String message = "**Raid planner** - " + formatRaid(raid) + "\n"
                + formatPlayer(joueur, personnage)
                + " " + describeSignupStatus(status, personnage, commentaire)
                + formatPreviousStatus(previousStatus);

        send(message);
    }

    public void notifySignupRemoval(Raid raid, Joueur joueur, Inscription removedSignup) {
        Personnage personnage = removedSignup != null ? removedSignup.getPersonnage() : null;
        String previousStatus = Optional.ofNullable(removedSignup)
                .map(Inscription::getStatut)
                .map(value -> DiscordCustomSignupService.SignupStatus.fromKey(value).getKey())
                .orElse(null);

        String message = "**Raid planner** - " + formatRaid(raid) + "\n"
                + formatPlayer(joueur, personnage)
                + " a retire son inscription"
                + formatPreviousStatus(previousStatus);

        send(message);
    }

    public void notifyCompositionConfirmation(Raid raid,
                                              Joueur joueur,
                                              RaidInscription.StatutInscription status) {
        String action = status == RaidInscription.StatutInscription.CONFIRME
                ? "a confirme sa presence dans la compo publiee"
                : "a annule sa presence dans la compo publiee";

        String message = "**Raid planner** - " + formatRaid(raid) + "\n"
                + formatPlayer(joueur, null)
                + " " + action;

        send(message);
    }

    private void send(String message) {
        String channelId = getConfiguredChannelId();
        if (channelId.isBlank()) {
            return;
        }

        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            log.warn("Salon Discord d'audit officier introuvable: {}", channelId);
            return;
        }

        channel.sendMessage(message).queue(
                ignored -> {
                },
                error -> log.warn("Impossible d'envoyer la notification d'audit officier: {}", error.getMessage())
        );
    }

    private String describeSignupStatus(DiscordCustomSignupService.SignupStatus status,
                                        Personnage personnage,
                                        String commentaire) {
        String characterClass = personnage != null && personnage.getClasse() != null
                ? escapeDiscord(personnage.getClasse())
                : "personnage";

        switch (status) {
            case TENTATIVE:
                return "s'est mis en tentative avec " + characterClass + formatComment(commentaire);
            case LATE:
                return "s'est mis en late avec " + characterClass;
            case BENCH:
                return "s'est mis bench avec " + characterClass;
            case ABSENCE:
                return "s'est mis absent";
            case TITULAIRE:
            default:
                return "s'est inscrit en " + characterClass;
        }
    }

    private String formatRaid(Raid raid) {
        if (raid == null) {
            return "raid inconnu";
        }

        StringBuilder builder = new StringBuilder();
        builder.append(escapeDiscord(raid.getNom()));
        if (raid.getDate() != null) {
            builder.append(" (").append(raid.getDate().format(RAID_DATE_FORMATTER)).append(")");
        }
        return builder.toString();
    }

    private String formatPlayer(Joueur joueur, Personnage personnage) {
        String playerName = Optional.ofNullable(joueur)
                .map(Joueur::getPseudoIhm)
                .filter(value -> !value.isBlank())
                .or(() -> Optional.ofNullable(joueur)
                        .map(Joueur::getServerPseudo)
                        .filter(value -> !value.isBlank()))
                .or(() -> Optional.ofNullable(joueur)
                        .map(Joueur::getPseudo)
                        .filter(value -> !value.isBlank()))
                .orElse("Joueur inconnu");

        String characterName = personnage != null && personnage.getNom() != null
                ? " (" + escapeDiscord(personnage.getNom()) + ")"
                : "";

        return "**" + escapeDiscord(playerName) + "**" + characterName;
    }

    private String formatComment(String commentaire) {
        if (commentaire == null || commentaire.isBlank()) {
            return "";
        }
        return " - raison: \"" + escapeDiscord(commentaire.trim()) + "\"";
    }

    private String formatPreviousStatus(String previousStatus) {
        if (previousStatus == null || previousStatus.isBlank()) {
            return ".";
        }
        return " (avant: " + escapeDiscord(previousStatus) + ").";
    }

    private String escapeDiscord(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("`", "\\`")
                .replace("~", "\\~")
                .replace("|", "\\|")
                .replace("@", "@\u200B");
    }
}
