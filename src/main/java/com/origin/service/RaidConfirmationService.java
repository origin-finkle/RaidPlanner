package com.origin.service;

import com.origin.dto.RaidConfirmationPlayerDTO;
import com.origin.dto.RaidConfirmationSummaryDTO;
import com.origin.entity.Personnage;
import com.origin.entity.Raid;
import com.origin.entity.RaidInscription;
import com.origin.repository.RaidInscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class RaidConfirmationService {

    private final RaidService raidService;
    private final RaidInscriptionRepository raidInscriptionRepository;

    @Transactional(readOnly = true)
    public RaidConfirmationSummaryDTO getSummary(Long raidId) {
        Raid raid = raidService.getRaidById(raidId);

        Map<Long, Personnage> assignedCharactersByPlayer = Stream.concat(raid.getGroup1().stream(), raid.getGroup2().stream())
                .filter(Objects::nonNull)
                .filter(personnage -> personnage.getJoueur() != null && personnage.getJoueur().getId() != null)
                .collect(Collectors.toMap(
                        personnage -> personnage.getJoueur().getId(),
                        personnage -> personnage,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        Map<Long, RaidInscription.StatutInscription> statusByPlayer = raidInscriptionRepository.findByRaidIdOrderByIdAsc(raidId).stream()
                .filter(inscription -> inscription.getJoueur() != null && inscription.getJoueur().getId() != null)
                .collect(Collectors.toMap(
                        inscription -> inscription.getJoueur().getId(),
                        RaidInscription::getStatut,
                        (left, right) -> right,
                        LinkedHashMap::new
                ));

        List<RaidConfirmationPlayerDTO> confirmedPlayers = new ArrayList<>();
        List<RaidConfirmationPlayerDTO> cancelledPlayers = new ArrayList<>();
        List<RaidConfirmationPlayerDTO> pendingPlayers = new ArrayList<>();

        for (Personnage personnage : assignedCharactersByPlayer.values()) {
            RaidInscription.StatutInscription status = statusByPlayer.get(personnage.getJoueur().getId());
            if (status == RaidInscription.StatutInscription.CONFIRME) {
                confirmedPlayers.add(toPlayerDto(personnage, "CONFIRME"));
            } else if (status == RaidInscription.StatutInscription.ANNULE) {
                cancelledPlayers.add(toPlayerDto(personnage, "ANNULE"));
            } else {
                pendingPlayers.add(toPlayerDto(personnage, "EN_ATTENTE"));
            }
        }

        Comparator<RaidConfirmationPlayerDTO> comparator = Comparator
                .comparing(RaidConfirmationPlayerDTO::getPseudoIhm, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(RaidConfirmationPlayerDTO::getPersonnageNom, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));

        confirmedPlayers.sort(comparator);
        cancelledPlayers.sort(comparator);
        pendingPlayers.sort(comparator);

        int totalPlayers = assignedCharactersByPlayer.size();
        int completionRate = totalPlayers == 0
                ? 0
                : Math.round(((confirmedPlayers.size() + cancelledPlayers.size()) * 100f) / totalPlayers);

        return RaidConfirmationSummaryDTO.builder()
                .raidId(raid.getId())
                .raidNom(raid.getNom())
                .raidDate(raid.getDate())
                .totalPlayers(totalPlayers)
                .confirmedCount(confirmedPlayers.size())
                .cancelledCount(cancelledPlayers.size())
                .pendingCount(pendingPlayers.size())
                .completionRate(completionRate)
                .confirmedPlayers(confirmedPlayers)
                .cancelledPlayers(cancelledPlayers)
                .pendingPlayers(pendingPlayers)
                .build();
    }

    private RaidConfirmationPlayerDTO toPlayerDto(Personnage personnage, String status) {
        return RaidConfirmationPlayerDTO.builder()
                .joueurId(personnage.getJoueur().getId())
                .personnageId(personnage.getId())
                .pseudoIhm(personnage.getJoueur().getPseudoIhm())
                .serverPseudo(personnage.getJoueur().getServerPseudo())
                .personnageNom(personnage.getNom())
                .classe(personnage.getClasse())
                .specialisation(personnage.getSpecialisation())
                .role(personnage.getRole())
                .confirmationStatus(status)
                .build();
    }
}
