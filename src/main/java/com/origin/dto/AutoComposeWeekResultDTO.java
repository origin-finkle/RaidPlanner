package com.origin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AutoComposeWeekResultDTO {
    private List<Long> selectedRaidIds;
    private List<Long> updatedRaidIds;
    private List<String> warnings;
}
