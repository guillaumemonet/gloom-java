package gloom.host;

/**
 * Dit sur quelle carte graphique le programme tourne : une ligne d'information dans la console.
 * (L'avertissement « carte intégrée » a été retiré à la demande de l'auteur, le 23/11/2026.)
 *
 * Pur Java (pas d'appel GL ici) : l'appelant fournit la chaîne {@code GL_RENDERER} lue dans SON
 * contexte (LWJGL pour le 2D, jMonkeyEngine pour Rebirth).
 */
public final class GpuCheck {

    private GpuCheck() {
    }

    /** Affiche la carte utilisée. */
    public static void report(String renderer, String vendor) {
        String name = renderer == null ? "(inconnu)" : renderer;
        System.out.println("[GPU] Rendu : " + name + (vendor != null ? "  (" + vendor + ")" : ""));
    }
}
