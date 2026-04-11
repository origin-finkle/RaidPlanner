package com.origin.service.discord;

import com.origin.dto.RaidSchedulerStatusDTO;
import com.origin.entity.RaidImportSchedulerSettings;
import com.origin.repository.RaidImportSchedulerSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RaidImportSchedulerSettingsService {

    private static final long SETTINGS_ID = 1L;

    private final RaidImportSchedulerSettingsRepository repository;
    private final RaidDiscordScannerService raidDiscordScannerService;
    private final JDA jda;

    @Value("${raid.import.default-enabled:true}")
    private boolean defaultEnabled;

    @Value("${raid.import.default-day-of-week:THURSDAY}")
    private String defaultDayOfWeek;

    @Value("${raid.import.default-hour:21}")
    private int defaultHour;

    @Value("${raid.import.default-minute:0}")
    private int defaultMinute;

    @Value("${raid.import.default-timezone:Europe/Paris}")
    private String defaultTimezone;

    @Transactional(readOnly = true)
    public RaidSchedulerStatusDTO getStatus() {
        RaidImportSchedulerSettings settings = getOrCreateSettings();
        return toDto(settings);
    }

    @Transactional
    public RaidSchedulerStatusDTO saveStatus(RaidSchedulerStatusDTO request) {
        RaidImportSchedulerSettings settings = getOrCreateSettings();
        settings.setId(SETTINGS_ID);
        settings.setEnabled(defaultIfNull(request.isEnabled(), defaultEnabled));
        settings.setDayOfWeek(normalizeDayOfWeek(request.getDayOfWeek()));
        settings.setHour(clamp(request.getHour(), 0, 23, defaultHour));
        settings.setMinute(clamp(request.getMinute(), 0, 59, defaultMinute));
        settings.setTimezone(resolveTimezone(request.getTimezone()));

        repository.save(settings);
        return toDto(settings);
    }

    @Transactional(readOnly = true)
    public RaidImportSchedulerSettings getOrCreateSettings() {
        return repository.findById(SETTINGS_ID)
                .orElseGet(this::buildDefaultEntity);
    }

    @Transactional
    public void updateLastRun(LocalDateTime lastRunAt, Integer importedCount) {
        RaidImportSchedulerSettings settings = getOrCreateSettings();
        settings.setId(SETTINGS_ID);
        settings.setLastRunAt(lastRunAt);
        settings.setLastImportedCount(importedCount);
        repository.save(settings);
    }

    private RaidSchedulerStatusDTO toDto(RaidImportSchedulerSettings settings) {
        List<String> channelIds = raidDiscordScannerService.getConfiguredRaidHelperChannelIds();
        List<String> channelNames = channelIds.stream()
                .map(this::resolveChannelName)
                .collect(Collectors.toList());

        DayOfWeek dayOfWeek = DayOfWeek.valueOf(settings.getDayOfWeek());
        String publicationDay = dayOfWeek.getDisplayName(TextStyle.FULL, Locale.FRANCE);
        String publicationTime = String.format("%02d:%02d", settings.getHour(), settings.getMinute());

        return RaidSchedulerStatusDTO.builder()
                .enabled(Boolean.TRUE.equals(settings.getEnabled()))
                .dayOfWeek(settings.getDayOfWeek())
                .hour(settings.getHour())
                .minute(settings.getMinute())
                .cron("Dynamic")
                .timezone(settings.getTimezone())
                .nextRunAt(computeNextRun(settings))
                .lastRunAt(settings.getLastRunAt())
                .lastImportedCount(settings.getLastImportedCount())
                .publicationDay(publicationDay)
                .publicationTime(publicationTime)
                .channelIds(channelIds)
                .channelNames(channelNames)
                .build();
    }

    private LocalDateTime computeNextRun(RaidImportSchedulerSettings settings) {
        if (!Boolean.TRUE.equals(settings.getEnabled())) {
            return null;
        }

        ZoneId zoneId = ZoneId.of(settings.getTimezone());
        ZonedDateTime now = ZonedDateTime.now(zoneId).withSecond(0).withNano(0);
        DayOfWeek targetDay = DayOfWeek.valueOf(settings.getDayOfWeek());

        ZonedDateTime next = now
                .withHour(settings.getHour())
                .withMinute(settings.getMinute());

        while (next.getDayOfWeek() != targetDay || !next.isAfter(now)) {
            next = next.plusDays(1).withHour(settings.getHour()).withMinute(settings.getMinute());
        }

        return next.toLocalDateTime();
    }

    private RaidImportSchedulerSettings buildDefaultEntity() {
        return RaidImportSchedulerSettings.builder()
                .id(SETTINGS_ID)
                .enabled(defaultEnabled)
                .dayOfWeek(normalizeDayOfWeek(defaultDayOfWeek))
                .hour(defaultHour)
                .minute(defaultMinute)
                .timezone(resolveTimezone(defaultTimezone))
                .build();
    }

    private String resolveChannelName(String channelId) {
        TextChannel channel = jda.getTextChannelById(channelId);
        return channel != null ? "#" + channel.getName() : "#" + channelId;
    }

    private String normalizeDayOfWeek(String value) {
        try {
            return DayOfWeek.valueOf(value == null ? defaultDayOfWeek : value.trim().toUpperCase(Locale.ROOT)).name();
        } catch (Exception exception) {
            return DayOfWeek.valueOf(defaultDayOfWeek.toUpperCase(Locale.ROOT)).name();
        }
    }

    private String resolveTimezone(String value) {
        try {
            return ZoneId.of(value == null || value.isBlank() ? defaultTimezone : value.trim()).getId();
        } catch (Exception exception) {
            log.warn("Fuseau invalide pour le scheduler d'import: {}", value);
            return ZoneId.of(defaultTimezone).getId();
        }
    }

    private Integer clamp(Integer value, int min, int max, int fallback) {
        if (value == null) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    private boolean defaultIfNull(boolean value, boolean fallback) {
        return value;
    }

    private Boolean defaultIfNull(Boolean value, boolean fallback) {
        return value != null ? value : fallback;
    }
}
