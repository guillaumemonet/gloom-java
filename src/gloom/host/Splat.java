package gloom.host;

import gloom.Maths;
import gloom.Mem;
import gloom.Vars;

/**
 * L'éclaboussure de sang sur la vue — {@code scrnblood} (gloom.s:5411) et le bloc {@code .done}
 * de {@code drawblood} (gloom.s:5500).
 *
 * Quand une goutte de sang passe à moins de 40 unités de la caméra, {@code drawblood} ne la dessine
 * pas comme un pixel dans la scène : il lève {@code scrnblood} et éteint la goutte
 * ({@code bl_color = 0}). En fin de passe, l'original blitte alors UN des 4 glyphes-éclaboussures
 * de la fonte (indices 51 à 54) à une position tirée au hasard dans la vue — du sang qui gicle sur
 * l'écran du joueur.
 *
 * Les tirages passent par {@code rndw2}/{@code rndn2}, le SECOND flux d'aléatoire, précisément
 * pour que l'affichage ne perturbe pas la suite pseudo-aléatoire de la simulation.
 */
public final class Splat {

    /** Premier glyphe d'éclaboussure dans la fonte, et leur nombre (gloom.s:5502 `add #51,d0`). */
    public static final int FIRST = 51, COUNT = 4;

    /** Glyphe et position tirés par le dernier {@link #take} ayant renvoyé true. */
    public static int idx, x, y;

    private Splat() {
    }

    /**
     * Consomme {@code scrnblood} et tire le glyphe puis la position (dans l'ordre de l'original :
     * glyphe, Y, X). Renvoie false s'il n'y a rien à dessiner cette frame.
     */
    public static boolean take(int viewW, int viewH) {
        if (Mem.w(Vars.scrnblood) == 0) return false;   // tst scrnblood ; beq .rts
        Mem.ww(Vars.scrnblood, 0);                      // clr scrnblood
        // ATTENTION : rndn/rndn2 rendent le résultat dans le MOT BAS (convention 68k du portage,
        // le reste du registre est un résidu) — d'où le masque, comme chez tous les appelants.
        idx = FIRST + (Maths.rndw2() & 3);              // rndw2 ; and #3 ; add #51
        y = Maths.rndn2(Math.max(1, viewH - 8)) & 0xffff;   // wi_bh - 8
        x = Maths.rndn2(Math.max(1, viewW - 8)) & 0xffff;   // wi_bw - 8 (+ wi_x)
        return true;
    }

    /** Chemin 2D : consomme et dessine directement dans le framebuffer du jeu. */
    public static void drawInto(int fb, int w, int h) {
        if (take(w, h)) Font.drawGlyph(fb, w, h, x, y, idx, 0xf00);
    }
}
