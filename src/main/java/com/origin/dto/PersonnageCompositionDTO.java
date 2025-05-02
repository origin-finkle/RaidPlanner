package com.origin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PersonnageCompositionDTO {
    private String nom;
    private String classe;
    private String role;
    private String pseudo; // pseudo du joueur
}