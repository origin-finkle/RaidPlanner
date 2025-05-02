package com.origin.entity;

import lombok.*;

import javax.persistence.*;

@Entity
@Table(name = "compositions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Composition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raid_id", nullable = false)
    private Raid raid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "personnage_id", nullable = false)
    private Personnage personnage;

    @Column(name = "role_assigne", nullable = false)
    private String roleAssigne; // Exemple : "Tank", "Heal", "DPS", ou même "RL", "Buff", etc.

    @Column(name = "ordre_groupe")
    private Integer ordreGroupe; // Ex : 1 à 5 pour les groupes de raid

    @Column
    private String commentaire; // Pour un message d'organisation
}