package com.origin.service.discord;

import com.origin.entity.RaidImportSchedulerSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class RaidImportSchedulerService {

    private final RaidDiscordScannerService raidDiscordScannerService;
    private final RaidImportSchedulerSettingsService settingsService;

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

        int importedCount = raidDiscordScannerService.scanConfiguredRaidHelperChannels();
        settingsService.updateLastRun(LocalDateTime.now(zoneId), importedCount);
        log.info("Import auto Raid-Helper execute | importedCount={}", importedCount);
    }
}
