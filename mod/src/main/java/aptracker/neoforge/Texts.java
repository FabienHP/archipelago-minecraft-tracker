package aptracker.neoforge;

import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/**
 * The tracker's own wording. Players join with an unmodded client, so the server cannot add
 * translation keys: it picks the text itself, from the language each client reports.
 */
enum Texts {
    ENGLISH,
    FRENCH;

    static Texts of(ServerPlayer player) {
        return language(player).startsWith("fr") ? FRENCH : ENGLISH;
    }

    /** Whether the client shows advancement titles in English, as Archipelago names them. */
    static boolean usesEnglishTitles(ServerPlayer player) {
        return language(player).startsWith("en");
    }

    private static String language(ServerPlayer player) {
        return player.clientInformation().language().toLowerCase(Locale.ROOT);
    }

    private String pick(String english, String french) {
        return this == FRENCH ? french : english;
    }

    String tabTitle() {
        return pick("Archipelago Tracker", "Suivi Archipelago");
    }

    String tabDescription() {
        return pick("The advancements left to do. The lit ones are doable with the items you have.",
                "Les succès restants. Ceux qui sont allumés sont faisables avec tes objets.");
    }

    String doable() {
        return pick("Doable now", "Faisable maintenant");
    }

    String missing(String items) {
        return pick("Missing: ", "Il manque : ") + items;
    }

    String outOfLogic() {
        return pick("Out of logic", "Hors logique");
    }

    String area(String region) {
        return pick("Area: ", "Zone : ") + region;
    }

    String fillerOnly() {
        return pick("Holds a filler item only", "Ne contient qu'un objet de remplissage");
    }

    String archipelagoName(String name) {
        return pick("Archipelago name: ", "Nom Archipelago : ") + name;
    }

    String progressTitle() {
        return pick("Progress", "Progression");
    }

    String doableCount(int count) {
        return pick("Doable now: ", "Faisables maintenant : ") + count;
    }

    String checkedCount(int checked, int total) {
        return pick("Done: ", "Faits : ") + checked + " / " + total;
    }

    String goal(int checked, int goal) {
        return pick("Goal: ", "Objectif : ") + checked + " / " + goal + pick(" advancements", " succès");
    }

    String eggShards(int owned, int required) {
        return pick("Dragon Egg Shards: ", "Fragments d'œuf de dragon : ") + owned + " / " + required;
    }

    String bossReady(String name) {
        return name + pick(": you have the items to defeat it", " : tu as les objets pour le vaincre");
    }

    String bossMissing(String name, String items) {
        return name + pick(": missing ", " : il manque ") + items;
    }

    String notConnected() {
        return pick("Not connected to Archipelago: the items you received are unknown.",
                "Non connecté à Archipelago : les objets reçus sont inconnus.");
    }

    String reachable() {
        return pick("Reachable", "Accessible");
    }

    String regionCounts(int doable, int left) {
        return doable + pick(" doable · ", " faisables · ") + left + pick(" left", " restants");
    }

    String locatedIn(String region) {
        return pick("Located in: ", "Se trouve dans : ") + region;
    }

    String nowDoable() {
        return pick("Now doable: ", "Maintenant faisable : ");
    }

    String andMore(int count) {
        return pick(" and " + count + " more", " et " + count + " de plus");
    }

    String disabled() {
        return pick("The tracker is off: the server has no readable .apmc file.",
                "Le suivi est désactivé : le serveur n'a pas de fichier .apmc lisible.");
    }

    String openTheTab() {
        return pick("Open the advancements screen and pick the \"Archipelago Tracker\" tab.",
                "Ouvre l'écran des succès et choisis l'onglet « Suivi Archipelago ».");
    }
}
