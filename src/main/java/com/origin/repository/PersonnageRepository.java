package com.origin.repository;

import com.origin.entity.Personnage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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


}