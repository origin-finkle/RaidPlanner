package com.origin.controller;

import com.origin.service.discord.RaidDiscordScannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/raids/import")
@RequiredArgsConstructor
public class RaidImportController {

    private final RaidDiscordScannerService raidDiscordScannerService;

    @PostMapping
    public ResponseEntity<String> scanDiscordForRaids() {
        int importedCount = raidDiscordScannerService.scanConfiguredRaidHelperChannels();

        return ResponseEntity.ok("Scan termine. " + importedCount + " raid(s) importe(s).");
    }
}
