package com.origin.service;

import com.origin.dto.JoueurDTO;
import com.origin.dto.PlayerEquityRowDTO;
import com.origin.dto.PlayerEquitySummaryDTO;
import com.origin.entity.Inscription;
import com.origin.entity.Joueur;
import com.origin.entity.Personnage;
import com.origin.entity.Raid;
import com.origin.repository.InscriptionRepository;
import com.origin.repository.RaidRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class PlayerEquityService {

    private final JoueurService joueurService;
    private final RaidRepository raidRepository;
    private final InscriptionRepository inscriptionRepository;

    @Transactional(readOnly = true)
    public PlayerEquitySummaryDTO getSummary() {
        LocalDate rangeStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.WEDNESDAY)).minusWeeks(3);
        LocalDate rangeEnd = rangeStart.plusDays(27);

        List<Raid> raids = raidRepository.findByDateGreaterThanEqualAndDateLessThanOrderByDateAsc(
                rangeStart.atStartOfDay(),
                rangeEnd.plusDays(1).atStartOfDay()
        );

        Map<Long, PlayerStats> statsByPlayer = joueurService.findAllJoueurs().stream()
                .filter(JoueurDTO::isRaider)
                .collect(Collectors.toMap(
                        JoueurDTO::getId,
                        PlayerStats::new,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        for (Raid raid : raids) {
            applyAssignments(raid, statsByPlayer);
            applySignups(inscriptionRepository.findByRaidIdOrderByIdAsc(raid.getId()), statsByPlayer);
        }

        List<PlayerEquityRowDTO> rows = statsByPlayer.values().stream()
                .map(PlayerStats::toDto)
                .sorted(Comparator
                        .comparingInt(PlayerEquityRowDTO::getRaidsAssigned).reversed()
                        .thenComparing(PlayerEquityRowDTO::getPseudoIhm, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        return PlayerEquitySummaryDTO.builder()
                .rangeStart(rangeStart)
                .rangeEnd(rangeEnd)
                .totalPlayers(rows.size())
                .players(rows)
                .build();
    }

    private void applyAssignments(Raid raid, Map<Long, PlayerStats> statsByPlayer) {
        Stream.concat(raid.getGroup1().stream(), raid.getGroup2().stream())
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(personnage -> personnage.getJoueur().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()))
                .forEach((joueurId, personnages) -> {
                    PlayerStats stats = statsByPlayer.get(joueurId);
                    if (stats == null) {
                        return;
                    }
                    stats.raidsAssigned++;
                    if (personnages.stream().anyMatch(Personnage::isMain)) {
                        stats.mainAssignments++;
                    } else {
                        stats.rerollAssignments++;
                    }
                });
    }

    private void applySignups(List<Inscription> inscriptions, Map<Long, PlayerStats> statsByPlayer) {
        for (Inscription inscription : inscriptions) {
            Joueur joueur = inscription.getPersonnage() != null ? inscription.getPersonnage().getJoueur() : null;
            if (joueur == null) {
                continue;
            }

            PlayerStats stats = statsByPlayer.get(joueur.getId());
            if (stats == null) {
                continue;
            }

            stats.signupsCount++;
            switch (normalizeStatus(inscription.getStatut())) {
                case "BENCH":
                    stats.benchCount++;
                    break;
                case "LATE":
                    stats.lateCount++;
                    break;
                case "TENTATIVE":
                    stats.tentativeCount++;
                    break;
                case "ABSENCE":
                    stats.absenceCount++;
                    break;
                default:
                    break;
            }
        }
    }

    private String normalizeStatus(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static class PlayerStats {
        private final JoueurDTO joueur;
        private int raidsAssigned;
        private int signupsCount;
        private int mainAssignments;
        private int rerollAssignments;
        private int benchCount;
        private int lateCount;
        private int tentativeCount;
        private int absenceCount;

        private PlayerStats(JoueurDTO joueur) {
            this.joueur = joueur;
        }

        private PlayerEquityRowDTO toDto() {
            return PlayerEquityRowDTO.builder()
                    .joueurId(joueur.getId())
                    .pseudoIhm(joueur.getPseudoIhm())
                    .serverPseudo(joueur.getServerPseudo())
                    .raidsAssigned(raidsAssigned)
                    .signupsCount(signupsCount)
                    .mainAssignments(mainAssignments)
                    .rerollAssignments(rerollAssignments)
                    .benchCount(benchCount)
                    .lateCount(lateCount)
                    .tentativeCount(tentativeCount)
                    .absenceCount(absenceCount)
                    .build();
        }
    }
}
