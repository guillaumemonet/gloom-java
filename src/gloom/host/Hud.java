package gloom.host;

import gloom.Defs;
import gloom.Mem;

/**
 * Affichage tête haute en surimpression du framebuffer du jeu.
 *
 * Reproduit la DISPOSITION de `showstats` (gloom.s:1505) avec des primitives hôte (l'original
 * dessine des glyphes-icônes de la fonte Amiga ; ici on les rend en graphismes nets) :
 *   - barre de vie  : 25 segments en haut-à-gauche (char 40 plein / 41 vide), `.hploop`  ;
 *   - vies          : icônes en haut-à-droite, de la droite vers la gauche (char 43), `.lvloop` ;
 *   - armes         : 5 emplacements sous la barre, l'arme courante surlignée (49-arme), `.wploop`.
 */
public final class Hud {

    private static final int SEGS = 25;          // points de vie max affichés (.hpdone : cap 25)

    public static void draw(int fb, int fbW, int fbH, int player) {
        if (player == 0) return;
        int hp = Math.max(0, Mem.w(player + Defs.ob_hitpoints));
        int lives = Mem.w(player + Defs.ob_lives);
        int weapon = Mem.w(player + Defs.ob_weapon);

        // --- barre de vie (haut-gauche) : SEGS segments, plein jusqu'à hp ---
        int sw = 4, sh = 6, x0 = 6, y0 = 4;
        int barW = SEGS * sw;
        fillRect(fb, fbW, fbH, x0 - 2, y0 - 2, barW + 4, sh + 4, 0x000);
        frame(fb, fbW, fbH, x0 - 2, y0 - 2, barW + 4, sh + 4, 0x889);
        int filled = Math.min(SEGS, hp);
        int col = hp <= 5 ? 0xf00 : hp <= 12 ? 0xfd0 : 0x0e0;     // rouge / ambre / vert
        for (int i = 0; i < SEGS; i++) {
            fillRect(fb, fbW, fbH, x0 + i * sw, y0, sw - 1, sh, i < filled ? col : 0x223);
        }

        // --- vies (haut-droite) : une icône par vie, de droite à gauche ---
        int lx = fbW - 12;
        for (int i = 0; i < lives && lx > barW + 12; i++) {
            lifeIcon(fb, fbW, fbH, lx, y0);
            lx -= 12;
        }

        // --- armes (sous la barre) : 5 emplacements, l'arme courante surlignée ---
        int wy = y0 + sh + 5;
        for (int w = 0; w < 5; w++) {
            int wx = x0 + w * 14;
            boolean cur = (w == weapon);
            fillRect(fb, fbW, fbH, wx, wy, 11, 9, cur ? 0x357 : 0x223);
            frame(fb, fbW, fbH, wx, wy, 11, 9, cur ? 0xffe : 0x445);
            Font.draw(fb, fbW, fbH, wx + 3, wy + 1, String.valueOf(w + 1), cur ? 0xffe : 0x99a);
        }
    }

    /** Petite icône « vie » (silhouette de marine simplifiée). */
    private static void lifeIcon(int fb, int fbW, int fbH, int x, int y) {
        fillRect(fb, fbW, fbH, x + 3, y, 3, 2, 0xfd9);          // tête
        fillRect(fb, fbW, fbH, x + 1, y + 2, 7, 4, 0x09c);      // torse
        fillRect(fb, fbW, fbH, x + 2, y + 6, 2, 2, 0x067);      // jambes
        fillRect(fb, fbW, fbH, x + 5, y + 6, 2, 2, 0x067);
    }

    private static void fillRect(int fb, int fbW, int fbH, int x, int y, int w, int h, int color) {
        for (int yy = Math.max(0, y); yy < Math.min(fbH, y + h); yy++) {
            for (int xx = Math.max(0, x); xx < Math.min(fbW, x + w); xx++) {
                Mem.ww(fb + (yy * fbW + xx) * 2, color);
            }
        }
    }

    private static void frame(int fb, int fbW, int fbH, int x, int y, int w, int h, int color) {
        for (int xx = x; xx < x + w; xx++) { px(fb, fbW, fbH, xx, y, color); px(fb, fbW, fbH, xx, y + h - 1, color); }
        for (int yy = y; yy < y + h; yy++) { px(fb, fbW, fbH, x, yy, color); px(fb, fbW, fbH, x + w - 1, yy, color); }
    }

    private static void px(int fb, int fbW, int fbH, int x, int y, int color) {
        if (x >= 0 && x < fbW && y >= 0 && y < fbH) Mem.ww(fb + (y * fbW + x) * 2, color);
    }
}
