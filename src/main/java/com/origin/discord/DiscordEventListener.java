package com.origin.discord;

import com.origin.entity.Raid;
import com.origin.repository.RaidRepository;
import com.origin.service.RaidInscriptionService;
import com.origin.service.RaidService;
import com.origin.service.discord.RaidDiscordScannerService;
import com.origin.service.discord.RaidHelperParserService;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
@Component
public class DiscordEventListener extends ListenerAdapter {

    private final RaidHelperParserService parserService;
    private final RaidDiscordScannerService raidScannerService;
    private final RaidInscriptionService raidInscriptionService;
    private final RaidService raidService;
    private final JDA jda;

    public DiscordEventListener(RaidHelperParserService parserService,
                                RaidDiscordScannerService raidScannerService, RaidInscriptionService raidInscriptionService,
                                RaidService raidService, JDA jda) {
        this.parserService = parserService;
        this.raidScannerService = raidScannerService;
        this.raidInscriptionService = raidInscriptionService;
        this.raidService = raidService;
        this.jda = jda;
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (!event.getAuthor().isBot()) return;

        String botName = event.getAuthor().getName();
        String channel = event.getChannel().getName();
        String raw = event.getMessage().getContentRaw();

        System.out.println("🤖 Message reçu d'un bot : " + botName);
        System.out.println("📍 Channel : #" + channel);

        // Texte brut (souvent vide pour Raid Helper)
        if (!raw.isEmpty()) {
            System.out.println("📝 Message brut : \n" + raw);
        }

        // Embeds (utilisé par Raid Helper)
        if (!event.getMessage().getEmbeds().isEmpty()) {
            MessageEmbed embed = event.getMessage().getEmbeds().get(0);
            if (parserService.isRaidHelperEmbed(event.getMessage())) {
                // 👇 Déclenche un scan du salon
                String channelId = event.getChannel().getId();
                raidScannerService.scanAndImportRaids(List.of(channelId));
            }
        }
    }

    @Override
    public void onMessageUpdate(@NotNull MessageUpdateEvent event) {
        if (!event.getAuthor().isBot()) return;

        List<MessageEmbed> embeds = event.getMessage().getEmbeds();
        if (embeds.isEmpty()) return;

        System.out.println("✏️ Message RAID HELPER modifié par : " + event.getAuthor().getName());

        if (parserService.isRaidHelperEmbed(event.getMessage())) {
            String channelId = event.getChannel().getId();
            raidScannerService.scanAndImportRaids(List.of(channelId));
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        try {
            String[] parts = event.getComponentId().split("_");
            if (parts.length != 2) return;

            String action = parts[0]; // "confirm" ou "cancel"
            Long raidId = Long.parseLong(parts[1]);
            String discordId = event.getUser().getId();

            // Récupération du raid
            Optional<Raid> raidOpt = Optional.ofNullable(raidService.getRaidById(raidId));
            if (raidOpt.isEmpty()) {
                event.reply("❌ Raid introuvable.").setEphemeral(true).queue();
                return;
            }

            Raid raid = raidOpt.get();

            // Vérification que le joueur est bien dans la compo
            boolean isInCompo = Stream.concat(
                            raid.getGroup1().stream(),
                            raid.getGroup2().stream()
                    ).map(p -> p.getJoueur().getDiscordId())
                    .filter(Objects::nonNull)
                    .anyMatch(id -> id.equals(discordId));

            if (!isInCompo) {
                event.reply("❌ Tu ne fais pas partie de la composition actuelle.").setEphemeral(true).queue();
                return;
            }

            // Appliquer l'action
            if (action.equals("confirm")) {
                raidInscriptionService.confirmParticipation(raidId, discordId);
                event.reply("✅ Tu es inscrit pour le raid !").setEphemeral(true).queue();
            } else if (action.equals("cancel")) {
                raidInscriptionService.cancelParticipation(raidId, discordId);
                event.reply("❌ Tu es désinscrit du raid.").setEphemeral(true).queue();
            }

            // Mise à jour du message Discord si existant
            if (raid.getDiscordMessageId() != null && raid.getChannelId() != null) {
                //Long chanelId = 1355602641748496394L;
                //TextChannel channel = jda.getTextChannelById(chanelId);
                TextChannel channel = jda.getTextChannelById(raid.getChannelId());

                if (channel != null) {
                    channel.retrieveMessageById(raid.getDiscordMessageId()).queue(
                            original -> {
                                // ✅ Message trouvé → mise à jour
                                Raid updatedRaid = raidService.getRaidById(raidId);
                                MessageEmbed updatedEmbed = raidService.buildTwoColumnEmbedWithConfirmations(updatedRaid);
                                original.editMessageEmbeds(updatedEmbed).queue();
                            },
                            error -> {
                                // ❌ Message supprimé → republier
                                log.warn("⚠️ Le message Discord d'origine a été supprimé. Republier un nouveau message...");

                                Raid updatedRaid = raidService.getRaidById(raidId);
                                MessageEmbed newEmbed = raidService.buildTwoColumnEmbedWithConfirmations(updatedRaid);

                                channel.sendMessageEmbeds(newEmbed).queue(newMsg -> {
                                    // ✅ Sauvegarde du nouveau messageId
                                    raid.setDiscordMessageId(Long.valueOf(newMsg.getId()));
                                    raidService.saveRaid(raid);
                                    log.info("✅ Nouveau message publié avec ID {}", newMsg.getId());
                                });
                            }
                    );
                }
            }else {
                System.out.println("⚠️ Pas d’ID de message ou de channel pour raidId " + raidId);
            }

        } catch (Exception e) {
            log.error("❌ Erreur onButtonInteraction : {}", e.getMessage(), e);
            event.reply("❌ Une erreur est survenue.").setEphemeral(true).queue();
        }
    }





}