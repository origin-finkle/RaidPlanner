package com.origin.dto;

import com.origin.enumOrigin.StatutParticipation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RaidSignupDiagnosticDTO {

    private Long joueurId;
    private String pseudo;
    private String pseudoIhm;
    private String serverPseudo;
    private Long personnageId;
    private String personnageNom;
    private String classe;
    private String specialisation;
    private String role;
    private boolean main;
    private StatutParticipation statutParticipation;
}
