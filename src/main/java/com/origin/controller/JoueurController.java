package com.origin.controller;


import com.origin.dto.JoueurDTO;
import com.origin.service.JoueurService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/joueurs")
@RequiredArgsConstructor
public class JoueurController {

    private final JoueurService joueurService;

    @PutMapping("/{id}/pseudo")
    public ResponseEntity<Void> updatePseudoIhm(
            @PathVariable Long id,
            @RequestBody Map<String, String> requestBody) {

        String nouveauPseudoIhm = requestBody.get("pseudoIhm");

        joueurService.updatePseudoIhm(id, nouveauPseudoIhm);

        return ResponseEntity.noContent().build(); // HTTP 204
    }

    @GetMapping
    public List<JoueurDTO> findAll() {
        return joueurService.findAllJoueurs();
    }

    @GetMapping("{id}")
    public JoueurDTO findById(@PathVariable Long id) {
        return joueurService.findById(id);
    }






}
