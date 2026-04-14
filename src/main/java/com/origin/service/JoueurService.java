package com.origin.service;

import com.origin.dto.JoueurDTO;
import com.origin.dto.PersonnageDTO;
import com.origin.entity.Joueur;
import com.origin.entity.Personnage;
import com.origin.repository.JoueurRepository;
import com.origin.repository.PersonnageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class JoueurService {

    private final JoueurRepository joueurRepository;
    private final PersonnageService personnageService;
    private final PersonnageRepository personnageRepository;

    public Joueur findByServerPseudo(String pseudo) {
        return joueurRepository.findByServerPseudoIgnoreCase(pseudo);
    }
    public Joueur createWithMainCharacter(Joueur joueur, Personnage main) {
        joueur.getPersonnages().forEach(personnage -> personnage.setMain(personnage.getId().equals(main.getId())));
        main.setMain(true);
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
                .map(joueur -> toDTO(joueur, true))
                .collect(Collectors.toList());
    }


    public JoueurDTO toDTO(Joueur joueur) {
        return toDTO(joueur, true);
    }

    public JoueurDTO toDTO(Joueur joueur, boolean filterPlaceholderDuplicates) {
        if (joueur == null) {
            return null;
        }

        Set<Personnage> all = joueur.getPersonnages();
        Personnage main = resolveDisplayedMainCharacter(joueur, all, filterPlaceholderDuplicates);
        PersonnageDTO mainDto = personnageService.toDTO(main);

        List<PersonnageDTO> rerolls = all.stream()
                .filter(p -> main == null || !p.getId().equals(main.getId()))
                .filter(p -> !filterPlaceholderDuplicates || !isPlaceholderDuplicate(joueur, p, all))
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
                null,
                null
        );
    }

    public JoueurDTO findById(Long id) {
        return this.toDTO(joueurRepository.findById(id).orElse(null), false);
    }

    private Personnage resolveDisplayedMainCharacter(Joueur joueur,
                                                     Set<Personnage> allCharacters,
                                                     boolean filterPlaceholderDuplicates) {
        Personnage currentMain = joueur.getMainCharacter();
        if (currentMain != null && (!filterPlaceholderDuplicates || !isPlaceholderDuplicate(joueur, currentMain, allCharacters))) {
            return currentMain;
        }

        Optional<Personnage> explicitMain = allCharacters.stream()
                .filter(Personnage::isMain)
                .filter(personnage -> !filterPlaceholderDuplicates || !isPlaceholderDuplicate(joueur, personnage, allCharacters))
                .findFirst();
        if (explicitMain.isPresent()) {
            return explicitMain.get();
        }

        Optional<Personnage> firstRealCharacter = allCharacters.stream()
                .filter(personnage -> !filterPlaceholderDuplicates || !isPlaceholderDuplicate(joueur, personnage, allCharacters))
                .findFirst();
        if (firstRealCharacter.isPresent()) {
            return firstRealCharacter.get();
        }

        return currentMain != null ? currentMain : allCharacters.stream().findFirst().orElse(null);
    }

    private boolean isPlaceholderDuplicate(Joueur joueur, Personnage candidate, Set<Personnage> allCharacters) {
        if (!looksLikeAccountName(joueur, candidate.getNom())) {
            return false;
        }

        return allCharacters.stream()
                .filter(other -> !other.getId().equals(candidate.getId()))
                .anyMatch(other ->
                        normalizeKey(other.getClasse()).equals(normalizeKey(candidate.getClasse()))
                                && normalizeKey(other.getSpecialisation()).equals(normalizeKey(candidate.getSpecialisation()))
                                && !looksLikeAccountName(joueur, other.getNom())
                );
    }

    private boolean looksLikeAccountName(Joueur joueur, String nom) {
        String normalizedName = normalizeKey(nom);
        return normalizedName.equals(normalizeKey(joueur.getServerPseudo()))
                || normalizedName.equals(normalizeKey(joueur.getPseudoIhm()))
                || normalizedName.equals(normalizeKey(joueur.getPseudo()));
    }

    private String normalizeKey(String value) {
        if (value == null) {
            return "";
        }

        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]", "");
    }
}

