package com.origin.repository;

import com.origin.entity.Personnage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.transaction.Transactional;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PersonnageRepository extends JpaRepository<Personnage, Long> {

    Optional<Personnage> findByNom(String nom);

    @Query(value = "SELECT * FROM personnages WHERE nom COLLATE utf8mb4_bin = :nom LIMIT 1", nativeQuery = true)
    Optional<Personnage> findByNomStrict(@Param("nom") String nom);

    @Query("SELECT p FROM Personnage p WHERE p.nom = :nom AND p.joueur.id = :joueurId")
    Optional<Personnage> findByNomAndJoueurId(@Param("nom") String nom, @Param("joueurId") Long joueurId);

    @Query("SELECT p FROM Personnage p WHERE p.joueur.pseudo = :pseudo AND p.main = true")
    Optional<Personnage> findMainByPseudoDiscord(@Param("pseudo") String pseudo);
    List<Personnage> findByJoueurId(Long idJoueur);

    @Query("SELECT p FROM Personnage p WHERE p.joueur.id = :idJoueur AND p != p.joueur.mainCharacter")
    List<Personnage> findByJoueurIdAndNotMain(@Param("idJoueur") Long idJoueur);

    @Modifying
    @Transactional
    @Query(value = "DELETE rg FROM raid_group1 rg JOIN personnages p ON rg.personnage_id = p.id WHERE p.joueur_id IN (:joueurIds)", nativeQuery = true)
    void deleteGroup1LinksByJoueurIds(@Param("joueurIds") Collection<Long> joueurIds);

    @Modifying
    @Transactional
    @Query(value = "DELETE rg FROM raid_group2 rg JOIN personnages p ON rg.personnage_id = p.id WHERE p.joueur_id IN (:joueurIds)", nativeQuery = true)
    void deleteGroup2LinksByJoueurIds(@Param("joueurIds") Collection<Long> joueurIds);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM raid_group1 WHERE personnage_id = :personnageId", nativeQuery = true)
    void deleteGroup1LinksByPersonnageId(@Param("personnageId") Long personnageId);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM raid_group2 WHERE personnage_id = :personnageId", nativeQuery = true)
    void deleteGroup2LinksByPersonnageId(@Param("personnageId") Long personnageId);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE raid_group1 rg
            SET rg.personnage_id = :targetPersonnageId
            WHERE rg.personnage_id = :sourcePersonnageId
              AND NOT EXISTS (
                SELECT 1
                FROM raid_group1 existing
                WHERE existing.raid_id = rg.raid_id
                  AND existing.personnage_id = :targetPersonnageId
              )
            """, nativeQuery = true)
    void replaceGroup1Links(@Param("sourcePersonnageId") Long sourcePersonnageId, @Param("targetPersonnageId") Long targetPersonnageId);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE raid_group2 rg
            SET rg.personnage_id = :targetPersonnageId
            WHERE rg.personnage_id = :sourcePersonnageId
              AND NOT EXISTS (
                SELECT 1
                FROM raid_group2 existing
                WHERE existing.raid_id = rg.raid_id
                  AND existing.personnage_id = :targetPersonnageId
              )
            """, nativeQuery = true)
    void replaceGroup2Links(@Param("sourcePersonnageId") Long sourcePersonnageId, @Param("targetPersonnageId") Long targetPersonnageId);

    void deleteByJoueurIn(Collection<com.origin.entity.Joueur> joueurs);

}
