package com.origin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthStatusDTO {
    private boolean configured;
    private boolean authenticated;
    private boolean officer;
    private String discordId;
    private String username;
    private String displayName;
}
