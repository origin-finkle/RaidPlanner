package com.origin.repository;

import com.origin.entity.RaidPublicationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import javax.transaction.Transactional;
import java.util.List;

public interface RaidPublicationHistoryRepository extends JpaRepository<RaidPublicationHistory, Long> {
    List<RaidPublicationHistory> findTop30ByOrderByPublishedAtDesc();

    @Modifying
    @Transactional
    void deleteByRaidId(Long raidId);
}
