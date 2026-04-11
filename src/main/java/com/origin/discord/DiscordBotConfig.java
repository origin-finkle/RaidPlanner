package com.origin.discord;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Icon;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DiscordBotConfig {

    @Value("${discord.bot.token}")
    private String botToken;

    @Bean
    public JDA jda() throws Exception {
        JDA jda = JDABuilder.createDefault(botToken)
                .enableIntents(
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.MESSAGE_CONTENT,
                        GatewayIntent.GUILD_MEMBERS
                )
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .setActivity(Activity.playing("preparer le raid..."))
                .build()
                .awaitReady();

        applyBotAvatar(jda);
        return jda;
    }

    private void applyBotAvatar(JDA jda) {
        try {
            ClassPathResource resource = new ClassPathResource("branding/origin-logo.png");
            if (!resource.exists()) {
                log.warn("Logo du bot introuvable dans les ressources: branding/origin-logo.png");
                return;
            }

            try (InputStream inputStream = resource.getInputStream()) {
                byte[] imageBytes = inputStream.readAllBytes();
                jda.getSelfUser()
                        .getManager()
                        .setAvatar(Icon.from(imageBytes))
                        .complete();
                log.info("Avatar du bot mis a jour avec le logo Origin");
            }
        } catch (Exception exception) {
            log.warn("Impossible de mettre a jour l'avatar du bot Discord", exception);
        }
    }
}
