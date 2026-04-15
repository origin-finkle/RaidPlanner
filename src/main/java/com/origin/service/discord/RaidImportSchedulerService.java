package com.origin.service.discord;

import com.origin.entity.RaidImportSchedulerSettings;
import com.origin.entity.RaidTemplate;
import com.origin.repository.RaidTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RaidImportSchedulerService {

    private final RaidDiscordScannerService raidDiscordScannerService;
    private final RaidImportSchedulerSettingsService settingsService;
    private final RaidQueryService raidQueryService;
    private final DiscordCustomSignupService discordCustomSignupService;
    private final RaidTemplateRepository raidTemplateRepository;

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
        Map<LocalDate, List<com.origin.entity.Raid>> raidsByDate = raidQueryService.getBestRaidsInRange(start, endExclusive).stream()
                .collect(Collectors.groupingBy(raid -> raid.getDate().toLocalDate()));
        List<RaidTemplate> templates = raidTemplateRepository.findAll().stream()
                .filter(template -> template.getChannelId() != null && !template.getChannelId().isBlank())
                .collect(Collectors.toList());

        int publishedCount = 0;
        for (RaidTemplate template : templates) {
            com.origin.entity.Raid raid = findRaidForTemplate(template, nextResetWeekStart, raidsByDate);
            if (raid == null) {
                log.warn("Aucun raid importe ne correspond au slot auto {} pour la semaine du {}", template.getNom(), nextResetWeekStart);
                continue;
            }

            try {
                discordCustomSignupService.publishSignupMessageToChannel(raid.getId(), template.getChannelId());
                publishedCount++;
            } catch (Exception exception) {
                log.error("Echec de publication signup pour le raid {} sur le salon {}", raid.getId(), template.getChannelId(), exception);
            }
        }

        return publishedCount;
    }

    private com.origin.entity.Raid findRaidForTemplate(RaidTemplate template,
                                                       LocalDate weekStart,
                                                       Map<LocalDate, List<com.origin.entity.Raid>> raidsByDate) {
        DayOfWeek dayOfWeek = normalizeDayOfWeek(template.getJourSemaine());
        if (dayOfWeek == null) {
            return null;
        }

        LocalDate targetDate = weekStart.plusDays(dayOffset(dayOfWeek));
        List<com.origin.entity.Raid> raids = raidsByDate.getOrDefault(targetDate, List.of());
        if (raids.isEmpty()) {
            return null;
        }

        LocalTime templateTime = parseTemplateTime(template.getHeure());
        return raids.stream()
                .min(Comparator.comparingLong(raid -> distanceToTemplateTime(raid, templateTime)))
                .orElse(null);
    }

    private DayOfWeek normalizeDayOfWeek(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        Map<String, DayOfWeek> aliases = Map.ofEntries(
                Map.entry("MONDAY", DayOfWeek.MONDAY),
                Map.entry("LUNDI", DayOfWeek.MONDAY),
                Map.entry("TUESDAY", DayOfWeek.TUESDAY),
                Map.entry("MARDI", DayOfWeek.TUESDAY),
                Map.entry("WEDNESDAY", DayOfWeek.WEDNESDAY),
                Map.entry("MERCREDI", DayOfWeek.WEDNESDAY),
                Map.entry("THURSDAY", DayOfWeek.THURSDAY),
                Map.entry("JEUDI", DayOfWeek.THURSDAY),
                Map.entry("FRIDAY", DayOfWeek.FRIDAY),
                Map.entry("VENDREDI", DayOfWeek.FRIDAY),
                Map.entry("SATURDAY", DayOfWeek.SATURDAY),
                Map.entry("SAMEDI", DayOfWeek.SATURDAY),
                Map.entry("SUNDAY", DayOfWeek.SUNDAY),
                Map.entry("DIMANCHE", DayOfWeek.SUNDAY)
        );

        return aliases.get(normalized);
    }

    private int dayOffset(DayOfWeek dayOfWeek) {
        switch (dayOfWeek) {
            case WEDNESDAY:
                return 0;
            case THURSDAY:
                return 1;
            case FRIDAY:
                return 2;
            case SATURDAY:
                return 3;
            case SUNDAY:
                return 4;
            case MONDAY:
                return 5;
            case TUESDAY:
                return 6;
            default:
                return 0;
        }
    }

    private LocalTime parseTemplateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return LocalTime.parse(value.trim());
        } catch (Exception exception) {
            return null;
        }
    }

    private long distanceToTemplateTime(com.origin.entity.Raid raid, LocalTime templateTime) {
        if (raid == null || raid.getDate() == null || templateTime == null) {
            return 0L;
        }

        return Math.abs(raid.getDate().toLocalTime().toSecondOfDay() - templateTime.toSecondOfDay());
    }
}
