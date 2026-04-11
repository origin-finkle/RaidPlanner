package com.origin.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder
public class RaidSchedulerStatusDTO {
    boolean enabled;
    String dayOfWeek;
    Integer hour;
    Integer minute;
    String cron;
    String timezone;
    LocalDateTime nextRunAt;
    LocalDateTime lastRunAt;
    Integer lastImportedCount;
    String publicationDay;
    String publicationTime;
    List<String> channelIds;
    List<String> channelNames;
}
