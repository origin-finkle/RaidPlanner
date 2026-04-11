package com.origin.controller;

import com.origin.dto.MergePersonnageRequestDTO;
import com.origin.dto.PersonnageDTO;
import com.origin.service.PersonnageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/personnages")
@RequiredArgsConstructor
public class PersonnageController {

    private final PersonnageService personnageService;

    @PutMapping("/{id}")
    public ResponseEntity<Void> updatePersonnage(@PathVariable Long id, @RequestBody PersonnageDTO dto) {
        personnageService.updatePersonnage(id, dto);
        return ResponseEntity.ok().build();
    }


    @PostMapping("/{id}/personnages")
    public ResponseEntity<Void> addPersonnage(@PathVariable Long id, @RequestBody PersonnageDTO dto) {
        personnageService.addPersonnage(id, dto);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePersonnage(@PathVariable Long id) {
        personnageService.deletePersonnage(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/joueurs/{joueurId}/merge")
    public ResponseEntity<Void> mergePersonnages(@PathVariable Long joueurId, @RequestBody MergePersonnageRequestDTO dto) {
        personnageService.mergePersonnages(joueurId, dto.getSourcePersonnageId(), dto.getTargetPersonnageId());
        return ResponseEntity.ok().build();
    }

}
