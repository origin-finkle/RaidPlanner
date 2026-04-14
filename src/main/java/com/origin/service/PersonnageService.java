package com.origin.service;

import com.origin.dto.PersonnageDTO;
import com.origin.entity.Joueur;
import com.origin.entity.Personnage;
import com.origin.repository.CompositionRepository;
import com.origin.repository.InscriptionRepository;
import com.origin.repository.JoueurRepository;
import com.origin.repository.PersonnageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.persistence.EntityNotFoundException;
import javax.transaction.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PersonnageService {

    private final PersonnageRepository personnageRepository;
    private final JoueurRepository joueurRepository;
    private final CompositionRepository compositionRepository;
    private final InscriptionRepository inscriptionRepository;


    public List<Personnage> gerRerolls (Long idJoueur) {
        return personnageRepository.findByJoueurIdAndNotMain(idJoueur);

    }



    public PersonnageDTO toDTO(Personnage personnage) {
        if (personnage == null) {
            return null;
        }

        String pseudo = personnage.getJoueur() != null ? personnage.getJoueur().getPseudo() : null;

        return new PersonnageDTO(
                personnage.getId(),
                personnage.getNom(),
                personnage.getClasse(),
                personnage.getRole(),
                pseudo,
                personnage.getSpecialisation(),
                personnage.isMain()
        );

    }

    public Personnage save(Personnage p) {
        return personnageRepository.save(p);
    }

    public void updatePersonnage(Long id, PersonnageDTO personnageDTO) {
        Personnage personnage = personnageRepository.findById(id)
                .orElseGet(() -> {
                    Personnage nouveau = new Personnage();
                    nouveau.setId(id); // ⚠️ si l’ID est généré automatiquement, ne pas faire ça !
                    return nouveau;
                });

        personnage.setNom(personnageDTO.getNom());
        personnage.setClasse(personnageDTO.getClasse());
        personnage.setSpecialisation(personnageDTO.getSpecialisation());
        personnage.setRole(personnageDTO.getRole());
        personnage.setMain(personnageDTO.isMain());



        personnageRepository.save(personnage);
    }

    @Transactional
    public PersonnageDTO addPersonnage(Long id, PersonnageDTO personnageDTO) {
        Joueur joueur = joueurRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Joueur introuvable"));

        if (isBlank(personnageDTO.getNom())
                || isBlank(personnageDTO.getClasse())
                || isBlank(personnageDTO.getSpecialisation())
                || isBlank(personnageDTO.getRole())) {
            throw new IllegalArgumentException("Tous les champs du personnage sont obligatoires.");
        }

        boolean shouldBecomeMain = personnageDTO.isMain() || joueur.getMainCharacter() == null;
        if (shouldBecomeMain) {
            personnageRepository.findByJoueurId(id).forEach(existing -> existing.setMain(false));
        }

        Personnage personnage = new Personnage();
        personnage.setNom(personnageDTO.getNom().trim());
        personnage.setClasse(personnageDTO.getClasse().trim());
        personnage.setSpecialisation(personnageDTO.getSpecialisation().trim());
        personnage.setRole(personnageDTO.getRole().trim());
        personnage.setMain(shouldBecomeMain);
        personnage.setJoueur(joueur);

        Personnage saved = personnageRepository.save(personnage);

        if (shouldBecomeMain) {
            joueur.setMainCharacter(saved);
            joueurRepository.save(joueur);
        }

        return toDTO(saved);
    }

    @Transactional
    public void deletePersonnage(Long id) {
        if (!personnageRepository.existsById(id)) {
            return;
        }

        personnageRepository.deleteGroup1LinksByPersonnageId(id);
        personnageRepository.deleteGroup2LinksByPersonnageId(id);
        joueurRepository.clearMainCharacterByPersonnageId(id);
        compositionRepository.deleteByPersonnageId(id);
        inscriptionRepository.deleteByPersonnageId(id);
        personnageRepository.deleteById(id);
    }

    @Transactional
    public void mergePersonnages(Long joueurId, Long sourcePersonnageId, Long targetPersonnageId) {
        if (sourcePersonnageId == null || targetPersonnageId == null) {
            throw new IllegalArgumentException("Les personnages source et cible sont obligatoires.");
        }

        if (sourcePersonnageId.equals(targetPersonnageId)) {
            throw new IllegalArgumentException("La source et la cible doivent etre differentes.");
        }

        Personnage source = personnageRepository.findById(sourcePersonnageId)
                .orElseThrow(() -> new EntityNotFoundException("Personnage source introuvable"));
        Personnage target = personnageRepository.findById(targetPersonnageId)
                .orElseThrow(() -> new EntityNotFoundException("Personnage cible introuvable"));

        if (source.getJoueur() == null || target.getJoueur() == null
                || !source.getJoueur().getId().equals(joueurId)
                || !target.getJoueur().getId().equals(joueurId)) {
            throw new IllegalArgumentException("La fusion doit concerner deux personnages du meme joueur.");
        }

        personnageRepository.replaceGroup1Links(sourcePersonnageId, targetPersonnageId);
        personnageRepository.replaceGroup2Links(sourcePersonnageId, targetPersonnageId);
        joueurRepository.replaceMainCharacter(sourcePersonnageId, targetPersonnageId);
        compositionRepository.replacePersonnageReferences(sourcePersonnageId, targetPersonnageId);
        inscriptionRepository.replacePersonnageReferences(sourcePersonnageId, targetPersonnageId);

        personnageRepository.deleteGroup1LinksByPersonnageId(sourcePersonnageId);
        personnageRepository.deleteGroup2LinksByPersonnageId(sourcePersonnageId);
        compositionRepository.deleteByPersonnageId(sourcePersonnageId);
        inscriptionRepository.deleteByPersonnageId(sourcePersonnageId);

        if (source.isMain()) {
            target.setMain(true);
        }

        if (source.getJoueur().getMainCharacter() != null
                && sourcePersonnageId.equals(source.getJoueur().getMainCharacter().getId())) {
            source.getJoueur().setMainCharacter(target);
        }

        joueurRepository.save(source.getJoueur());
        personnageRepository.save(target);
        personnageRepository.deleteById(sourcePersonnageId);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }


}

