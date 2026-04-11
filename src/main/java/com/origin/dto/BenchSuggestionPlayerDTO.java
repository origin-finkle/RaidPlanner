package com.origin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BenchSuggestionPlayerDTO {
    private Long joueurId;
    private Long personnageId;
    private String pseudoIhm;
    private String serverPseudo;
    private String personnageNom;
    private String classe;
    private String specialisation;
    private String role;
    private boolean mainCharacter;
    private String signupStatus;
    private String confirmationStatus;
    private int fairnessScore;
    private List<String> reasons;
}
