package com.origin.service.discord;

import com.origin.entity.Inscription;
import com.origin.entity.Joueur;
import com.origin.entity.Personnage;
import com.origin.entity.Raid;
import com.origin.repository.InscriptionRepository;
import com.origin.repository.JoueurRepository;
import com.origin.repository.PersonnageRepository;
import com.origin.repository.RaidRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RaidHelperImporterService {

    private final JoueurRepository joueurRepository;
    private final PersonnageRepository personnageRepository;
    private final RaidRepository raidRepository;
    private final InscriptionRepository inscriptionRepository;

    public void importerInscription(String discordName, String persoNom, String classe, String spec, String role) {
        // 1. Joueur
        Joueur joueur = joueurRepository.findByPseudo(discordName)
                .orElseGet(() -> joueurRepository.save(Joueur.builder().pseudo(discordName).build()));

        // 2. Personnage
        Personnage perso = personnageRepository.findByNom(persoNom)
                .orElseGet(() -> personnageRepository.save(
                        Personnage.builder()
                                .nom(persoNom)
                                .classe(classe)
                                .specialisation(spec)
                                .role(role)
                                .joueur(joueur)
                                .build()
                ));

        // 3. Raid (à remplacer par un vrai raid si besoin)
        Raid raid = raidRepository.findById(1L).orElseGet(() -> {
            Raid r = Raid.builder()
                    .nom("Raid temporaire")
                    .date(LocalDateTime.now())
                    .build();
            return raidRepository.save(r);
        });

        // 4. Inscription
        Inscription inscription = new Inscription();
        inscription.setRaid(raid);
        inscription.setPersonnage(perso);
        inscription.setStatut("confirmed");

        inscriptionRepository.save(inscription);
    }
}