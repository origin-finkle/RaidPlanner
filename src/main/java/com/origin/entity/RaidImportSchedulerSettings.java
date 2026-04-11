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
import java.time.LocalDateTime;

@Entity
@Table(name = "raid_import_scheduler_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RaidImportSchedulerSettings {

    @Id
    private Long id;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "day_of_week", nullable = false)
    private String dayOfWeek;

    @Column(name = "run_hour", nullable = false)
    private Integer hour;

    @Column(name = "run_minute", nullable = false)
    private Integer minute;

    @Column(name = "timezone", nullable = false)
    private String timezone;

    @Column(name = "last_run_at")
    private LocalDateTime lastRunAt;

    @Column(name = "last_imported_count")
    private Integer lastImportedCount;
}
