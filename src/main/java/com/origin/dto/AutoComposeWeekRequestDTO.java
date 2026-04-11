package com.origin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AutoComposeWeekRequestDTO {
    private Integer maxRaids;
    private Integer targetTanks;
    private Integer targetHeals;
    private Boolean preferMains;
    private Boolean balanceAcrossRaids;
    private Boolean prioritizeBuffCoverage;
    private Boolean huntersFillMissingBuffs;
}
