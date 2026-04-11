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
public class PlanningHealthIssueDTO {
    private Long raidId;
    private String raidNom;
    private LocalDateTime raidDate;
    private String severity;
    private int liveSignupCount;
    private int snapshotSignupCount;
    private boolean published;
    private List<String> issues;
}
