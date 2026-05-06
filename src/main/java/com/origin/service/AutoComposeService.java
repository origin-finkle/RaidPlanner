package com.origin.service;

import com.origin.dto.AutoComposeWeekRequestDTO;
import com.origin.dto.AutoComposeWeekResultDTO;
import com.origin.dto.AutoComposePreviewRaidDTO;
import com.origin.dto.AutoComposePreviewResultDTO;
import com.origin.dto.JoueurDTO;
import com.origin.dto.PersonnageCompositionDTO;
import com.origin.dto.PersonnageDTO;
import com.origin.dto.RaidCompositionDTO;
import com.origin.entity.Raid;
import com.origin.enumOrigin.StatutParticipation;
import com.origin.repository.RaidRepository;
import com.origin.service.discord.RaidQueryService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoComposeService {

    private static final int RAID_SIZE = 10;

    private final RaidRepository raidRepository;
    private final RaidQueryService raidQueryService;
    private final RaidService raidService;
    private final AutoComposeSettingsService autoComposeSettingsService;

    public AutoComposeWeekResultDTO autoComposeWeek(Long anchorRaidId) {
        return autoComposeWeek(anchorRaidId, null);
    }

    public AutoComposeWeekResultDTO autoComposeWeek(Long anchorRaidId, AutoComposeWeekRequestDTO request) {
        AutoComposePlan plan = buildAutoComposePlan(anchorRaidId, request);
        for (AutoComposePlanEntry entry : plan.entries) {
            CompositionResult composition = entry.composition;
            raidService.saveComposition(new RaidCompositionDTO(
                    entry.raid.getId(),
                    toCompositionDtos(composition.group1),
                    toCompositionDtos(composition.group2)
            ));

            if (composition.selected.size() < RAID_SIZE) {
                plan.warnings.add("Le raid " + entry.raid.getNom() + " du "
                        + entry.raid.getDate().toLocalDate()
                        + " n'a pu etre rempli qu'a " + composition.selected.size() + "/" + RAID_SIZE + ".");
            }
        }

        return new AutoComposeWeekResultDTO(plan.selectedRaidIds, plan.selectedRaidIds, plan.warnings);
    }

    public AutoComposePreviewResultDTO previewAutoComposeWeek(Long anchorRaidId, AutoComposeWeekRequestDTO request) {
        AutoComposePlan plan = buildAutoComposePlan(anchorRaidId, request);
        List<AutoComposePreviewRaidDTO> previewRaids = plan.entries.stream()
                .map(entry -> new AutoComposePreviewRaidDTO(
                        entry.raid.getId(),
                        entry.raid.getNom(),
                        entry.raid.getDate(),
                        toPreviewDtos(entry.composition.group1),
                        toPreviewDtos(entry.composition.group2),
                        entry.composition.selected.size()
                ))
                .collect(Collectors.toList());

        return new AutoComposePreviewResultDTO(plan.selectedRaidIds, plan.warnings, previewRaids);
    }

    private RaidWithSignups loadSignups(Raid raid) {
        List<JoueurDTO> signups = raidQueryService.getInscriptionsFromRaidHelper(raid);
        return new RaidWithSignups(raid, signups);
    }

    private AutoComposePlan buildAutoComposePlan(Long anchorRaidId, AutoComposeWeekRequestDTO request) {
        Raid anchorRaid = raidRepository.findById(anchorRaidId)
                .orElseThrow(() -> new IllegalArgumentException("Raid introuvable : " + anchorRaidId));
        ComposeRules rules = ComposeRules.from(autoComposeSettingsService.getSettings(), request);

        WeekRange weekRange = getResetWeekRange(anchorRaid.getDate().toLocalDate());
        List<Raid> weekRaids = raidRepository.findByDateGreaterThanEqualAndDateLessThanOrderByDateAsc(
                weekRange.start.atStartOfDay(),
                weekRange.endExclusive.atStartOfDay()
        );

        List<RaidWithSignups> eligibleRaids = weekRaids.stream()
                .map(this::loadSignups)
                .filter(raid -> !raid.signups.isEmpty())
                .sorted(Comparator
                        .comparingInt((RaidWithSignups raid) -> raid.signups.size()).reversed()
                        .thenComparing(raid -> raid.raid.getDate()))
                .limit(rules.maxRaids)
                .collect(Collectors.toList());

        List<String> warnings = new ArrayList<>();
        if (eligibleRaids.isEmpty()) {
            warnings.add("Aucun raid avec inscrits n'a ete trouve sur la semaine selectionnee.");
            return new AutoComposePlan(List.of(), List.of(), warnings);
        }
        if (eligibleRaids.size() < rules.maxRaids) {
            warnings.add("Seulement " + eligibleRaids.size() + " raid(s) exploitable(s) ont ete trouves sur cette semaine de reset.");
        }

        Map<Long, Integer> weeklyUsage = new HashMap<>();
        List<RaidWithSignups> buildOrder = new ArrayList<>(eligibleRaids);
        buildOrder.sort(Comparator
                .comparingInt((RaidWithSignups raid) -> raid.signups.size())
                .thenComparing(raid -> raid.raid.getDate()));

        List<AutoComposePlanEntry> entries = new ArrayList<>();
        for (RaidWithSignups raidWithSignups : buildOrder) {
            CompositionResult composition = buildCompositionForRaid(raidWithSignups, weeklyUsage, rules);
            entries.add(new AutoComposePlanEntry(raidWithSignups.raid, composition));
        }

        List<Long> selectedRaidIds = entries.stream()
                .map(entry -> entry.raid.getId())
                .collect(Collectors.toList());
        return new AutoComposePlan(entries, selectedRaidIds, warnings);
    }

    private CompositionResult buildCompositionForRaid(RaidWithSignups raidWithSignups,
                                                      Map<Long, Integer> weeklyUsage,
                                                      ComposeRules rules) {
        List<CandidateCharacter> candidates = buildCandidates(raidWithSignups.signups);
        List<CandidateCharacter> selected = new ArrayList<>();
        Set<Long> usedPlayers = new HashSet<>();

        fillRole(candidates, selected, usedPlayers, weeklyUsage, "TANK", rules.targetTanks, rules);
        fillRole(candidates, selected, usedPlayers, weeklyUsage, "HEAL", rules.targetHeals, rules);

        while (selected.size() < RAID_SIZE) {
            CandidateCharacter next = pickBestCandidate(candidates, selected, usedPlayers, weeklyUsage, null, rules);
            if (next == null) {
                break;
            }
            selectCandidate(next, selected, usedPlayers, weeklyUsage, rules);
        }

        selected.sort(Comparator
                .comparingInt((CandidateCharacter candidate) -> rolePriority(candidate.personnage.getRole()))
                .thenComparing(candidate -> -candidate.scoreSignature)
                .thenComparing(candidate -> candidate.personnage.getNom(), String.CASE_INSENSITIVE_ORDER));

        List<CandidateCharacter> group1 = new ArrayList<>();
        List<CandidateCharacter> group2 = new ArrayList<>();
        for (CandidateCharacter candidate : selected) {
            if (group1.size() <= group2.size() && group1.size() < 5) {
                group1.add(candidate);
            } else if (group2.size() < 5) {
                group2.add(candidate);
            } else if (group1.size() < 5) {
                group1.add(candidate);
            }
        }

        return new CompositionResult(selected, group1, group2);
    }

    private void fillRole(List<CandidateCharacter> candidates,
                          List<CandidateCharacter> selected,
                          Set<Long> usedPlayers,
                          Map<Long, Integer> weeklyUsage,
                          String role,
                          int target,
                          ComposeRules rules) {
        while (countRole(selected, role) < target && selected.size() < RAID_SIZE) {
            CandidateCharacter next = pickBestCandidate(candidates, selected, usedPlayers, weeklyUsage, role, rules);
            if (next == null) {
                break;
            }
            selectCandidate(next, selected, usedPlayers, weeklyUsage, rules);
        }
    }

    private int countRole(List<CandidateCharacter> selected, String role) {
        return (int) selected.stream()
                .filter(candidate -> normalizeKey(candidate.personnage.getRole()).equals(normalizeKey(role)))
                .count();
    }

    private CandidateCharacter pickBestCandidate(List<CandidateCharacter> candidates,
                                                 List<CandidateCharacter> selected,
                                                 Set<Long> usedPlayers,
                                                 Map<Long, Integer> weeklyUsage,
                                                 String desiredRole,
                                                 ComposeRules rules) {
        CandidateCharacter best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (CandidateCharacter candidate : candidates) {
            if (usedPlayers.contains(candidate.joueur.getId())) {
                continue;
            }

            double score = scoreCandidate(candidate, selected, weeklyUsage, desiredRole, rules);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        if (best != null) {
            best.scoreSignature = bestScore;
        }
        return best;
    }

    private void selectCandidate(CandidateCharacter candidate,
                                 List<CandidateCharacter> selected,
                                 Set<Long> usedPlayers,
                                 Map<Long, Integer> weeklyUsage,
                                 ComposeRules rules) {
        selected.add(candidate);
        usedPlayers.add(candidate.joueur.getId());
        if (rules.balanceAcrossRaids) {
            weeklyUsage.merge(candidate.joueur.getId(), 1, Integer::sum);
        }
    }

    private double scoreCandidate(CandidateCharacter candidate,
                                  List<CandidateCharacter> selected,
                                  Map<Long, Integer> weeklyUsage,
                                  String desiredRole,
                                  ComposeRules rules) {
        double score = statusScore(candidate.joueur.getStatutParticipation());
        if (rules.preferMains && candidate.preferredMain) {
            score += 25;
        }

        if (rules.balanceAcrossRaids) {
            int usageCount = weeklyUsage.getOrDefault(candidate.joueur.getId(), 0);
            score -= usageCount * 45;
        }

        if (desiredRole != null) {
            if (normalizeKey(candidate.personnage.getRole()).equals(normalizeKey(desiredRole))) {
                score += 140;
            } else {
                score -= 220;
            }
        } else {
            score += roleNeedBonus(candidate.personnage, selected, rules);
        }

        if (rules.prioritizeBuffCoverage) {
            int beforeBuffCoverage = getEffectiveRaidBuffCoverage(selected, rules);
            List<CandidateCharacter> hypothetical = new ArrayList<>(selected);
            hypothetical.add(candidate);
            int afterBuffCoverage = getEffectiveRaidBuffCoverage(hypothetical, rules);
            score += (afterBuffCoverage - beforeBuffCoverage) * 22;
        }

        return score;
    }

    private int roleNeedBonus(PersonnageDTO personnage, List<CandidateCharacter> selected, ComposeRules rules) {
        String role = normalizeKey(personnage.getRole());
        if ("tank".equals(role) && countRole(selected, "TANK") < rules.targetTanks) {
            return 80;
        }
        if ("heal".equals(role) && countRole(selected, "HEAL") < rules.targetHeals) {
            return 70;
        }
        if ("dps".equals(role)) {
            return 35;
        }
        return 0;
    }

    private double statusScore(StatutParticipation status) {
        if (status == null) {
            return 80;
        }

        switch (status) {
            case TITULAIRE:
                return 100;
            case LATE:
                return 70;
            case TENTATIVE:
                return 55;
            case BENCH:
                return 40;
            default:
                return 50;
        }
    }

    private int getEffectiveRaidBuffCoverage(List<CandidateCharacter> selected, ComposeRules rules) {
        Map<String, List<BuffProviderRule>> buffRules = getRaidBuffRules();
        Set<String> directBuffs = new HashSet<>();
        List<PersonnageDTO> hunters = new ArrayList<>();
        Set<String> assignedHunters = new HashSet<>();

        for (CandidateCharacter candidate : selected) {
            PersonnageDTO personnage = candidate.personnage;
            if ("chasseur".equals(normalizeKey(personnage.getClasse()))) {
                hunters.add(personnage);
            }

            for (Map.Entry<String, List<BuffProviderRule>> entry : buffRules.entrySet()) {
                if (entry.getValue().stream().anyMatch(rule -> !isHunterFallbackRule(rule) && matchesRule(personnage, rule))) {
                    directBuffs.add(entry.getKey());
                }
            }
        }

        if (!rules.huntersFillMissingBuffs) {
            return directBuffs.size();
        }

        int effectiveCoverage = directBuffs.size();

        for (Map.Entry<String, List<BuffProviderRule>> entry : buffRules.entrySet()) {
            if (directBuffs.contains(entry.getKey())) {
                continue;
            }

            PersonnageDTO availableHunter = hunters.stream()
                    .filter(hunter -> !assignedHunters.contains(normalizeKey(hunter.getNom())))
                    .filter(hunter -> entry.getValue().stream()
                            .anyMatch(rule -> isHunterFallbackRule(rule) && matchesRule(hunter, rule)))
                    .findFirst()
                    .orElse(null);

            if (availableHunter == null) {
                continue;
            }

            assignedHunters.add(normalizeKey(availableHunter.getNom()));
            effectiveCoverage++;
        }

        return effectiveCoverage;
    }

    private List<CandidateCharacter> buildCandidates(List<JoueurDTO> signups) {
        List<CandidateCharacter> candidates = new ArrayList<>();

        for (JoueurDTO joueur : signups) {
            if (joueur == null || joueur.getId() == null) {
                continue;
            }

            if (joueur.getPersonnageMain() != null) {
                candidates.add(new CandidateCharacter(joueur, hydrateCharacter(joueur.getPersonnageMain(), joueur), true, 0));
            }

            if (joueur.getRerolls() != null) {
                for (PersonnageDTO reroll : joueur.getRerolls()) {
                    if (reroll == null) {
                        continue;
                    }
                    candidates.add(new CandidateCharacter(joueur, hydrateCharacter(reroll, joueur), false, 0));
                }
            }
        }

        return deduplicateCandidates(candidates);
    }

    private List<CandidateCharacter> deduplicateCandidates(List<CandidateCharacter> candidates) {
        Map<String, CandidateCharacter> unique = new LinkedHashMap<>();
        for (CandidateCharacter candidate : candidates) {
            String key = candidate.joueur.getId() + "::" + normalizeKey(candidate.personnage.getNom());
            unique.putIfAbsent(key, candidate);
        }
        return new ArrayList<>(unique.values());
    }

    private PersonnageDTO hydrateCharacter(PersonnageDTO personnage, JoueurDTO joueur) {
        PersonnageDTO hydrated = new PersonnageDTO();
        hydrated.setId(personnage.getId());
        hydrated.setNom(personnage.getNom());
        hydrated.setClasse(personnage.getClasse());
        hydrated.setSpecialisation(personnage.getSpecialisation());
        hydrated.setRole(personnage.getRole());
        hydrated.setMain(personnage.isMain());
        hydrated.setPseudo(joueur.getPseudo());
        return hydrated;
    }

    private List<PersonnageCompositionDTO> toCompositionDtos(List<CandidateCharacter> candidates) {
        return candidates.stream()
                .map(candidate -> new PersonnageCompositionDTO(
                        candidate.personnage.getNom(),
                        candidate.personnage.getClasse(),
                        candidate.personnage.getRole(),
                        candidate.joueur.getPseudo()
                ))
                .collect(Collectors.toList());
    }

    private List<PersonnageDTO> toPreviewDtos(List<CandidateCharacter> candidates) {
        return candidates.stream()
                .map(candidate -> candidate.personnage)
                .collect(Collectors.toList());
    }

    private WeekRange getResetWeekRange(LocalDate date) {
        LocalDate start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.WEDNESDAY));
        return new WeekRange(start, start.plusDays(7));
    }

    private Map<String, List<BuffProviderRule>> getRaidBuffRules() {
        Map<String, List<BuffProviderRule>> rules = new LinkedHashMap<>();
        rules.put("Puissance d'attaque", List.of(
                new BuffProviderRule("DK"),
                new BuffProviderRule("Chasseur"),
                new BuffProviderRule("Guerrier")
        ));
        rules.put("Chance de critique", List.of(
                new BuffProviderRule("Druide", "Feral", "Gardien"),
                new BuffProviderRule("Mage"),
                new BuffProviderRule("Moine", "Marche vent"),
                new BuffProviderRule("Chasseur")
        ));
        rules.put("Maitrise", List.of(
                new BuffProviderRule("Chasseur"),
                new BuffProviderRule("Paladin"),
                new BuffProviderRule("Chaman")
        ));
        rules.put("Hate physique", List.of(
                new BuffProviderRule("DK", "Givre", "Impie"),
                new BuffProviderRule("Voleur"),
                new BuffProviderRule("Chaman", "Amelioration"),
                new BuffProviderRule("Chasseur")
        ));
        rules.put("Hate des sorts", List.of(
                new BuffProviderRule("Druide", "Equilibre"),
                new BuffProviderRule("Pretre", "Ombre"),
                new BuffProviderRule("Chaman"),
                new BuffProviderRule("Chasseur")
        ));
        rules.put("Puissance des sorts", List.of(
                new BuffProviderRule("Mage"),
                new BuffProviderRule("Chaman"),
                new BuffProviderRule("Demoniste"),
                new BuffProviderRule("Chasseur")
        ));
        rules.put("Endurance", List.of(
                new BuffProviderRule("Pretre"),
                new BuffProviderRule("Demoniste"),
                new BuffProviderRule("Guerrier"),
                new BuffProviderRule("Chasseur", "BM")
        ));
        rules.put("Stats", List.of(
                new BuffProviderRule("Druide"),
                new BuffProviderRule("Moine"),
                new BuffProviderRule("Paladin"),
                new BuffProviderRule("Chasseur", "BM")
        ));
        return rules;
    }

    private boolean matchesRule(PersonnageDTO personnage, BuffProviderRule rule) {
        if (!normalizeKey(personnage.getClasse()).equals(normalizeKey(rule.classe))) {
            return false;
        }
        if (rule.specialisations.isEmpty()) {
            return true;
        }
        String normalizedSpec = normalizeKey(personnage.getSpecialisation());
        return rule.specialisations.stream().anyMatch(spec -> normalizeKey(spec).equals(normalizedSpec));
    }

    private boolean isHunterFallbackRule(BuffProviderRule rule) {
        return "chasseur".equals(normalizeKey(rule.classe));
    }

    private int rolePriority(String role) {
        String normalized = normalizeKey(role);
        if ("tank".equals(normalized)) {
            return 0;
        }
        if ("heal".equals(normalized)) {
            return 1;
        }
        return 2;
    }

    private String normalizeKey(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
    }

    @AllArgsConstructor
    private static class RaidWithSignups {
        private final Raid raid;
        private final List<JoueurDTO> signups;
    }

    @AllArgsConstructor
    private static class CandidateCharacter {
        private final JoueurDTO joueur;
        private final PersonnageDTO personnage;
        private final boolean preferredMain;
        private double scoreSignature;
    }

    @AllArgsConstructor
    private static class CompositionResult {
        private final List<CandidateCharacter> selected;
        private final List<CandidateCharacter> group1;
        private final List<CandidateCharacter> group2;
    }

    @AllArgsConstructor
    private static class AutoComposePlanEntry {
        private final Raid raid;
        private final CompositionResult composition;
    }

    @AllArgsConstructor
    private static class AutoComposePlan {
        private final List<AutoComposePlanEntry> entries;
        private final List<Long> selectedRaidIds;
        private final List<String> warnings;
    }

    @AllArgsConstructor
    @Getter
    private static class WeekRange {
        private final LocalDate start;
        private final LocalDate endExclusive;
    }

    private static class BuffProviderRule {
        private final String classe;
        private final List<String> specialisations;

        private BuffProviderRule(String classe, String... specialisations) {
            this.classe = classe;
            this.specialisations = List.of(specialisations);
        }
    }

    private static class ComposeRules {
        private final int maxRaids;
        private final int targetTanks;
        private final int targetHeals;
        private final boolean preferMains;
        private final boolean balanceAcrossRaids;
        private final boolean prioritizeBuffCoverage;
        private final boolean huntersFillMissingBuffs;

        private ComposeRules(int maxRaids,
                             int targetTanks,
                             int targetHeals,
                             boolean preferMains,
                             boolean balanceAcrossRaids,
                             boolean prioritizeBuffCoverage,
                             boolean huntersFillMissingBuffs) {
            this.maxRaids = maxRaids;
            this.targetTanks = targetTanks;
            this.targetHeals = targetHeals;
            this.preferMains = preferMains;
            this.balanceAcrossRaids = balanceAcrossRaids;
            this.prioritizeBuffCoverage = prioritizeBuffCoverage;
            this.huntersFillMissingBuffs = huntersFillMissingBuffs;
        }

        private static ComposeRules from(AutoComposeWeekRequestDTO defaults, AutoComposeWeekRequestDTO request) {
            AutoComposeWeekRequestDTO resolvedDefaults = defaults != null ? defaults : defaults();
            AutoComposeWeekRequestDTO resolvedRequest = request != null ? request : new AutoComposeWeekRequestDTO();

            int maxRaids = clamp(firstNonNull(resolvedRequest.getMaxRaids(), resolvedDefaults.getMaxRaids()), 1, 4, 2);
            int targetTanks = clamp(firstNonNull(resolvedRequest.getTargetTanks(), resolvedDefaults.getTargetTanks()), 0, 3, 2);
            int targetHeals = clamp(firstNonNull(resolvedRequest.getTargetHeals(), resolvedDefaults.getTargetHeals()), 0, 4, 2);

            if (targetTanks + targetHeals > RAID_SIZE) {
                targetHeals = Math.max(0, RAID_SIZE - targetTanks);
            }

            return new ComposeRules(
                    maxRaids,
                    targetTanks,
                    targetHeals,
                    firstNonNull(resolvedRequest.getPreferMains(), resolvedDefaults.getPreferMains(), true),
                    firstNonNull(resolvedRequest.getBalanceAcrossRaids(), resolvedDefaults.getBalanceAcrossRaids(), true),
                    firstNonNull(resolvedRequest.getPrioritizeBuffCoverage(), resolvedDefaults.getPrioritizeBuffCoverage(), true),
                    firstNonNull(resolvedRequest.getHuntersFillMissingBuffs(), resolvedDefaults.getHuntersFillMissingBuffs(), true)
            );
        }

        private static AutoComposeWeekRequestDTO defaults() {
            return new AutoComposeWeekRequestDTO(2, 2, 2, true, true, true, true);
        }

        private static int clamp(Integer value, int min, int max, int fallback) {
            if (value == null) {
                return fallback;
            }
            return Math.max(min, Math.min(max, value));
        }

        private static Integer firstNonNull(Integer first, Integer second) {
            return first != null ? first : second;
        }

        private static Boolean firstNonNull(Boolean first, Boolean second, boolean fallback) {
            if (first != null) {
                return first;
            }
            if (second != null) {
                return second;
            }
            return fallback;
        }
    }
}
