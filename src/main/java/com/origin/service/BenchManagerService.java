package com.origin.service;

import com.origin.dto.BenchRecommendationDTO;
import com.origin.dto.BenchSuggestionPlayerDTO;
import com.origin.dto.JoueurDTO;
import com.origin.dto.PlayerEquityRowDTO;
import com.origin.dto.RaidConfirmationSummaryDTO;
import com.origin.entity.Personnage;
import com.origin.entity.Raid;
import com.origin.enumOrigin.StatutParticipation;
import com.origin.service.discord.RaidQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class BenchManagerService {

    private final RaidService raidService;
    private final RaidQueryService raidQueryService;
    private final PlayerEquityService playerEquityService;
    private final RaidConfirmationService raidConfirmationService;

    @Transactional(readOnly = true)
    public BenchRecommendationDTO getRecommendations(Long raidId) {
        Raid raid = raidService.getRaidById(raidId);
        List<JoueurDTO> signups = raidQueryService.getInscriptionsFromRaidHelper(raid);
        Map<Long, PlayerEquityRowDTO> equityByPlayer = playerEquityService.getSummary().getPlayers().stream()
                .collect(Collectors.toMap(PlayerEquityRowDTO::getJoueurId, row -> row, (left, right) -> left));
        RaidConfirmationSummaryDTO confirmationSummary = raidConfirmationService.getSummary(raidId);
        Map<Long, String> confirmationStatusByPlayer = Stream.of(
                        confirmationSummary.getConfirmedPlayers(),
                        confirmationSummary.getCancelledPlayers(),
                        confirmationSummary.getPendingPlayers()
                )
                .flatMap(List::stream)
                .collect(Collectors.toMap(
                        entry -> entry.getJoueurId(),
                        entry -> entry.getConfirmationStatus(),
                        (left, right) -> right
                ));

        Map<Long, Personnage> assignedCharactersByPlayer = Stream.concat(raid.getGroup1().stream(), raid.getGroup2().stream())
                .filter(Objects::nonNull)
                .filter(personnage -> personnage.getJoueur() != null && personnage.getJoueur().getId() != null)
                .collect(Collectors.toMap(
                        personnage -> personnage.getJoueur().getId(),
                        personnage -> personnage,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        List<BenchSuggestionPlayerDTO> benchCandidates = assignedCharactersByPlayer.values().stream()
                .map(personnage -> toBenchCandidate(personnage, equityByPlayer.get(personnage.getJoueur().getId()), confirmationStatusByPlayer.get(personnage.getJoueur().getId())))
                .sorted(Comparator.comparingInt(BenchSuggestionPlayerDTO::getFairnessScore).reversed()
                        .thenComparing(BenchSuggestionPlayerDTO::getPseudoIhm, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .collect(Collectors.toList());

        Set<Long> assignedPlayerIds = assignedCharactersByPlayer.keySet();
        List<BenchSuggestionPlayerDTO> reserveCandidates = signups.stream()
                .filter(Objects::nonNull)
                .filter(signup -> signup.getId() != null && !assignedPlayerIds.contains(signup.getId()))
                .filter(signup -> signup.getPersonnageMain() != null)
                .filter(signup -> signup.getStatutParticipation() != StatutParticipation.BENCH)
                .collect(Collectors.toMap(
                        JoueurDTO::getId,
                        signup -> signup,
                        (left, right) -> left,
                        LinkedHashMap::new
                ))
                .values().stream()
                .map(signup -> toReserveCandidate(signup, equityByPlayer.get(signup.getId())))
                .sorted(Comparator.comparingInt(BenchSuggestionPlayerDTO::getFairnessScore).reversed()
                        .thenComparing(BenchSuggestionPlayerDTO::getPseudoIhm, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .collect(Collectors.toList());

        List<String> warnings = new ArrayList<>();
        if (reserveCandidates.isEmpty()) {
            warnings.add("Aucun remplaçant propre n'a ete trouve dans les inscrits actuels.");
        }
        if (benchCandidates.isEmpty()) {
            warnings.add("La compo actuelle est vide: pas de bench possible.");
        }

        return BenchRecommendationDTO.builder()
                .raidId(raid.getId())
                .raidNom(raid.getNom())
                .raidDate(raid.getDate())
                .assignedCount(assignedCharactersByPlayer.size())
                .reserveCount(reserveCandidates.size())
                .benchCandidates(benchCandidates)
                .reserveCandidates(reserveCandidates)
                .warnings(warnings)
                .build();
    }

    private BenchSuggestionPlayerDTO toBenchCandidate(Personnage personnage,
                                                      PlayerEquityRowDTO equity,
                                                      String confirmationStatus) {
        List<String> reasons = new ArrayList<>();
        int score = 0;

        if (!personnage.isMain()) {
            score += 30;
            reasons.add("reroll dans la compo");
        }

        if ("ANNULE".equalsIgnoreCase(confirmationStatus)) {
            score += 80;
            reasons.add("a refuse la compo");
        } else if ("EN_ATTENTE".equalsIgnoreCase(confirmationStatus) || confirmationStatus == null) {
            score += 15;
            reasons.add("pas encore de reponse");
        }

        if (equity != null) {
            if (equity.getRaidsAssigned() >= 2) {
                score += equity.getRaidsAssigned() * 6;
                reasons.add("joue deja beaucoup sur les dernieres semaines");
            }
            score -= equity.getBenchCount() * 4;
            if (equity.getBenchCount() >= 2) {
                reasons.add("deja bench souvent, a proteger");
            }
        }

        return BenchSuggestionPlayerDTO.builder()
                .joueurId(personnage.getJoueur().getId())
                .personnageId(personnage.getId())
                .pseudoIhm(personnage.getJoueur().getPseudoIhm())
                .serverPseudo(personnage.getJoueur().getServerPseudo())
                .personnageNom(personnage.getNom())
                .classe(personnage.getClasse())
                .specialisation(personnage.getSpecialisation())
                .role(personnage.getRole())
                .mainCharacter(personnage.isMain())
                .signupStatus("TITULAIRE")
                .confirmationStatus(confirmationStatus != null ? confirmationStatus : "EN_ATTENTE")
                .fairnessScore(score)
                .reasons(reasons)
                .build();
    }

    private BenchSuggestionPlayerDTO toReserveCandidate(JoueurDTO signup, PlayerEquityRowDTO equity) {
        List<String> reasons = new ArrayList<>();
        int score = 0;

        if (signup.getPersonnageMain().isMain()) {
            score += 25;
            reasons.add("main disponible");
        }

        String signupStatus = signup.getStatutParticipation() != null
                ? signup.getStatutParticipation().name()
                : StatutParticipation.TITULAIRE.name();

        if (StatutParticipation.TITULAIRE.name().equals(signupStatus)) {
            score += 20;
            reasons.add("inscrit titulaire");
        } else if (StatutParticipation.TENTATIVE.name().equals(signupStatus)) {
            score += 8;
            reasons.add("tentative a confirmer");
        } else if (StatutParticipation.LATE.name().equals(signupStatus)) {
            score += 4;
            reasons.add("disponible en late");
        }

        if (equity != null) {
            score += Math.max(0, 15 - (equity.getRaidsAssigned() * 4));
            if (equity.getRaidsAssigned() <= 1) {
                reasons.add("peu joue recemment");
            }
            if (equity.getBenchCount() >= 1) {
                score += equity.getBenchCount() * 3;
                reasons.add("a deja ete bench, priorite a reintégrer");
            }
        }

        return BenchSuggestionPlayerDTO.builder()
                .joueurId(signup.getId())
                .personnageId(signup.getPersonnageMain().getId())
                .pseudoIhm(signup.getPseudoIhm())
                .serverPseudo(signup.getServerPseudo())
                .personnageNom(signup.getPersonnageMain().getNom())
                .classe(signup.getPersonnageMain().getClasse())
                .specialisation(signup.getPersonnageMain().getSpecialisation())
                .role(signup.getPersonnageMain().getRole())
                .mainCharacter(signup.getPersonnageMain().isMain())
                .signupStatus(signupStatus)
                .confirmationStatus("HORS_COMPO")
                .fairnessScore(score)
                .reasons(reasons)
                .build();
    }
}
