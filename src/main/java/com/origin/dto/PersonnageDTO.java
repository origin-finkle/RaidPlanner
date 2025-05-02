package com.origin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data

public class PersonnageDTO {
    private Long id;
    private String nom;
    private String classe;
    private String role;
    private String pseudo;
    private String specialisation;
    private boolean isMain;

    public PersonnageDTO() {
    }

    public PersonnageDTO(Long id, String nom, String classe, String role, String pseudo, String specialisation, Boolean isMain) {
        this.id = id;
        this.nom = nom;
        this.classe = classe;
        this.role = role;
        this.pseudo = pseudo;
        this.specialisation = specialisation;
        this.isMain = isMain;
    }

    public PersonnageDTO(String nom, String classe, String role) {
        this.nom = nom;
        this.classe = classe;
        this.role = role;
    }

}