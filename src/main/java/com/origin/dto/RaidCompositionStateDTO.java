package com.origin.dto;

import com.origin.enumOrigin.CompositionWorkflowStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RaidCompositionStateDTO {
    private Long raidId;
    private CompositionWorkflowStatus status;
    private boolean locked;
    private LocalDateTime lastPublishedAt;
    private boolean hasPublishedSnapshot;
}
