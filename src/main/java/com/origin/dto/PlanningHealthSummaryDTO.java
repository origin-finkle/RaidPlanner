package com.origin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanningHealthSummaryDTO {
    private int totalRaids;
    private int raidsWithIssues;
    private int raidsWithoutSignups;
    private int unpublishedRaids;
    private int outdatedRaids;
    private List<PlanningHealthIssueDTO> issues;
}
