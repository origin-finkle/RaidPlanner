package com.origin.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

import javax.persistence.*;

@Entity
@Table(name = "personnages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Personnage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String classe;

    @Column(nullable = false)
    private String specialisation;

    @Column(nullable = false)
    private String role; // Ex: "Tank", "Heal", "DPS"

    @Column(nullable = false)
    private boolean main;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "joueur_id")
    @JsonIgnore
    private Joueur joueur;

    @Override
    public String toString() {
        return "Personnage{" +
                "id=" + id +
                ", joueurId=" + (joueur != null ? joueur.getDiscordId() : null) +
                '}';
    }
}