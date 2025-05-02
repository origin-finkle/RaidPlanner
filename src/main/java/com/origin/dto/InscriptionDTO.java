package com.origin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class InscriptionDTO {
    private String personnage;
    private String classe;
    private String role;
    private JoueurDTO joueur;
}