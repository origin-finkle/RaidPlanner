package com.origin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlayerEquityRowDTO {
    private Long joueurId;
    private String pseudoIhm;
    private String serverPseudo;
    private int raidsAssigned;
    private int signupsCount;
    private int mainAssignments;
    private int rerollAssignments;
    private int benchCount;
    private int lateCount;
    private int tentativeCount;
    private int absenceCount;
}
