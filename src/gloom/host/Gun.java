package gloom.host;

import gloom.Defs;
import gloom.Mem;
import gloom.Objects;
import gloom.data.Tables;

import java.util.Random;

/**
 * L'arme à l'écran de Gloom Deluxe ({@code misc/gun.bin}), dessinée comme un sprite 3D placé devant
 * la caméra (L_001f56 / L_004fe0 — cf. rebirth-docs/gloom-java/docs/DELUXE_HUD_GUN.md §3).
 *
 * <ul>
 *   <li>au repos : image 0 en (balancement_x, 112 − balancement_y, 128) ;</li>
 *   <li>en tir ({@link Objects#gunFlash} > 0) : flamme {2,3,3,4,4}[arme] en (0, 76, 112), échelle
 *       1 + rnd/4, puis image 1 en (0, 112, 128) ;</li>
 *   <li>rien si le joueur est mort ; assombrissement du fond si le joueur est invisible.</li>
 * </ul>
 * Absent (pas de gun.bin, ex. mode dossier GloomAmiga) → rien n'est dessiné.
 */
public final class Gun {

    private static final int[] FLASH = {2, 3, 3, 4, 4};

    private static Anim anim;
    private static boolean tried;
    /** Taille de la flamme : hasard propre à l'affichage, pour ne pas toucher au RNG du jeu. */
    private static final Random RND = new Random();

    private Gun() {
    }

    public static boolean available() {
        if (!tried) {
            tried = true;
            Anim a = Anim.load("misc/gun.bin");
            anim = (a != null && a.frames() == 5) ? a : null;
        }
        return anim != null;
    }

    /**
     * Dessine l'arme dans la vue (fb de largeur {@code stride}, vue viewW×viewH en haut). {@code focal}
     * = F de la projection (Deluxe : largeur/2 = 160 en 320 de large).
     */
    public static void draw(int fb, int stride, int viewW, int viewH, double focal, int player) {
        if (player == 0 || !available()) return;
        if (Mem.w(player + Defs.ob_hitpoints) <= 0) return;
        if (Objects.gunFlash > 0) {
            int w = Math.min(4, Math.max(0, Mem.w(player + Defs.ob_weapon)));
            anim.project(fb, stride, viewW, viewH, focal, FLASH[w], 0, 76, 112, 256 + RND.nextInt(64), false);
            anim.project(fb, stride, viewW, viewH, focal, 1, 0, 112, 128, 256, false);
            return;
        }
        // balancement (L_004558) : phase = ob_bounce & 255, sinus de la table camrots (2e mot)
        int ph = Mem.uw(player + Defs.ob_bounce) & 255;
        int bx = (sin(ph) * 25) >> 16;
        int by = (sin((ph * 2 + 32) & 255) * 7) >> 16;
        boolean invisible = Mem.w(player + Defs.ob_invisible) != 0;
        anim.project(fb, stride, viewW, viewH, focal, 0, bx, 112 - by, 128, 256, invisible);
    }

    private static int sin(int angle) {
        return Mem.w(Tables.camrotsinc + angle * 8 + 2);
    }
}
