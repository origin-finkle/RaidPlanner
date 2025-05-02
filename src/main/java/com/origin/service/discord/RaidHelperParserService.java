package com.origin.service.discord;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RaidHelperParserService {

    private final RaidHelperImporterService importerService;


    private static final Pattern INSCRIT_PATTERN = Pattern.compile("`\\d+`\\s+\\*\\*\\((.*?)\\)(.*?)\\*\\*");

    public void parse(List<MessageEmbed.Field> fields) {
        for (MessageEmbed.Field field : fields) {
            String fieldName = field.getName();
            String fieldValue = field.getValue();

            if (fieldValue == null || fieldValue.isEmpty()) continue;

            // 🔎 On cible uniquement les classes connues (tu peux étendre la liste)
            if (!isClassField(fieldName)) continue;

            String classe = extractClassName(fieldName);

            String[] lines = fieldValue.split("\n");

            for (String line : lines) {
                Matcher matcher = INSCRIT_PATTERN.matcher(line);
                if (matcher.find()) {
                    String discordName = matcher.group(1).trim();
                    String personnage = matcher.group(2).trim();

                    String specialisation = extractSpec(line);
                    String role = guessRoleFromClass(classe); // Optionnel à améliorer

                    importerService.importerInscription(discordName, personnage, classe, specialisation, role);
                }
            }
        }
    }

    private boolean isClassField(String name) {
        return name.contains("Druid") || name.contains("Paladin") || name.contains("Mage") ||
                name.contains("Warrior") || name.contains("Priest") || name.contains("Rogue") ||
                name.contains("Death") || name.contains("Shaman") || name.contains("Hunter") ||
                name.contains("Warlock");
    }

    private String extractClassName(String raw) {
        return raw.replaceAll("<:.+?:\\d+>", "") // remove emoji
                .replaceAll("[*_\\s]", "")     // remove markdown and spaces
                .trim();
    }

    private String extractSpec(String line) {
        Matcher matcher = Pattern.compile("<:([A-Za-z0-9_]+):").matcher(line);
        if (matcher.find()) {
            return matcher.group(1); // Ex: Balance, Holy1, Arms
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
        return "DPS"; // Par défaut
    }
    public List<String> extractPseudosFromEmbed(MessageEmbed embed) {
        return embed.getFields().stream()
                .flatMap(field -> Arrays.stream(field.getValue().split("\n")))
                .map(line -> {
                    Matcher m = Pattern.compile("\\*\\*(.*?)\\*\\*").matcher(line);
                    return m.find() ? m.group(1) : null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public boolean isRaidHelperEmbed(Message message) {
        List<MessageEmbed> embeds = message.getEmbeds();
        if (embeds.isEmpty()) return false;

        MessageEmbed embed = embeds.get(0);

        // On check si l'embed contient un lien raid-helper.dev
        if (embed.getFields() != null) {
            for (MessageEmbed.Field field : embed.getFields()) {
                if (field.getValue() != null && field.getValue().contains("raid-helper.dev")) {
                    return true;
                }
            }
        }

        // Optionnel : fallback sur le titre ou description s’il contient un pattern typique
        if (embed.getDescription() != null && embed.getDescription().contains("**<:T:")) {
            return true;
        }

        return false;
    }

    public String extractNom(MessageEmbed embed) {
        String desc = embed.getDescription();
        if (desc != null) {
            String[] lines = desc.split("\n");
            for (String line : lines) {
                if (!line.contains("<:") && !line.isBlank()) {
                    return line.strip(); // test3 dans ton cas
                }
            }
        }
        return "Raid sans nom";
    }

    public Optional<String> extractRaidHelperId(MessageEmbed embed) {
        return embed.getFields().stream()
                .filter(field -> field.getValue() != null && field.getValue().contains("raid-helper.dev/event/"))
                .findFirst()
                .flatMap(field -> {
                    String value = field.getValue();
                    Pattern pattern = Pattern.compile("raid-helper\\.dev/event/(\\d+)");
                    Matcher matcher = pattern.matcher(value);
                    if (matcher.find()) {
                        return Optional.of(matcher.group(1));
                    }
                    return Optional.empty();
                });
    }

    public LocalDateTime extractDateFromEmbed(MessageEmbed embed) {
        for (MessageEmbed.Field field : embed.getFields()) {
            String value = field.getValue();
            if (value != null && value.contains("<t:")) {
                Matcher matcher = Pattern.compile("<t:(\\d+):D>").matcher(value);
                if (matcher.find()) {
                    long timestamp = Long.parseLong(matcher.group(1));
                    return Instant.ofEpochSecond(timestamp)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDateTime();
                }
            }
        }
        return null;
    }




}