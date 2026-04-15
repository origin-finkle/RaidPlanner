package com.origin.service;

import com.origin.dto.RaidTemplateDTO;
import com.origin.entity.Raid;
import com.origin.entity.RaidTemplate;
import com.origin.repository.RaidRepository;
import com.origin.repository.RaidTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RaidTemplateService {

    private final RaidRepository raidRepository;
    private final RaidTemplateRepository raidTemplateRepository;

    @Transactional(readOnly = true)
    public List<RaidTemplateDTO> getAllTemplates() {
        return raidTemplateRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public RaidTemplateDTO saveTemplate(RaidTemplateDTO dto) {
        RaidTemplate template = dto.getId() != null
                ? raidTemplateRepository.findById(dto.getId()).orElse(new RaidTemplate())
                : new RaidTemplate();

        template.setNom(dto.getNom());
        template.setJourSemaine(dto.getJourSemaine());
        template.setHeure(dto.getHeure());
        template.setChannelId(dto.getChannelId());
        template.setMessageId(dto.getMessageId());
        template.setRaidSize(dto.getRaidSize());
        template.setTargetTanks(dto.getTargetTanks());
        template.setTargetHeals(dto.getTargetHeals());
        template.setPreferMains(dto.getPreferMains());
        template.setPrioritizeBuffCoverage(dto.getPrioritizeBuffCoverage());
        template.setHuntersFillMissingBuffs(dto.getHuntersFillMissingBuffs());

        return toDto(raidTemplateRepository.save(template));
    }

    @Transactional
    public void deleteTemplate(Long templateId) {
        List<Raid> linkedRaids = raidRepository.findByTemplateIdOrderByDateAsc(templateId);
        if (!linkedRaids.isEmpty()) {
            linkedRaids.forEach(raid -> raid.setTemplate(null));
            raidRepository.saveAll(linkedRaids);
        }
        raidTemplateRepository.deleteById(templateId);
    }

    private RaidTemplateDTO toDto(RaidTemplate template) {
        return RaidTemplateDTO.builder()
                .id(template.getId())
                .nom(template.getNom())
                .jourSemaine(template.getJourSemaine())
                .heure(template.getHeure())
                .channelId(template.getChannelId())
                .messageId(template.getMessageId())
                .raidSize(template.getRaidSize())
                .targetTanks(template.getTargetTanks())
                .targetHeals(template.getTargetHeals())
                .preferMains(template.getPreferMains())
                .prioritizeBuffCoverage(template.getPrioritizeBuffCoverage())
                .huntersFillMissingBuffs(template.getHuntersFillMissingBuffs())
                .build();
    }
}
