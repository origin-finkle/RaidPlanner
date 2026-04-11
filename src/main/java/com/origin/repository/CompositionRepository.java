package com.origin.repository;

import com.origin.entity.Composition;
import com.origin.entity.Joueur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.transaction.Transactional;
import java.util.Collection;

public interface CompositionRepository extends JpaRepository<Composition, Long> {
    void deleteByPersonnage_JoueurIn(Collection<Joueur> joueurs);

    @Modifying
    @Transactional
    @Query("DELETE FROM Composition c WHERE c.personnage.id = :personnageId")
    void deleteByPersonnageId(@Param("personnageId") Long personnageId);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE compositions c
            SET c.personnage_id = :targetPersonnageId
            WHERE c.personnage_id = :sourcePersonnageId
              AND NOT EXISTS (
                SELECT 1
                FROM compositions existing
                WHERE existing.raid_id = c.raid_id
                  AND existing.personnage_id = :targetPersonnageId
              )
            """, nativeQuery = true)
    void replacePersonnageReferences(@Param("sourcePersonnageId") Long sourcePersonnageId, @Param("targetPersonnageId") Long targetPersonnageId);
}
