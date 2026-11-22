package gloom.host;

import java.util.regex.Pattern;

/**
 * Dit sur quelle carte graphique le programme tourne, et AVERTIT s'il s'agit d'une puce intégrée.
 *
 * Sur un portable à double carte, le pilote donne par défaut l'iGPU à {@code java.exe} (ce n'est pas
 * un jeu connu de son profil), et un processus Java ne peut pas choisir lui-même : les symboles
 * {@code NvOptimusEnablement} / {@code AmdPowerXpressRequestHighPerformance} doivent être exportés
 * par l'exécutable natif. On ne peut donc que le détecter et donner le correctif.
 *
 * Pur Java (pas d'appel GL ici) : l'appelant fournit la chaîne {@code GL_RENDERER} lue dans SON
 * contexte (LWJGL pour le 2D, jMonkeyEngine pour Rebirth).
 */
public final class GpuCheck {

    private static final Pattern INTEGRATED = Pattern.compile(
            "Intel.*(HD|UHD|Iris)|AMD Radeon\\(TM\\) Graphics|Microsoft Basic Render",
            Pattern.CASE_INSENSITIVE);

    /** Correctif Windows, à afficher tel quel. */
    public static final String FIX =
            "Parametres Windows > Systeme > Affichage > Graphiques > ajouter java.exe et javaw.exe"
            + " > \"Hautes performances\", puis relancer.";

    private GpuCheck() {
    }

    /** true si le nom de rendu ressemble à une carte intégrée. */
    public static boolean looksIntegrated(String renderer) {
        return renderer != null && INTEGRATED.matcher(renderer).find();
    }

    /**
     * Affiche la carte utilisée ; si elle semble intégrée, imprime un AVERTISSEMENT bien visible.
     * @return le texte d'avertissement court (à afficher aussi à l'écran), ou null si tout va bien
     */
    public static String report(String renderer, String vendor) {
        String name = renderer == null ? "(inconnu)" : renderer;
        System.out.println("[GPU] Rendu : " + name + (vendor != null ? "  (" + vendor + ")" : ""));
        if (!looksIntegrated(renderer)) return null;
        String bar = "=".repeat(78);
        System.err.println(bar);
        System.err.println("AVERTISSEMENT : le jeu tourne sur la carte graphique INTEGREE (" + name + ").");
        System.err.println("Si la machine a une carte dediee, elle n'est PAS utilisee : performances");
        System.err.println("et mesures seront celles de l'iGPU. Correctif :");
        System.err.println("  " + FIX);
        System.err.println(bar);
        return "CARTE INTEGREE : " + name;
    }
}
