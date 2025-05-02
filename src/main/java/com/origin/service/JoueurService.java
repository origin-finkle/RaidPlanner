package com.origin.service;

import com.origin.dto.JoueurDTO;
import com.origin.dto.PersonnageDTO;
import com.origin.entity.Joueur;
import com.origin.entity.Personnage;
import com.origin.repository.JoueurRepository;
import com.origin.repository.PersonnageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class JoueurService {

    private final JoueurRepository joueurRepository;
    private final PersonnageService personnageService;

    public Joueur findByServerPseudo(String pseudo) {
        return joueurRepository.findByServerPseudoIgnoreCase(pseudo);
    }
    public Joueur createWithMainCharacter(Joueur joueur, Personnage main) {

        joueur.setMainCharacter(main);
        return joueurRepository.save(joueur);
    }

    public Optional<Joueur> updatePseudoIhm(Long id, String pseudoIhm) {
        joueurRepository.updatePseudoIhmById(pseudoIhm, id);
        return joueurRepository.findById(id);
    }

    public List<JoueurDTO> findAllJoueurs() {
        return joueurRepository.findAll()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }


    public JoueurDTO toDTO(Joueur joueur) {
        if (joueur == null) {
            return null;
        }

        PersonnageDTO mainDto = personnageService.toDTO(joueur.getMainCharacter());

        Set<Personnage> all = joueur.getPersonnages();
        Personnage main = joueur.getMainCharacter();

        List<PersonnageDTO> rerolls = all.stream()
                .filter(p -> main == null || !p.getId().equals(main.getId()))
                .map(personnageService::toDTO)
                .collect(Collectors.toList());

        return new JoueurDTO(
                joueur.getId(),
                joueur.getPseudo(),
                joueur.getPseudoIhm(),
                joueur.getServerPseudo(),
                mainDto,
                rerolls,
                Boolean.TRUE.equals(joueur.getIsRaider()),
                null
        );
    }

    public JoueurDTO findById(Long id) {
        return this.toDTO(joueurRepository.findById(id).orElse(null));
    }
}

