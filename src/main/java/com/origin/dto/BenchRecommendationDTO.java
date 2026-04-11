package com.origin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BenchRecommendationDTO {
    private Long raidId;
    private String raidNom;
    private LocalDateTime raidDate;
    private int assignedCount;
    private int reserveCount;
    private List<BenchSuggestionPlayerDTO> benchCandidates;
    private List<BenchSuggestionPlayerDTO> reserveCandidates;
    private List<String> warnings;
}
