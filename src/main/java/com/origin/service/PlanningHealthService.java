package com.origin.service;

import com.origin.dto.PlanningHealthIssueDTO;
import com.origin.dto.PlanningHealthSummaryDTO;
import com.origin.dto.RaidDiagnosticDTO;
import com.origin.entity.Raid;
import com.origin.service.discord.RaidQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PlanningHealthService {

    private final RaidQueryService raidQueryService;

    public PlanningHealthSummaryDTO getPlanningHealthSummary() {
        LocalDate start = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.WEDNESDAY));
        LocalDateTime startDate = start.atStartOfDay();
        LocalDateTime endExclusive = start.plusDays(14).atStartOfDay();

        List<Raid> raids = raidQueryService.getBestRaidsInRange(startDate, endExclusive);
        List<PlanningHealthIssueDTO> issues = new ArrayList<>();
        int raidsWithoutSignups = 0;
        int unpublishedRaids = 0;
        int outdatedRaids = 0;

        for (Raid raid : raids) {
            RaidDiagnosticDTO diagnostic = raidQueryService.getRaidDiagnostic(raid.getId());
            List<String> raidIssues = new ArrayList<>();

            int liveCount = diagnostic.getLiveSignups() != null ? diagnostic.getLiveSignups().size() : 0;
            int snapshotCount = diagnostic.getSnapshotSignups() != null ? diagnostic.getSnapshotSignups().size() : 0;
            boolean published = diagnostic.getPublishedMessageId() != null;

            if (liveCount == 0 && snapshotCount == 0) {
                raidIssues.add("Aucun inscrit detecte");
                raidsWithoutSignups++;
            }

            if (diagnostic.isSourceChanged()) {
                raidIssues.add("La meilleure source differe du message stocke");
                outdatedRaids++;
            }

            if ((diagnostic.getLiveOnlyPlayers() != null && !diagnostic.getLiveOnlyPlayers().isEmpty())
                    || (diagnostic.getSnapshotOnlyPlayers() != null && !diagnostic.getSnapshotOnlyPlayers().isEmpty())) {
                raidIssues.add("Le snapshot d'inscrits est en ecart avec le live");
                outdatedRaids++;
            }

            if (!published) {
                raidIssues.add("Compo non publiee");
                unpublishedRaids++;
            }

            if (!raidIssues.isEmpty()) {
                issues.add(PlanningHealthIssueDTO.builder()
                        .raidId(raid.getId())
                        .raidNom(raid.getNom())
                        .raidDate(raid.getDate())
                        .severity(raidIssues.size() >= 3 ? "high" : raidIssues.size() == 2 ? "medium" : "low")
                        .liveSignupCount(liveCount)
                        .snapshotSignupCount(snapshotCount)
                        .published(published)
                        .issues(raidIssues)
                        .build());
            }
        }

        issues.sort(Comparator
                .comparing((PlanningHealthIssueDTO issue) -> severityRank(issue.getSeverity()))
                .thenComparing(PlanningHealthIssueDTO::getRaidDate));

        return PlanningHealthSummaryDTO.builder()
                .totalRaids(raids.size())
                .raidsWithIssues(issues.size())
                .raidsWithoutSignups(raidsWithoutSignups)
                .unpublishedRaids(unpublishedRaids)
                .outdatedRaids(outdatedRaids)
                .issues(issues)
                .build();
    }

    private int severityRank(String severity) {
        if ("high".equalsIgnoreCase(severity)) {
            return 0;
        }
        if ("medium".equalsIgnoreCase(severity)) {
            return 1;
        }
        return 2;
    }
}
