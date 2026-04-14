package com.origin.service;

import com.origin.dto.ExportCompoRequestDto;
import com.origin.dto.PersonnageDTO;
import com.origin.dto.PersonnageCompositionDTO;
import com.origin.dto.RaidCompositionStateDTO;
import com.origin.dto.RaidCompositionDTO;
import com.origin.dto.RaidPublicationComparisonDTO;
import com.origin.dto.RaidPublicationHistoryDTO;
import com.origin.dto.UpdateRaidCompositionStateRequestDTO;
import com.origin.entity.Joueur;
import com.origin.entity.Inscription;
import com.origin.entity.Personnage;
import com.origin.entity.Raid;
import com.origin.entity.RaidInscription;
import com.origin.entity.RaidPublicationHistory;
import com.origin.enumOrigin.CompositionWorkflowStatus;
import com.origin.repository.PersonnageRepository;
import com.origin.repository.InscriptionRepository;
import com.origin.repository.RaidInscriptionRepository;
import com.origin.repository.RaidPublicationHistoryRepository;
import com.origin.repository.RaidRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class RaidService {
    private static final DateTimeFormatter DISCORD_RAID_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("EEEE d MMMM 'a' HH:mm", Locale.FRANCE);

    private final RaidRepository raidRepository;
    private final PersonnageRepository personnageRepository;
    private final InscriptionRepository inscriptionRepository;
    private final JDA jda;
    private final RaidInscriptionRepository raidInscriptionRepository;
    private final RaidPublicationHistoryRepository raidPublicationHistoryRepository;

    public void saveComposition(RaidCompositionDTO dto) {
        Raid raid = raidRepository.findById(dto.getRaidId())
                .orElseThrow(() -> new IllegalArgumentException("Raid non trouve : " + dto.getRaidId()));

        if (raid.isCompositionLocked()) {
            throw new IllegalStateException("La composition de ce raid est verrouillee.");
        }

        raid.getGroup1().clear();
        raid.getGroup2().clear();
        raid.setGroup1(mapToPersonnages(dto.getGroup1()));
        raid.setGroup2(mapToPersonnages(dto.getGroup2()));
        if (raid.getCompositionStatus() == CompositionWorkflowStatus.PUBLISHED) {
            raid.setCompositionStatus(CompositionWorkflowStatus.READY);
        } else if (raid.getCompositionStatus() == null) {
            raid.setCompositionStatus(CompositionWorkflowStatus.DRAFT);
        }
        raidRepository.save(raid);
    }

    @Transactional
    public void addManualSignup(Long raidId, Long personnageId) {
        if (personnageId == null) {
            throw new IllegalArgumentException("Le personnage est obligatoire.");
        }

        Raid raid = raidRepository.findById(raidId)
                .orElseThrow(() -> new IllegalArgumentException("Raid non trouve : " + raidId));
        Personnage personnage = personnageRepository.findById(personnageId)
                .orElseThrow(() -> new IllegalArgumentException("Personnage non trouve : " + personnageId));

        if (personnage.getJoueur() == null) {
            throw new IllegalArgumentException("Le personnage selectionne n'est rattache a aucun joueur.");
        }

        inscriptionRepository.deleteByRaidIdAndJoueurId(raidId, personnage.getJoueur().getId());
        inscriptionRepository.save(Inscription.builder()
                .raid(raid)
                .personnage(personnage)
                .statut("TITULAIRE")
                .commentaire("MANUAL_OFFICER_ADD")
                .build());
    }

    @Transactional
    public void removeManualSignup(Long raidId, Long personnageId) {
        if (personnageId == null) {
            throw new IllegalArgumentException("Le personnage est obligatoire.");
        }

        Raid raid = raidRepository.findById(raidId)
                .orElseThrow(() -> new IllegalArgumentException("Raid non trouve : " + raidId));
        Personnage personnage = personnageRepository.findById(personnageId)
                .orElseThrow(() -> new IllegalArgumentException("Personnage non trouve : " + personnageId));

        if (personnage.getJoueur() == null) {
            throw new IllegalArgumentException("Le personnage selectionne n'est rattache a aucun joueur.");
        }

        Long joueurId = personnage.getJoueur().getId();
        boolean hasManualSignup = inscriptionRepository.findDetailedByRaidIdOrderByIdAsc(raidId).stream()
                .anyMatch(inscription ->
                        inscription.getPersonnage() != null
                                && inscription.getPersonnage().getJoueur() != null
                                && joueurId.equals(inscription.getPersonnage().getJoueur().getId())
                                && "MANUAL_OFFICER_ADD".equals(inscription.getCommentaire()));

        if (!hasManualSignup) {
            return;
        }

        raid.getGroup1().removeIf(member ->
                member != null
                        && member.getJoueur() != null
                        && joueurId.equals(member.getJoueur().getId()));
        raid.getGroup2().removeIf(member ->
                member != null
                        && member.getJoueur() != null
                        && joueurId.equals(member.getJoueur().getId()));
        raidRepository.save(raid);

        inscriptionRepository.deleteByRaidIdAndJoueurId(raidId, joueurId);
    }

    public RaidCompositionStateDTO updateCompositionState(Long raidId, UpdateRaidCompositionStateRequestDTO request) {
        Raid raid = getRaidById(raidId);

        if (request.getStatus() != null) {
            raid.setCompositionStatus(request.getStatus());
        }
        if (request.getLocked() != null) {
            raid.setCompositionLocked(request.getLocked());
        }

        raidRepository.save(raid);
        return toCompositionStateDto(raid);
    }

    public RaidCompositionStateDTO getCompositionState(Long raidId) {
        return toCompositionStateDto(getRaidById(raidId));
    }

    public RaidPublicationComparisonDTO getPublicationComparison(Long raidId) {
        Raid raid = getRaidById(raidId);

        List<PersonnageDTO> currentGroup1 = raid.getGroup1().stream()
                .map(this::personnageToDto)
                .collect(Collectors.toList());
        List<PersonnageDTO> currentGroup2 = raid.getGroup2().stream()
                .map(this::personnageToDto)
                .collect(Collectors.toList());

        List<PersonnageDTO> publishedGroup1 = loadSnapshotCharacters(raid.getLastPublishedGroup1Snapshot()).stream()
                .map(this::personnageToDto)
                .collect(Collectors.toList());
        List<PersonnageDTO> publishedGroup2 = loadSnapshotCharacters(raid.getLastPublishedGroup2Snapshot()).stream()
                .map(this::personnageToDto)
                .collect(Collectors.toList());

        return RaidPublicationComparisonDTO.builder()
                .raidId(raid.getId())
                .raidNom(raid.getNom())
                .raidDate(raid.getDate())
                .lastPublishedAt(raid.getLastPublishedAt())
                .hasPublishedSnapshot(hasPublishedSnapshot(raid))
                .currentGroup1(currentGroup1)
                .currentGroup2(currentGroup2)
                .publishedGroup1(publishedGroup1)
                .publishedGroup2(publishedGroup2)
                .currentOnlyPlayers(computeCharacterDifference(
                        Stream.concat(currentGroup1.stream(), currentGroup2.stream()).collect(Collectors.toList()),
                        Stream.concat(publishedGroup1.stream(), publishedGroup2.stream()).collect(Collectors.toList())
                ))
                .publishedOnlyPlayers(computeCharacterDifference(
                        Stream.concat(publishedGroup1.stream(), publishedGroup2.stream()).collect(Collectors.toList()),
                        Stream.concat(currentGroup1.stream(), currentGroup2.stream()).collect(Collectors.toList())
                ))
                .build();
    }

    public void exportFormattedComposition(Long raidId, ExportCompoRequestDto request) {
        Raid raid = getRaidById(raidId);
        if (!request.isEnvoyerSurDiscord()) {
            return;
        }

        String targetChannelId = request.getOverrideChannelId() != null && !request.getOverrideChannelId().isBlank()
                ? request.getOverrideChannelId().trim()
                : raid.getChannelId();

        TextChannel channel = jda.getTextChannelById(targetChannelId);
        if (channel == null) {
            log.warn("Salon Discord introuvable pour ID : {}", targetChannelId);
            return;
        }

        boolean isUpdate = raid.getPublishedMessageId() != null
                && Objects.equals(raid.getPublishedChannelId(), targetChannelId);

        if (isUpdate) {
            channel.retrieveMessageById(raid.getPublishedMessageId()).queue(
                    existingMessage -> {
                        if (existingMessage.getAuthor().getId().equals(jda.getSelfUser().getId())) {
                            existingMessage.delete().queue(
                                    success -> publishCompositionMessage(channel, raid.getId(), targetChannelId, true),
                                    error -> publishCompositionMessage(channel, raid.getId(), targetChannelId, true)
                            );
                        } else {
                            publishCompositionMessage(channel, raid.getId(), targetChannelId, true);
                        }
                    },
                    error -> publishCompositionMessage(channel, raid.getId(), targetChannelId, true)
            );
            return;
        }

        publishCompositionMessage(channel, raid.getId(), targetChannelId, false);
    }

    public MessageEmbed buildTwoColumnEmbedWithConfirmations(Raid raid) {
        return buildTwoColumnEmbedWithConfirmations(raid, false);
    }

    public MessageEmbed buildTwoColumnEmbedWithConfirmations(Raid raid, boolean updated) {
        List<Personnage> group1Members = raid.getGroup1().stream()
                .sorted(Comparator.comparing(Personnage::getNom, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
        List<Personnage> group2Members = raid.getGroup2().stream()
                .sorted(Comparator.comparing(Personnage::getNom, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        Map<Long, RaidInscription.StatutInscription> confirmationMap = raidInscriptionRepository
                .findByRaidIdOrderByIdAsc(raid.getId()).stream()
                .collect(Collectors.toMap(
                        inscription -> inscription.getJoueur().getId(),
                        RaidInscription::getStatut,
                        (left, right) -> right,
                        HashMap::new
                ));

        int totalPlayers = group1Members.size() + group2Members.size();
        int confirmedCount = countConfirmations(group1Members, group2Members, confirmationMap, RaidInscription.StatutInscription.CONFIRME);
        int cancelledCount = countConfirmations(group1Members, group2Members, confirmationMap, RaidInscription.StatutInscription.ANNULE);
        int pendingCount = Math.max(0, totalPlayers - confirmedCount - cancelledCount);

        EmbedBuilder builder = new EmbedBuilder()
                .setTitle(updated
                        ? "Mise a jour - Composition du raid : " + raid.getNom()
                        : "Composition du raid : " + raid.getNom())
                .setColor(0x5865F2)
                .setThumbnail(getBotAvatarUrl())
                .setDescription(updated
                        ? "Composition mise a jour. Reponds directement avec les boutons ci-dessous."
                        : "Composition prete. Reponds directement avec les boutons ci-dessous.")
                .addField("Quand", formatRaidDate(raid.getDate()), true)
                .addField("Composition", buildRosterSummary(group1Members, group2Members), true)
                .addField("Reponses", buildConfirmationSummary(confirmedCount, cancelledCount, pendingCount), true);

        String group1Text = formatGroupWithConfirmation(group1Members, confirmationMap);
        String group2Text = formatGroupWithConfirmation(group2Members, confirmationMap);

        builder.addField("Groupe 1", group1Text.isEmpty() ? "-" : group1Text, true);
        builder.addField("Groupe 2", group2Text.isEmpty() ? "-" : group2Text, true);
        builder.setFooter(updated
                ? "Origin Raid Planner | composition mise a jour"
                : "Origin Raid Planner | reponds avec les boutons");
        return builder.build();
    }

    public Raid getRaidById(Long raidId) {
        return raidRepository.findWithGroups(raidId)
                .orElseThrow(() -> new IllegalArgumentException("Raid introuvable : " + raidId));
    }

    public void saveRaid(Raid raid) {
        raidRepository.save(raid);
    }

    public List<RaidPublicationHistoryDTO> getPublicationHistory() {
        return raidPublicationHistoryRepository.findTop30ByOrderByPublishedAtDesc().stream()
                .map(entry -> RaidPublicationHistoryDTO.builder()
                        .id(entry.getId())
                        .raidId(entry.getRaid().getId())
                        .raidNom(entry.getRaid().getNom())
                        .raidDate(entry.getRaid().getDate())
                        .channelId(entry.getChannelId())
                        .guildId(entry.getGuildId())
                        .messageId(entry.getMessageId())
                        .updated(entry.isUpdated())
                        .testPublication(entry.isTestPublication())
                        .publishedAt(entry.getPublishedAt())
                        .messageUrl(buildDiscordMessageUrl(entry.getGuildId(), entry.getChannelId(), entry.getMessageId()))
                        .build())
                .collect(Collectors.toList());
    }

    private void publishCompositionMessage(TextChannel channel, Long raidId, String targetChannelId, boolean updated) {
        Raid freshRaid = getRaidById(raidId);
        MessageEmbed embed = buildTwoColumnEmbedWithConfirmations(freshRaid, updated);
        String mentions = generateMentionLine(getJoueursFromRaid(freshRaid));

        channel.sendMessageEmbeds(embed)
                .setActionRow(
                        Button.success("confirm_" + freshRaid.getId(), "✅ Confirmer"),
                        Button.danger("cancel_" + freshRaid.getId(), "❌ Annuler")
                )
                .addContent(mentions)
                .queue(message -> {
                    boolean mainPublication = Objects.equals(targetChannelId, freshRaid.getChannelId());
                    if (mainPublication) {
                        freshRaid.setPublishedMessageId(message.getIdLong());
                        freshRaid.setPublishedChannelId(targetChannelId);
                        freshRaid.setLastPublishedAt(LocalDateTime.now());
                        freshRaid.setLastPublishedGroup1Snapshot(serializeSnapshot(freshRaid.getGroup1()));
                        freshRaid.setLastPublishedGroup2Snapshot(serializeSnapshot(freshRaid.getGroup2()));
                        freshRaid.setCompositionStatus(CompositionWorkflowStatus.PUBLISHED);
                    }
                    raidRepository.save(freshRaid);
                    raidPublicationHistoryRepository.save(RaidPublicationHistory.builder()
                            .raid(freshRaid)
                            .channelId(targetChannelId)
                            .guildId(channel.getGuild() != null ? channel.getGuild().getId() : null)
                            .messageId(message.getIdLong())
                            .updated(updated)
                            .testPublication(!Objects.equals(targetChannelId, freshRaid.getChannelId()))
                            .publishedAt(LocalDateTime.now())
                            .build());
                });
    }

    private RaidCompositionStateDTO toCompositionStateDto(Raid raid) {
        return RaidCompositionStateDTO.builder()
                .raidId(raid.getId())
                .status(raid.getCompositionStatus())
                .locked(raid.isCompositionLocked())
                .lastPublishedAt(raid.getLastPublishedAt())
                .hasPublishedSnapshot(hasPublishedSnapshot(raid))
                .build();
    }

    private Set<Personnage> mapToPersonnages(List<PersonnageCompositionDTO> dtoList) {
        return dtoList.stream()
                .map(dto -> personnageRepository.findByNomStrict(dto.getNom())
                        .orElseThrow(() -> new RuntimeException("Personnage non trouve : " + dto.getNom())))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String formatGroupWithConfirmation(Set<Personnage> group,
                                               Map<Long, RaidInscription.StatutInscription> confirmationMap) {
        StringBuilder sb = new StringBuilder();
        int index = 1;

        for (Personnage personnage : group) {
            String emoji = getEmojiFor(personnage);
            boolean confirmed = confirmationMap.getOrDefault(
                    personnage.getJoueur().getId(),
                    RaidInscription.StatutInscription.ANNULE
            ) == RaidInscription.StatutInscription.CONFIRME;

            sb.append(emoji)
                    .append(" `")
                    .append(index++)
                    .append("` **")
                    .append(personnage.getNom())
                    .append("**")
                    .append(confirmed ? " ✅" : " ❌")
                    .append("\n");
        }

        return sb.toString();
    }

    private String formatGroupWithConfirmation(List<Personnage> group,
                                               Map<Long, RaidInscription.StatutInscription> confirmationMap) {
        StringBuilder sb = new StringBuilder();
        int index = 1;

        for (Personnage personnage : group) {
            sb.append(getEmojiFor(personnage))
                    .append(" `")
                    .append(index++)
                    .append("` **")
                    .append(personnage.getNom())
                    .append("**")
                    .append(personnage.isMain() ? "" : " `R`")
                    .append(" `")
                    .append(getConfirmationMarker(confirmationMap.get(
                            personnage.getJoueur() != null ? personnage.getJoueur().getId() : null
                    )))
                    .append("`")
                    .append("\n");
        }

        return sb.toString();
    }

    private String formatRaidDate(LocalDateTime raidDate) {
        if (raidDate == null) {
            return "-";
        }

        String formatted = DISCORD_RAID_DATE_FORMATTER.format(raidDate);
        if (formatted.isBlank()) {
            return "-";
        }

        return Character.toUpperCase(formatted.charAt(0)) + formatted.substring(1);
    }

    private String buildRosterSummary(List<Personnage> group1, List<Personnage> group2) {
        List<Personnage> roster = Stream.concat(group1.stream(), group2.stream()).collect(Collectors.toList());
        long tanks = roster.stream().filter(personnage -> hasRole(personnage, "TANK")).count();
        long heals = roster.stream().filter(personnage -> hasRole(personnage, "HEAL")).count();
        long dps = roster.stream().filter(personnage -> hasRole(personnage, "DPS")).count();

        return "Total: **" + roster.size() + "**\n"
                + "Tanks: **" + tanks + "** | Heals: **" + heals + "** | DPS: **" + dps + "**";
    }

    private String buildConfirmationSummary(int confirmedCount, int cancelledCount, int pendingCount) {
        return "Confirmes: **" + confirmedCount + "**\n"
                + "Annules: **" + cancelledCount + "**\n"
                + "En attente: **" + pendingCount + "**";
    }

    private int countConfirmations(List<Personnage> group1,
                                   List<Personnage> group2,
                                   Map<Long, RaidInscription.StatutInscription> confirmationMap,
                                   RaidInscription.StatutInscription expectedStatus) {
        return (int) Stream.concat(group1.stream(), group2.stream())
                .map(Personnage::getJoueur)
                .filter(Objects::nonNull)
                .map(joueur -> confirmationMap.get(joueur.getId()))
                .filter(expectedStatus::equals)
                .count();
    }

    private boolean hasRole(Personnage personnage, String expectedRole) {
        return personnage != null
                && personnage.getRole() != null
                && expectedRole.equalsIgnoreCase(personnage.getRole().trim());
    }

    private String getConfirmationMarker(RaidInscription.StatutInscription status) {
        if (status == RaidInscription.StatutInscription.CONFIRME) {
            return "OK";
        }

        if (status == RaidInscription.StatutInscription.ANNULE) {
            return "NON";
        }

        return "ATT";
    }

    private String getEmojiFor(Personnage personnage) {
        Map<String, String> emojiMap = Map.ofEntries(
                Map.entry("DK-Sang", "<:dk_sang:1363215681570603170>"),
                Map.entry("DK-Givre", "<:dk_givre:1363215048675299479>"),
                Map.entry("DK-Impie", "<:dk_impie:1363215050155884745>"),
                Map.entry("Druide-Feral", "<:druide_feral:1363215056023588924>"),
                Map.entry("Druide-Restauration", "<:druide_restauration:1363229950353608787>"),
                Map.entry("Druide-Equilibre", "<:druide_equilibre:1363215053142364221>"),
                Map.entry("Moine-Maitre brasseur", "<:Brewmaster:637564262167871489>"),
                Map.entry("Moine-Tisse-brume", "<:Mistweaver:637564262289637433>"),
                Map.entry("Moine-Marche-vent", "<:Windwalker:637564262054625281>"),
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

        String key = personnage.getClasse() + "-" + personnage.getSpecialisation();
        String exactEmoji = emojiMap.get(key);
        if (exactEmoji != null) {
            return exactEmoji;
        }

        String normalizedKey = normalizeEmojiKey(key);
        return emojiMap.entrySet().stream()
                .filter(entry -> normalizeEmojiKey(entry.getKey()).equals(normalizedKey))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("🧍");
    }

    private List<Joueur> getJoueursFromRaid(Raid raid) {
        return Stream.concat(raid.getGroup1().stream(), raid.getGroup2().stream())
                .map(Personnage::getJoueur)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    private String generateMentionLine(List<Joueur> joueurs) {
        return joueurs.stream()
                .map(joueur -> "<@" + joueur.getDiscordId() + ">")
                .collect(Collectors.joining(" "));
    }

    private String normalizeEmojiKey(String value) {
        if (value == null) {
            return "";
        }

        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]", "");
    }

    private String getBotAvatarUrl() {
        if (jda.getSelfUser().getEffectiveAvatarUrl() != null) {
            return jda.getSelfUser().getEffectiveAvatarUrl();
        }
        return jda.getSelfUser().getDefaultAvatarUrl();
    }

    private String buildDiscordMessageUrl(String guildId, String channelId, Long messageId) {
        if (guildId == null || channelId == null || messageId == null) {
            return null;
        }
        return "https://discord.com/channels/" + guildId + "/" + channelId + "/" + messageId;
    }

    private boolean hasPublishedSnapshot(Raid raid) {
        return raid.getLastPublishedAt() != null
                && ((raid.getLastPublishedGroup1Snapshot() != null && !raid.getLastPublishedGroup1Snapshot().isBlank())
                || (raid.getLastPublishedGroup2Snapshot() != null && !raid.getLastPublishedGroup2Snapshot().isBlank()));
    }

    private String serializeSnapshot(Set<Personnage> personnages) {
        return personnages.stream()
                .map(Personnage::getId)
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    private List<Personnage> loadSnapshotCharacters(String snapshot) {
        if (snapshot == null || snapshot.isBlank()) {
            return List.of();
        }

        List<Long> ids = Stream.of(snapshot.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(Long::parseLong)
                .collect(Collectors.toList());

        if (ids.isEmpty()) {
            return List.of();
        }

        Map<Long, Personnage> byId = personnageRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Personnage::getId, personnage -> personnage));

        return ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private List<String> computeCharacterDifference(List<PersonnageDTO> left, List<PersonnageDTO> right) {
        Set<String> rightKeys = right.stream()
                .map(this::characterKey)
                .collect(Collectors.toSet());

        return left.stream()
                .filter(personnage -> !rightKeys.contains(characterKey(personnage)))
                .map(PersonnageDTO::getNom)
                .distinct()
                .collect(Collectors.toList());
    }

    private String characterKey(PersonnageDTO personnage) {
        return personnage.getId() + "::" + personnage.getNom();
    }

    private PersonnageDTO personnageToDto(Personnage personnage) {
        return new PersonnageDTO(
                personnage.getId(),
                personnage.getNom(),
                personnage.getClasse(),
                personnage.getRole(),
                personnage.getJoueur() != null ? personnage.getJoueur().getPseudo() : null,
                personnage.getSpecialisation(),
                personnage.isMain()
        );
    }
}
