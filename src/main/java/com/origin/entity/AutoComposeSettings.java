package com.origin.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "auto_compose_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutoComposeSettings {

    @Id
    private Long id;

    @Column(name = "max_raids", nullable = false)
    private Integer maxRaids;

    @Column(name = "target_tanks", nullable = false)
    private Integer targetTanks;

    @Column(name = "target_heals", nullable = false)
    private Integer targetHeals;

    @Column(name = "prefer_mains", nullable = false)
    private Boolean preferMains;

    @Column(name = "balance_across_raids", nullable = false)
    private Boolean balanceAcrossRaids;

    @Column(name = "prioritize_buff_coverage", nullable = false)
    private Boolean prioritizeBuffCoverage;

    @Column(name = "hunters_fill_missing_buffs", nullable = false)
    private Boolean huntersFillMissingBuffs;
}
