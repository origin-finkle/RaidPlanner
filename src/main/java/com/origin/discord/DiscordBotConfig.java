package com.origin.discord;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
@RequiredArgsConstructor
public class DiscordBotConfig {


    @Value("${discord.bot.token}")
    private String botToken;

    @Bean
    public JDA jda() throws Exception {
        return JDABuilder.createDefault(botToken)
                .enableIntents(
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.MESSAGE_CONTENT,
                        GatewayIntent.GUILD_MEMBERS
                )
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .setActivity(Activity.playing("préparer le raid..."))
                .build()
                .awaitReady();
    }
}
