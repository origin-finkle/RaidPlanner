package com.origin.discord;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.JDA;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DiscordListenerRegistrar implements ApplicationListener<ContextRefreshedEvent> {

    private final JDA jda;
    private final DiscordEventListener discordEventListener;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        jda.addEventListener(discordEventListener);
    }
}
