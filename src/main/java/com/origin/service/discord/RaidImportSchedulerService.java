package com.origin.service.discord;

import com.origin.entity.RaidImportSchedulerSettings;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RaidImportSchedulerService {

    private final RaidDiscordScannerService raidDiscordScannerService;
    private final RaidImportSchedulerSettingsService settingsService;
    private final RaidQueryService raidQueryService;
    private final DiscordCustomSignupService discordCustomSignupService;

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
        int publishedCount = publishNextWeekSignupMessages(zoneId);
        settingsService.updateLastRun(LocalDateTime.now(zoneId), importedCount);
        log.info("Import auto Raid-Helper execute | importedCount={} | publishedSignupCount={}", importedCount, publishedCount);
    }

    private int publishNextWeekSignupMessages(ZoneId zoneId) {
        LocalDate today = LocalDate.now(zoneId);
        LocalDate nextResetWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.WEDNESDAY)).plusWeeks(1);
        LocalDateTime start = nextResetWeekStart.atStartOfDay();
        LocalDateTime endExclusive = nextResetWeekStart.plusDays(7).atStartOfDay();
        Set<String> configuredChannelIds = new HashSet<>(raidDiscordScannerService.getConfiguredRaidHelperChannelIds());

        List<com.origin.entity.Raid> raids = raidQueryService.getBestRaidsInRange(start, endExclusive).stream()
                .filter(raid -> configuredChannelIds.contains(raid.getChannelId()))
                .collect(Collectors.toList());

        int publishedCount = 0;
        for (com.origin.entity.Raid raid : raids) {
            try {
                discordCustomSignupService.publishSignupMessageToRaidChannel(raid.getId());
                publishedCount++;
            } catch (Exception exception) {
                log.error("Echec de publication signup pour le raid {}", raid.getId(), exception);
            }
        }

        return publishedCount;
    }
}
