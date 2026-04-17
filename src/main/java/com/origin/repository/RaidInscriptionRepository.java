package com.origin.repository;

import com.origin.entity.Joueur;
import com.origin.entity.Raid;
import com.origin.entity.RaidInscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import javax.transaction.Transactional;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RaidInscriptionRepository extends JpaRepository<RaidInscription, Long> {
    Optional<RaidInscription> findByRaidAndJoueur(Raid raid, Joueur joueur);
    List<RaidInscription> findByRaidIdOrderByIdAsc(Long raidId);

    @Modifying
    @Transactional
    void deleteByRaidId(Long raidId);

    void deleteByJoueurIn(Collection<Joueur> joueurs);
}
