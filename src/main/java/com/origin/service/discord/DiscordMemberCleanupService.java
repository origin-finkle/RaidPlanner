package com.origin.service.discord;

import com.origin.entity.Joueur;
import com.origin.repository.CompositionRepository;
import com.origin.repository.InscriptionRepository;
import com.origin.repository.JoueurRepository;
import com.origin.repository.PersonnageRepository;
import com.origin.repository.RaidInscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DiscordMemberCleanupService {

    private final JoueurRepository joueurRepository;
    private final PersonnageRepository personnageRepository;
    private final CompositionRepository compositionRepository;
    private final RaidInscriptionRepository raidInscriptionRepository;
    private final InscriptionRepository inscriptionRepository;

    @Transactional
    public void deleteUnauthorizedPlayers(List<Joueur> joueursASupprimer) {
        Set<Long> joueurIds = joueursASupprimer.stream()
                .map(Joueur::getId)
                .collect(HashSet::new, Set::add, Set::addAll);

        personnageRepository.deleteGroup1LinksByJoueurIds(joueurIds);
        personnageRepository.deleteGroup2LinksByJoueurIds(joueurIds);
        joueurRepository.clearMainCharacterByIds(joueurIds);
        compositionRepository.deleteByPersonnage_JoueurIn(joueursASupprimer);
        inscriptionRepository.deleteByPersonnage_JoueurIn(joueursASupprimer);
        raidInscriptionRepository.deleteByJoueurIn(joueursASupprimer);
        personnageRepository.deleteByJoueurIn(joueursASupprimer);
        joueurRepository.deleteAll(joueursASupprimer);
    }
}
