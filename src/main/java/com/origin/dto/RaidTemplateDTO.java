package com.origin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RaidTemplateDTO {
    private Long id;
    private String nom;
    private String jourSemaine;
    private String heure;
    private String channelId;
    private String messageId;
    private Integer raidSize;
    private Integer targetTanks;
    private Integer targetHeals;
    private Boolean preferMains;
    private Boolean prioritizeBuffCoverage;
    private Boolean huntersFillMissingBuffs;
}
