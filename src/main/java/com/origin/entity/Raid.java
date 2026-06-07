package com.origin.entity;

import com.origin.enumOrigin.CompositionWorkflowStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "raids")
@Getter
@Setter
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

    @Column(name = "published_message_id")
    private Long publishedMessageId;

    @Column(name = "published_channel_id")
    private String publishedChannelId;

    @Column(name = "signup_message_id")
    private Long signupMessageId;

    @Column(name = "signup_channel_id")
    private String signupChannelId;

    @Column(name = "last_signup_published_at")
    private LocalDateTime lastSignupPublishedAt;

    @Column(name = "last_missing_ping_source_message_id")
    private Long lastMissingPingSourceMessageId;

    @Column(name = "last_missing_ping_at")
    private LocalDateTime lastMissingPingAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "composition_status")
    @Builder.Default
    private CompositionWorkflowStatus compositionStatus = CompositionWorkflowStatus.DRAFT;

    @Column(name = "composition_locked")
    @Builder.Default
    private Boolean compositionLocked = false;

    @Column(name = "last_published_at")
    private LocalDateTime lastPublishedAt;

    @Column(name = "last_published_group1_snapshot", length = 255)
    private String lastPublishedGroup1Snapshot;

    @Column(name = "last_published_group2_snapshot", length = 255)
    private String lastPublishedGroup2Snapshot;

    @Column(name = "ignore_weekly_conflicts")
    @Builder.Default
    private Boolean ignoreWeeklyConflicts = false;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "raid_group1",
            joinColumns = @JoinColumn(name = "raid_id"),
            inverseJoinColumns = @JoinColumn(name = "personnage_id")
    )
    @Builder.Default
    private Set<Personnage> group1 = new LinkedHashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "raid_group2",
            joinColumns = @JoinColumn(name = "raid_id"),
            inverseJoinColumns = @JoinColumn(name = "personnage_id")
    )
    @Builder.Default
    private Set<Personnage> group2 = new LinkedHashSet<>();

    public boolean isCompositionLocked() {
        return Boolean.TRUE.equals(compositionLocked);
    }

    public boolean isIgnoreWeeklyConflicts() {
        return Boolean.TRUE.equals(ignoreWeeklyConflicts);
    }
}

