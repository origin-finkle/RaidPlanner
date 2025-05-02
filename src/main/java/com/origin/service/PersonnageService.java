package com.origin.service;

import com.origin.dto.PersonnageDTO;
import com.origin.entity.Joueur;
import com.origin.entity.Personnage;
import com.origin.repository.JoueurRepository;
import com.origin.repository.PersonnageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PersonnageService {

    private final PersonnageRepository personnageRepository;
    private final JoueurRepository joueurRepository;


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

    public void addPersonnage(Long id, PersonnageDTO personnageDTO) {
        Joueur joueur = joueurRepository.findById(id).orElse(null);
        Personnage personnage = new Personnage();
        personnage.setNom(personnageDTO.getNom());
        personnage.setClasse(personnageDTO.getClasse());
        personnage.setSpecialisation(personnageDTO.getSpecialisation());
        personnage.setRole(personnageDTO.getRole());
        personnage.setMain(personnageDTO.isMain());
        personnage.setJoueur(joueur);

        personnageRepository.save(personnage);
    }

    public void deletePersonnage(Long id) {
        personnageRepository.deleteById(id);
    }


}

