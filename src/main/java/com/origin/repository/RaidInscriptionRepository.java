package com.origin.repository;

import com.origin.entity.RaidInscription;
import com.origin.entity.Raid;
import com.origin.entity.Joueur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RaidInscriptionRepository extends JpaRepository<RaidInscription, Long> {
    Optional<RaidInscription> findByRaidAndJoueur(Raid raid, Joueur joueur);
}
