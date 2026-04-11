package com.origin.service.discord;

import com.origin.entity.Raid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyMissingPingSchedulerService {

    private final RaidDiscordScannerService raidDiscordScannerService;
    private final RaidQueryService raidQueryService;
    private final MissingRaidPingService missingRaidPingService;

    @Value("${raid.missing-ping.timezone:Europe/Paris}")
    private String timezone;

    @Scheduled(
            cron = "${raid.missing-ping.next-week.cron:0 0 21 * * THU}",
            zone = "${raid.missing-ping.timezone:Europe/Paris}"
    )
    public void sendMissingPingsForNextResetWeek() {
        ZoneId zoneId = ZoneId.of(timezone);
        LocalDate today = LocalDate.now(zoneId);
        LocalDate nextResetWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.WEDNESDAY)).plusWeeks(1);
        LocalDateTime start = nextResetWeekStart.atStartOfDay();
        LocalDateTime endExclusive = nextResetWeekStart.plusDays(7).atStartOfDay();

        int importedCount = raidDiscordScannerService.scanConfiguredRaidHelperChannels();
        List<Raid> raids = raidQueryService.getBestRaidsInRange(start, endExclusive);

        log.info(
                "Relance auto des non-inscrits - semaine suivante {} -> {} | raids={} | importsRecents={}",
                start.toLocalDate(),
                endExclusive.minusDays(1).toLocalDate(),
                raids.size(),
                importedCount
        );

        for (Raid raid : raids) {
            try {
                missingRaidPingService.sendMissingPingToRaidChannelIfNeeded(raid.getId());
            } catch (Exception exception) {
                log.error("Echec de la relance auto pour le raid {}", raid.getId(), exception);
            }
        }
    }
}
