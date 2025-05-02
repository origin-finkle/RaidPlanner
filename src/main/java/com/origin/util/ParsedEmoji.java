package com.origin.util;

import lombok.NoArgsConstructor;

import java.util.Map;

@NoArgsConstructor
public class ParsedEmoji {

    public String classe;
    public  String specialisation;
    public  String role;

    public ParsedEmoji(String classe, String specialisation, String role) {
        this.classe = classe;
        this.specialisation = specialisation;
        this.role = role;
    }



    public ParsedEmoji parseEmoji(String emojiId) {
        String classe = emojiToClasse.getOrDefault(emojiId, "Inconnue");
        String specialisation = emojiToSpec.getOrDefault(emojiId, "Inconnue");
        String role = emojiToRole.getOrDefault(emojiId, "DPS");

        return new ParsedEmoji(classe, specialisation, role);
    }

    private static final Map<String, String> emojiToClasse = Map.ofEntries(
            Map.entry("1013371175210065960", "DK"), // Blood_Tank
            Map.entry("1013371107610468445", "DK"), // DK Frost
            Map.entry("1013371108575162419", "DK"), // DK Unholy
            Map.entry("637564171696734209", "Druide"),// Druide Tank
            Map.entry("637564172007112723", "Druide"), // Drude heal
            Map.entry("637564171994529798", "Druide"), // Boomie
            Map.entry("637564297622454272", "Paladin"), // Hpal
            Map.entry("637564297953673216", "Paladin"), // Ret
            Map.entry("637564379595931649", "Chaman"), // Elem
            Map.entry("637564379772223489", "Chaman"), // Amelio
            Map.entry("637564379847458846", "Chaman"), // Cham heal
            Map.entry("637564445031399474", "Guerrier"), // War arme
            Map.entry("637564352333086720", "Voleur"),// Voleur combat
            Map.entry("637564202130866186", "Chasseur"), // Hunt survie
            Map.entry("637564202084466708", "Chasseur"),// Hunt précis
            Map.entry("637564231239073802", "Mage"),// Mage fire
            Map.entry("637564407001513984", "Démoniste"),// Demono
            Map.entry("637564323442720768", "Prêtre"),// DP
            Map.entry("637564323291725825", "Prêtre")// SP



            // etc.
    );

    private static final Map<String, String> emojiToRole = Map.ofEntries(
            Map.entry("1013371175210065960", "Tank"),
            Map.entry("1013371107610468445", "DPS"),
            Map.entry("1013371108575162419", "DPS"),
            Map.entry("637564171696734209", "Tank"),
            Map.entry("637564172007112723", "Heal"),
            Map.entry("637564171994529798", "DPS"),
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

            // etc.
    );

    private static final Map<String, String> emojiToSpec = Map.ofEntries(
            Map.entry("1013371175210065960", "Sang"),
            Map.entry("1013371107610468445", "Givre"),
            Map.entry("1013371108575162419", "Impie"),
            Map.entry("637564171696734209", "Feral"),
            Map.entry("637564172007112723", "Restauration"),
            Map.entry("637564171994529798", "Equilibre"),
            Map.entry("637564297622454272", "Sacré"),
            Map.entry("637564297953673216", "Retribution"),
            Map.entry("637564379595931649", "Elem"),
            Map.entry("637564379772223489", "Amélio"),
            Map.entry("637564379847458846", "Restauration"),
            Map.entry("637564445031399474", "Arme"),
            Map.entry("637564352333086720", "Combat"),
            Map.entry("637564202130866186", "Survie"),
            Map.entry("637564202084466708", "Précision"),
            Map.entry("637564231239073802", "Feu"),
            Map.entry("637564407001513984", "Démonologie"),
            Map.entry("637564323442720768", "Discipline"),
            Map.entry("637564323291725825", "Ombre")

            // etc.
    );
}
