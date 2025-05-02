package com.origin.service;

import com.origin.dto.ExportCompoRequestDto;
import com.origin.dto.PersonnageCompositionDTO;
import com.origin.dto.RaidCompositionDTO;
import com.origin.dto.RaidDTO;
import com.origin.entity.Joueur;
import com.origin.entity.Personnage;
import com.origin.entity.Raid;
import com.origin.entity.RaidInscription;
import com.origin.repository.JoueurRepository;
import com.origin.repository.PersonnageRepository;
import com.origin.repository.RaidInscriptionRepository;
import com.origin.repository.RaidRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class RaidService {
    private final RaidRepository raidRepository;
    private final JoueurRepository joueurRepository;
    private final PersonnageRepository personnageRepository;
    private final JDA jda;
    private final RaidInscriptionRepository raidInscriptionRepository;

    public void saveComposition(RaidCompositionDTO dto) {
        Optional<Raid> optionalRaid = raidRepository.findById(dto.getRaidId());
        if (optionalRaid.isEmpty()) {
            throw new IllegalArgumentException("Raid non trouvé : " + dto.getRaidId());
        }

        Raid raid = optionalRaid.get();

        // Clear anciennes compositions si tu les enregistres
        raid.getGroup1().clear();
        raid.getGroup2().clear();

        raid.setGroup1(mapToPersonnages(dto.getGroup1()));
        raid.setGroup2(mapToPersonnages(dto.getGroup2()));

        raidRepository.save(raid);
    }

    private Set<Personnage> mapToPersonnages(List<PersonnageCompositionDTO> dtoList) {
        return dtoList.stream()
                .map(dto -> personnageRepository.findByNomStrict(dto.getNom())
                        .orElseThrow(() -> new RuntimeException("Personnage non trouvé : " + dto.getNom())))
                .collect(Collectors.toSet());
    }


    public void exportFormattedComposition(Long raidId, ExportCompoRequestDto request) {
        Optional<Raid> raidOpt = raidRepository.findById(raidId);
        if (raidOpt.isEmpty()) throw new IllegalArgumentException("Raid introuvable : " + raidId);

        Raid raid = raidOpt.get();
        boolean publier = request.isEnvoyerSurDiscord();

        if (!publier) return;

        TextChannel channel = jda.getTextChannelById(raid.getChannelId());
        //Long chanelId = 1355602641748496394L;
        //TextChannel channel = jda.getTextChannelById(chanelId);
        if (channel == null) {
            log.warn("❌ Salon Discord introuvable pour ID : {}", raid.getChannelId());
            return;
        }

        MessageEmbed embed = buildTwoColumnEmbedWithConfirmations(raid);
        List<Joueur> joueurs = getJoueursFromRaid(raid);
        String mentions = generateMentionLine(joueurs);

        channel.sendMessageEmbeds(embed)
                .setActionRow(
                        Button.success("confirm_" + raidId, "✅ Confirmer"),
                        Button.danger("cancel_" + raidId, "❌ Annuler")
                )
                .addContent(mentions)
                .queue(message -> {
                    raid.setDiscordMessageId(message.getIdLong());
                    raidRepository.save(raid);
                });
    }


    public MessageEmbed buildTwoColumnEmbedWithConfirmations(Raid raid) {
        EmbedBuilder builder = new EmbedBuilder()
                .setTitle("🛡️ Composition du raid : " + raid.getNom())
                .setColor(0x5865F2);

        Map<Long, RaidInscription.StatutInscription> confirmationMap = raidInscriptionRepository
                .findAll().stream()
                .filter(i -> i.getRaid().getId().equals(raid.getId()))
                .collect(Collectors.toMap(
                        i -> i.getJoueur().getId(),
                        RaidInscription::getStatut
                ));

        String group1Text = formatGroupWithConfirmation(raid.getGroup1(), confirmationMap);
        String group2Text = formatGroupWithConfirmation(raid.getGroup2(), confirmationMap);

        builder.addField("Groupe 1", group1Text.isEmpty() ? "—" : group1Text, true);
        builder.addField("Groupe 2", group2Text.isEmpty() ? "—" : group2Text, true);

        return builder.build();
    }


    private String formatGroupWithConfirmation(Set<Personnage> group, Map<Long, RaidInscription.StatutInscription> confirmationMap) {
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (Personnage p : group) {
            String emoji = getEmojiFor(p);
            boolean isConfirmed = confirmationMap.getOrDefault(p.getJoueur().getId(), RaidInscription.StatutInscription.ANNULE)
                    == RaidInscription.StatutInscription.CONFIRME;
            sb.append(emoji).append(" `").append(i++).append("` **").append(p.getNom()).append("**");
            if (isConfirmed) {
                sb.append(" ✅");
            }
            else{
                sb.append(" ❌");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String getEmojiFor(Personnage p) {
        Map<String, String> emojiMap = Map.ofEntries(
                Map.entry("DK-Sang", "<:dk_sang:1363215681570603170>"),
                Map.entry("DK-Givre", "<:dk_givre:1363215048675299479>"),
                Map.entry("DK-Impie", "<:dk_impie:1363215050155884745>"),
                Map.entry("Druide-Feral", "<:druide_feral:1363215056023588924>"),
                Map.entry("Druide-Restauration", "<:druide_restauration:1363229950353608787>"),
                Map.entry("Druide-Equilibre", "<:druide_equilibre:1363215053142364221>"),
                Map.entry("Paladin-Sacré", "<:paladin_sacre:1363215077452419254>"),
                Map.entry("Paladin-Rétribution", "<:paladin_retribution:1363215074520727735>"),
                Map.entry("Paladin-Protection", "<:paladin-protection:1363215984923513033>"),
                Map.entry("Chaman-Elem", "<:chaman_elem:1363215015540166768>"),
                Map.entry("Chaman-Amélio", "<:chaman_amelioration:1363214654284894429>"),
                Map.entry("Chaman-Restauration", "<:chaman_restauration:1363215037757522172>"),
                Map.entry("Guerrier-Arme", "<:guerrier_arme:1363215059429495024>"),
                Map.entry("Guerrier-Fury", "<:guerrier_fury:1363215740328611991>"),
                Map.entry("Guerrier-Protection", "<:guerrier_protection:1363215062927544470>"),
                Map.entry("Voleur-Combat", "<:voleur_combat:1363215091125850224>"),
                Map.entry("Voleur-Finesse", "<:voleur_finesse:1363216048442179836>"),
                Map.entry("Voleur-Assassinat", "<:voleur_assassinat:1363215089427153016>"),
                Map.entry("Chasseur-Survie", "<:chasseur_survie:1363215042094432286>"),
                Map.entry("Chasseur-Précision", "<:chasseur_precision:1363215040487887061>"),
                Map.entry("Chasseur-BM", "<:chasseur_bm:1363215038911090908>"),
                Map.entry("Mage-Feu", "<:mage_feu:1363215067826360492>"),
                Map.entry("Mage-Arcane", "<:mage_arcane:1363215952573104268>"),
                Map.entry("Mage-Givre", "<:mage_givre:637564231239073802>"),
                Map.entry("Démoniste-Démonologie", "<:demoniste_demonologie:1363215045768773873>"),
                Map.entry("Démoniste-Affliction", "<:demoniste_affliction:1363215043453260068>"),
                Map.entry("Démoniste-Destruction", "<:demoniste_destruction:1363215047337316624>"),
                Map.entry("Prêtre-Discipline", "<:pretre_discipline:1363215080027853051>"),
                Map.entry("Prêtre-Ombre", "<:pretre_ombre:1363215649018740847>"),
                Map.entry("Prêtre-Sacré", "<:pretre_sacre:1363215084003917984>")
        );

        String classe = p.getClasse();
        String spe = p.getSpecialisation();
        String key = classe + "-" + spe;

        return emojiMap.getOrDefault(key, "🧍");
    }


    private List<Joueur> getJoueursFromRaid(Raid raid) {
        return Stream.concat(
                        raid.getGroup1().stream(),
                        raid.getGroup2().stream()
                )
                .map(Personnage::getJoueur)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }


    private String generateMentionLine(List<Joueur> joueurs) {
        return joueurs.stream()
                .map(j -> "<@" + j.getDiscordId() + ">")
                .collect(Collectors.joining(" "));
    }

    public Raid getRaidById(Long raidId) {
        return raidRepository.findWithGroups(raidId).orElseThrow(() -> new IllegalArgumentException("Raid introuvable : " + raidId));
    }


    public void saveRaid(Raid raid) {
        raidRepository.save(raid);
    }



}
