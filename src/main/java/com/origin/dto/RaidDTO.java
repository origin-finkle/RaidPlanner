package com.origin.dto;

import com.origin.enumOrigin.CompositionWorkflowStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
public class RaidDTO {
    private Long id;
    private String nom;
    private LocalDateTime heure;
    private String channelId;
    private List<JoueurDTO> joueurDTOList;
    private List<PersonnageDTO> group1;
    private List<PersonnageDTO> group2;
    private CompositionWorkflowStatus compositionStatus;
    private boolean compositionLocked;
    private LocalDateTime lastPublishedAt;
    private boolean ignoreWeeklyConflicts;
}
