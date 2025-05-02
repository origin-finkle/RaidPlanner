package com.origin.entity;

import lombok.*;

import javax.persistence.*;

@Entity
@Table(name = "raid_templates")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RaidTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nom; // Ex : "Bastion 25 - Mercredi"

    @Column(name = "jour_semaine", nullable = false)
    private String jourSemaine; // Exemple : "Mercredi"

    @Column(nullable = false)
    private String heure; // Format HH:mm, ex: "21:00"

    @Column(name = "channel_id", nullable = false)
    private String channelId; // ID du salon Discord

    @Column(name = "message_id")
    private String messageId; // ID du message Raid Helper à surveiller (si applicable)
}