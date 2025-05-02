package com.origin.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "raids")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Raid {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private LocalDateTime date;

    @Column(name = "channel_id", nullable = false)
    private String channelId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private RaidTemplate template;

    @OneToMany(mappedBy = "raid", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Inscription> inscriptions = new HashSet<>();

    @Column(name = "raid_helper_id", unique = true)
    private String raidHelperId;

    @Column(name = "discord_message_id")
    private Long discordMessageId;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "group1_id")
    private Set<Personnage> group1;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "group2_id")
    private Set<Personnage> group2;
}

