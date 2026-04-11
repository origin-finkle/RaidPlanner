package com.origin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class MissingRaidPingDTO {
    private String message;
    private int missingCount;
    private List<String> missingPlayers;
}
