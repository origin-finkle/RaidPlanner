package com.origin.discord;

import com.origin.entity.Raid;
import com.origin.service.RaidInscriptionService;
import com.origin.service.RaidService;
import com.origin.service.discord.DiscordCustomSignupService;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Slf4j
@Component
public class DiscordEventListener extends ListenerAdapter {

    private final RaidInscriptionService raidInscriptionService;
    private final RaidService raidService;
    private final DiscordCustomSignupService discordCustomSignupService;
    private final JDA jda;

    public DiscordEventListener(RaidInscriptionService raidInscriptionService,
                                RaidService raidService,
                                DiscordCustomSignupService discordCustomSignupService,
                                JDA jda) {
        this.raidInscriptionService = raidInscriptionService;
        this.raidService = raidService;
        this.discordCustomSignupService = discordCustomSignupService;
        this.jda = jda;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        try {
            if (event.getComponentId().startsWith("signup_")) {
                handleCustomSignupButton(event);
                return;
            }

            String[] parts = event.getComponentId().split("_");
            if (parts.length != 2) {
                return;
            }

            String action = parts[0];
            Long raidId = Long.parseLong(parts[1]);
            String discordId = event.getUser().getId();

            Optional<Raid> raidOpt = Optional.ofNullable(raidService.getRaidById(raidId));
            if (raidOpt.isEmpty()) {
                event.reply("Raid introuvable.").setEphemeral(true).queue();
                return;
            }

            Raid raid = raidOpt.get();

            boolean isInCompo = Stream.concat(raid.getGroup1().stream(), raid.getGroup2().stream())
                    .map(p -> p.getJoueur().getDiscordId())
                    .filter(Objects::nonNull)
                    .anyMatch(id -> id.equals(discordId));

            if (!isInCompo) {
                event.reply("Tu ne fais pas partie de la composition actuelle.").setEphemeral(true).queue();
                return;
            }

            if ("confirm".equals(action)) {
                raidInscriptionService.confirmParticipation(raidId, discordId);
                replyEphemeralAndDelete(event, "Tu es inscrit pour le raid !");
            } else if ("cancel".equals(action)) {
                raidInscriptionService.cancelParticipation(raidId, discordId);
                replyEphemeralAndDelete(event, "Tu es desinscrit du raid.");
            } else {
                return;
            }

            refreshClickedMessageOrFallback(event, raidId, raid);
        } catch (Exception e) {
            log.error("Erreur onButtonInteraction: {}", e.getMessage(), e);
            event.reply("Une erreur est survenue.").setEphemeral(true).queue();
        }
    }

    @Override
    public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event) {
        try {
            if (!event.getComponentId().startsWith("signup_select_")) {
                return;
            }

            String[] parts = event.getComponentId().split("_");
            if (parts.length != 5 || event.getValues().isEmpty()) {
                return;
            }

            String statusKey = parts[2];
            Long raidId = Long.parseLong(parts[3]);
            long sourceMessageId = Long.parseLong(parts[4]);
            Long personnageId = Long.parseLong(event.getValues().get(0));

            if (discordCustomSignupService.isSignupClosed(raidId)) {
                event.reply(discordCustomSignupService.getSignupClosedMessage(raidId))
                        .setEphemeral(true)
                        .queue();
                return;
            }

            if ("tentative".equalsIgnoreCase(statusKey)) {
                TextInput reasonInput = TextInput.create("reason", "Raison de la tentative", TextInputStyle.PARAGRAPH)
                        .setPlaceholder("Ex: peut-etre en retard, disponible sous reserve, reroll si besoin...")
                        .setMinLength(3)
                        .setMaxLength(120)
                        .setRequired(true)
                        .build();

                event.replyModal(Modal.create(
                                discordCustomSignupService.buildTentativeReasonModalId(raidId, sourceMessageId, personnageId),
                                "Motif de la tentative")
                        .addActionRow(reasonInput)
                        .build()).queue();
                return;
            }

            String message = discordCustomSignupService.registerSignup(
                    raidId,
                    event.getUser().getId(),
                    personnageId,
                    statusKey
            );

            replyEphemeralAndDelete(event, message);
            discordCustomSignupService.refreshSignupMessage(event.getChannel().getId(), sourceMessageId, raidId);
        } catch (Exception e) {
            log.error("Erreur onStringSelectInteraction: {}", e.getMessage(), e);
            event.reply("Une erreur est survenue.").setEphemeral(true).queue();
        }
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        try {
            if (!event.getModalId().startsWith("signup_reason_")) {
                return;
            }

            String[] parts = event.getModalId().split("_");
            if (parts.length != 5) {
                return;
            }

            Long raidId = Long.parseLong(parts[2]);
            long sourceMessageId = Long.parseLong(parts[3]);
            Long personnageId = Long.parseLong(parts[4]);
            String reason = Optional.ofNullable(event.getValue("reason"))
                    .map(value -> value.getAsString())
                    .orElse("");

            if (discordCustomSignupService.isSignupClosed(raidId)) {
                event.reply(discordCustomSignupService.getSignupClosedMessage(raidId))
                        .setEphemeral(true)
                        .queue();
                return;
            }

            String message = discordCustomSignupService.registerSignup(
                    raidId,
                    event.getUser().getId(),
                    personnageId,
                    "tentative",
                    reason
            );

            replyEphemeralAndDelete(event, message);
            discordCustomSignupService.refreshSignupMessage(event.getChannel().getId(), sourceMessageId, raidId);
        } catch (Exception e) {
            log.error("Erreur onModalInteraction: {}", e.getMessage(), e);
            event.reply("Une erreur est survenue.").setEphemeral(true).queue();
        }
    }

    private void handleCustomSignupButton(ButtonInteractionEvent event) {
        String[] parts = event.getComponentId().split("_");
        if (parts.length != 3) {
            return;
        }

        String statusKey = parts[1];
        Long raidId = Long.parseLong(parts[2]);

        if (discordCustomSignupService.isSignupClosed(raidId)) {
            event.reply(discordCustomSignupService.getSignupClosedMessage(raidId))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if ("remove".equalsIgnoreCase(statusKey)) {
            String message = discordCustomSignupService.removeSignup(raidId, event.getUser().getId());
            replyEphemeralAndDelete(event, message);
            discordCustomSignupService.refreshSignupMessage(event.getChannel().getId(), event.getMessageIdLong(), raidId);
            return;
        }

        if ("absence".equalsIgnoreCase(statusKey) || "bench".equalsIgnoreCase(statusKey)) {
            DiscordCustomSignupService.CharacterChoice preferredChoice =
                    discordCustomSignupService.getPreferredCharacterChoice(raidId, event.getUser().getId());

            String message = discordCustomSignupService.registerSignup(
                    raidId,
                    event.getUser().getId(),
                    preferredChoice.getPersonnageId(),
                    statusKey
            );

            replyEphemeralAndDelete(event, message);
            discordCustomSignupService.refreshSignupMessage(event.getChannel().getId(), event.getMessageIdLong(), raidId);
            return;
        }

        if ("tentative".equalsIgnoreCase(statusKey)) {
            DiscordCustomSignupService.CharacterChoice preferredChoice =
                    discordCustomSignupService.getPreferredCharacterChoice(raidId, event.getUser().getId());

            TextInput reasonInput = TextInput.create("reason", "Raison de la tentative", TextInputStyle.PARAGRAPH)
                    .setPlaceholder("Ex: peut-etre en retard, disponible sous reserve, reroll si besoin...")
                    .setMinLength(3)
                    .setMaxLength(120)
                    .setRequired(true)
                    .build();

            event.replyModal(Modal.create(
                            discordCustomSignupService.buildTentativeReasonModalId(
                                    raidId,
                                    event.getMessageIdLong(),
                                    preferredChoice.getPersonnageId()
                            ),
                            "Motif de la tentative")
                    .addActionRow(reasonInput)
                    .build()).queue();
            return;
        }

        List<DiscordCustomSignupService.CharacterChoice> choices = discordCustomSignupService.getCharacterChoices(
                raidId,
                event.getUser().getId()
        );

        event.reply("Choisis le personnage a utiliser pour ce statut.")
                .addActionRow(discordCustomSignupService.buildCharacterSelectMenu(
                        statusKey,
                        raidId,
                        event.getMessageIdLong(),
                        choices
                ))
                .setEphemeral(true)
                .queue();
    }

    private void refreshClickedMessageOrFallback(ButtonInteractionEvent event, Long raidId, Raid raid) {
        Message clickedMessage = event.getMessage();
        if (clickedMessage != null && clickedMessage.getAuthor().getId().equals(jda.getSelfUser().getId())) {
            Raid updatedRaid = raidService.getRaidById(raidId);
            MessageEmbed updatedEmbed = raidService.buildTwoColumnEmbedWithConfirmations(updatedRaid);
            clickedMessage.editMessageEmbeds(updatedEmbed).queue();
            return;
        }

        if (raid.getPublishedMessageId() == null || raid.getPublishedChannelId() == null) {
            log.warn("Pas d'ID de message ou de channel pour raidId {}", raidId);
            return;
        }

        TextChannel channel = jda.getTextChannelById(raid.getPublishedChannelId());
        if (channel == null) {
            log.warn("Channel Discord introuvable pour raidId {}", raidId);
            return;
        }

        channel.retrieveMessageById(raid.getPublishedMessageId()).queue(
                original -> {
                    if (!original.getAuthor().getId().equals(jda.getSelfUser().getId())) {
                        log.info("Message {} non modifie: il appartient a {} et non au bot.", original.getId(), original.getAuthor().getName());
                        return;
                    }

                    Raid updatedRaid = raidService.getRaidById(raidId);
                    MessageEmbed updatedEmbed = raidService.buildTwoColumnEmbedWithConfirmations(updatedRaid);
                    original.editMessageEmbeds(updatedEmbed).queue();
                },
                error -> log.warn("Impossible de recuperer le message de composition {} pour le raid {}.", raid.getPublishedMessageId(), raidId)
        );
    }

    private void replyEphemeralAndDelete(IReplyCallback event, String message) {
        event.reply(message)
                .setEphemeral(true)
                .queue(hook -> hook.deleteOriginal().queueAfter(4, TimeUnit.SECONDS));
    }
}
