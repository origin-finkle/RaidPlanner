package com.origin.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "raid_publication_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RaidPublicationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raid_id", nullable = false)
    private Raid raid;

    @Column(name = "channel_id", nullable = false)
    private String channelId;

    @Column(name = "guild_id")
    private String guildId;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "is_updated", nullable = false)
    private boolean updated;

    @Column(name = "is_test_publication", nullable = false)
    private boolean testPublication;

    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;
}
