package com.origin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AutoComposePreviewResultDTO {
    private List<Long> selectedRaidIds;
    private List<String> warnings;
    private List<AutoComposePreviewRaidDTO> previewRaids;
}
