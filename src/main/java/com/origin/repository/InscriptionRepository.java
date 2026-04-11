package com.origin.repository;

import com.origin.entity.Inscription;
import com.origin.entity.Joueur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.transaction.Transactional;
import java.util.Collection;
import java.util.List;

public interface InscriptionRepository extends JpaRepository<Inscription, Long> {
    void deleteByPersonnage_JoueurIn(Collection<Joueur> joueurs);
    List<Inscription> findByRaidIdOrderByIdAsc(Long raidId);

    @Query("""
            SELECT i
            FROM Inscription i
            JOIN FETCH i.personnage p
            JOIN FETCH p.joueur
            WHERE i.raid.id = :raidId
            ORDER BY i.id ASC
            """)
    List<Inscription> findDetailedByRaidIdOrderByIdAsc(@Param("raidId") Long raidId);

    @Modifying
    @Transactional
    void deleteByRaidId(Long raidId);

    @Modifying
    @Transactional
    @Query(value = """
            DELETE i
            FROM inscriptions i
            INNER JOIN personnages p ON p.id = i.personnage_id
            WHERE i.raid_id = :raidId
              AND p.joueur_id = :joueurId
            """, nativeQuery = true)
    void deleteByRaidIdAndJoueurId(@Param("raidId") Long raidId, @Param("joueurId") Long joueurId);

    @Modifying
    @Transactional
    @Query("DELETE FROM Inscription i WHERE i.personnage.id = :personnageId")
    void deleteByPersonnageId(@Param("personnageId") Long personnageId);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE inscriptions i
            SET i.personnage_id = :targetPersonnageId
            WHERE i.personnage_id = :sourcePersonnageId
              AND NOT EXISTS (
                SELECT 1
                FROM inscriptions existing
                WHERE existing.raid_id = i.raid_id
                  AND existing.personnage_id = :targetPersonnageId
              )
            """, nativeQuery = true)
    void replacePersonnageReferences(@Param("sourcePersonnageId") Long sourcePersonnageId, @Param("targetPersonnageId") Long targetPersonnageId);
}
