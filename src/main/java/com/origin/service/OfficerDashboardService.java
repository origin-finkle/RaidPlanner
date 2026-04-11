package com.origin.service;

import com.origin.dto.OfficerDashboardDTO;
import com.origin.dto.OfficerDashboardRaidDTO;
import com.origin.dto.PlanningHealthIssueDTO;
import com.origin.dto.PlanningHealthSummaryDTO;
import com.origin.dto.RaidConfirmationSummaryDTO;
import com.origin.entity.Raid;
import com.origin.enumOrigin.CompositionWorkflowStatus;
import com.origin.service.discord.RaidQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OfficerDashboardService {

    private final RaidQueryService raidQueryService;
    private final PlanningHealthService planningHealthService;
    private final RaidConfirmationService raidConfirmationService;

    public OfficerDashboardDTO getDashboard() {
        LocalDate resetStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.WEDNESDAY));
        LocalDateTime start = resetStart.atStartOfDay();
        LocalDateTime endExclusive = resetStart.plusDays(14).atStartOfDay();

        List<Raid> raids = raidQueryService.getBestRaidsInRange(start, endExclusive);
        PlanningHealthSummaryDTO planningHealth = planningHealthService.getPlanningHealthSummary();
        Map<Long, PlanningHealthIssueDTO> healthByRaid = planningHealth.getIssues().stream()
                .collect(Collectors.toMap(PlanningHealthIssueDTO::getRaidId, issue -> issue, (left, right) -> left, LinkedHashMap::new));

        int readyToPublishCount = 0;
        int pendingConfirmationRaidCount = 0;
        int raidsWithDeclines = 0;

        List<OfficerDashboardRaidDTO> raidRows = new ArrayList<>();
        for (Raid raid : raids) {
            RaidConfirmationSummaryDTO confirmations = raidConfirmationService.getSummary(raid.getId());
            PlanningHealthIssueDTO healthIssue = healthByRaid.get(raid.getId());
            List<String> actions = new ArrayList<>();

            if (raid.getCompositionStatus() == CompositionWorkflowStatus.READY && raid.getPublishedMessageId() == null) {
                readyToPublishCount++;
                actions.add("Publication a faire");
            }
            if (confirmations.getPendingCount() > 0) {
                pendingConfirmationRaidCount++;
                actions.add(confirmations.getPendingCount() + " reponse(s) en attente");
            }
            if (confirmations.getCancelledCount() > 0) {
                raidsWithDeclines++;
                actions.add(confirmations.getCancelledCount() + " refus a retraiter");
            }
            if (healthIssue != null && healthIssue.getIssues() != null) {
                actions.addAll(healthIssue.getIssues());
            }

            raidRows.add(OfficerDashboardRaidDTO.builder()
                    .raidId(raid.getId())
                    .raidNom(raid.getNom())
                    .raidDate(raid.getDate())
                    .compositionStatus(raid.getCompositionStatus() != null ? raid.getCompositionStatus().name() : "DRAFT")
                    .compositionLocked(raid.isCompositionLocked())
                    .published(raid.getPublishedMessageId() != null)
                    .totalAssignedPlayers(confirmations.getTotalPlayers())
                    .confirmedCount(confirmations.getConfirmedCount())
                    .cancelledCount(confirmations.getCancelledCount())
                    .pendingCount(confirmations.getPendingCount())
                    .liveSignupCount(confirmations.getTotalPlayers() + Math.max(0, confirmations.getCancelledCount()))
                    .healthIssueCount(healthIssue != null && healthIssue.getIssues() != null ? healthIssue.getIssues().size() : 0)
                    .actions(actions)
                    .build());
        }

        raidRows.sort(Comparator
                .comparingInt((OfficerDashboardRaidDTO raid) -> raid.getActions() != null ? raid.getActions().size() : 0).reversed()
                .thenComparing(OfficerDashboardRaidDTO::getRaidDate));

        return OfficerDashboardDTO.builder()
                .trackedRaids(raids.size())
                .readyToPublishCount(readyToPublishCount)
                .pendingConfirmationRaidCount(pendingConfirmationRaidCount)
                .raidsWithDeclines(raidsWithDeclines)
                .raidsWithHealthIssues(planningHealth.getRaidsWithIssues())
                .raids(raidRows)
                .build();
    }
}
