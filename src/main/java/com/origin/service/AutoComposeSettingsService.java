package com.origin.service;

import com.origin.dto.AutoComposeWeekRequestDTO;
import com.origin.entity.AutoComposeSettings;
import com.origin.repository.AutoComposeSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AutoComposeSettingsService {

    private static final long SETTINGS_ID = 1L;

    private final AutoComposeSettingsRepository autoComposeSettingsRepository;

    @Transactional(readOnly = true)
    public AutoComposeWeekRequestDTO getSettings() {
        AutoComposeSettings settings = autoComposeSettingsRepository.findById(SETTINGS_ID)
                .orElseGet(this::buildDefaultEntity);

        return new AutoComposeWeekRequestDTO(
                settings.getMaxRaids(),
                settings.getTargetTanks(),
                settings.getTargetHeals(),
                settings.getPreferMains(),
                settings.getBalanceAcrossRaids(),
                settings.getPrioritizeBuffCoverage(),
                settings.getHuntersFillMissingBuffs()
        );
    }

    @Transactional
    public AutoComposeWeekRequestDTO saveSettings(AutoComposeWeekRequestDTO request) {
        AutoComposeSettings settings = autoComposeSettingsRepository.findById(SETTINGS_ID)
                .orElseGet(this::buildDefaultEntity);

        settings.setId(SETTINGS_ID);
        settings.setMaxRaids(defaultIfNull(request.getMaxRaids(), 2));
        settings.setTargetTanks(defaultIfNull(request.getTargetTanks(), 2));
        settings.setTargetHeals(defaultIfNull(request.getTargetHeals(), 2));
        settings.setPreferMains(defaultIfNull(request.getPreferMains(), true));
        settings.setBalanceAcrossRaids(defaultIfNull(request.getBalanceAcrossRaids(), true));
        settings.setPrioritizeBuffCoverage(defaultIfNull(request.getPrioritizeBuffCoverage(), true));
        settings.setHuntersFillMissingBuffs(defaultIfNull(request.getHuntersFillMissingBuffs(), true));

        autoComposeSettingsRepository.save(settings);
        return getSettings();
    }

    private AutoComposeSettings buildDefaultEntity() {
        return AutoComposeSettings.builder()
                .id(SETTINGS_ID)
                .maxRaids(2)
                .targetTanks(2)
                .targetHeals(2)
                .preferMains(true)
                .balanceAcrossRaids(true)
                .prioritizeBuffCoverage(true)
                .huntersFillMissingBuffs(true)
                .build();
    }

    private static <T> T defaultIfNull(T value, T fallback) {
        return value != null ? value : fallback;
    }
}
