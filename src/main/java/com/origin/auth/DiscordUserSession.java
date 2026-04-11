package com.origin.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiscordUserSession implements Serializable {
    private String discordId;
    private String username;
    private String displayName;
    private boolean officer;
}
