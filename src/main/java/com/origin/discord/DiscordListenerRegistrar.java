package com.origin.discord;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import com.origin.service.discord.RaidDiscordScannerService;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DiscordListenerRegistrar implements ApplicationListener<ContextRefreshedEvent> {

    private final JDA jda;
    private final DiscordEventListener discordEventListener;
    private final RaidDiscordScannerService raidDiscordScannerService;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        jda.addEventListener(discordEventListener);
        int importedCount = raidDiscordScannerService.scanConfiguredRaidHelperChannels();
        log.info("Import initial termine - {} raid(s) importe(s)", importedCount);
    }
}
