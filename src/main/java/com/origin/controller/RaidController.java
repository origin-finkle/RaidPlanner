package com.origin.controller;

import com.origin.dto.AutoComposeWeekRequestDTO;
import com.origin.dto.AutoComposePreviewResultDTO;
import com.origin.dto.BenchRecommendationDTO;
import com.origin.dto.CreateRaidRequestDTO;
import com.origin.dto.DiscordChannelOptionDTO;
import com.origin.dto.ExportCompoRequestDto;
import com.origin.dto.MissingRaidPingDTO;
import com.origin.dto.ManualRaidSignupRequestDTO;
import com.origin.dto.OfficerDashboardDTO;
import com.origin.dto.PlayerEquitySummaryDTO;
import com.origin.dto.PlanningHealthSummaryDTO;
import com.origin.dto.RaidDiagnosticDTO;
import com.origin.dto.RaidConfirmationSummaryDTO;
import com.origin.dto.RaidCompositionStateDTO;
import com.origin.dto.RaidCompositionDTO;
import com.origin.dto.RaidDayResponse;
import com.origin.dto.RaidDTO;
import com.origin.dto.RaidPublicationComparisonDTO;
import com.origin.dto.RaidSchedulerStatusDTO;
import com.origin.dto.AutoComposeWeekResultDTO;
import com.origin.dto.RaidTemplateDTO;
import com.origin.dto.UpdateRaidCompositionStateRequestDTO;
import com.origin.dto.UpdateRaidRequestDTO;
import com.origin.service.AutoComposeService;
import com.origin.service.AutoComposeSettingsService;
import com.origin.service.BenchManagerService;
import com.origin.service.OfficerDashboardService;
import com.origin.service.PlayerEquityService;
import com.origin.service.PlanningHealthService;
import com.origin.service.RaidConfirmationService;
import com.origin.service.RaidService;
import com.origin.service.RaidTemplateOccurrenceService;
import com.origin.service.RaidTemplateService;
import com.origin.service.discord.RaidDiscordScannerService;
import com.origin.service.discord.MissingRaidPingService;
import com.origin.service.discord.DiscordCustomSignupService;
import com.origin.service.discord.DiscordChannelDirectoryService;
import com.origin.service.discord.DiscordOfficerAuditService;
import com.origin.service.discord.RaidImportSchedulerSettingsService;
import com.origin.service.discord.RaidQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import com.origin.dto.RaidPublicationHistoryDTO;

@RestController
@RequestMapping("/api/raids")
@RequiredArgsConstructor
@Slf4j
public class RaidController {

    private final RaidQueryService raidQueryService;
    private final RaidService raidService;
    private final AutoComposeService autoComposeService;
    private final AutoComposeSettingsService autoComposeSettingsService;
    private final RaidConfirmationService raidConfirmationService;
    private final BenchManagerService benchManagerService;
    private final PlayerEquityService playerEquityService;
    private final PlanningHealthService planningHealthService;
    private final OfficerDashboardService officerDashboardService;
    private final MissingRaidPingService missingRaidPingService;
    private final RaidDiscordScannerService raidDiscordScannerService;
    private final RaidTemplateService raidTemplateService;
    private final RaidTemplateOccurrenceService raidTemplateOccurrenceService;
    private final DiscordCustomSignupService discordCustomSignupService;
    private final DiscordChannelDirectoryService discordChannelDirectoryService;
    private final DiscordOfficerAuditService discordOfficerAuditService;
    private final RaidImportSchedulerSettingsService raidImportSchedulerSettingsService;


    @GetMapping
    public List<RaidDayResponse> getAllRaids() {
        return raidQueryService.getRaidsGroupedByDay();
    }

    @PostMapping
    public ResponseEntity<RaidDTO> createManualRaid(@RequestBody CreateRaidRequestDTO request) {
        RaidDTO createdRaid = raidService.createManualRaid(request);

        try {
            discordCustomSignupService.publishSignupMessageToChannel(createdRaid.getId(), createdRaid.getChannelId());
        } catch (Exception exception) {
            log.warn("Raid {} cree mais publication de l'inscription impossible: {}",
                    createdRaid.getId(),
                    exception.getMessage());
        }

        return ResponseEntity.ok(createdRaid);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRaid(@PathVariable Long id) {
        raidService.deleteRaid(id);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}")
    public ResponseEntity<RaidDTO> updateRaid(@PathVariable Long id, @RequestBody UpdateRaidRequestDTO request) {
        return ResponseEntity.ok(raidService.updateRaid(id, request));
    }

    @GetMapping("/{id}/diagnostic")
    public ResponseEntity<RaidDiagnosticDTO> getRaidDiagnostic(@PathVariable Long id) {
        return ResponseEntity.ok(raidQueryService.getRaidDiagnostic(id));
    }

    @GetMapping("/debug/channel/{channelId}")
    public List<Map<String, Object>> debugChannel(@PathVariable String channelId,
                                                  @RequestParam(defaultValue = "15") int limit) {
        return raidQueryService.debugChannelMessages(channelId, limit);
    }

    @PostMapping("/composition")
    public ResponseEntity<Void> saveComposition(@RequestBody RaidCompositionDTO dto) {
        raidService.saveComposition(dto);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/auto-compose-settings")
    public ResponseEntity<AutoComposeWeekRequestDTO> getAutoComposeSettings() {
        return ResponseEntity.ok(autoComposeSettingsService.getSettings());
    }

    @PutMapping("/auto-compose-settings")
    public ResponseEntity<AutoComposeWeekRequestDTO> updateAutoComposeSettings(@RequestBody AutoComposeWeekRequestDTO request) {
        return ResponseEntity.ok(autoComposeSettingsService.saveSettings(request));
    }

    @GetMapping("/{id}/composition-state")
    public ResponseEntity<RaidCompositionStateDTO> getCompositionState(@PathVariable Long id) {
        return ResponseEntity.ok(raidService.getCompositionState(id));
    }

    @PatchMapping("/{id}/composition-state")
    public ResponseEntity<RaidCompositionStateDTO> updateCompositionState(@PathVariable Long id,
                                                                          @RequestBody UpdateRaidCompositionStateRequestDTO request) {
        return ResponseEntity.ok(raidService.updateCompositionState(id, request));
    }

    @GetMapping("/{id}/publication-compare")
    public ResponseEntity<RaidPublicationComparisonDTO> getPublicationComparison(@PathVariable Long id) {
        return ResponseEntity.ok(raidService.getPublicationComparison(id));
    }

    @GetMapping("/{id}/confirmations")
    public ResponseEntity<RaidConfirmationSummaryDTO> getRaidConfirmations(@PathVariable Long id) {
        return ResponseEntity.ok(raidConfirmationService.getSummary(id));
    }

    @GetMapping("/{id}/bench-manager")
    public ResponseEntity<BenchRecommendationDTO> getBenchRecommendations(@PathVariable Long id) {
        return ResponseEntity.ok(benchManagerService.getRecommendations(id));
    }

    @PostMapping("/{id}/export")
    public ResponseEntity<Void> exportRaidHelperFormat(@PathVariable Long id, @RequestBody ExportCompoRequestDto request) {
        raidService.exportFormattedComposition(id, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/auto-compose-week")
    public ResponseEntity<AutoComposeWeekResultDTO> autoComposeWeek(@PathVariable Long id,
                                                                    @RequestBody(required = false) AutoComposeWeekRequestDTO request) {
        return ResponseEntity.ok(autoComposeService.autoComposeWeek(id, request));
    }

    @PostMapping("/{id}/auto-compose-week/preview")
    public ResponseEntity<AutoComposePreviewResultDTO> previewAutoComposeWeek(@PathVariable Long id,
                                                                              @RequestBody(required = false) AutoComposeWeekRequestDTO request) {
        return ResponseEntity.ok(autoComposeService.previewAutoComposeWeek(id, request));
    }

    @PostMapping("/{id}/rescan")
    public ResponseEntity<String> rescanRaid(@PathVariable Long id) {
        int importedCount = raidDiscordScannerService.rescanRaid(id);
        return ResponseEntity.ok("Rescan termine. " + importedCount + " raid(s) importe(s) ou mis a jour.");
    }

    @GetMapping("/{id}/missing-ping")
    public ResponseEntity<MissingRaidPingDTO> buildMissingPing(@PathVariable Long id) {
        return ResponseEntity.ok(missingRaidPingService.buildMissingPing(id));
    }

    @PostMapping("/{id}/missing-ping/test")
    public ResponseEntity<MissingRaidPingDTO> sendMissingPingToTestChannel(@PathVariable Long id) {
        return ResponseEntity.ok(missingRaidPingService.sendMissingPingToTestChannel(id));
    }

    @PostMapping("/{id}/missing-ping/publish")
    public ResponseEntity<MissingRaidPingDTO> sendMissingPingToRaidChannel(@PathVariable Long id) {
        return ResponseEntity.ok(missingRaidPingService.sendMissingPingToRaidChannel(id));
    }

    @PostMapping("/{id}/signup-flow/test")
    public ResponseEntity<String> publishCustomSignupFlowToTestChannel(@PathVariable Long id) {
        return ResponseEntity.ok(discordCustomSignupService.publishTestSignupMessage(id));
    }

    @PostMapping("/{id}/signup-flow/publish")
    public ResponseEntity<String> publishCustomSignupFlowToRaidChannel(@PathVariable Long id,
                                                                       @RequestParam(required = false) String channelId) {
        if (channelId != null && !channelId.isBlank()) {
            return ResponseEntity.ok(discordCustomSignupService.publishSignupMessageToChannel(id, channelId.trim()));
        }
        return ResponseEntity.ok(discordCustomSignupService.publishSignupMessageToRaidChannel(id));
    }

    @PostMapping("/{id}/manual-signups")
    public ResponseEntity<Void> addManualSignup(@PathVariable Long id, @RequestBody ManualRaidSignupRequestDTO request) {
        raidService.addManualSignup(id, request.getPersonnageId());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/manual-signups/{personnageId}")
    public ResponseEntity<Void> removeManualSignup(@PathVariable Long id, @PathVariable Long personnageId) {
        raidService.removeManualSignup(id, personnageId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/planning-health")
    public ResponseEntity<PlanningHealthSummaryDTO> getPlanningHealthSummary() {
        return ResponseEntity.ok(planningHealthService.getPlanningHealthSummary());
    }

    @GetMapping("/officer-dashboard")
    public ResponseEntity<OfficerDashboardDTO> getOfficerDashboard() {
        return ResponseEntity.ok(officerDashboardService.getDashboard());
    }

    @GetMapping("/publication-history")
    public ResponseEntity<List<RaidPublicationHistoryDTO>> getPublicationHistory() {
        return ResponseEntity.ok(raidService.getPublicationHistory());
    }

    @GetMapping("/scheduler-status")
    public ResponseEntity<RaidSchedulerStatusDTO> getRaidSchedulerStatus() {
        return ResponseEntity.ok(raidImportSchedulerSettingsService.getStatus());
    }

    @PutMapping("/scheduler-status")
    public ResponseEntity<RaidSchedulerStatusDTO> updateRaidSchedulerStatus(@RequestBody RaidSchedulerStatusDTO request) {
        return ResponseEntity.ok(raidImportSchedulerSettingsService.saveStatus(request));
    }

    @GetMapping("/player-equity")
    public ResponseEntity<PlayerEquitySummaryDTO> getPlayerEquitySummary() {
        return ResponseEntity.ok(playerEquityService.getSummary());
    }

    @GetMapping("/templates")
    public ResponseEntity<List<RaidTemplateDTO>> getRaidTemplates() {
        return ResponseEntity.ok(raidTemplateService.getAllTemplates());
    }

    @GetMapping("/discord/channels")
    public ResponseEntity<List<DiscordChannelOptionDTO>> getWritableDiscordChannels() {
        return ResponseEntity.ok(discordChannelDirectoryService.getWritableTextChannels());
    }

    @PostMapping("/officer-audit/test")
    public ResponseEntity<Map<String, Object>> sendOfficerAuditTest() {
        return ResponseEntity.ok(Map.of(
                "channelId", discordOfficerAuditService.getConfiguredChannelId(),
                "message", discordOfficerAuditService.sendTestMessage()
        ));
    }

    @PostMapping("/templates")
    public ResponseEntity<RaidTemplateDTO> saveRaidTemplate(@RequestBody RaidTemplateDTO dto) {
        return ResponseEntity.ok(raidTemplateService.saveTemplate(dto));
    }

    @PostMapping("/templates/{templateId}/signup-flow/test")
    public ResponseEntity<String> publishTemplateSignupFlowToTestChannel(@PathVariable Long templateId,
                                                                         @RequestParam(defaultValue = "0") int weekOffset) {
        return ResponseEntity.ok(
                raidTemplateOccurrenceService.publishTemplateToTestChannel(templateId, weekOffset, getSchedulerZoneId())
        );
    }

    @PostMapping("/templates/{templateId}/signup-flow/publish")
    public ResponseEntity<String> publishTemplateSignupFlowToRaidChannel(@PathVariable Long templateId,
                                                                         @RequestParam(defaultValue = "0") int weekOffset) {
        return ResponseEntity.ok(
                raidTemplateOccurrenceService.publishTemplateToConfiguredChannel(templateId, weekOffset, getSchedulerZoneId())
        );
    }

    @DeleteMapping("/templates/{templateId}")
    public ResponseEntity<Void> deleteRaidTemplate(@PathVariable Long templateId) {
        raidTemplateService.deleteTemplate(templateId);
        return ResponseEntity.ok().build();
    }

    private ZoneId getSchedulerZoneId() {
        return ZoneId.of(raidImportSchedulerSettingsService.getOrCreateSettings().getTimezone());
    }
}

