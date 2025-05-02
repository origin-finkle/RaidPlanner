package com.origin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class RaidDayResponse {
    private String date; // "2025-03-30"
    private List<RaidDTO> raids;
}