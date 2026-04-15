package com.origin.repository;

import com.origin.entity.Raid;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RaidRepository extends JpaRepository<Raid, Long> {


    boolean existsByNomAndDate(String nom, LocalDateTime date);

    boolean existsByDiscordMessageId(Long discordMessageId);

    boolean existsByRaidHelperId(String raidHelperId);

    Optional<Raid> findByRaidHelperId(String raidHelperId);

    List<Raid> findAllByDiscordMessageId(Long discordMessageId);

    List<Raid> findAllByPublishedMessageId(Long publishedMessageId);

    List<Raid> findAllByLastMissingPingSourceMessageId(Long lastMissingPingSourceMessageId);

    Optional<Raid> findByNomAndDate(String nom, LocalDateTime date);

    Optional<Raid> findFirstByTemplateIdAndDateGreaterThanEqualAndDateLessThanOrderByDateAsc(Long templateId,
                                                                                              LocalDateTime start,
                                                                                              LocalDateTime end);

    List<Raid> findByTemplateIdOrderByDateAsc(Long templateId);

    List<Raid> findByChannelIdAndDateGreaterThanEqualAndDateLessThanOrderByDateAsc(String channelId,
                                                                                    LocalDateTime start,
                                                                                    LocalDateTime end);

    List<Raid> findByDateGreaterThanEqualOrderByDateAsc(LocalDateTime start);

    List<Raid> findByDateGreaterThanEqualAndDateLessThanOrderByDateAsc(LocalDateTime start, LocalDateTime end);

    @Query("SELECT DISTINCT r FROM Raid r " +
            "LEFT JOIN FETCH r.group1 g1 " +
            "LEFT JOIN FETCH g1.joueur " +
            "LEFT JOIN FETCH r.group2 g2 " +
            "LEFT JOIN FETCH g2.joueur " +
            "WHERE r.id = :id")
    Optional<Raid> findWithGroups(@Param("id") Long id);

    @Query("SELECT DISTINCT r FROM Raid r " +
            "LEFT JOIN FETCH r.group1 g1 " +
            "LEFT JOIN FETCH g1.joueur " +
            "LEFT JOIN FETCH r.group2 g2 " +
            "LEFT JOIN FETCH g2.joueur")
    List<Raid> findAllWithGroups();
}
