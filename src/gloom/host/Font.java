package gloom.host;

import gloom.Assets;
import gloom.Mem;

/**
 * Fonte AUTHENTIQUE de Gloom (misc/smallfont.bin), décodée depuis la shapetable blitter Amiga.
 *
 * Format (rétro-ingénierie de `blit` gloom.s:1595 + `printmess2` 1368) : table d'offsets à +4
 * (char N → long à fontBase+4+N*4), puis chaque glyphe = [maskOff(l)][mod(w)][bltsize(w)][image][mask].
 * L'image est faite pour un bitmap Amiga ENTRELACÉ 7 plans : 7 mots consécutifs = les 7 plans d'UNE
 * ligne de pixels (le blit avance de 40 o = une ligne-pixel tous les 7 mots, car 280/40=7). Donc la
 * ligne r du glyphe = OR des 7 mots `image[r*7 .. r*7+6]`, et il y a (imgMots/7) lignes (7 ici).
 *
 * Mapping ASCII→glyphe (printmess2) : ' '/'\\'→vide ; '0'-'9'→0-9 ; '\\''→57 ; '!'→36 ; '.'→37 ;
 * ':'→38 ; 127→39 ; lettres → (c & 31) + 9 (A→10 … Z→35). Avance = fontw (6 px).
 */
public final class Font {

    public static final int CW = 6, CH = 8;        // largeur d'avance / hauteur de ligne
    private static final int PLANES = 7, FONTW = 6, ROWS = 7;
    private static final boolean[][] GLYPH = new boolean[64][];   // masque fontw×rows par index

    /** Palette de la fonte (16 entrées $0RGB) — recopiée sur les index 0..15 des images (makeiff 11276). */
    public static final int[] FONTPAL = new int[16];

    static {
        try {
            byte[] f = Assets.read("misc/smallfont.bin");
            int p0 = u32(f, 0);                                    // font[0] : offset de la palette interne
            FONTPAL[0] = 0;
            for (int i = 1; i <= 15; i++) FONTPAL[i] = u16(f, p0 + 2 + (i - 1) * 2) & 0x0fff;
            for (int c = 0; c < 63; c++) {
                int g = u32(f, 4 + c * 4);
                int maskOff = u32(f, g);
                int rows = ((maskOff - 8) / 2) / PLANES;            // = 7
                boolean[] bits = new boolean[FONTW * ROWS];
                for (int r = 0; r < rows && r < ROWS; r++) {
                    int orw = 0;
                    for (int p = 0; p < PLANES; p++) orw |= u16(f, g + 8 + (r * PLANES + p) * 2);
                    for (int x = 0; x < FONTW; x++) bits[r * FONTW + x] = ((orw >> (15 - x)) & 1) != 0;
                }
                GLYPH[c] = bits;
            }
        } catch (Exception e) {
            System.err.println("[Font] smallfont.bin illisible : " + e.getMessage());
        }
    }

    private static int u16(byte[] d, int o) { return ((d[o] & 0xff) << 8) | (d[o + 1] & 0xff); }
    private static int u32(byte[] d, int o) { return (u16(d, o) << 16) | u16(d, o + 2); }

    /** ASCII → index de glyphe (printmess2, gloom.s:1376) ; -1 = espace (avance seulement). */
    private static int glyphIndex(char ch) {
        if (ch == ' ' || ch == '\\') return -1;
        if (ch >= '0' && ch <= '9') return ch - '0';
        if (ch == '\'') return 57;
        if (ch == '!') return 36;
        if (ch == '.') return 37;
        if (ch == ':') return 38;
        if (ch == 127) return 39;
        return (ch & 31) + 9;                      // lettres (A→10 … Z→35) ; insensible à la casse
    }

    /** Dessine `s` (couleur $0RGB) en (x,y), taille 1× (HUD). */
    public static void draw(int fb, int fbW, int fbH, int x, int y, String s, int color) {
        drawScaled(fb, fbW, fbH, x, y, s, color, 1);
    }

    /** Variante grand format (2×) avec contour noir — lisible sur fond chargé (menus, histoire). */
    public static void drawBig(int fb, int fbW, int fbH, int x, int y, String s, int color) {
        // contour : 4 décalages en noir, puis le texte par-dessus
        drawScaled(fb, fbW, fbH, x - 1, y, s, 0x000, 2);
        drawScaled(fb, fbW, fbH, x + 1, y, s, 0x000, 2);
        drawScaled(fb, fbW, fbH, x, y - 1, s, 0x000, 2);
        drawScaled(fb, fbW, fbH, x, y + 1, s, 0x000, 2);
        drawScaled(fb, fbW, fbH, x, y, s, color, 2);
    }

    /** Cœur du rendu : chaque pixel de glyphe devient un bloc scale×scale. */
    private static void drawScaled(int fb, int fbW, int fbH, int x, int y, String s, int color, int scale) {
        for (int i = 0; i < s.length(); i++) {
            int idx = glyphIndex(s.charAt(i));
            if (idx >= 0 && idx < 64 && GLYPH[idx] != null) {
                boolean[] g = GLYPH[idx];
                for (int gy = 0; gy < ROWS; gy++) {
                    for (int gx = 0; gx < FONTW; gx++) {
                        if (!g[gy * FONTW + gx]) continue;
                        for (int sy = 0; sy < scale; sy++) {
                            int py = y + gy * scale + sy;
                            if (py < 0 || py >= fbH) continue;
                            for (int sx = 0; sx < scale; sx++) {
                                int px = x + gx * scale + sx;
                                if (px < 0 || px >= fbW) continue;
                                Mem.ww(fb + (py * fbW + px) * 2, color);
                            }
                        }
                    }
                }
            }
            x += CW * scale;
        }
    }

    /** Dessine `s` centré horizontalement à la ligne y (taille 1×). */
    public static void drawCentered(int fb, int fbW, int fbH, int y, String s, int color) {
        draw(fb, fbW, fbH, (fbW - s.length() * CW) / 2, y, s, color);
    }

    /** Dessine `s` centré, grand format (2× + contour). */
    public static void drawCenteredBig(int fb, int fbW, int fbH, int y, String s, int color) {
        drawBig(fb, fbW, fbH, (fbW - s.length() * CW * 2) / 2, y, s, color);
    }

    public static int width(String s) { return s.length() * CW; }
    public static int widthBig(String s) { return s.length() * CW * 2; }
}
