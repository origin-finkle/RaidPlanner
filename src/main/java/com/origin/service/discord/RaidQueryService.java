package com.origin.service.discord;

import com.origin.dto.JoueurDTO;
import com.origin.dto.PersonnageDTO;
import com.origin.dto.RaidDiagnosticDTO;
import com.origin.dto.RaidDTO;
import com.origin.dto.RaidDayResponse;
import com.origin.dto.RaidMessageDiagnosticDTO;
import com.origin.dto.RaidSignupDiagnosticDTO;
import com.origin.entity.Inscription;
import com.origin.entity.Joueur;
import com.origin.entity.Personnage;
import com.origin.entity.Raid;
import com.origin.enumOrigin.StatutParticipation;
import com.origin.repository.InscriptionRepository;
import com.origin.repository.PersonnageRepository;
import com.origin.repository.RaidRepository;
import com.origin.service.JoueurService;
import com.origin.service.PersonnageService;
import com.origin.service.RaidTemplateOccurrenceService;
import com.origin.util.ParsedEmoji;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

@Slf4j
@Service
@RequiredArgsConstructor
public class RaidQueryService {
    private static final String MANUAL_SIGNUP_COMMENT = "MANUAL_OFFICER_ADD";

    private static final Pattern EMOJI_PATTERN = Pattern.compile("<:([\\w]+):(\\d+)>");
    private static final Pattern STATUS_NAME_PATTERN = Pattern.compile("\\*\\*(.+?)\\*\\*");

    private final JDA jda;
    private final RaidRepository raidRepository;
    private final InscriptionRepository inscriptionRepository;
    private final PersonnageRepository personnageRepository;
    private final PersonnageService personnageService;
    private final RaidHelperParserService parserService;
    private final JoueurService joueurService;
    private final RaidTemplateOccurrenceService raidTemplateOccurrenceService;

    public List<RaidDayResponse> getRaidsGroupedByDay() {
        LocalDateTime start = getCurrentResetWeekStart();
        LocalDateTime endExclusive = start.plusDays(14);
        raidTemplateOccurrenceService.ensureOccurrencesForPublicationWindow(start.toLocalDate(), 2);

        List<Raid> allRaids = filterDisplayedRaids(
                raidRepository.findByDateGreaterThanEqualAndDateLessThanOrderByDateAsc(start, endExclusive)
        );
        Map<Long, List<JoueurDTO>> signupsByRaidId = loadPersistedSignupsByRaidId(allRaids);
        Map<Long, Raid> raidsWithGroupsById = loadRaidsWithGroupsById(allRaids);
        List<RaidDTO> raidDTOList = new ArrayList<>();

        for (Raid raid : allRaids) {
            Raid detailedRaid = raidsWithGroupsById.getOrDefault(raid.getId(), raid);
            List<JoueurDTO> joueurDTOList = signupsByRaidId.getOrDefault(raid.getId(), List.of());
            raidDTOList.add(toRaidDTO(detailedRaid, joueurDTOList));
        }

        Map<LocalDate, List<RaidDTO>> grouped = raidDTOList.stream()
                .collect(Collectors.groupingBy(raid -> raid.getHeure().toLocalDate()));

        return grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new RaidDayResponse(
                        entry.getKey().toString(),
                        entry.getValue().stream()
                                .sorted(Comparator.comparing(RaidDTO::getHeure))
                                .collect(Collectors.toList())
                ))
                .collect(Collectors.toList());
    }

    public List<Raid> getBestRaidsInRange(LocalDateTime start, LocalDateTime endExclusive) {
        raidTemplateOccurrenceService.ensureOccurrencesForPublicationWindow(start.toLocalDate(), 2);
        return filterDisplayedRaids(
                raidRepository.findByDateGreaterThanEqualAndDateLessThanOrderByDateAsc(start, endExclusive)
        );
    }

    private LocalDateTime getCurrentResetWeekStart() {
        LocalDate today = LocalDate.now();
        LocalDate resetStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.WEDNESDAY));
        return LocalDateTime.of(resetStart, LocalTime.MIN);
    }

    public List<Map<String, Object>> debugChannelMessages(String channelId, int limit) {
        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            return List.of(Map.of("error", "channel_not_found", "channelId", channelId));
        }

        List<Message> messages = channel.getHistory().retrievePast(Math.max(1, Math.min(limit, 30))).complete();
        List<Map<String, Object>> debug = new ArrayList<>();

        for (Message message : messages) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("messageId", message.getId());
            item.put("createdAt", message.getTimeCreated().toString());
            item.put("author", message.getAuthor().getName());
            item.put("bot", message.getAuthor().isBot());
            item.put("contentRaw", message.getContentRaw());
            item.put("buttonUrls", message.getButtons().stream().map(Button::getUrl).collect(Collectors.toList()));

            if (message.getEmbeds().isEmpty()) {
                item.put("hasEmbed", false);
                debug.add(item);
                continue;
            }

            MessageEmbed embed = message.getEmbeds().get(0);
            item.put("hasEmbed", true);
            item.put("title", embed.getTitle());
            item.put("description", embed.getDescription());
            item.put("fieldCount", embed.getFields().size());
            item.put("parsedAsRaidHelper", parserService.isRaidHelperEmbed(message));
            item.put("isCompositionTool", parserService.isCompositionToolEmbed(embed));
            item.put("extractedNom", parserService.extractNom(embed));
            item.put("extractedDate", parserService.extractDateFromEmbed(embed));
            item.put("raidHelperId", parserService.extractRaidHelperId(message).orElse(null));
            item.put("embedDiagnostic", parserService.describeEmbed(embed));
            debug.add(item);
        }

        return debug;
    }

    public RaidDiagnosticDTO getRaidDiagnostic(Long raidId) {
        Raid raid = raidRepository.findById(raidId)
                .orElseThrow(() -> new IllegalArgumentException("Raid introuvable : " + raidId));

        List<RaidSignupDiagnosticDTO> snapshotSignups = getPersistedSignups(raid).stream()
                .map(this::toSignupDiagnosticDto)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return RaidDiagnosticDTO.builder()
                .raidId(raid.getId())
                .nom(raid.getNom())
                .date(raid.getDate())
                .storedChannelId(raid.getChannelId())
                .storedMessageId(raid.getDiscordMessageId())
                .storedRaidHelperId(raid.getRaidHelperId())
                .publishedChannelId(raid.getPublishedChannelId())
                .publishedMessageId(raid.getPublishedMessageId())
                .storedMessage(null)
                .resolvedMessage(null)
                .sourceChanged(false)
                .liveSignups(snapshotSignups)
                .snapshotSignups(snapshotSignups)
                .liveOnlyPlayers(List.of())
                .snapshotOnlyPlayers(List.of())
                .build();
    }

    private Map<Long, Raid> loadRaidsWithGroupsById(List<Raid> raids) {
        List<Long> raidIds = raids.stream()
                .map(Raid::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (raidIds.isEmpty()) {
            return Map.of();
        }

        return raidRepository.findAllWithGroupsByIdIn(raidIds).stream()
                .collect(Collectors.toMap(Raid::getId, raid -> raid));
    }

    private List<Raid> filterDisplayedRaids(List<Raid> raids) {
        Map<String, Raid> canonicalWeeklyRaids = new LinkedHashMap<>();
        List<Raid> displayableRaids = new ArrayList<>();

        for (Raid raid : raids) {
            if (raid.getTemplate() != null && !isAlignedTemplateOccurrence(raid)) {
                log.warn(
                        "Occurrence template ignoree car incoherente: raidId={} nom={} date={} templateId={} jourTemplate={}",
                        raid.getId(),
                        raid.getNom(),
                        raid.getDate(),
                        raid.getTemplate().getId(),
                        raid.getTemplate().getJourSemaine()
                );
                continue;
            }

            String canonicalSlot = resolveCanonicalWeeklySlot(raid);
            if (canonicalSlot == null || raid.getDate() == null) {
                displayableRaids.add(raid);
                continue;
            }

            LocalDate weekStart = raid.getDate().toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.WEDNESDAY));
            String key = canonicalSlot + ":" + weekStart;
            canonicalWeeklyRaids.merge(key, raid, this::pickDisplayCanonicalRaid);
        }

        displayableRaids.addAll(canonicalWeeklyRaids.values());
        displayableRaids.sort(Comparator
                .comparing(Raid::getDate)
                .thenComparing(raid -> raid.getId() != null ? raid.getId() : Long.MAX_VALUE));
        return displayableRaids;
    }

    private boolean isAlignedTemplateOccurrence(Raid raid) {
        if (raid == null || raid.getTemplate() == null || raid.getDate() == null) {
            return true;
        }

        String expectedSlot = normalizeDayOfWeek(raid.getTemplate().getJourSemaine());
        String actualSlot = normalizeDayOfWeek(raid.getDate().getDayOfWeek().name());
        return expectedSlot == null || actualSlot == null || expectedSlot.equals(actualSlot);
    }

    private Raid pickDisplayCanonicalRaid(Raid left, Raid right) {
        int leftScore = displayAlignmentScore(left);
        int rightScore = displayAlignmentScore(right);
        if (leftScore != rightScore) {
            return rightScore > leftScore ? right : left;
        }

        LocalDateTime leftDate = left.getDate() != null ? left.getDate() : LocalDateTime.MAX;
        LocalDateTime rightDate = right.getDate() != null ? right.getDate() : LocalDateTime.MAX;
        int dateComparison = leftDate.compareTo(rightDate);
        if (dateComparison != 0) {
            return dateComparison <= 0 ? left : right;
        }

        long leftId = left.getId() != null ? left.getId() : Long.MAX_VALUE;
        long rightId = right.getId() != null ? right.getId() : Long.MAX_VALUE;
        return leftId <= rightId ? left : right;
    }

    private int displayAlignmentScore(Raid raid) {
        if (raid.getDate() == null) {
            return 0;
        }

        int score = 0;
        if (raid.getTemplate() != null) {
            score += 6;
        }

        String canonicalSlot = resolveCanonicalWeeklySlot(raid);
        if (canonicalSlot != null && canonicalSlot.equals(normalizeDayOfWeek(raid.getDate().getDayOfWeek().name()))) {
            score += 4;
        }

        if (raid.getTemplate() != null && raid.getTemplate().getNom() != null && raid.getTemplate().getNom().equals(raid.getNom())) {
            score += 4;
        }

        if (raid.getSignupMessageId() != null) {
            score += 3;
        }
        if (raid.getPublishedMessageId() != null) {
            score += 2;
        }

        return score;
    }

    private String resolveCanonicalWeeklySlot(Raid raid) {
        if (raid == null) {
            return null;
        }

        if (raid.getTemplate() == null) {
            return null;
        }

        String slotFromName = canonicalSlotFromName(raid.getNom());
        if (slotFromName != null) {
            return slotFromName;
        }

        if (raid.getTemplate() != null) {
            String slotFromTemplate = normalizeDayOfWeek(raid.getTemplate().getJourSemaine());
            if (slotFromTemplate != null) {
                return slotFromTemplate;
            }
        }

        return null;
    }

    private String canonicalSlotFromName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = normalizeText(value);
        if (normalized.startsWith("raid du ") || normalized.startsWith("raid de ") || normalized.startsWith("raid d ")) {
            if (normalized.contains(" mercredi")) {
                return "WEDNESDAY";
            }
            if (normalized.contains(" jeudi")) {
                return "THURSDAY";
            }
            if (normalized.contains(" dimanche")) {
                return "SUNDAY";
            }
            if (normalized.contains(" lundi")) {
                return "MONDAY";
            }
            if (normalized.contains(" mardi")) {
                return "TUESDAY";
            }
            if (normalized.contains(" vendredi")) {
                return "FRIDAY";
            }
            if (normalized.contains(" samedi")) {
                return "SATURDAY";
            }
        }

        return null;
    }

    private String normalizeDayOfWeek(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        switch (normalized) {
            case "MONDAY":
            case "LUNDI":
                return "MONDAY";
            case "TUESDAY":
            case "MARDI":
                return "TUESDAY";
            case "WEDNESDAY":
            case "MERCREDI":
                return "WEDNESDAY";
            case "THURSDAY":
            case "JEUDI":
                return "THURSDAY";
            case "FRIDAY":
            case "VENDREDI":
                return "FRIDAY";
            case "SATURDAY":
            case "SAMEDI":
                return "SATURDAY";
            case "SUNDAY":
            case "DIMANCHE":
                return "SUNDAY";
            default:
                return null;
        }
    }

    private String normalizeText(String value) {
        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace('\'', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private RaidDTO toRaidDTO(Raid raid, List<JoueurDTO> joueurDTOList) {
        return new RaidDTO(
                raid.getId(),
                raid.getNom(),
                raid.getDate(),
                raid.getChannelId(),
                joueurDTOList,
                raid.getGroup1().stream().map(personnageService::toDTO).collect(Collectors.toList()),
                raid.getGroup2().stream().map(personnageService::toDTO).collect(Collectors.toList()),
                raid.getCompositionStatus(),
                raid.isCompositionLocked(),
                raid.getLastPublishedAt(),
                raid.isIgnoreWeeklyConflicts()
        );
    }

    public List<JoueurDTO> getInscriptionsFromRaidHelper(String channelId, Long messageId) {
        return getInscriptionsFromRaidHelper(channelId, messageId, null, null);
    }

    @Transactional
    public List<JoueurDTO> getInscriptionsFromRaidHelper(Raid raid) {
        if (raid == null) {
            return List.of();
        }
        return getPersistedSignups(raid);
    }

    @Transactional(readOnly = true)
    public List<JoueurDTO> getPersistedSignups(Raid raid) {
        return loadPersistedSignups(raid);
    }

    private List<JoueurDTO> mergeWithManualSignups(Raid raid, List<JoueurDTO> liveSignups) {
        if (raid == null || raid.getId() == null) {
            return liveSignups;
        }

        Map<Long, JoueurDTO> mergedByPlayerId = new LinkedHashMap<>();
        for (JoueurDTO signup : liveSignups) {
            if (signup != null && signup.getId() != null) {
                mergedByPlayerId.put(signup.getId(), signup);
            }
        }

        inscriptionRepository.findDetailedByRaidIdOrderByIdAsc(raid.getId()).stream()
                .filter(this::isManualSignup)
                .map(this::toSignupDto)
                .filter(Objects::nonNull)
                .forEach(signup -> {
                    if (signup.getId() != null) {
                        mergedByPlayerId.putIfAbsent(signup.getId(), signup);
                    }
                });

        return new ArrayList<>(mergedByPlayerId.values());
    }

    private List<JoueurDTO> getInscriptionsFromRaidHelper(String channelId,
                                                          Long messageId,
                                                          String expectedNom,
                                                          LocalDateTime expectedDate) {
        return extractSignupsFromMessage(channelId, messageId, resolveSignupSource(channelId, messageId, expectedNom, expectedDate).message());
    }

    private List<JoueurDTO> extractSignupsFromMessage(String channelId, Long messageId, Message message) {
        if (messageId == null) {
            log.warn("Aucun message Discord source pour le salon {}", channelId);
            return List.of();
        }

        if (message == null) {
            return List.of();
        }
        if (message.getEmbeds().isEmpty()) {
            log.warn("No embed found in message {}", messageId);
            return List.of();
        }

        MessageEmbed embed = message.getEmbeds().get(0);
        List<MessageEmbed.Field> fields = embed.getFields();

        Map<String, StatutParticipation> pseudoToStatus = new HashMap<>();
        for (MessageEmbed.Field field : fields) {
            String raw = field.getValue();
            if (raw == null) {
                continue;
            }

            String rawLower = raw.toLowerCase(Locale.ROOT);
            if (rawLower.startsWith("<:tentative:")) {
                extractStatus(pseudoToStatus, rawLower, StatutParticipation.TENTATIVE);
            } else if (rawLower.startsWith("<:bench:")) {
                extractStatus(pseudoToStatus, rawLower, StatutParticipation.BENCH);
            } else if (rawLower.startsWith("<:late:")) {
                extractStatus(pseudoToStatus, rawLower, StatutParticipation.LATE);
            }
        }

        List<JoueurDTO> joueurDTOList = new ArrayList<>();
        Set<String> addedPlayers = new HashSet<>();

        for (MessageEmbed.Field field : fields) {
            String value = field.getValue();
            if (value == null) {
                continue;
            }

            String[] lines = value.split("\\n");
            for (String line : lines) {
                Optional<JoueurDTO> joueurDTO = extractJoueurFromLine(line, pseudoToStatus);
                if (joueurDTO.isEmpty()) {
                    continue;
                }

                String dedupeKey = cleanServerPseudo(joueurDTO.get().getServerPseudo());
                if (!addedPlayers.add(dedupeKey)) {
                    continue;
                }

                joueurDTOList.add(joueurDTO.get());
            }
        }

        for (Map.Entry<String, StatutParticipation> entry : pseudoToStatus.entrySet()) {
            String cleaned = cleanServerPseudo(entry.getKey());
            if (cleaned == null || addedPlayers.contains(cleaned)) {
                continue;
            }

            JoueurDTO joueurDTO = extractJoueurStatus(entry.getKey(), entry.getValue());
            if (joueurDTO != null) {
                joueurDTOList.add(joueurDTO);
                addedPlayers.add(cleaned);
            }
        }

        return joueurDTOList;
    }

    private void persistSignupSnapshot(Raid raid, List<JoueurDTO> signups) {
        if (raid.getId() == null) {
            return;
        }

        List<Inscription> preservedManualSignups = inscriptionRepository.findDetailedByRaidIdOrderByIdAsc(raid.getId()).stream()
                .filter(this::isManualSignup)
                .collect(Collectors.toList());

        inscriptionRepository.deleteByRaidId(raid.getId());

        List<Inscription> snapshot = signups.stream()
                .map(this::toSnapshotInscription)
                .filter(Objects::nonNull)
                .map(inscription -> {
                    inscription.setRaid(raid);
                    return inscription;
                })
                .collect(Collectors.toList());

        if (!snapshot.isEmpty()) {
            inscriptionRepository.saveAll(snapshot);
        }

        if (!preservedManualSignups.isEmpty()) {
            Set<Long> livePlayerIds = snapshot.stream()
                    .map(Inscription::getPersonnage)
                    .filter(Objects::nonNull)
                    .map(Personnage::getJoueur)
                    .filter(Objects::nonNull)
                    .map(Joueur::getId)
                    .collect(Collectors.toSet());

            List<Inscription> manualToRestore = preservedManualSignups.stream()
                    .filter(inscription -> inscription.getPersonnage() != null && inscription.getPersonnage().getJoueur() != null)
                    .filter(inscription -> !livePlayerIds.contains(inscription.getPersonnage().getJoueur().getId()))
                    .map(inscription -> Inscription.builder()
                            .raid(raid)
                            .personnage(inscription.getPersonnage())
                            .statut(Optional.ofNullable(inscription.getStatut()).orElse(StatutParticipation.TITULAIRE.name()))
                            .commentaire(MANUAL_SIGNUP_COMMENT)
                            .build())
                    .collect(Collectors.toList());

            if (!manualToRestore.isEmpty()) {
                inscriptionRepository.saveAll(manualToRestore);
            }
        }
    }

    private Inscription toSnapshotInscription(JoueurDTO joueurDTO) {
        PersonnageDTO personnageDTO = joueurDTO.getPersonnageMain();
        if (personnageDTO == null || personnageDTO.getId() == null) {
            return null;
        }

        Optional<Personnage> personnageOpt = personnageRepository.findById(personnageDTO.getId());
        if (personnageOpt.isEmpty()) {
            return null;
        }

        return Inscription.builder()
                .personnage(personnageOpt.get())
                .statut(Optional.ofNullable(joueurDTO.getStatutParticipation())
                        .orElse(StatutParticipation.TITULAIRE)
                        .name())
                .commentaire(joueurDTO.getCommentaireInscription())
                .build();
    }

    private List<JoueurDTO> loadPersistedSignups(Raid raid) {
        if (raid.getId() == null) {
            return List.of();
        }

        return deduplicateSignupsByPlayer(inscriptionRepository.findDetailedByRaidIdOrderByIdAsc(raid.getId()).stream()
                .map(this::toSignupDto)
                .filter(Objects::nonNull)
                .collect(Collectors.toList()));
    }

    private Map<Long, List<JoueurDTO>> loadPersistedSignupsByRaidId(List<Raid> raids) {
        List<Long> raidIds = raids.stream()
                .map(Raid::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (raidIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<JoueurDTO>> signupsByRaidId = new HashMap<>();
        for (Inscription inscription : inscriptionRepository.findDetailedByRaidIdInOrderByRaidIdAscIdAsc(raidIds)) {
            JoueurDTO signup = toSignupDto(inscription);
            if (signup == null || inscription.getRaid() == null || inscription.getRaid().getId() == null) {
                continue;
            }

            signupsByRaidId
                    .computeIfAbsent(inscription.getRaid().getId(), ignored -> new ArrayList<>())
                    .add(signup);
        }

        signupsByRaidId.replaceAll((raidId, signups) -> deduplicateSignupsByPlayer(signups));
        return signupsByRaidId;
    }

    private List<JoueurDTO> deduplicateSignupsByPlayer(List<JoueurDTO> signups) {
        Map<String, JoueurDTO> latestByPlayer = new LinkedHashMap<>();

        for (JoueurDTO signup : signups) {
            String key = signupDedupKey(signup);
            if (key == null) {
                continue;
            }
            JoueurDTO current = latestByPlayer.get(key);
            if (current == null || compareSignupPriority(signup, current) < 0) {
                latestByPlayer.put(key, signup);
            }
        }

        return new ArrayList<>(latestByPlayer.values());
    }

    private String signupDedupKey(JoueurDTO signup) {
        if (signup == null) {
            return null;
        }
        String discordId = cleanServerPseudo(signup.getDiscordId());
        if (discordId != null && !discordId.isBlank()) {
            return "discord:" + discordId;
        }
        String serverPseudo = cleanServerPseudo(signup.getServerPseudo());
        if (serverPseudo != null && !serverPseudo.isBlank()) {
            return "server:" + serverPseudo;
        }

        String pseudo = cleanServerPseudo(signup.getPseudo());
        if (pseudo != null && !pseudo.isBlank()) {
            return "pseudo:" + pseudo;
        }

        if (signup.getId() != null) {
            return "id:" + signup.getId();
        }

        return null;
    }

    private int compareSignupPriority(JoueurDTO left, JoueurDTO right) {
        int leftScore = signupPriority(left);
        int rightScore = signupPriority(right);
        if (leftScore != rightScore) {
            return rightScore - leftScore;
        }

        long leftId = left.getId() != null ? left.getId() : Long.MAX_VALUE;
        long rightId = right.getId() != null ? right.getId() : Long.MAX_VALUE;
        return Long.compare(leftId, rightId);
    }

    private int signupPriority(JoueurDTO joueur) {
        if (joueur == null || joueur.getStatutParticipation() == null) {
            return 1;
        }

        switch (joueur.getStatutParticipation()) {
            case ABSENCE:
                return 5;
            case BENCH:
                return 4;
            case LATE:
                return 3;
            case TENTATIVE:
                return 2;
            case TITULAIRE:
            default:
                return 1;
        }
    }

    private boolean isManualSignup(Inscription inscription) {
        return inscription != null && MANUAL_SIGNUP_COMMENT.equals(inscription.getCommentaire());
    }

    private RaidSignupDiagnosticDTO toSignupDiagnosticDto(JoueurDTO joueurDTO) {
        if (joueurDTO == null || joueurDTO.getPersonnageMain() == null) {
            return null;
        }

        PersonnageDTO personnage = joueurDTO.getPersonnageMain();
        return RaidSignupDiagnosticDTO.builder()
                .joueurId(joueurDTO.getId())
                .pseudo(joueurDTO.getPseudo())
                .pseudoIhm(joueurDTO.getPseudoIhm())
                .serverPseudo(joueurDTO.getServerPseudo())
                .personnageId(personnage.getId())
                .personnageNom(personnage.getNom())
                .classe(personnage.getClasse())
                .specialisation(personnage.getSpecialisation())
                .role(personnage.getRole())
                .main(personnage.isMain())
                .statutParticipation(joueurDTO.getStatutParticipation())
                .build();
    }

    private List<String> computeSignupDifference(List<RaidSignupDiagnosticDTO> base, List<RaidSignupDiagnosticDTO> comparison) {
        Set<String> comparisonKeys = comparison.stream()
                .map(this::signupDisplayKey)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return base.stream()
                .map(this::signupDisplayKey)
                .filter(Objects::nonNull)
                .filter(key -> !comparisonKeys.contains(key))
                .distinct()
                .collect(Collectors.toList());
    }

    private String signupDisplayKey(RaidSignupDiagnosticDTO signup) {
        if (signup == null) {
            return null;
        }

        String characterName = Optional.ofNullable(signup.getPersonnageNom()).orElse("").trim();
        String fallback = Optional.ofNullable(signup.getPseudoIhm()).orElse(signup.getServerPseudo());
        String displayName = !characterName.isBlank() ? characterName : fallback;
        if (displayName == null || displayName.isBlank()) {
            return null;
        }

        return displayName;
    }

    private RaidMessageDiagnosticDTO toMessageDiagnostic(Message message) {
        if (message == null) {
            return null;
        }

        MessageEmbed embed = message.getEmbeds().isEmpty() ? null : message.getEmbeds().get(0);
        Message channelMessage = message;
        String channelId = channelMessage.getChannel().getId();
        String channelName = channelMessage.getChannel().getName();
        String guildId = channelMessage.getGuild() != null ? channelMessage.getGuild().getId() : null;
        Optional<RaidHelperParserService.DiscordMessageLink> linkedMessage = parserService.extractLinkedDiscordMessage(message);

        return RaidMessageDiagnosticDTO.builder()
                .channelId(channelId)
                .channelName(channelName)
                .guildId(guildId)
                .messageId(channelMessage.getIdLong())
                .url(buildDiscordMessageUrl(guildId, channelId, channelMessage.getIdLong()))
                .author(channelMessage.getAuthor().getName())
                .bot(channelMessage.getAuthor().isBot())
                .createdAt(channelMessage.getTimeCreated().toLocalDateTime())
                .title(embed != null ? embed.getTitle() : null)
                .description(embed != null ? embed.getDescription() : null)
                .parsedAsRaidHelper(parserService.isRaidHelperEmbed(message))
                .compositionTool(embed != null && parserService.isCompositionToolEmbed(embed))
                .placeholderSignup(embed != null && parserService.isPlaceholderSignupEmbed(embed))
                .extractedNom(embed != null ? parserService.extractNom(embed) : null)
                .extractedDate(embed != null ? parserService.extractDateFromEmbed(embed) : null)
                .raidHelperId(parserService.extractRaidHelperId(message).orElse(null))
                .signupLineCount(embed != null ? countLikelySignupLines(embed) : 0)
                .linkedChannelId(linkedMessage.map(RaidHelperParserService.DiscordMessageLink::getChannelId).orElse(null))
                .linkedMessageId(linkedMessage.map(link -> Long.valueOf(link.getMessageId())).orElse(null))
                .build();
    }

    private String buildDiscordMessageUrl(String guildId, String channelId, Long messageId) {
        if (guildId == null || channelId == null || messageId == null) {
            return null;
        }

        return "https://discord.com/channels/" + guildId + "/" + channelId + "/" + messageId;
    }

    private Message loadMessage(String channelId, Long messageId) {
        if (channelId == null || messageId == null) {
            return null;
        }

        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            return null;
        }

        try {
            return channel.retrieveMessageById(messageId).complete();
        } catch (Exception exception) {
            log.debug("Impossible de charger le message {} du salon {}: {}", messageId, channelId, exception.getMessage());
            return null;
        }
    }

    private JoueurDTO toSignupDto(Inscription inscription) {
        if (inscription.getPersonnage() == null || inscription.getPersonnage().getJoueur() == null) {
            return null;
        }

        JoueurDTO joueurDTO = joueurService.toDTO(inscription.getPersonnage().getJoueur(), false);
        StatutParticipation statut = parseStatut(inscription.getStatut());
        return new JoueurDTO(
                joueurDTO.getId(),
                joueurDTO.getDiscordId(),
                joueurDTO.getPseudo(),
                joueurDTO.getPseudoIhm(),
                joueurDTO.getServerPseudo(),
                joueurDTO.getPersonnageMain(),
                joueurDTO.getRerolls(),
                joueurDTO.isRaider(),
                statut,
                inscription.getCommentaire()
        );
    }

    private StatutParticipation parseStatut(String value) {
        if (value == null || value.isBlank()) {
            return StatutParticipation.TITULAIRE;
        }

        try {
            return StatutParticipation.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return StatutParticipation.TITULAIRE;
        }
    }

    private ResolvedSignupSource resolveSignupSource(String channelId,
                                                     Long messageId,
                                                     String expectedNom,
                                                     LocalDateTime expectedDate) {
        if (messageId == null) {
            return new ResolvedSignupSource(channelId, messageId, null);
        }

        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            log.warn("Channel not found: {}", channelId);
            return new ResolvedSignupSource(channelId, messageId, null);
        }

        Message message;
        try {
            message = channel.retrieveMessageById(messageId).complete();
        } catch (Exception exception) {
            logMessageRetrievalFailure(channelId, messageId, exception);
            return new ResolvedSignupSource(channelId, messageId, null);
        }

        if (message == null) {
            return new ResolvedSignupSource(channelId, messageId, null);
        }

        Message resolvedMessage = resolveBestSignupSourceMessage(message, expectedNom, expectedDate);
        String resolvedChannelId = resolvedMessage.getChannel().getId();
        Long resolvedMessageId = resolvedMessage.getIdLong();
        return new ResolvedSignupSource(resolvedChannelId, resolvedMessageId, resolvedMessage);
    }

    private void persistResolvedSignupSource(Raid raid, ResolvedSignupSource resolvedSource) {
        if (raid == null || resolvedSource == null || resolvedSource.message() == null) {
            return;
        }

        boolean changed = false;
        if (!Objects.equals(raid.getChannelId(), resolvedSource.channelId())) {
            raid.setChannelId(resolvedSource.channelId());
            changed = true;
        }
        if (!Objects.equals(raid.getDiscordMessageId(), resolvedSource.messageId())) {
            raid.setDiscordMessageId(resolvedSource.messageId());
            changed = true;
        }

        String resolvedRaidHelperId = parserService.extractRaidHelperId(resolvedSource.message()).orElse(null);
        if (resolvedRaidHelperId != null && !Objects.equals(raid.getRaidHelperId(), resolvedRaidHelperId)) {
            Optional<Raid> owner = raidRepository.findByRaidHelperId(resolvedRaidHelperId);
            if (owner.isEmpty() || Objects.equals(owner.get().getId(), raid.getId())) {
                raid.setRaidHelperId(resolvedRaidHelperId);
                changed = true;
            } else {
                log.debug(
                        "RaidHelperId {} deja attache au raid {}, on conserve seulement le cache message/channel pour le raid {}",
                        resolvedRaidHelperId,
                        owner.get().getId(),
                        raid.getId()
                );
            }
        }

        if (changed) {
            raidRepository.save(raid);
        }
    }

    private Message resolveBestSignupSourceMessage(Message initialMessage,
                                                   String expectedNom,
                                                   LocalDateTime expectedDate) {
        if (initialMessage.getEmbeds().isEmpty()) {
            return initialMessage;
        }

        MessageEmbed initialEmbed = initialMessage.getEmbeds().get(0);
        String targetNom = expectedNom != null ? expectedNom : parserService.extractNom(initialEmbed);
        LocalDateTime targetDate = expectedDate != null ? expectedDate : parserService.extractDateFromEmbed(initialEmbed);
        Optional<Message> linkedMessage = resolveLinkedDiscordMessage(initialMessage);

        List<TextChannel> candidateChannels = new ArrayList<>();
        TextChannel initialChannel = initialMessage.getChannel().asTextChannel();
        if (initialChannel != null) {
            candidateChannels.add(initialChannel);
        }
        linkedMessage.map(message -> message.getChannel().asTextChannel())
                .filter(Objects::nonNull)
                .filter(channel -> candidateChannels.stream().noneMatch(existing -> existing.getId().equals(channel.getId())))
                .ifPresent(candidateChannels::add);

        List<Message> candidates = new ArrayList<>();
        candidates.add(initialMessage);
        linkedMessage.ifPresent(candidates::add);

        for (TextChannel candidateChannel : candidateChannels) {
            try {
                candidates.addAll(candidateChannel.getHistory().retrievePast(100).complete());
            } catch (Exception exception) {
                log.debug("Impossible de charger l'historique du salon {}: {}", candidateChannel.getId(), exception.getMessage());
            }
        }

        return candidates.stream()
                .filter(Objects::nonNull)
                .filter(candidate -> !candidate.getEmbeds().isEmpty())
                .filter(parserService::isRaidHelperEmbed)
                .filter(candidate -> matchesExpectedRaid(candidate, targetNom, targetDate))
                .max(Comparator
                        .comparingInt(this::scoreSignupSourceMessage)
                        .thenComparingLong(Message::getIdLong))
                .orElse(initialMessage);
    }

    private Optional<Message> resolveLinkedDiscordMessage(Message message) {
        return parserService.extractLinkedDiscordMessage(message)
                .flatMap(link -> {
                    TextChannel targetChannel = jda.getTextChannelById(link.getChannelId());
                    if (targetChannel == null) {
                        return Optional.empty();
                    }

                    try {
                        return Optional.ofNullable(targetChannel.retrieveMessageById(link.getMessageId()).complete());
                    } catch (Exception exception) {
                        log.debug("Impossible de suivre le lien Discord vers {} dans le salon {}: {}",
                                link.getMessageId(),
                                link.getChannelId(),
                                exception.getMessage());
                        return Optional.empty();
                    }
                });
    }

    private void logMessageRetrievalFailure(String channelId, Long messageId, Exception exception) {
        String error = exception.getMessage() == null ? "" : exception.getMessage();
        String context = "message " + messageId + " in channel " + channelId;

        if (error.contains("10008") || error.toLowerCase(Locale.ROOT).contains("unknown message")) {
            if (canWriteInCurrentTransaction()) {
                cleanupUnknownMessageReferences(channelId, messageId);
            }
            log.debug("Message Discord introuvable pour {}.", context);
            return;
        }

        if (error.contains("503")) {
            log.warn("Discord indisponible temporairement pour {}: {}", context, error);
            return;
        }

        log.error("Impossible de recuperer {}: {}", context, error);
    }

    private boolean canWriteInCurrentTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            return true;
        }
        return !TransactionSynchronizationManager.isCurrentTransactionReadOnly();
    }

    @Transactional
    protected void cleanupUnknownMessageReferences(String channelId, Long messageId) {
        boolean changed = false;

        for (Raid raid : raidRepository.findAllByDiscordMessageId(messageId)) {
            if (Objects.equals(raid.getChannelId(), channelId) && Objects.equals(raid.getDiscordMessageId(), messageId)) {
                raid.setDiscordMessageId(null);
                if (Objects.equals(raid.getLastMissingPingSourceMessageId(), messageId)) {
                    raid.setLastMissingPingSourceMessageId(null);
                }
                if (raid.getRaidHelperId() != null) {
                    raid.setRaidHelperId(null);
                }
                raidRepository.save(raid);
                changed = true;
            }
        }

        for (Raid raid : raidRepository.findAllByPublishedMessageId(messageId)) {
            if (Objects.equals(raid.getPublishedChannelId(), channelId) && Objects.equals(raid.getPublishedMessageId(), messageId)) {
                raid.setPublishedMessageId(null);
                raid.setPublishedChannelId(null);
                raidRepository.save(raid);
                changed = true;
            }
        }

        for (Raid raid : raidRepository.findAllByLastMissingPingSourceMessageId(messageId)) {
            if (Objects.equals(raid.getLastMissingPingSourceMessageId(), messageId)) {
                raid.setLastMissingPingSourceMessageId(null);
                raidRepository.save(raid);
                changed = true;
            }
        }

        if (changed) {
            log.info("References Discord obsoletes nettoyees pour le message {} du salon {}", messageId, channelId);
        }
    }

    private boolean matchesExpectedRaid(Message message, String expectedNom, LocalDateTime expectedDate) {
        if (message.getEmbeds().isEmpty()) {
            return false;
        }

        MessageEmbed embed = message.getEmbeds().get(0);
        String extractedNom = parserService.extractNom(embed);
        if (expectedNom != null && !normalizeCharacterKey(extractedNom).equals(normalizeCharacterKey(expectedNom))) {
            return false;
        }

        if (expectedDate == null) {
            return true;
        }

        LocalDateTime explicitDate = parserService.extractDateFromEmbed(embed);
        if (explicitDate != null) {
            return explicitDate.toLocalDate().equals(expectedDate.toLocalDate());
        }

        return !parserService.isPlaceholderSignupEmbed(embed);
    }

    private int scoreSignupSourceMessage(Message message) {
        if (message.getEmbeds().isEmpty()) {
            return 0;
        }

        MessageEmbed embed = message.getEmbeds().get(0);
        if (parserService.isPlaceholderSignupEmbed(embed)) {
            return 0;
        }

        int score = 0;
        if (parserService.extractDateFromEmbed(embed) != null) {
            score += 100;
        }
        if (parserService.isCompositionToolEmbed(embed)) {
            score += 20;
        }

        score += Math.min(50, countLikelySignupLines(embed) * 5);
        return score;
    }

    private int countLikelySignupLines(MessageEmbed embed) {
        int count = 0;

        for (MessageEmbed.Field field : embed.getFields()) {
            String value = field.getValue();
            if (value == null) {
                continue;
            }

            for (String line : value.split("\\n")) {
                if (extractPseudoFromLine(line) != null && extractPlayableEmojiId(line).isPresent()) {
                    count++;
                }
            }
        }

        return count;
    }

    private static class ResolvedSignupSource {
        private final String channelId;
        private final Long messageId;
        private final Message message;

        private ResolvedSignupSource(String channelId, Long messageId, Message message) {
            this.channelId = channelId;
            this.messageId = messageId;
            this.message = message;
        }

        private String channelId() {
            return channelId;
        }

        private Long messageId() {
            return messageId;
        }

        private Message message() {
            return message;
        }
    }

    public String cleanServerPseudo(String input) {
        if (input == null) {
            return null;
        }
        return input.replaceAll("\\s+", "").replaceAll("[^\\p{ASCII}]", "").trim();
    }

    private Optional<JoueurDTO> extractJoueurFromLine(String line, Map<String, StatutParticipation> pseudoToStatus) {
        String pseudo = extractPseudoFromLine(line);
        if (pseudo == null) {
            return Optional.empty();
        }

        String cleaned = cleanServerPseudo(pseudo);
        if (cleaned == null || cleaned.isBlank()) {
            return Optional.empty();
        }

        Joueur joueur = joueurService.findByServerPseudo(cleaned);
        Optional<String> emojiIdOpt = extractPlayableEmojiId(line);
        ParsedEmoji parsedEmoji = emojiIdOpt.map(id -> new ParsedEmoji().parseEmoji(id)).orElse(null);
        List<Personnage> existingCharacters = joueur != null ? personnageRepository.findByJoueurId(joueur.getId()) : List.of();
        Personnage matchingCharacter = findMatchingExistingCharacter(existingCharacters, parsedEmoji, pseudo, cleaned);

        if (joueur == null && parsedEmoji == null) {
            return Optional.empty();
        }

        if (joueur == null) {
            log.info("pseudo qui pose pb : {}", cleaned);
            return Optional.empty();
        }

        if (joueur.getMainCharacter() == null && matchingCharacter != null) {
            joueur = joueurService.createWithMainCharacter(joueur, matchingCharacter);
        }

        if (joueur.getMainCharacter() == null) {
            if (parsedEmoji == null || parsedEmoji.classe == null || "Inconnue".equalsIgnoreCase(parsedEmoji.classe)) {
                return Optional.empty();
            }

            Personnage p = Personnage.builder()
                    .nom(cleaned)
                    .classe(parsedEmoji.classe)
                    .role(parsedEmoji.role)
                    .specialisation(parsedEmoji.specialisation)
                    .main(true)
                    .joueur(joueur)
                    .build();
            p = personnageService.save(p);
            joueur = joueurService.createWithMainCharacter(joueur, p);

            log.info("Joueur auto-cree : {} [{} - {} - {}]", pseudo, parsedEmoji.classe, parsedEmoji.role, parsedEmoji.specialisation);
        }

        StatutParticipation statut = pseudoToStatus.getOrDefault(cleaned, StatutParticipation.TITULAIRE);
        JoueurDTO joueurDTO = joueurService.toDTO(joueur, false);

        return Optional.of(new JoueurDTO(
                joueurDTO.getId(),
                joueurDTO.getDiscordId(),
                joueurDTO.getPseudo(),
                joueurDTO.getPseudoIhm(),
                joueurDTO.getServerPseudo(),
                joueurDTO.getPersonnageMain(),
                joueurDTO.getRerolls(),
                joueurDTO.isRaider(),
                statut,
                null
        ));
    }

    private Optional<String> extractPlayableEmojiId(String line) {
        Matcher matcher = EMOJI_PATTERN.matcher(line);
        ParsedEmoji parser = new ParsedEmoji();

        while (matcher.find()) {
            String emojiId = matcher.group(2);
            ParsedEmoji parsedEmoji = parser.parseEmoji(emojiId);
            if (!"Inconnue".equalsIgnoreCase(parsedEmoji.classe)) {
                return Optional.of(emojiId);
            }
        }

        return Optional.empty();
    }

    private String extractPseudoFromLine(String line) {
        String sanitized = line
                .replaceAll("<:[^:]+:\\d+>", " ")
                .replace("**", " ")
                .replace("`", " ")
                .replace("\u2022", " ")
                .trim();

        sanitized = sanitized.replaceAll("\\s+", " ").trim();
        sanitized = sanitized.replaceAll("^[\\p{So}\\p{Cntrl}\\s-]+", "").trim();
        sanitized = sanitized.replaceAll("^\\d+[.)-]?\\s*", "").trim();

        if (sanitized.isBlank()) {
            return null;
        }

        String lower = sanitized.toLowerCase(Locale.ROOT);
        if (lower.startsWith("group ")
                || lower.startsWith("sent by ")
                || lower.startsWith("absence")
                || lower.contains(",")
                || lower.contains(" melee ")
                || lower.contains(" ranged ")
                || lower.contains(" healers ")
                || "-".equals(sanitized)) {
            return null;
        }

        return sanitized;
    }

    private Personnage findMatchingExistingCharacter(List<Personnage> characters,
                                                     ParsedEmoji parsedEmoji,
                                                     String rawPseudo,
                                                     String cleanedPseudo) {
        if (characters == null || characters.isEmpty()) {
            return null;
        }

        String normalizedRawPseudo = normalizeCharacterKey(rawPseudo);
        String normalizedCleanedPseudo = normalizeCharacterKey(cleanedPseudo);

        Optional<Personnage> byName = characters.stream()
                .filter(character -> {
                    String normalizedCharacterName = normalizeCharacterKey(character.getNom());
                    return normalizedCharacterName.equals(normalizedRawPseudo)
                            || normalizedCharacterName.equals(normalizedCleanedPseudo);
                })
                .findFirst();
        if (byName.isPresent()) {
            return byName.get();
        }

        if (parsedEmoji == null) {
            return null;
        }

        String normalizedClass = normalizeCharacterKey(parsedEmoji.classe);
        String normalizedSpec = normalizeCharacterKey(parsedEmoji.specialisation);

        return characters.stream()
                .filter(character -> normalizeCharacterKey(character.getClasse()).equals(normalizedClass))
                .filter(character -> normalizeCharacterKey(character.getSpecialisation()).equals(normalizedSpec))
                .findFirst()
                .orElse(null);
    }

    private String normalizeCharacterKey(String value) {
        if (value == null) {
            return "";
        }

        return value
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace("é", "e")
                .replace("è", "e")
                .replace("ê", "e")
                .replace("à", "a")
                .replace("ù", "u")
                .replace("î", "i")
                .replace("ï", "i")
                .replace("ô", "o")
                .replace("ö", "o")
                .replace("û", "u")
                .replace("ü", "u")
                .replaceAll("[^a-z0-9]", "");
    }

    private Optional<String> extractEmojiId(String line) {
        Matcher matcher = EMOJI_PATTERN.matcher(line);
        if (matcher.find()) {
            return Optional.of(matcher.group(2));
        }
        return Optional.empty();
    }

    private void extractStatus(Map<String, StatutParticipation> mapStatus, String raidHelperString, StatutParticipation statutParticipation) {
        int indexAbsence = raidHelperString.indexOf("absence");
        if (indexAbsence != -1) {
            raidHelperString = raidHelperString.substring(0, indexAbsence);
        }

        Matcher matcher = STATUS_NAME_PATTERN.matcher(raidHelperString);
        while (matcher.find()) {
            mapStatus.put(cleanServerPseudo(matcher.group(1).trim()), statutParticipation);
        }
    }

    private JoueurDTO extractJoueurStatus(String pseudo, StatutParticipation statutParticipation) {
        String cleaned = cleanServerPseudo(pseudo);
        Joueur joueur = joueurService.findByServerPseudo(cleaned);
        if (joueur == null) {
            return null;
        }

        List<Personnage> rerolls = personnageService.gerRerolls(joueur.getId());

        return new JoueurDTO(
                joueur.getId(),
                joueur.getDiscordId(),
                joueur.getPseudo(),
                joueur.getPseudoIhm(),
                joueur.getServerPseudo(),
                personnageService.toDTO(joueur.getMainCharacter()),
                rerolls.stream().map(personnageService::toDTO).collect(Collectors.toList()),
                Boolean.TRUE.equals(joueur.getIsRaider()),
                statutParticipation,
                null
        );
    }
}
