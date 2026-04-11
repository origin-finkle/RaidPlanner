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
public class RaidConfirmationSummaryDTO {
    private Long raidId;
    private String raidNom;
    private LocalDateTime raidDate;
    private int totalPlayers;
    private int confirmedCount;
    private int cancelledCount;
    private int pendingCount;
    private int completionRate;
    private List<RaidConfirmationPlayerDTO> confirmedPlayers;
    private List<RaidConfirmationPlayerDTO> cancelledPlayers;
    private List<RaidConfirmationPlayerDTO> pendingPlayers;
}
