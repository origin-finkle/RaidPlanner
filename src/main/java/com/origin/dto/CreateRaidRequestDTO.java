package com.origin.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreateRaidRequestDTO {
    private String nom;
    private LocalDateTime date;
    private String channelId;
}
