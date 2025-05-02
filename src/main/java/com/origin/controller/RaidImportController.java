package com.origin.controller;

import com.origin.service.discord.RaidDiscordScannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/raids/import")
@RequiredArgsConstructor
public class RaidImportController {

    private final RaidDiscordScannerService raidDiscordScannerService;

    @PostMapping
    public ResponseEntity<String> scanDiscordForRaids() {
        List<String> channelIds = List.of(
                "1355602641748496394"
        );

        raidDiscordScannerService.scanAndImportRaids(channelIds);

        return ResponseEntity.ok("Scan terminé.");
    }
}