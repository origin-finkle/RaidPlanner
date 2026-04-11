package com.origin.dto;

import com.origin.enumOrigin.CompositionWorkflowStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRaidCompositionStateRequestDTO {
    private CompositionWorkflowStatus status;
    private Boolean locked;
}
