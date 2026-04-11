package com.origin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RaidConfirmationPlayerDTO {
    private Long joueurId;
    private Long personnageId;
    private String pseudoIhm;
    private String serverPseudo;
    private String personnageNom;
    private String classe;
    private String specialisation;
    private String role;
    private String confirmationStatus;
}
