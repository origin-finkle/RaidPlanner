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
public class OfficerDashboardRaidDTO {
    private Long raidId;
    private String raidNom;
    private LocalDateTime raidDate;
    private String compositionStatus;
    private boolean compositionLocked;
    private boolean published;
    private int totalAssignedPlayers;
    private int confirmedCount;
    private int cancelledCount;
    private int pendingCount;
    private int liveSignupCount;
    private int healthIssueCount;
    private List<String> actions;
}
