package com.origin.service.discord;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RaidHelperParserService {

    private static final Pattern INSCRIT_PATTERN = Pattern.compile("`\\d+`\\s+\\*\\*\\((.*?)\\)(.*?)\\*\\*");
    private static final Pattern DISCORD_TIMESTAMP_PATTERN = Pattern.compile("<t:(\\d+)(?::[tTdDfFR])?>");
    private static final Pattern RAID_HELPER_EVENT_PATTERN = Pattern.compile("raid-helper\\.(?:dev|xyz)/event/(\\d+)");
    private static final Pattern RAID_HELPER_RAIDPLAN_PATTERN = Pattern.compile("raid-helper\\.(?:dev|xyz)/raidplan/(\\d+)");
    private static final Pattern DISCORD_MESSAGE_LINK_PATTERN = Pattern.compile("discord(?:app)?\\.com/channels/\\d+/(\\d+)/(\\d+)");

    private final RaidHelperImporterService importerService;

    public void parse(List<MessageEmbed.Field> fields) {
        for (MessageEmbed.Field field : fields) {
            String fieldName = field.getName();
            String fieldValue = field.getValue();

            if (fieldValue == null || fieldValue.isEmpty()) {
                continue;
            }

            if (!isClassField(fieldName)) {
                continue;
            }

            String classe = extractClassName(fieldName);
            String[] lines = fieldValue.split("\\n");

            for (String line : lines) {
                Matcher matcher = INSCRIT_PATTERN.matcher(line);
                if (!matcher.find()) {
                    continue;
                }

                String discordName = matcher.group(1).trim();
                String personnage = matcher.group(2).trim();
                String specialisation = extractSpec(line);
                String role = guessRoleFromClass(classe);

                importerService.importerInscription(discordName, personnage, classe, specialisation, role);
            }
        }
    }

    public List<String> extractPseudosFromEmbed(MessageEmbed embed) {
        return embed.getFields().stream()
                .filter(field -> field.getValue() != null)
                .flatMap(field -> Arrays.stream(field.getValue().split("\\n")))
                .map(line -> {
                    Matcher matcher = Pattern.compile("\\*\\*(.*?)\\*\\*").matcher(line);
                    return matcher.find() ? matcher.group(1) : null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public boolean isRaidHelperEmbed(Message message) {
        List<MessageEmbed> embeds = message.getEmbeds();
        if (embeds.isEmpty()) {
            return false;
        }

        MessageEmbed embed = embeds.get(0);
        if (containsRaidHelperLink(message, embed)) {
            return true;
        }

        String authorName = Optional.ofNullable(message.getAuthor().getName()).orElse("").toLowerCase(Locale.ROOT);
        if (!authorName.contains("raid-helper")) {
            return false;
        }

        return looksLikeWeeklyRaidTitle(embed.getTitle()) || looksLikeWeeklyRaidTitle(embed.getDescription());
    }

    public boolean isCompositionToolEmbed(MessageEmbed embed) {
        return containsPattern(embed, RAID_HELPER_RAIDPLAN_PATTERN);
    }

    public String extractNom(MessageEmbed embed) {
        String title = embed.getTitle();
        Optional<String> canonicalWeeklyTitle = canonicalWeeklyRaidTitle(title);
        if (canonicalWeeklyTitle.isPresent()) {
            return canonicalWeeklyTitle.get();
        }

        String desc = embed.getDescription();
        canonicalWeeklyTitle = canonicalWeeklyRaidTitle(desc);
        if (canonicalWeeklyTitle.isPresent()) {
            return canonicalWeeklyTitle.get();
        }

        if (desc != null) {
            String[] lines = desc.split("\\n");
            for (String line : lines) {
                if (!line.contains("<:") && !line.isBlank()) {
                    return line.strip();
                }
            }
        }

        if (embed.getTitle() != null && !embed.getTitle().isBlank()) {
            return embed.getTitle().strip();
        }

        return "Raid sans nom";
    }

    private boolean looksLikeWeeklyRaidTitle(String title) {
        return canonicalWeeklyRaidTitle(title).isPresent();
    }

    public Optional<String> extractRaidHelperId(Message message) {
        if (!message.getEmbeds().isEmpty()) {
            Optional<String> fromEmbed = findPattern(message.getEmbeds().get(0), RAID_HELPER_EVENT_PATTERN)
                    .map(matcher -> matcher.group(1));
            if (fromEmbed.isPresent()) {
                return fromEmbed;
            }
        }

        for (Button button : message.getButtons()) {
            String url = button.getUrl();
            if (url == null) {
                continue;
            }

            Matcher matcher = RAID_HELPER_EVENT_PATTERN.matcher(url);
            if (matcher.find()) {
                return Optional.of(matcher.group(1));
            }
        }

        return Optional.empty();
    }

    public Optional<DiscordMessageLink> extractLinkedDiscordMessage(Message message) {
        if (!message.getEmbeds().isEmpty()) {
            Optional<DiscordMessageLink> fromEmbed = findPattern(message.getEmbeds().get(0), DISCORD_MESSAGE_LINK_PATTERN)
                    .map(matcher -> new DiscordMessageLink(matcher.group(1), matcher.group(2)));
            if (fromEmbed.isPresent()) {
                return fromEmbed;
            }
        }

        String rawContent = message.getContentRaw();
        if (rawContent != null) {
            Matcher matcher = DISCORD_MESSAGE_LINK_PATTERN.matcher(rawContent);
            if (matcher.find()) {
                return Optional.of(new DiscordMessageLink(matcher.group(1), matcher.group(2)));
            }
        }

        return Optional.empty();
    }

    public boolean isPlaceholderSignupEmbed(MessageEmbed embed) {
        if (embed == null) {
            return false;
        }

        String description = Optional.ofNullable(embed.getDescription()).orElse("").strip().toLowerCase(Locale.ROOT);
        if (!"inscription".equals(description)) {
            return false;
        }

        if (embed.getFields().isEmpty()) {
            return true;
        }

        return embed.getFields().stream()
                .map(MessageEmbed.Field::getValue)
                .filter(Objects::nonNull)
                .map(value -> value.strip().toLowerCase(Locale.ROOT))
                .allMatch(value -> value.startsWith("envoye par:")
                        || value.startsWith("envoyé par:")
                        || value.startsWith("sent by"));
    }

    public LocalDateTime extractDateFromEmbed(MessageEmbed embed) {
        if (embed.getDescription() != null) {
            LocalDateTime date = extractDateFromText(embed.getDescription());
            if (date != null) {
                return date;
            }
        }

        for (MessageEmbed.Field field : embed.getFields()) {
            if (field.getName() != null) {
                LocalDateTime date = extractDateFromText(field.getName());
                if (date != null) {
                    return date;
                }
            }

            if (field.getValue() != null) {
                LocalDateTime date = extractDateFromText(field.getValue());
                if (date != null) {
                    return date;
                }
            }
        }

        if (embed.getTitle() != null) {
            LocalDateTime date = extractDateFromText(embed.getTitle());
            if (date != null) {
                return date;
            }
        }

        if (embed.getTimestamp() != null) {
            return embed.getTimestamp().toLocalDateTime();
        }

        return null;
    }

    public String describeEmbed(MessageEmbed embed) {
        StringBuilder sb = new StringBuilder();
        sb.append("title=").append(safe(embed.getTitle())).append(" | ");
        sb.append("description=").append(safe(embed.getDescription())).append(" | ");
        sb.append("timestamp=").append(embed.getTimestamp()).append(" | ");
        sb.append("fields=[");

        for (int i = 0; i < embed.getFields().size(); i++) {
            MessageEmbed.Field field = embed.getFields().get(i);
            if (i > 0) {
                sb.append(" ; ");
            }

            sb.append("{name=")
                    .append(safe(field.getName()))
                    .append(", value=")
                    .append(safe(field.getValue()))
                    .append("}");
        }

        sb.append("]");
        return sb.toString();
    }

    private boolean isClassField(String name) {
        return name != null && (name.contains("Druid") || name.contains("Paladin") || name.contains("Mage")
                || name.contains("Warrior") || name.contains("Priest") || name.contains("Rogue")
                || name.contains("Death") || name.contains("Shaman") || name.contains("Hunter")
                || name.contains("Warlock"));
    }

    private String extractClassName(String raw) {
        return raw.replaceAll("<:.+?:\\d+>", "")
                .replaceAll("[*_\\s]", "")
                .trim();
    }

    private String extractSpec(String line) {
        Matcher matcher = Pattern.compile("<:([A-Za-z0-9_]+):").matcher(line);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "Unknown";
    }

    private String guessRoleFromClass(String clazz) {
        if (clazz.equalsIgnoreCase("Paladin") || clazz.equalsIgnoreCase("Priest") || clazz.equalsIgnoreCase("Druid")) {
            return "Heal";
        }
        if (clazz.equalsIgnoreCase("Warrior") || clazz.equalsIgnoreCase("DeathKnight")) {
            return "Tank";
        }
        return "DPS";
    }

    private LocalDateTime extractDateFromText(String text) {
        Matcher matcher = DISCORD_TIMESTAMP_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }

        long timestamp = Long.parseLong(matcher.group(1));
        return Instant.ofEpochSecond(timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }

    private boolean containsRaidHelperLink(Message message, MessageEmbed embed) {
        if (containsPattern(embed, RAID_HELPER_EVENT_PATTERN)
                || containsPattern(embed, RAID_HELPER_RAIDPLAN_PATTERN)) {
            return true;
        }

        for (Button button : message.getButtons()) {
            String url = button.getUrl();
            if (url == null) {
                continue;
            }

            if (RAID_HELPER_EVENT_PATTERN.matcher(url).find() || RAID_HELPER_RAIDPLAN_PATTERN.matcher(url).find()) {
                return true;
            }
        }

        return false;
    }

    private Optional<String> canonicalWeeklyRaidTitle(String text) {
        Optional<String> weekday = extractFrenchWeekday(text);
        if (weekday.isEmpty()) {
            return Optional.empty();
        }

        String compact = compactNormalize(text);
        if (!compact.contains("raid")) {
            return Optional.empty();
        }

        return Optional.of("Raid du " + weekday.get());
    }

    private Optional<String> extractFrenchWeekday(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }

        String compact = compactNormalize(text);
        if (compact.contains("lundi")) {
            return Optional.of("lundi");
        }
        if (compact.contains("mardi")) {
            return Optional.of("mardi");
        }
        if (compact.contains("mercredi")) {
            return Optional.of("mercredi");
        }
        if (compact.contains("jeudi")) {
            return Optional.of("jeudi");
        }
        if (compact.contains("vendredi")) {
            return Optional.of("vendredi");
        }
        if (compact.contains("samedi")) {
            return Optional.of("samedi");
        }
        if (compact.contains("dimanche")) {
            return Optional.of("dimanche");
        }

        return Optional.empty();
    }

    private String compactNormalize(String text) {
        return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^A-Za-z]", "")
                .toLowerCase(Locale.ROOT);
    }

    private boolean containsPattern(MessageEmbed embed, Pattern pattern) {
        return findPattern(embed, pattern).isPresent();
    }

    private Optional<Matcher> findPattern(MessageEmbed embed, Pattern pattern) {
        if (embed.getTitle() != null) {
            Matcher matcher = pattern.matcher(embed.getTitle());
            if (matcher.find()) {
                return Optional.of(matcher);
            }
        }

        if (embed.getDescription() != null) {
            Matcher matcher = pattern.matcher(embed.getDescription());
            if (matcher.find()) {
                return Optional.of(matcher);
            }
        }

        for (MessageEmbed.Field field : embed.getFields()) {
            if (field.getName() != null) {
                Matcher matcher = pattern.matcher(field.getName());
                if (matcher.find()) {
                    return Optional.of(matcher);
                }
            }

            if (field.getValue() != null) {
                Matcher matcher = pattern.matcher(field.getValue());
                if (matcher.find()) {
                    return Optional.of(matcher);
                }
            }
        }

        return Optional.empty();
    }

    private String safe(String text) {
        if (text == null) {
            return "null";
        }

        String singleLine = text.replace("\r", "").replace("\n", "\\n");
        StringBuilder escaped = new StringBuilder();

        for (int i = 0; i < singleLine.length(); i++) {
            char current = singleLine.charAt(i);

            if (current >= 32 && current <= 126 && current != '\\') {
                escaped.append(current);
            } else if (current == '\\') {
                escaped.append("\\\\");
            } else {
                escaped.append(String.format("\\u%04x", (int) current));
            }

            if (escaped.length() > 300) {
                return escaped.substring(0, 300) + "...";
            }
        }

        return escaped.toString();
    }

    public static class DiscordMessageLink {
        private final String channelId;
        private final String messageId;

        public DiscordMessageLink(String channelId, String messageId) {
            this.channelId = channelId;
            this.messageId = messageId;
        }

        public String getChannelId() {
            return channelId;
        }

        public String getMessageId() {
            return messageId;
        }
    }
}
