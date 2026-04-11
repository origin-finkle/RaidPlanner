package com.origin.repository;

import com.origin.entity.RaidPublicationHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RaidPublicationHistoryRepository extends JpaRepository<RaidPublicationHistory, Long> {
    List<RaidPublicationHistory> findTop30ByOrderByPublishedAtDesc();
}
