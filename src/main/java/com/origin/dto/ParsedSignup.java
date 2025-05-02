package com.origin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
@Data
@AllArgsConstructor
public class ParsedSignup {
    private String nom;
    private String classe;
    private String role;
}