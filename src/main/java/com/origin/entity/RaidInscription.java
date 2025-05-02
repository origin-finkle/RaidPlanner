package com.origin.entity;

import lombok.*;

import javax.persistence.*;

@Entity
@Table(name = "raid_inscriptions", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"raid_id", "joueur_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RaidInscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "raid_id")
    private Raid raid;

    @ManyToOne(optional = false)
    @JoinColumn(name = "joueur_id")
    private Joueur joueur;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    private StatutInscription statut;

    public enum StatutInscription {
        CONFIRME,
        ANNULE
    }
}
