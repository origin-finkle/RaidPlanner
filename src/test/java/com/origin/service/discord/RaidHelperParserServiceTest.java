package com.origin.service.discord;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RaidHelperParserServiceTest {

    private TimeZone previousTimeZone;
    private RaidHelperParserService parserService;

    @BeforeEach
    void setUp() {
        previousTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Paris"));
        parserService = new RaidHelperParserService(mock(RaidHelperImporterService.class));
    }

    @AfterEach
    void tearDown() {
        TimeZone.setDefault(previousTimeZone);
    }

    @Test
    void recognizesStyledWeeklyEventAndExtractsNomDateAndRaidHelperId() {
        MessageEmbed embed = mock(MessageEmbed.class);
        MessageEmbed.Field dateField = mockField("<:DateX:1> __<t:1776019500:D>__");
        MessageEmbed.Field timeField = mockField("<:TimeX:1> __<t:1776019500:t>__");
        MessageEmbed.Field webViewField = mockField("-# **[Web View](https://raid-helper.xyz/event/1489323535326511356)**");

        when(embed.getTitle()).thenReturn(null);
        when(embed.getDescription()).thenReturn("**<:R:1> <:A:2> <:I:3> <:D:4> <:empty:5> <:D:4> <:U:6> <:empty:5> <:D:4> <:I:3> <:M:7> <:A:2> <:N:8> <:C:9> <:H:10> <:E:11> **\n\nVenez avec vos consos et soyez a l'heure.");
        when(embed.getFields()).thenReturn(List.of(dateField, timeField, webViewField));
        when(embed.getTimestamp()).thenReturn(null);

        Message message = mockMessage(embed, "Raid-Helper");

        assertTrue(parserService.isRaidHelperEmbed(message));
        assertEquals("Raid du dimanche", parserService.extractNom(embed));
        assertEquals(LocalDateTime.of(2026, 4, 12, 20, 45), parserService.extractDateFromEmbed(embed));
        assertEquals(Optional.of("1489323535326511356"), parserService.extractRaidHelperId(message));
    }

    @Test
    void detectsCompositionToolEmbedFromFieldLink() {
        MessageEmbed embed = mock(MessageEmbed.class);
        MessageEmbed.Field field = mockField("Sent by <@123> | [Composition Tool](https://raid-helper.dev/raidplan/1463971516193574982)");

        when(embed.getTitle()).thenReturn("Raid du mercredi");
        when(embed.getDescription()).thenReturn("MaJ compo");
        when(embed.getFields()).thenReturn(List.of(field));
        when(embed.getTimestamp()).thenReturn(null);

        assertTrue(parserService.isCompositionToolEmbed(embed));
        assertEquals("Raid du mercredi", parserService.extractNom(embed));
    }

    @Test
    void detectsPlaceholderSignupEmbed() {
        MessageEmbed embed = mock(MessageEmbed.class);
        MessageEmbed.Field field = mockField("Envoye par: <@175968769745747968>");

        when(embed.getDescription()).thenReturn("inscription");
        when(embed.getFields()).thenReturn(List.of(field));

        assertTrue(parserService.isPlaceholderSignupEmbed(embed));
    }

    @Test
    void extractsLinkedDiscordMessageFromNotificationEmbed() {
        MessageEmbed embed = mock(MessageEmbed.class);

        when(embed.getTitle()).thenReturn(null);
        when(embed.getDescription()).thenReturn("[**Raid du mercredi** (<t:1776278700:f>)](https://discordapp.com/channels/670711708628811805/1402054085632331866/1491860253346631730)\nAvalona inscrit.");
        when(embed.getFields()).thenReturn(List.of());
        when(embed.getTimestamp()).thenReturn(null);

        Message message = mockMessage(embed, "Raid-Helper");

        Optional<RaidHelperParserService.DiscordMessageLink> linkedMessage = parserService.extractLinkedDiscordMessage(message);
        assertTrue(linkedMessage.isPresent());
        assertEquals("1402054085632331866", linkedMessage.get().getChannelId());
        assertEquals("1491860253346631730", linkedMessage.get().getMessageId());
    }

    private Message mockMessage(MessageEmbed embed, String authorName) {
        Message message = mock(Message.class);
        User author = mock(User.class);

        when(author.getName()).thenReturn(authorName);
        when(message.getAuthor()).thenReturn(author);
        when(message.getEmbeds()).thenReturn(List.of(embed));
        when(message.getButtons()).thenReturn(List.of());
        when(message.getContentRaw()).thenReturn("");
        return message;
    }

    private MessageEmbed.Field mockField(String value) {
        MessageEmbed.Field field = mock(MessageEmbed.Field.class);
        when(field.getName()).thenReturn("\u200e");
        when(field.getValue()).thenReturn(value);
        return field;
    }
}
