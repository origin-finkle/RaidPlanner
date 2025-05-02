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

    boolean existsByRaidHelperId(String raidHelperId);

    Optional<Raid> findByRaidHelperId(String raidHelperId);

    @Query("SELECT r FROM Raid r WHERE r.date > CURRENT_TIMESTAMP")
    List<Raid> findUpcomingRaids();

    @Query("SELECT r FROM Raid r LEFT JOIN FETCH r.group1 LEFT JOIN FETCH r.group2 WHERE r.id = :id")
    Optional<Raid> findWithGroups(@Param("id") Long id);
}