package com.origin.service.discord;

import com.origin.dto.DiscordChannelOptionDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordChannelDirectoryService {

    private final JDA jda;

    @Value("${discord.guild.id}")
    private String guildId;

    public List<DiscordChannelOptionDTO> getWritableTextChannels() {
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            log.warn("Impossible de lister les salons Discord: guilde introuvable {}", guildId);
            return List.of();
        }

        Member selfMember = guild.getSelfMember();
        if (selfMember == null) {
            log.warn("Impossible de lister les salons Discord: self member introuvable pour {}", guildId);
            return List.of();
        }

        return guild.getTextChannels().stream()
                .filter(channel -> selfMember.hasAccess(channel))
                .filter(channel -> selfMember.hasPermission(channel, Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND))
                .sorted(Comparator.comparing(TextChannel::getName, String.CASE_INSENSITIVE_ORDER))
                .map(channel -> DiscordChannelOptionDTO.builder()
                        .id(channel.getId())
                        .name(channel.getName())
                        .label(buildChannelLabel(channel))
                        .build())
                .collect(Collectors.toList());
    }

    private String buildChannelLabel(TextChannel channel) {
        if (channel.getParentCategory() == null) {
            return "#" + channel.getName();
        }
        return "#" + channel.getName() + " · " + channel.getParentCategory().getName();
    }
}
