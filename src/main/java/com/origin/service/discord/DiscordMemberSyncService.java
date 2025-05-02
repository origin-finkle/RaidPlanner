package com.origin.service.discord;


import com.origin.entity.Joueur;
import com.origin.repository.JoueurRepository;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DiscordMemberSyncService {

    private final JDA jda;
    private final JoueurRepository joueurRepository;

    @PostConstruct
    public void syncMembersWithRole() {
        String guildId = "670711708628811805"; // Remplace par ton ID de serveur
        List<String> roleNames = List.of("Apply", "Officiers", "Vétérans", "Membres");


        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            System.err.println("❌ Guild non trouvée !");
            return;
        }

        guild.loadMembers().onSuccess(members -> {
            System.out.printf("🔍 Scanning %d membres dans le serveur %s%n", members.size(), guild.getName());

            members.stream()
                    .filter(m -> m.getRoles().stream()
                            .anyMatch(r -> roleNames.stream()
                                    .anyMatch(target -> r.getName().equalsIgnoreCase(target))))
                    .forEach(this::enregistrerJoueur);
        });
    }

    private void enregistrerJoueur(Member member) {
        String discordId = member.getId();                      // ID unique Discord
        String currentPseudo = member.getUser().getName();      // Pseudo actuel
        String serverPseudo = member.getEffectiveName();
        List<String> roleNames = List.of("Apply", "Officiers", "Vétérans");


        Optional<Joueur> existing = joueurRepository.findByDiscordId(discordId);
        String pseudoNormalized = cleanServerPseudo(serverPseudo);
        if (existing.isPresent()) {
            Joueur joueur = existing.get();

            // 🔄 Si le pseudo Discord a changé, on le met à jour
            if (hasRoleFromList(member, roleNames)) {
                if (!joueur.getPseudo().equals(currentPseudo) || !joueur.getServerPseudo().equals(pseudoNormalized) || !joueur.getIsRaider()) {
                    joueur.setPseudo(currentPseudo);
                    joueur.setServerPseudo(pseudoNormalized);
                    joueur.setIsRaider(true);
                    joueurRepository.save(joueur);
                    System.out.printf("🔁 Pseudo mis à jour : %s → %s (server: %s)%n", joueur.getPseudo(), currentPseudo, serverPseudo);
                } else {
                    System.out.println("⏭️ Joueur déjà à jour : " + currentPseudo);
                }
            } else {
                joueur.setIsRaider(false);
                joueurRepository.save(joueur);
            }
        } else {
            // 🆕 Nouveau joueur
            Joueur joueur = Joueur.builder()
                    .discordId(discordId)
                    .pseudo(currentPseudo)
                    .serverPseudo(serverPseudo)
                    .pseudoIhm(pseudoNormalized)
                    .isRaider(true)
                    .build();

            joueurRepository.save(joueur);
            System.out.println("✅ Nouveau joueur ajouté : " + currentPseudo);
        }
    }

    public String cleanServerPseudo(String input) {
        if (input == null) return null;

        // Supprimer tous les espaces, y compris insécables, tabs, etc.
        String cleaned = input.replaceAll("\\s+", "");

        // Supprimer les caractères non ASCII (tu peux ajuster si tu veux garder certains caractères)
        cleaned = cleaned.replaceAll("[^\\p{ASCII}]", "");

        // Trim final pour sécurité
        return cleaned.trim();
    }

    public boolean hasRoleFromList(Member member, List<String> roleNames) {
        return member.getRoles().stream()
                .anyMatch(role -> roleNames.stream()
                        .anyMatch(target -> role.getName().equalsIgnoreCase(target)));
    }
}