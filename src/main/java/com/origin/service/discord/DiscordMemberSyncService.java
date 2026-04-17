package com.origin.service.discord;

import com.origin.entity.Joueur;
import com.origin.repository.JoueurRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class DiscordMemberSyncService {

    private static final List<String> ALLOWED_ROLE_NAMES = List.of("Apply", "Officiers", "Veterans");

    private final JDA jda;
    private final JoueurRepository joueurRepository;
    private final DiscordMemberCleanupService discordMemberCleanupService;
    private final AtomicBoolean syncInProgress = new AtomicBoolean(false);

    @Value("${discord.guild.id}")
    private String guildId;

    @Scheduled(
            initialDelayString = "${discord.member-sync.initial-delay-ms:30000}",
            fixedDelayString = "${discord.member-sync.fixed-delay-ms:600000}"
    )
    public void syncMembersWithRole() {
        if (!syncInProgress.compareAndSet(false, true)) {
            log.info("Synchro membres Discord ignoree: une synchro est deja en cours.");
            return;
        }

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            log.warn("Guild non trouvee");
            syncInProgress.set(false);
            return;
        }

        guild.loadMembers().onSuccess(members -> {
            try {
                log.info("Scanning {} membres dans le serveur {}", members.size(), guild.getName());

                List<Member> allowedMembers = members.stream()
                        .filter(member -> hasRoleFromList(member, ALLOWED_ROLE_NAMES))
                        .collect(Collectors.toList());

                allowedMembers.forEach(this::enregistrerJoueur);
                supprimerJoueursNonAutorises(allowedMembers);
            } finally {
                syncInProgress.set(false);
            }
        }).onError(error -> {
            log.error("Erreur pendant la synchro des membres Discord", error);
            syncInProgress.set(false);
        });
    }

    private void enregistrerJoueur(Member member) {
        String discordId = member.getId();
        String currentPseudo = member.getUser().getName();
        String pseudoNormalized = cleanServerPseudo(member.getEffectiveName());

        Optional<Joueur> existing = joueurRepository.findByDiscordId(discordId);
        if (existing.isPresent()) {
            Joueur joueur = existing.get();
            if (!joueur.getPseudo().equals(currentPseudo)
                    || !joueur.getServerPseudo().equals(pseudoNormalized)
                    || !joueur.getPseudoIhm().equals(pseudoNormalized)
                    || !Boolean.TRUE.equals(joueur.getIsRaider())) {
                joueur.setPseudo(currentPseudo);
                joueur.setServerPseudo(pseudoNormalized);
                joueur.setPseudoIhm(pseudoNormalized);
                joueur.setIsRaider(true);
                joueurRepository.save(joueur);
                log.info("Pseudo mis a jour: {} -> {} (server: {})", joueur.getPseudo(), currentPseudo, member.getEffectiveName());
            } else {
                log.info("Joueur deja a jour: {}", currentPseudo);
            }
            return;
        }

        Joueur joueur = Joueur.builder()
                .discordId(discordId)
                .pseudo(currentPseudo)
                .serverPseudo(pseudoNormalized)
                .pseudoIhm(pseudoNormalized)
                .isRaider(true)
                .build();

        joueurRepository.save(joueur);
        log.info("Nouveau joueur ajoute: {}", currentPseudo);
    }

    protected void supprimerJoueursNonAutorises(List<Member> allowedMembers) {
        Set<String> allowedDiscordIds = allowedMembers.stream()
                .map(Member::getId)
                .collect(Collectors.toSet());

        List<Joueur> joueursASupprimer = joueurRepository.findAll().stream()
                .filter(joueur -> !allowedDiscordIds.contains(joueur.getDiscordId()))
                .collect(Collectors.toList());

        if (joueursASupprimer.isEmpty()) {
            log.info("Aucun joueur a supprimer apres synchro Discord");
            return;
        }

        discordMemberCleanupService.deleteUnauthorizedPlayers(joueursASupprimer);
        log.info("{} joueur(s) supprime(s) car sans role Discord autorise", joueursASupprimer.size());
    }

    public String cleanServerPseudo(String input) {
        if (input == null) {
            return null;
        }

        String cleaned = input.replaceAll("\\s+", "");
        cleaned = cleaned.replaceAll("[^\\p{ASCII}]", "");
        return cleaned.trim();
    }

    public boolean hasRoleFromList(Member member, List<String> roleNames) {
        return member.getRoles().stream()
                .map(role -> normalizeRoleName(role.getName()))
                .anyMatch(roleName -> roleNames.stream()
                        .map(this::normalizeRoleName)
                        .anyMatch(roleName::equals));
    }

    private String normalizeRoleName(String roleName) {
        String normalized = Normalizer.normalize(roleName, Normalizer.Form.NFD);
        normalized = normalized.replaceAll("\\p{M}+", "");
        return normalized.toLowerCase(Locale.ROOT);
    }
}
