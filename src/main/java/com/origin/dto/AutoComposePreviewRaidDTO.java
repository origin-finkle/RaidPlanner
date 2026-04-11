package com.origin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AutoComposePreviewRaidDTO {
    private Long raidId;
    private String raidNom;
    private LocalDateTime raidDate;
    private List<PersonnageDTO> group1;
    private List<PersonnageDTO> group2;
    private int assignedCount;
}
