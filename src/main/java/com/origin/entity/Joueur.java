package com.origin.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "joueurs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Joueur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "discord_id", nullable = false, unique = true)
    private String discordId;

    @Column(nullable = false)
    private String pseudo;

    @Column(nullable = false)
    private String serverPseudo;

    @Column(nullable = false)
    private String pseudoIhm;

    @Column(nullable = false)
    private Boolean isRaider;

    @OneToMany(mappedBy = "joueur", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<Personnage> personnages;

    @OneToOne
    @JoinColumn(name = "main_character_id", referencedColumnName = "id")
    private Personnage mainCharacter;

    @Override
    public int hashCode() {
        return Objects.hash(discordId); // uniquement l'identifiant
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Joueur joueur = (Joueur) o;
        return Objects.equals(discordId, joueur.discordId);
    }

}
