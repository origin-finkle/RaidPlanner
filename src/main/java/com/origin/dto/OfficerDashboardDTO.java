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
public class OfficerDashboardDTO {
    private int trackedRaids;
    private int readyToPublishCount;
    private int pendingConfirmationRaidCount;
    private int raidsWithDeclines;
    private int raidsWithHealthIssues;
    private List<OfficerDashboardRaidDTO> raids;
}
