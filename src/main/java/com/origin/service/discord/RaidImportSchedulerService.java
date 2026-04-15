package com.origin.service.discord;

import com.origin.entity.RaidImportSchedulerSettings;
import com.origin.service.RaidTemplateOccurrenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

@Slf4j
@Service
@RequiredArgsConstructor
public class RaidImportSchedulerService {

    private final RaidImportSchedulerSettingsService settingsService;
    private final RaidTemplateOccurrenceService raidTemplateOccurrenceService;

    @Scheduled(fixedDelayString = "${raid.import.scheduler.poll-ms:60000}")
    public void triggerImportIfDue() {
        RaidImportSchedulerSettings settings = settingsService.getOrCreateSettings();
        if (!Boolean.TRUE.equals(settings.getEnabled())) {
            return;
        }

        ZoneId zoneId = ZoneId.of(settings.getTimezone());
        ZonedDateTime now = ZonedDateTime.now(zoneId).withSecond(0).withNano(0);
        DayOfWeek targetDay = DayOfWeek.valueOf(settings.getDayOfWeek());
        ZonedDateTime scheduledRun = now.withHour(settings.getHour()).withMinute(settings.getMinute());

        if (now.getDayOfWeek() != targetDay || now.isBefore(scheduledRun)) {
            return;
        }

        LocalDateTime lastRunAt = settings.getLastRunAt();
        LocalDateTime currentSlot = scheduledRun.toLocalDateTime();
        if (lastRunAt != null && !lastRunAt.isBefore(currentSlot)) {
            return;
        }

        int publishedCount = publishNextWeekSignupMessages(zoneId);
        settingsService.updateLastRun(LocalDateTime.now(zoneId), publishedCount);
        log.info("Publication auto executee | generatedAndPublishedCount={}", publishedCount);
    }

    private int publishNextWeekSignupMessages(ZoneId zoneId) {
        LocalDate today = LocalDate.now(zoneId);
        LocalDate nextResetWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.WEDNESDAY)).plusWeeks(1);
        return raidTemplateOccurrenceService.publishWeek(nextResetWeekStart);
    }
}
