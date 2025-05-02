package com.origin.controller;

import com.origin.dto.ExportCompoRequestDto;
import com.origin.dto.RaidCompositionDTO;
import com.origin.dto.RaidDayResponse;
import com.origin.service.RaidService;
import com.origin.service.discord.RaidQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/raids")
@RequiredArgsConstructor
public class RaidController {

    private final RaidQueryService raidQueryService;
    private final RaidService raidService;


    @GetMapping
    public List<RaidDayResponse> getAllRaids() {
        return raidQueryService.getRaidsGroupedByDay();
    }

    @PostMapping("/composition")
    public ResponseEntity<Void> saveComposition(@RequestBody RaidCompositionDTO dto) {
        raidService.saveComposition(dto);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/export")
    public ResponseEntity<Void> exportRaidHelperFormat(@PathVariable Long id, @RequestBody ExportCompoRequestDto request) {
        raidService.exportFormattedComposition(id, request);
        return ResponseEntity.ok().build();
    }
}

