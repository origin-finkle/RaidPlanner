package com.origin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class RaidCompositionDTO {
    private Long raidId;
    private List<PersonnageCompositionDTO> group1;
    private List<PersonnageCompositionDTO> group2;
}
