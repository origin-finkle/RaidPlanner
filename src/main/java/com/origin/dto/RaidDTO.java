package com.origin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Data
@AllArgsConstructor
public class RaidDTO {
    private Long id;
    private String nom;
    private LocalDateTime heure;
    private List<JoueurDTO> joueurDTOList;
    private List<PersonnageDTO> group1;
    private List<PersonnageDTO> group2;
}