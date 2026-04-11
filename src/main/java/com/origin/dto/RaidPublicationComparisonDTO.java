package com.origin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RaidPublicationComparisonDTO {
    private Long raidId;
    private String raidNom;
    private LocalDateTime raidDate;
    private LocalDateTime lastPublishedAt;
    private boolean hasPublishedSnapshot;
    private List<PersonnageDTO> currentGroup1;
    private List<PersonnageDTO> currentGroup2;
    private List<PersonnageDTO> publishedGroup1;
    private List<PersonnageDTO> publishedGroup2;
    private List<String> currentOnlyPlayers;
    private List<String> publishedOnlyPlayers;
}
