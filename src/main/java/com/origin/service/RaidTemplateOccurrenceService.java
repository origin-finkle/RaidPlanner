package com.origin.service;

import com.origin.entity.Raid;
import com.origin.entity.RaidTemplate;
import com.origin.repository.RaidRepository;
import com.origin.repository.RaidTemplateRepository;
import com.origin.service.discord.DiscordCustomSignupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
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
public class RaidTemplateOccurrenceService {

    private final RaidTemplateRepository raidTemplateRepository;
    private final RaidRepository raidRepository;
    private final DiscordCustomSignupService discordCustomSignupService;

    @Transactional
    public Raid ensureRaidOccurrence(Long templateId, int weekOffset, ZoneId zoneId) {
        RaidTemplate template = loadTemplate(templateId);
        LocalDate weekStart = getDefaultPublicationWeekStart(zoneId).plusWeeks(weekOffset);
        return ensureRaidOccurrence(template, weekStart);
    }

    @Transactional
    public Raid ensureRaidOccurrence(RaidTemplate template, LocalDate weekStart) {
        LocalDateTime targetDateTime = resolveTargetDateTime(template, weekStart);
        LocalDateTime dayStart = targetDateTime.toLocalDate().atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);

        Raid raid = null;
        if (template.getId() != null) {
            raid = raidRepository.findFirstByTemplateIdAndDateGreaterThanEqualAndDateLessThanOrderByDateAsc(
                    template.getId(),
                    dayStart,
                    dayEnd
            ).orElse(null);
        }

        if (raid == null && template.getChannelId() != null && !template.getChannelId().isBlank()) {
            raid = raidRepository.findByChannelIdAndDateGreaterThanEqualAndDateLessThanOrderByDateAsc(
                            template.getChannelId(),
                            dayStart,
                            dayEnd
                    ).stream()
                    .min(Comparator.comparingLong(existing -> distanceToTargetTime(existing, targetDateTime.toLocalTime())))
                    .orElse(null);
        }

        if (raid == null) {
            raid = raidRepository.findByNomAndDate(template.getNom(), targetDateTime).orElse(null);
        }

        if (raid == null) {
            raid = Raid.builder()
                    .nom(template.getNom())
                    .date(targetDateTime)
                    .channelId(template.getChannelId())
                    .template(template)
                    .build();
            raid = raidRepository.save(raid);
            log.info("Occurrence de raid creee depuis le template {} pour {}", template.getNom(), targetDateTime);
            return raid;
        }

        boolean changed = false;
        if (!targetDateTime.equals(raid.getDate())) {
            raid.setDate(targetDateTime);
            changed = true;
        }
        if (!template.getNom().equals(raid.getNom())) {
            raid.setNom(template.getNom());
            changed = true;
        }
        if (!template.getChannelId().equals(raid.getChannelId())) {
            raid.setChannelId(template.getChannelId());
            changed = true;
        }
        if (raid.getTemplate() == null || !template.getId().equals(raid.getTemplate().getId())) {
            raid.setTemplate(template);
            changed = true;
        }

        if (changed) {
            raid = raidRepository.save(raid);
            log.info("Occurrence de raid mise a jour depuis le template {} pour {}", template.getNom(), targetDateTime);
        }

        return raid;
    }

    @Transactional
    public String publishTemplateToTestChannel(Long templateId, int weekOffset, ZoneId zoneId) {
        Raid raid = ensureRaidOccurrence(templateId, weekOffset, zoneId);
        return discordCustomSignupService.publishTestSignupMessage(raid.getId());
    }

    @Transactional
    public String publishTemplateToConfiguredChannel(Long templateId, int weekOffset, ZoneId zoneId) {
        Raid raid = ensureRaidOccurrence(templateId, weekOffset, zoneId);
        return discordCustomSignupService.publishSignupMessageToChannel(raid.getId(), raid.getChannelId());
    }

    @Transactional
    public int publishWeek(LocalDate weekStart) {
        List<RaidTemplate> templates = raidTemplateRepository.findAll().stream()
                .filter(template -> template.getChannelId() != null && !template.getChannelId().isBlank())
                .collect(Collectors.toList());

        int publishedCount = 0;
        for (RaidTemplate template : templates) {
            try {
                Raid raid = ensureRaidOccurrence(template, weekStart);
                discordCustomSignupService.publishSignupMessageToChannel(raid.getId(), raid.getChannelId());
                publishedCount++;
            } catch (Exception exception) {
                log.error(
                        "Echec de creation/publication auto pour le template {} sur le salon {}",
                        template.getNom(),
                        template.getChannelId(),
                        exception
                );
            }
        }

        return publishedCount;
    }

    public LocalDate getDefaultPublicationWeekStart(ZoneId zoneId) {
        LocalDate today = ZonedDateTime.now(zoneId).toLocalDate();
        return today.with(TemporalAdjusters.previousOrSame(DayOfWeek.WEDNESDAY)).plusWeeks(1);
    }

    private RaidTemplate loadTemplate(Long templateId) {
        return raidTemplateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template introuvable : " + templateId));
    }

    private LocalDateTime resolveTargetDateTime(RaidTemplate template, LocalDate weekStart) {
        DayOfWeek dayOfWeek = normalizeDayOfWeek(template.getJourSemaine());
        if (dayOfWeek == null) {
            throw new IllegalArgumentException("Jour invalide pour le template : " + template.getJourSemaine());
        }

        LocalDate targetDate = weekStart.plusDays(dayOffset(dayOfWeek));
        LocalTime targetTime = parseTemplateTime(template.getHeure());
        return LocalDateTime.of(targetDate, targetTime);
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
            return LocalTime.of(20, 45);
        }

        try {
            return LocalTime.parse(value.trim());
        } catch (Exception exception) {
            return LocalTime.of(20, 45);
        }
    }

    private long distanceToTargetTime(Raid raid, LocalTime targetTime) {
        if (raid == null || raid.getDate() == null || targetTime == null) {
            return Long.MAX_VALUE;
        }

        return Math.abs(raid.getDate().toLocalTime().toSecondOfDay() - targetTime.toSecondOfDay());
    }
}
