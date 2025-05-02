package com.origin.service;

import com.origin.entity.*;
import com.origin.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RaidInscriptionService {

    private final RaidRepository raidRepository;
    private final JoueurRepository joueurRepository;
    private final RaidInscriptionRepository raidInscriptionRepository;

    public void confirmParticipation(Long raidId, String discordId) {
        Raid raid = raidRepository.findById(raidId)
                .orElseThrow(() -> new IllegalArgumentException("Raid non trouvé"));

        Joueur joueur = joueurRepository.findByDiscordId(discordId)
                .orElseThrow(() -> new IllegalArgumentException("Joueur non trouvé"));

        RaidInscription inscription = raidInscriptionRepository
                .findByRaidAndJoueur(raid, joueur)
                .orElse(RaidInscription.builder()
                        .raid(raid)
                        .joueur(joueur)
                        .build());

        inscription.setStatut(RaidInscription.StatutInscription.CONFIRME);
        raidInscriptionRepository.save(inscription);
    }

    public void cancelParticipation(Long raidId, String discordId) {
        Raid raid = raidRepository.findById(raidId)
                .orElseThrow(() -> new IllegalArgumentException("Raid non trouvé"));

        Joueur joueur = joueurRepository.findByDiscordId(discordId)
                .orElseThrow(() -> new IllegalArgumentException("Joueur non trouvé"));

        RaidInscription inscription = raidInscriptionRepository
                .findByRaidAndJoueur(raid, joueur)
                .orElse(RaidInscription.builder()
                        .raid(raid)
                        .joueur(joueur)
                        .build());

        inscription.setStatut(RaidInscription.StatutInscription.ANNULE);
        raidInscriptionRepository.save(inscription);
    }
}
