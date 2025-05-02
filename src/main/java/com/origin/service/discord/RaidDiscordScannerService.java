package com.origin.service.discord;

import com.origin.entity.Raid;
import com.origin.repository.RaidRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RaidDiscordScannerService {

    private JDA jda;
    private final RaidRepository raidRepository;
    private final RaidHelperParserService parserService;

    @Autowired
    public void setJda(@Lazy JDA jda) {
        this.jda = jda;
    }

    public void scanAndImportRaids(List<String> channelIds) {
        for (String channelId : channelIds) {
            TextChannel channel = jda.getTextChannelById(channelId);
            if (channel == null) continue;

            List<Message> messages = channel.getHistory().retrievePast(50).complete();

            for (Message message : messages) {
                if (!message.getAuthor().isBot()) continue;
                if (message.getEmbeds().isEmpty()) continue;

                MessageEmbed embed = message.getEmbeds().get(0);
                if (!parserService.isRaidHelperEmbed(message)) {
                    log.debug("Embed ignoré : {}", embed.getTitle());
                    continue;
                }

                String nom = parserService.extractNom(embed);
                LocalDateTime date = parserService.extractDateFromEmbed(embed);
                Optional<String> idOpt = parserService.extractRaidHelperId(embed);

                if (nom == null || date == null) {
                    log.warn("Impossible d'extraire nom/date depuis l'embed : {}", embed.getTitle());
                    continue;
                }

                if (raidRepository.existsByNomAndDate(nom, date)) {
                    log.debug("Raid déjà existant (nom/date)");
                    continue;
                }

                if (idOpt.isPresent() && raidRepository.existsByRaidHelperId(idOpt.get())) {
                    log.debug("Raid déjà existant (raidHelperId)");
                    continue;
                }

                Raid raid = Raid.builder()
                        .nom(nom)
                        .date(date)
                        .channelId(channelId)
                        .raidHelperId(idOpt.orElse(null))
                        .build();

                log.info("Raid détecté - nom: {}, date: {}", raid.getNom(), raid.getDate());
                raidRepository.save(raid);
            }
        }
    }
}