package com.origin.util;

import lombok.NoArgsConstructor;

import java.util.Map;

@NoArgsConstructor
public class ParsedEmoji {

    public String classe;
    public String specialisation;
    public String role;

    public ParsedEmoji(String classe, String specialisation, String role) {
        this.classe = classe;
        this.specialisation = specialisation;
        this.role = role;
    }

    public ParsedEmoji parseEmoji(String emojiId) {
        String classe = EMOJI_TO_CLASSE.getOrDefault(emojiId, "Inconnue");
        String specialisation = EMOJI_TO_SPEC.getOrDefault(emojiId, "Inconnue");
        String role = EMOJI_TO_ROLE.getOrDefault(emojiId, "DPS");

        return new ParsedEmoji(classe, specialisation, role);
    }

    private static final Map<String, String> EMOJI_TO_CLASSE = Map.ofEntries(
            Map.entry("1013371175210065960", "DK"),
            Map.entry("1013371107610468445", "DK"),
            Map.entry("1013371108575162419", "DK"),
            Map.entry("637564171696734209", "Druide"),
            Map.entry("637564172007112723", "Druide"),
            Map.entry("637564171994529798", "Druide"),
            Map.entry("637564262167871489", "Moine"),
            Map.entry("637564262289637433", "Moine"),
            Map.entry("637564262054625281", "Moine"),
            Map.entry("637564297622454272", "Paladin"),
            Map.entry("637564297953673216", "Paladin"),
            Map.entry("637564379595931649", "Chaman"),
            Map.entry("637564379772223489", "Chaman"),
            Map.entry("637564379847458846", "Chaman"),
            Map.entry("637564445031399474", "Guerrier"),
            Map.entry("637564352333086720", "Voleur"),
            Map.entry("637564202130866186", "Chasseur"),
            Map.entry("637564202084466708", "Chasseur"),
            Map.entry("637564231239073802", "Mage"),
            Map.entry("637564407001513984", "Demoniste"),
            Map.entry("637564323442720768", "Pretre"),
            Map.entry("637564323291725825", "Pretre")
    );

    private static final Map<String, String> EMOJI_TO_ROLE = Map.ofEntries(
            Map.entry("1013371175210065960", "Tank"),
            Map.entry("1013371107610468445", "DPS"),
            Map.entry("1013371108575162419", "DPS"),
            Map.entry("637564171696734209", "Tank"),
            Map.entry("637564172007112723", "Heal"),
            Map.entry("637564171994529798", "DPS"),
            Map.entry("637564262167871489", "Tank"),
            Map.entry("637564262289637433", "Heal"),
            Map.entry("637564262054625281", "DPS"),
            Map.entry("637564297622454272", "Heal"),
            Map.entry("637564297953673216", "DPS"),
            Map.entry("637564379595931649", "DPS"),
            Map.entry("637564379772223489", "DPS"),
            Map.entry("637564379847458846", "Heal"),
            Map.entry("637564445031399474", "DPS"),
            Map.entry("637564352333086720", "DPS"),
            Map.entry("637564202130866186", "DPS"),
            Map.entry("637564202084466708", "DPS"),
            Map.entry("637564231239073802", "DPS"),
            Map.entry("637564407001513984", "DPS"),
            Map.entry("637564323442720768", "Heal"),
            Map.entry("637564323291725825", "DPS")
    );

    private static final Map<String, String> EMOJI_TO_SPEC = Map.ofEntries(
            Map.entry("1013371175210065960", "Sang"),
            Map.entry("1013371107610468445", "Givre"),
            Map.entry("1013371108575162419", "Impie"),
            Map.entry("637564171696734209", "Feral"),
            Map.entry("637564172007112723", "Restauration"),
            Map.entry("637564171994529798", "Equilibre"),
            Map.entry("637564262167871489", "Maitre brasseur"),
            Map.entry("637564262289637433", "Tisse-brume"),
            Map.entry("637564262054625281", "Marche-vent"),
            Map.entry("637564297622454272", "Sacre"),
            Map.entry("637564297953673216", "Retri"),
            Map.entry("637564379595931649", "Elem"),
            Map.entry("637564379772223489", "Amelio"),
            Map.entry("637564379847458846", "Restauration"),
            Map.entry("637564445031399474", "Arme"),
            Map.entry("637564352333086720", "Combat"),
            Map.entry("637564202130866186", "Survie"),
            Map.entry("637564202084466708", "Precision"),
            Map.entry("637564231239073802", "Feu"),
            Map.entry("637564407001513984", "Demonologie"),
            Map.entry("637564323442720768", "Discipline"),
            Map.entry("637564323291725825", "Ombre")
    );
}
