package com.origin.repository;


import com.origin.entity.Joueur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.transaction.Transactional;
import java.util.Optional;

public interface JoueurRepository extends JpaRepository<Joueur, Long> {
    Optional<Joueur> findByDiscordId(String discordId);
    Optional<Joueur> findByPseudo(String pseudo);


    Joueur findByServerPseudo(String serverPseudo);

    @Query("SELECT j FROM Joueur j WHERE LOWER(j.serverPseudo) = LOWER(:serverPseudo)")
    Joueur findByServerPseudoIgnoreCase(@Param("serverPseudo") String serverPseudo);

    @Modifying
    @Transactional
    @Query("UPDATE Joueur j SET j.pseudoIhm = :pseudoIhm WHERE j.pseudo = :pseudo")
    int updatePseudoIhmByPseudo(@Param("pseudoIhm") String pseudoIhm, @Param("pseudo") String pseudo);

    @Modifying
    @Transactional
    @Query("UPDATE Joueur j SET j.pseudoIhm = :pseudoIhm WHERE j.id = :id")
    int updatePseudoIhmById(@Param("pseudoIhm") String pseudoIhm, @Param("id") Long id);
}