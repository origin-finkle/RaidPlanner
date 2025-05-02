package com.origin.dto;

import com.origin.enumOrigin.StatutParticipation;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class JoueurDTO {
    private Long id;
    private String pseudo;
    private String pseudoIhm;
    private String serverPseudo;
    private PersonnageDTO personnageMain;
    private List<PersonnageDTO> rerolls;
    private boolean isRaider;
    private StatutParticipation statutParticipation; // "titulaire", "tentative", "bench", "late"
}