package com.origin.service.discord;

import com.origin.entity.Inscription;
import com.origin.entity.Joueur;
import com.origin.entity.Personnage;
import com.origin.entity.Raid;
import com.origin.enumOrigin.CompositionWorkflowStatus;
import com.origin.repository.InscriptionRepository;
import com.origin.repository.JoueurRepository;
import com.origin.repository.PersonnageRepository;
import com.origin.repository.RaidRepository;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordCustomSignupService {

    public static final String TEST_CHANNEL_ID = "1355602641748496394";
    private static final List<String> SIGNUP_PING_ROLE_NAMES = List.of("Apply", "Veterans", "Officiers");
    private static final int TENTATIVE_REASON_MAX_LENGTH = 120;
    private static final DateTimeFormatter RAID_DATE_FORMATTER = DateTimeFormatter.ofPattern("EEEE d MMMM 'a' HH:mm", Locale.FRANCE);

    private final RaidRepository raidRepository;
    private final JoueurRepository joueurRepository;
    private final PersonnageRepository personnageRepository;
    private final InscriptionRepository inscriptionRepository;
    private final JDA jda;

    public String publishTestSignupMessage(Long raidId) {
        Raid raid = loadRaid(raidId);
        TextChannel channel = Optional.ofNullable(jda.getTextChannelById(TEST_CHANNEL_ID))
                .orElseThrow(() -> new IllegalStateException("Salon de test introuvable: " + TEST_CHANNEL_ID));

        channel.getHistory().retrievePast(30).queue(messages -> {
            Optional<Message> existing = messages.stream()
                    .filter(message -> isSignupTestMessageForRaid(message, raid))
                    .findFirst();

            if (existing.isPresent()) {
                existing.get().editMessageEmbeds(buildSignupEmbed(raid))
                        .setComponents(buildSignupActionRows(raidId))
                        .queue();
                return;
            }

            channel.sendMessageEmbeds(buildSignupEmbed(raid))
                    .setComponents(buildSignupActionRows(raidId))
                    .queue();
        });

        return "Prototype d'inscription maison publie ou mis a jour dans le salon de test " + TEST_CHANNEL_ID + ".";
    }

    @Transactional
    public String publishSignupMessageToRaidChannel(Long raidId) {
        Raid raid = loadRaid(raidId);
        return publishSignupMessageToChannel(raidId, raid.getChannelId());
    }

    @Transactional
    public String publishSignupMessageToChannel(Long raidId, String targetChannelId) {
        Raid raid = loadRaid(raidId);
        TextChannel channel = Optional.ofNullable(jda.getTextChannelById(targetChannelId))
                .orElseThrow(() -> new IllegalStateException("Salon de raid introuvable: " + targetChannelId));

        Message existingMessage = resolveExistingSignupMessage(raid, channel).orElse(null);
        MessageEmbed embed = buildSignupEmbed(raid);
        List<ActionRow> components = buildSignupActionRows(raidId);

        Message publishedMessage;
        boolean updated = false;

        if (existingMessage != null) {
            publishedMessage = existingMessage.editMessageEmbeds(embed)
                    .setComponents(components)
                    .complete();
            updated = true;
        } else {
            String roleMentions = buildSignupRoleMentions(channel);
            publishedMessage = channel.sendMessage(roleMentions)
                    .addEmbeds(embed)
                    .setComponents(components)
                    .complete();
        }

        raid.setSignupChannelId(channel.getId());
        raid.setSignupMessageId(publishedMessage.getIdLong());
        raid.setLastSignupPublishedAt(LocalDateTime.now());
        raidRepository.save(raid);

        return updated
                ? "Message d'inscription mis a jour pour " + raid.getNom() + " dans #" + channel.getName() + "."
                : "Message d'inscription publie pour " + raid.getNom() + " dans #" + channel.getName() + ".";
    }

    public List<CharacterChoice> getCharacterChoices(Long raidId, String discordId) {
        loadRaid(raidId);

        Joueur joueur = joueurRepository.findByDiscordId(discordId)
                .orElseThrow(() -> new IllegalArgumentException("Joueur Discord introuvable."));

        List<Personnage> personnages = personnageRepository.findByJoueurId(joueur.getId()).stream()
                .sorted(Comparator
                        .comparing(Personnage::isMain).reversed()
                        .thenComparing(Personnage::getNom, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        if (personnages.isEmpty()) {
            throw new IllegalStateException("Aucun personnage configure pour ce compte Discord.");
        }

        return personnages.stream()
                .map(personnage -> CharacterChoice.builder()
                        .personnageId(personnage.getId())
                        .label(personnage.getNom())
                        .description(buildCharacterDescription(personnage))
                        .main(personnage.isMain())
                        .build())
                .collect(Collectors.toList());
    }

    public StringSelectMenu buildCharacterSelectMenu(String statusKey,
                                                     Long raidId,
                                                     long sourceMessageId,
                                                     List<CharacterChoice> choices) {
        return StringSelectMenu.create(buildSelectComponentId(statusKey, raidId, sourceMessageId))
                .setPlaceholder("Choisis ton personnage")
                .addOptions(choices.stream()
                        .limit(25)
                        .map(choice -> SelectOption.of(choice.getLabel(), String.valueOf(choice.getPersonnageId()))
                                .withDescription(choice.getDescription()))
                        .collect(Collectors.toList()))
                .build();
    }

    @Transactional
    public String registerSignup(Long raidId, String discordId, Long personnageId, String statusKey) {
        return registerSignup(raidId, discordId, personnageId, statusKey, null);
    }

    @Transactional
    public String registerSignup(Long raidId,
                                 String discordId,
                                 Long personnageId,
                                 String statusKey,
                                 String commentaire) {
        Raid raid = loadRaid(raidId);
        ensureSignupOpenForChanges(raid);
        Joueur joueur = joueurRepository.findByDiscordId(discordId)
                .orElseThrow(() -> new IllegalArgumentException("Joueur Discord introuvable."));
        Personnage personnage = personnageRepository.findById(personnageId)
                .orElseThrow(() -> new IllegalArgumentException("Personnage introuvable."));

        if (personnage.getJoueur() == null || !Objects.equals(personnage.getJoueur().getId(), joueur.getId())) {
            throw new IllegalArgumentException("Ce personnage ne t'appartient pas.");
        }

        SignupStatus status = SignupStatus.fromKey(statusKey);
        inscriptionRepository.deleteByRaidIdAndJoueurId(raidId, joueur.getId());
        inscriptionRepository.save(Inscription.builder()
                .raid(raid)
                .personnage(personnage)
                .statut(status.name())
                .commentaire(sanitizeComment(status, commentaire))
                .build());

        return status.getSuccessMessage(personnage.getNom());
    }

    @Transactional
    public String removeSignup(Long raidId, String discordId) {
        Raid raid = loadRaid(raidId);
        ensureSignupOpenForChanges(raid);
        Joueur joueur = joueurRepository.findByDiscordId(discordId)
                .orElseThrow(() -> new IllegalArgumentException("Joueur Discord introuvable."));

        inscriptionRepository.deleteByRaidIdAndJoueurId(raid.getId(), joueur.getId());
        return "Ton inscription a ete retiree pour " + raid.getNom() + ".";
    }

    public MessageEmbed buildSignupEmbed(Long raidId) {
        return buildSignupEmbed(loadRaid(raidId));
    }

    public void refreshSignupMessage(String channelId, long messageId, Long raidId) {
        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            return;
        }

        channel.retrieveMessageById(messageId).queue(message -> {
            if (!Objects.equals(message.getAuthor().getId(), jda.getSelfUser().getId())) {
                return;
            }
            message.editMessageEmbeds(buildSignupEmbed(raidId))
                    .setComponents(buildSignupActionRows(raidId))
                    .queue();
        });
    }

    private Optional<Message> resolveExistingSignupMessage(Raid raid, TextChannel channel) {
        if (raid.getSignupMessageId() != null && Objects.equals(raid.getSignupChannelId(), channel.getId())) {
            try {
                Message storedMessage = channel.retrieveMessageById(raid.getSignupMessageId()).complete();
                if (isSignupMessageForRaid(storedMessage, raid)) {
                    return Optional.of(storedMessage);
                }
            } catch (Exception exception) {
                raid.setSignupMessageId(null);
                raid.setSignupChannelId(null);
                log.debug("Impossible de recharger le message signup {} pour le raid {}: {}",
                        raid.getSignupMessageId(),
                        raid.getId(),
                        exception.getMessage());
            }
        }

        try {
            return channel.getHistory().retrievePast(30).complete().stream()
                    .filter(message -> isSignupMessageForRaid(message, raid))
                    .findFirst();
        } catch (Exception exception) {
            log.debug("Impossible d'inspecter l'historique du salon {} pour le raid {}: {}",
                    channel.getId(),
                    raid.getId(),
                    exception.getMessage());
            return Optional.empty();
        }
    }

    private boolean isSignupMessageForRaid(Message message, Raid raid) {
        if (message == null || message.getEmbeds().isEmpty()) {
            return false;
        }

        if (!Objects.equals(message.getAuthor().getId(), jda.getSelfUser().getId())) {
            return false;
        }

        MessageEmbed embed = message.getEmbeds().get(0);
        String expectedTitle = "Inscriptions Origin : " + raid.getNom();
        if (!Objects.equals(embed.getTitle(), expectedTitle)) {
            return false;
        }

        String expectedDate = formatRaidDate(raid);
        return embed.getFields().stream()
                .anyMatch(field -> "Quand".equals(field.getName()) && Objects.equals(field.getValue(), expectedDate));
    }

    private MessageEmbed buildSignupEmbed(Raid raid) {
        SignupSummary summary = buildSummary(raid);
        SignupPhase phase = resolveSignupPhase(raid);

        EmbedBuilder builder = new EmbedBuilder()
                .setTitle("Inscriptions Origin : " + raid.getNom())
                .setDescription(phase.getDescription())
                .setColor(0x3B82F6)
                .setThumbnail(getBotAvatarUrl());

        builder.addField("Quand", formatRaidDate(raid), true);
        builder.addField("Etat", phase.getLabel(), true);
        builder.addField("Composition", summary.renderRoleSummary(), true);
        builder.addField("Reponses", summary.renderResponseSummary(), true);
        builder.addField("Inscrits (" + summary.getCount(SignupStatus.TITULAIRE) + ")", summary.render(SignupStatus.TITULAIRE), false);
        if (summary.getCount(SignupStatus.TENTATIVE) > 0) {
            builder.addField("Tentatives (" + summary.getCount(SignupStatus.TENTATIVE) + ")", summary.render(SignupStatus.TENTATIVE), false);
        }
        if (summary.hasSpecialStatuses()) {
            builder.addField("Disponibilites speciales", summary.renderSpecialStatuses(), false);
        }
        builder.setFooter("Salon test · Inscriptions Origin");
        builder.setFooter("Origin Raid Planner | " + phase.getFooter());
        return builder.build();
    }

    public List<ActionRow> buildSignupActionRows(Long raidId) {
        Raid raid = loadRaid(raidId);
        SignupPhase phase = resolveSignupPhase(raid);
        return List.of(
                ActionRow.of(buildStatusButtons(raidId, phase)),
                ActionRow.of(disableIfClosed(
                        Button.danger(buildStatusComponentId(SignupStatus.REMOVE, raidId), "Retirer mon inscription"),
                        phase
                ))
        );
    }

    private List<Button> buildStatusButtons(Long raidId, SignupPhase phase) {
        return List.of(
                disableIfClosed(Button.success(buildStatusComponentId(SignupStatus.TITULAIRE, raidId), "✅ Inscrit"), phase),
                disableIfClosed(Button.primary(buildStatusComponentId(SignupStatus.TENTATIVE, raidId), "❔ Tentative"), phase),
                disableIfClosed(Button.secondary(buildStatusComponentId(SignupStatus.LATE, raidId), "🕒 Late"), phase),
                disableIfClosed(Button.secondary(buildStatusComponentId(SignupStatus.BENCH, raidId), "🪑 Bench"), phase),
                disableIfClosed(Button.danger(buildStatusComponentId(SignupStatus.ABSENCE, raidId), "❌ Absence"), phase)
        );
    }

    private SignupSummary buildSummary(Raid raid) {
        Map<SignupStatus, List<String>> linesByStatus = new EnumMap<>(SignupStatus.class);
        Map<String, Integer> roleCounts = new LinkedHashMap<>();
        roleCounts.put("Tank", 0);
        roleCounts.put("Heal", 0);
        roleCounts.put("DPS", 0);

        for (SignupStatus status : SignupStatus.values()) {
            linesByStatus.put(status, new ArrayList<>());
        }

        inscriptionRepository.findDetailedByRaidIdOrderByIdAsc(raid.getId()).forEach(inscription -> {
            Personnage personnage = inscription.getPersonnage();
            if (personnage == null) {
                return;
            }

            SignupStatus status = SignupStatus.fromKey(inscription.getStatut());
            linesByStatus.get(status).add(formatSignupLine(personnage));
            incrementRoleCount(roleCounts, personnage, status);
        });

        return new SignupSummary(linesByStatus, roleCounts);
    }

    private Raid loadRaid(Long raidId) {
        return raidRepository.findById(raidId)
                .orElseThrow(() -> new IllegalArgumentException("Raid introuvable."));
    }

    private String buildCharacterDescription(Personnage personnage) {
        StringBuilder builder = new StringBuilder();
        builder.append(personnage.getClasse()).append(" ").append(personnage.getSpecialisation());
        if (personnage.isMain()) {
            builder.append(" - Main");
        }
        return builder.toString();
    }

    private String buildStatusComponentId(SignupStatus status, Long raidId) {
        return "signup_" + status.getKey() + "_" + raidId;
    }

    private String buildSelectComponentId(String statusKey, Long raidId, long sourceMessageId) {
        return "signup_select_" + statusKey + "_" + raidId + "_" + sourceMessageId;
    }

    public String buildTentativeReasonModalId(Long raidId, long sourceMessageId, Long personnageId) {
        return "signup_reason_" + raidId + "_" + sourceMessageId + "_" + personnageId;
    }

    private String sanitizeComment(SignupStatus status, String commentaire) {
        if (status != SignupStatus.TENTATIVE) {
            return null;
        }

        String cleaned = commentaire == null ? "" : commentaire.trim();
        if (cleaned.isBlank()) {
            throw new IllegalArgumentException("La raison est obligatoire pour une tentative.");
        }

        if (cleaned.length() > TENTATIVE_REASON_MAX_LENGTH) {
            return cleaned.substring(0, TENTATIVE_REASON_MAX_LENGTH);
        }

        return cleaned;
    }

    private void incrementRoleCount(Map<String, Integer> roleCounts, Personnage personnage, SignupStatus status) {
        if (status == SignupStatus.ABSENCE || status == SignupStatus.BENCH) {
            return;
        }

        String role = personnage.getRole() == null ? "DPS" : personnage.getRole().trim();
        String normalizedRole = role.equalsIgnoreCase("tank")
                ? "Tank"
                : role.equalsIgnoreCase("heal") || role.equalsIgnoreCase("healer")
                ? "Heal"
                : "DPS";
        roleCounts.compute(normalizedRole, (key, value) -> value == null ? 1 : value + 1);
    }

    private String formatSignupLine(Personnage personnage) {
        return getEmojiFor(personnage)
                + " **"
                + personnage.getNom()
                + "**"
                + (personnage.isMain() ? "" : " `R`")
                + " · "
                + personnage.getClasse()
                + " "
                + personnage.getSpecialisation();
    }

    private String formatRaidDate(Raid raid) {
        if (raid.getDate() == null) {
            return "-";
        }

        String formatted = raid.getDate().format(RAID_DATE_FORMATTER);
        if (formatted.isBlank()) {
            return "-";
        }

        return Character.toUpperCase(formatted.charAt(0)) + formatted.substring(1);
    }

    public boolean isSignupClosed(Long raidId) {
        return resolveSignupPhase(loadRaid(raidId)) == SignupPhase.CLOSED;
    }

    public String getSignupClosedMessage(Long raidId) {
        return resolveSignupPhase(loadRaid(raidId)) == SignupPhase.CLOSED
                ? "Les inscriptions sont fermees pour ce raid."
                : "Les inscriptions restent ouvertes.";
    }

    private void ensureSignupOpenForChanges(Raid raid) {
        if (resolveSignupPhase(raid) == SignupPhase.CLOSED) {
            throw new IllegalStateException("Les inscriptions sont fermees pour ce raid.");
        }
    }

    private SignupPhase resolveSignupPhase(Raid raid) {
        if (raid.isCompositionLocked() || raid.getCompositionStatus() == CompositionWorkflowStatus.PUBLISHED) {
            return SignupPhase.CLOSED;
        }

        if (raid.getCompositionStatus() == CompositionWorkflowStatus.READY) {
            return SignupPhase.FINALIZING;
        }

        return SignupPhase.OPEN;
    }

    private Button disableIfClosed(Button button, SignupPhase phase) {
        return phase == SignupPhase.CLOSED ? button.asDisabled() : button;
    }

    private String getEmojiFor(Personnage personnage) {
        Map<String, String> emojiMap = Map.ofEntries(
                Map.entry("DK-Sang", "<:dk_sang:1363215681570603170>"),
                Map.entry("DK-Givre", "<:dk_givre:1363215048675299479>"),
                Map.entry("DK-Impie", "<:dk_impie:1363215050155884745>"),
                Map.entry("Druide-Feral", "<:druide_feral:1363215056023588924>"),
                Map.entry("Druide-Restauration", "<:druide_restauration:1363229950353608787>"),
                Map.entry("Druide-Equilibre", "<:druide_equilibre:1363215053142364221>"),
                Map.entry("Moine-Maitre brasseur", "<:moine_maitre_brasseur:1493745119952638103>"),
                Map.entry("Moine-Tisse-brume", "<:moine_tissebrume:1493745192241598595>"),
                Map.entry("Moine-Marche-vent", "<:moine_marchevent:1493745166878638180>"),
                Map.entry("Paladin-Sacre", "<:paladin_sacre:1363215077452419254>"),
                Map.entry("Paladin-Retribution", "<:paladin_retribution:1363215074520727735>"),
                Map.entry("Paladin-Protection", "<:paladin_protection:1363215984923513033>"),
                Map.entry("Chaman-Elem", "<:chaman_elem:1363215015540166768>"),
                Map.entry("Chaman-Amelioration", "<:chaman_amelioration:1363214654284894429>"),
                Map.entry("Chaman-Restauration", "<:chaman_restauration:1363215037757522172>"),
                Map.entry("Guerrier-Arme", "<:guerrier_arme:1363215059429495024>"),
                Map.entry("Guerrier-Fury", "<:guerrier_fury:1363215740328611991>"),
                Map.entry("Guerrier-Protection", "<:guerrier_protection:1363215062927544470>"),
                Map.entry("Voleur-Combat", "<:voleur_combat:1363215091125850224>"),
                Map.entry("Voleur-Finesse", "<:voleur_finesse:1363216048442179836>"),
                Map.entry("Voleur-Assassinat", "<:voleur_assassinat:1363215089427153016>"),
                Map.entry("Chasseur-Survie", "<:chasseur_survie:1363215042094432286>"),
                Map.entry("Chasseur-Precision", "<:chasseur_precision:1363215040487887061>"),
                Map.entry("Chasseur-BM", "<:chasseur_bm:1363215038911090908>"),
                Map.entry("Mage-Feu", "<:mage_feu:1363215067826360492>"),
                Map.entry("Mage-Arcane", "<:mage_arcane:1363215952573104268>"),
                Map.entry("Mage-Givre", "<:mage_givre:1363215071160959178>"),
                Map.entry("Demoniste-Demonologie", "<:demoniste_demonologie:1363215045768773873>"),
                Map.entry("Demoniste-Affliction", "<:demoniste_affliction:1363215043453260068>"),
                Map.entry("Demoniste-Destruction", "<:demoniste_destruction:1363215047337316624>"),
                Map.entry("Pretre-Discipline", "<:pretre_discipline:1363215080027853051>"),
                Map.entry("Pretre-Ombre", "<:pretre_ombre:1363215649018740847>"),
                Map.entry("Pretre-Sacre", "<:pretre_sacre:1363215084003917984>")
        );

        String key = canonicalEmojiKey(personnage.getClasse(), personnage.getSpecialisation());
        String exactEmoji = emojiMap.get(key);
        if (exactEmoji != null) {
            return exactEmoji;
        }

        String normalizedKey = normalizeEmojiKey(key);
        return emojiMap.entrySet().stream()
                .filter(entry -> normalizeEmojiKey(entry.getKey()).equals(normalizedKey))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("`?`");
    }

    private String normalizeEmojiKey(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return normalized
                .replace("'", "")
                .replace("_", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private String canonicalEmojiKey(String classe, String specialisation) {
        return canonicalEmojiClass(classe) + "-" + canonicalEmojiSpec(specialisation);
    }

    private String buildSignupRoleMentions(TextChannel channel) {
        List<String> mentionTokens = SIGNUP_PING_ROLE_NAMES.stream()
                .map(roleName -> findRoleByName(channel, roleName))
                .flatMap(Optional::stream)
                .map(Role::getId)
                .distinct()
                .map(roleId -> "<@&" + roleId + ">")
                .collect(Collectors.toList());

        if (mentionTokens.isEmpty()) {
            log.warn("Aucun role de ping trouve pour la publication signup dans le salon {}", channel.getId());
            return "";
        }

        return String.join(" ", mentionTokens);
    }

    private Optional<Role> findRoleByName(TextChannel channel, String expectedRoleName) {
        if (channel.getGuild() == null) {
            return Optional.empty();
        }

        String normalizedExpectedRoleName = normalizeEmojiKey(expectedRoleName);
        return channel.getGuild().getRoles().stream()
                .filter(role -> normalizeEmojiKey(role.getName()).equals(normalizedExpectedRoleName))
                .findFirst();
    }

    private String canonicalEmojiClass(String value) {
        String normalized = normalizeEmojiKey(value);
        switch (normalized) {
            case "death knight":
            case "deathknight":
            case "chevalier de la mort":
            case "dk":
                return "DK";
            case "druide":
            case "druid":
                return "Druide";
            case "moine":
            case "monk":
                return "Moine";
            case "paladin":
                return "Paladin";
            case "chaman":
            case "shaman":
                return "Chaman";
            case "guerrier":
            case "warrior":
                return "Guerrier";
            case "voleur":
            case "rogue":
                return "Voleur";
            case "chasseur":
            case "hunter":
                return "Chasseur";
            case "mage":
                return "Mage";
            case "demoniste":
            case "warlock":
                return "Demoniste";
            case "pretre":
            case "priest":
                return "Pretre";
            default:
                return value;
        }
    }

    private String canonicalEmojiSpec(String value) {
        String normalized = normalizeEmojiKey(value);
        switch (normalized) {
            case "blood":
            case "sang":
                return "Sang";
            case "frost":
            case "givre":
                return "Givre";
            case "unholy":
            case "impie":
                return "Impie";
            case "feral":
                return "Feral";
            case "balance":
            case "equilibre":
                return "Equilibre";
            case "restoration":
            case "restauration":
                return "Restauration";
            case "brewmaster":
            case "maitre brasseur":
                return "Maitre brasseur";
            case "mistweaver":
            case "tisse brume":
            case "tisse-brume":
                return "Tisse-brume";
            case "windwalker":
            case "marche vent":
            case "marche-vent":
                return "Marche-vent";
            case "holy":
            case "sacre":
                return "Sacre";
            case "retribution":
            case "retri":
            case "ret":
                return "Retribution";
            case "protection":
                return "Protection";
            case "elemental":
            case "elem":
                return "Elem";
            case "enhancement":
            case "amelio":
            case "amelioration":
                return "Amelioration";
            case "arms":
            case "arme":
                return "Arme";
            case "fury":
                return "Fury";
            case "combat":
                return "Combat";
            case "assassination":
            case "assassinat":
                return "Assassinat";
            case "subtlety":
            case "finesse":
                return "Finesse";
            case "survival":
            case "survie":
                return "Survie";
            case "marksmanship":
            case "precision":
                return "Precision";
            case "beast mastery":
            case "beastmastery":
            case "bm":
                return "BM";
            case "fire":
            case "feu":
                return "Feu";
            case "arcane":
                return "Arcane";
            case "demonology":
            case "demonologie":
                return "Demonologie";
            case "affliction":
                return "Affliction";
            case "destruction":
                return "Destruction";
            case "discipline":
                return "Discipline";
            case "shadow":
            case "ombre":
                return "Ombre";
            default:
                return value;
        }
    }

    private String getBotAvatarUrl() {
        if (jda.getSelfUser().getEffectiveAvatarUrl() != null) {
            return jda.getSelfUser().getEffectiveAvatarUrl();
        }
        return jda.getSelfUser().getDefaultAvatarUrl();
    }

    private boolean isSignupTestMessageForRaid(Message message, Raid raid) {
        if (!Objects.equals(message.getAuthor().getId(), jda.getSelfUser().getId())) {
            return false;
        }
        if (message.getEmbeds().isEmpty()) {
            return false;
        }

        MessageEmbed embed = message.getEmbeds().get(0);
        return Objects.equals(embed.getTitle(), "Inscriptions Origin : " + raid.getNom());
    }

    @Value
    @Builder
    public static class CharacterChoice {
        Long personnageId;
        String label;
        String description;
        boolean main;
    }

    private static class SignupSummary {
        private final Map<SignupStatus, List<String>> linesByStatus;
        private final Map<String, Integer> roleCounts;

        private SignupSummary(Map<SignupStatus, List<String>> linesByStatus,
                              Map<String, Integer> roleCounts) {
            this.linesByStatus = linesByStatus;
            this.roleCounts = roleCounts;
        }

        private int getCount(SignupStatus status) {
            return linesByStatus.getOrDefault(status, List.of()).size();
        }

        private String render(SignupStatus status) {
            List<String> lines = linesByStatus.getOrDefault(status, List.of());
            return lines.isEmpty() ? "-" : String.join("\n", lines);
        }

        private String renderCombined(SignupStatus first, SignupStatus second) {
            List<String> lines = new ArrayList<>();
            lines.addAll(linesByStatus.getOrDefault(first, List.of()));
            lines.addAll(linesByStatus.getOrDefault(second, List.of()));
            return lines.isEmpty() ? "-" : String.join("\n", lines);
        }

        private String renderRoleSummary() {
            return "Tank: **" + roleCounts.getOrDefault("Tank", 0) + "**  |  "
                    + "Heal: **" + roleCounts.getOrDefault("Heal", 0) + "**  |  "
                    + "DPS: **" + roleCounts.getOrDefault("DPS", 0) + "**";
        }

        private String renderResponseSummary() {
            int activeResponses = getCount(SignupStatus.TITULAIRE)
                    + getCount(SignupStatus.TENTATIVE)
                    + getCount(SignupStatus.LATE)
                    + getCount(SignupStatus.BENCH)
                    + getCount(SignupStatus.ABSENCE);
            return "Total: **" + activeResponses + "**\n"
                    + "Inscrits: **" + getCount(SignupStatus.TITULAIRE) + "**\n"
                    + "Tentatives: **" + getCount(SignupStatus.TENTATIVE) + "**\n"
                    + "Speciaux: **"
                    + (getCount(SignupStatus.LATE) + getCount(SignupStatus.BENCH) + getCount(SignupStatus.ABSENCE))
                    + "**";
        }

        private String renderSpecialStatuses() {
            List<String> sections = new ArrayList<>();

            if (getCount(SignupStatus.LATE) > 0) {
                sections.add("**Late (" + getCount(SignupStatus.LATE) + ")**\n" + render(SignupStatus.LATE));
            }
            if (getCount(SignupStatus.BENCH) > 0) {
                sections.add("**Bench (" + getCount(SignupStatus.BENCH) + ")**\n" + render(SignupStatus.BENCH));
            }
            if (getCount(SignupStatus.ABSENCE) > 0) {
                sections.add("**Absences (" + getCount(SignupStatus.ABSENCE) + ")**\n" + render(SignupStatus.ABSENCE));
            }

            return sections.isEmpty() ? "Aucun statut special pour le moment." : String.join("\n\n", sections);
        }

        private boolean hasSpecialStatuses() {
            return getCount(SignupStatus.LATE) > 0
                    || getCount(SignupStatus.BENCH) > 0
                    || getCount(SignupStatus.ABSENCE) > 0;
        }
    }

    public enum SignupStatus {
        TITULAIRE("titulaire", "Inscription enregistree pour %s."),
        TENTATIVE("tentative", "Tentative enregistree pour %s."),
        LATE("late", "Statut late enregistre pour %s."),
        BENCH("bench", "Statut bench enregistre pour %s."),
        ABSENCE("absence", "Absence enregistree pour %s."),
        REMOVE("remove", "Inscription retiree.");

        private final String key;
        private final String successTemplate;

        SignupStatus(String key, String successTemplate) {
            this.key = key;
            this.successTemplate = successTemplate;
        }

        public String getKey() {
            return key;
        }

        public String getSuccessMessage(String characterName) {
            return String.format(successTemplate, characterName);
        }

        public static SignupStatus fromKey(String value) {
            String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            for (SignupStatus status : values()) {
                if (status.key.equals(normalized) || status.name().equalsIgnoreCase(normalized)) {
                    return status;
                }
            }
            return TITULAIRE;
        }
    }

    private enum SignupPhase {
        OPEN(
                "🟢 Ouvert",
                "Inscris-toi ou ajuste ton statut directement depuis ce message.",
                "reponds avec les boutons"
        ),
        FINALIZING(
                "🟠 En finalisation",
                "Le raid est en cours de finalisation. Tu peux encore ajuster ton statut pour le moment.",
                "raid en finalisation"
        ),
        CLOSED(
                "🔴 Ferme",
                "Les inscriptions sont fermees pour ce raid.",
                "inscriptions fermees"
        );

        private final String label;
        private final String description;
        private final String footer;

        SignupPhase(String label, String description, String footer) {
            this.label = label;
            this.description = description;
            this.footer = footer;
        }

        public String getLabel() {
            return label;
        }

        public String getDescription() {
            return description;
        }

        public String getFooter() {
            return footer;
        }
    }
}
