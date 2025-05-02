package com.origin.service.discord;

import com.origin.dto.JoueurDTO;
import com.origin.dto.RaidDTO;
import com.origin.dto.RaidDayResponse;
import com.origin.entity.Joueur;
import com.origin.entity.Personnage;
import com.origin.entity.Raid;
import com.origin.enumOrigin.StatutParticipation;
import com.origin.repository.PersonnageRepository;
import com.origin.repository.RaidRepository;
import com.origin.service.JoueurService;
import com.origin.service.PersonnageService;
import com.origin.util.ParsedEmoji;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RaidQueryService {

    private final JDA jda;
    private final RaidRepository raidRepository;
    private final PersonnageRepository personnageRepository;
    private final PersonnageService personnageService;
    private final RaidHelperParserService parserService;
    private final JoueurService joueurService;

    public List<RaidDayResponse> getRaidsGroupedByDay() {
        List<Raid> allRaids = raidRepository.findUpcomingRaids();
        List<RaidDTO> raidDTOList = new ArrayList<>();
        for (Raid raid : allRaids) {
            List<JoueurDTO> joueurDTOList = getInscriptionsFromRaidHelper(raid.getChannelId(), raid.getRaidHelperId());
            raidDTOList.add(toRaidDTO(raid, joueurDTOList));
        }
        Map<LocalDate, List<RaidDTO>> grouped = raidDTOList.stream()
                .collect(Collectors.groupingBy(raid -> raid.getHeure().toLocalDate()));

        return grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new RaidDayResponse(entry.getKey().toString(), entry.getValue()))
                .collect(Collectors.toList());
    }

    private RaidDTO toRaidDTO(Raid raid, List<JoueurDTO> joueurDTOList) {
        return new RaidDTO(
                raid.getId(),
                raid.getNom(),
                raid.getDate(),
                joueurDTOList,
                raid.getGroup1().stream().map(personnageService::toDTO).collect(Collectors.toList()),
                raid.getGroup2().stream().map(personnageService::toDTO).collect(Collectors.toList())
        );
    }

    public List<JoueurDTO> getInscriptionsFromRaidHelper(String channelId, String messageId) {
        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            log.warn("Channel not found: {}", channelId);
            return List.of();
        }

        Message message;
        try {
            message = channel.retrieveMessageById(messageId).complete();
        } catch (Exception e) {
            log.error("Failed to retrieve message: {}", e.getMessage());
            return List.of();
        }

        if (message == null || message.getEmbeds().isEmpty()) {
            log.warn("No embed found in message {}", messageId);
            return List.of();
        }

        MessageEmbed embed = message.getEmbeds().get(0);
        List<MessageEmbed.Field> fields = embed.getFields();

        // Étape 1 : Map pseudo → statut
        Map<String, StatutParticipation> pseudoToStatus = new HashMap<>();
        for (MessageEmbed.Field field : fields) {
            String raw = field.getValue();
            if (raw == null) continue;
            String status;

            String rawLower = raw.toLowerCase();

            if (rawLower.startsWith("<:tentative:")) {
                extractStatus(pseudoToStatus, rawLower, StatutParticipation.TENTATIVE);
            } else if (rawLower.startsWith("<:bench:")) {
                extractStatus(pseudoToStatus, rawLower, StatutParticipation.BENCH);
            } else if (rawLower.startsWith("<:late:")) {
                extractStatus(pseudoToStatus, rawLower, StatutParticipation.LATE);
            } else {
                continue;
            }


            String value = field.getValue();
            if (value == null) continue;
        }

        // Étape 2 : Création des JoueurDTO
        List<JoueurDTO> joueurDTOList = new ArrayList<>();
        for (MessageEmbed.Field field : fields) {
            String value = field.getValue();
            if (value == null) continue;

            String[] lines = value.split("\n");
            for (String line : lines) {
                Pattern pattern = Pattern.compile("<:[^:]+:(\\d+)>\\s+`\\d+`\\s+\\*\\*(.+?)\\*\\*");
                Matcher matcher = pattern.matcher(line);

                if (!matcher.matches()) continue;

                String emojiId = matcher.group(1);
                String pseudo = matcher.group(2).trim();
                String cleaned = cleanServerPseudo(pseudo);

                ParsedEmoji parsedEmoji = new ParsedEmoji();
                parsedEmoji = parsedEmoji.parseEmoji(emojiId);

                if (parsedEmoji.classe != null) {
                    Joueur joueur = joueurService.findByServerPseudo(cleaned);
                    if (joueur == null) {
                        log.info("pseudo qui pose pb : {}", cleaned);
                        continue;
                    }

                    if (joueur.getMainCharacter() == null) {
                        Personnage p = Personnage.builder()
                                .nom(cleaned)
                                .classe(parsedEmoji.classe)
                                .role(parsedEmoji.role)
                                .specialisation(parsedEmoji.specialisation)
                                .main(true)
                                .joueur(joueur)
                                .build();
                        p = personnageService.save(p);
                        joueur = joueurService.createWithMainCharacter(joueur, p);

                        log.info("🆕 Joueur auto-créé : {} [{} - {} - {}]", pseudo, parsedEmoji.classe, parsedEmoji.role, parsedEmoji.specialisation);
                    }

                    List<Personnage> rerolls = personnageService.gerRerolls(joueur.getId());

                    JoueurDTO joueurDTO = new JoueurDTO(
                            null,
                            pseudo,
                            pseudo,
                            pseudo,
                            personnageService.toDTO(joueur.getMainCharacter()),
                            rerolls.stream().map(personnageService::toDTO).collect(Collectors.toList()),
                            false,
                            // Ajout du statut ici
                            StatutParticipation.valueOf(
                                    String.valueOf(pseudoToStatus.getOrDefault(cleaned, StatutParticipation.TITULAIRE))
                            )
                    );

                    joueurDTOList.add(joueurDTO);
                } else {
                    log.info("Pas dans la map :  {} {}", emojiId, line);
                }
            }
        }

        for (Map.Entry<String, StatutParticipation> entry : pseudoToStatus.entrySet()) {
            joueurDTOList.add(extractJoueurStatus(entry.getKey(), entry.getValue()));
        }
        return joueurDTOList;
    }


    public String cleanServerPseudo(String input) {
        if (input == null) return null;
        return input.replaceAll("\\s+", "").replaceAll("[^\\p{ASCII}]", "").trim();
    }

    private Optional<String> extractEmojiId(String line) {
        Pattern pattern = Pattern.compile("<:(\\w+):(\\d+)>");
        Matcher matcher = pattern.matcher(line);
        if (matcher.find()) {
            return Optional.of(matcher.group(2));
        }
        return Optional.empty();
    }

    private void extractStatus(Map<String,StatutParticipation> mapStatus, String raidHelperString, StatutParticipation statutParticipation) {

        int indexAbsence = raidHelperString.indexOf("absence");
        if (indexAbsence != -1) {
            raidHelperString = raidHelperString.substring(0, indexAbsence);
        }


        // Match tous les **Pseudo**
        Pattern pattern = Pattern.compile("\\*\\*(.+?)\\*\\*");
        Matcher matcher = pattern.matcher(raidHelperString);

        while (matcher.find()) {
            mapStatus.put(matcher.group(1).trim(), statutParticipation);
        }
    }

    private JoueurDTO extractJoueurStatus(String pseudo, StatutParticipation statutParticipation) {

        String cleaned = cleanServerPseudo(pseudo);
        Joueur joueur = joueurService.findByServerPseudo(cleaned);

        List<Personnage> rerolls = personnageService.gerRerolls(joueur.getId());

        JoueurDTO joueurDTO = new JoueurDTO(
                null,
                pseudo,
                pseudo,
                pseudo,
                personnageService.toDTO(joueur.getMainCharacter()),
                rerolls.stream().map(personnageService::toDTO).collect(Collectors.toList()),
                false,
                statutParticipation
        );

        return joueurDTO;
    }



}
