package gloom.host;

import gloom.Assets;
import gloom.Mem;

/**
 * Fontes AUTHENTIQUES de Gloom (misc/smallfont.bin 6×8, misc/bigfont.bin 8×10), décodées depuis
 * les shapetables blitter Amiga.
 *
 * Format (rétro-ingénierie de `blit` gloom.s:1595 + `printmess2` 1368) : table d'offsets à +4
 * (char N → long à fontBase+4+N*4), puis chaque glyphe = [maskOff(l)][mod(w)][bltsize(w)][image][mask].
 * L'image cible un bitmap Amiga ENTRELACÉ 7 plans : 7 mots consécutifs = les 7 plans d'UNE ligne de
 * pixels. La fonte n'est PAS monochrome : c'est une fonte BISEAUTÉE (3 teintes — sombre/moyen/clair),
 * d'où les 7 plans. Pour chaque pixel on reconstitue l'INDICE de couleur (bits des 7 plans) ; 0 =
 * transparent, sinon un NIVEAU de luminosité (1..max) rendu comme une teinte de la couleur demandée
 * → on conserve le biseau dans n'importe quelle couleur (menu violet, histoire cyan…).
 *
 * Mapping ASCII→glyphe (printmess2) : ' '/'\\'→vide ; '0'-'9'→0-9 ; '\\''→57 ; '!'→36 ; '.'→37 ;
 * ':'→38 ; 127→39 ; lettres → (c & 31) + 9 (A→10 … Z→35).
 */
public final class Font {

    /** Une fonte décodée : glyphes (niveaux de teinte par pixel) + métriques. */
    private static final class Glyphs {
        final int w, h, advance;
        final byte[][] glyph;       // par index : w*h niveaux (0 = transparent, 1..maxLevel)
        final int maxLevel;
        Glyphs(int w, int h, int advance, byte[][] glyph, int maxLevel) {
            this.w = w; this.h = h; this.advance = advance; this.glyph = glyph; this.maxLevel = maxLevel;
        }
    }

    private static final int PLANES = 7;
    private static final Glyphs SMALL = load("misc/smallfont.bin", 6, 8, 64);
    private static final Glyphs BIG   = load("misc/bigfont.bin", 8, 10, 40);

    // métriques héritées (HUD/anciens appels) = petite fonte ; BW/BH = grande fonte
    public static final int CW = 6, CH = 8;
    public static final int BW = 8, BH = 10;

    private static Glyphs load(String path, int w, int h, int nchars) {
        try {
            byte[] f = Assets.read(path);
            byte[][] glyph = new byte[nchars][];
            int maxLevel = 1;
            for (int c = 0; c < nchars; c++) {
                int g = u32(f, 4 + c * 4);
                int maskOff = u32(f, g);
                int rows = ((maskOff - 8) / 2) / PLANES;       // 7 (small) ou 9 (big)
                byte[] px = new byte[w * h];
                for (int r = 0; r < rows && r < h; r++) {
                    for (int x = 0; x < w; x++) {
                        int idx = 0;                            // indice couleur = bits des 7 plans
                        for (int p = 0; p < PLANES; p++) {
                            if ((u16(f, g + 8 + (r * PLANES + p) * 2) >> (15 - x) & 1) != 0) idx |= 1 << p;
                        }
                        px[r * w + x] = (byte) idx;
                        if (idx > maxLevel) maxLevel = idx;
                    }
                }
                glyph[c] = px;
            }
            return new Glyphs(w, h, w, glyph, maxLevel);
        } catch (Exception e) {
            System.err.println("[Font] " + path + " illisible : " + e.getMessage());
            return new Glyphs(w, h, w, new byte[nchars][w * h], 1);
        }
    }

    private static int u16(byte[] d, int o) { return ((d[o] & 0xff) << 8) | (d[o + 1] & 0xff); }
    private static int u32(byte[] d, int o) { return (u16(d, o) << 16) | u16(d, o + 2); }

    /** ASCII → index de glyphe (printmess2, gloom.s:1376) ; -1 = espace (avance seulement). */
    private static int glyphIndex(char ch, int nchars) {
        int idx;
        if (ch == ' ' || ch == '\\') return -1;
        else if (ch >= '0' && ch <= '9') idx = ch - '0';
        else if (ch == '\'') idx = 57;
        else if (ch == '!') idx = 36;
        else if (ch == '.') idx = 37;
        else if (ch == ':') idx = 38;
        else if (ch == 127) idx = 39;
        else idx = (ch & 31) + 9;                              // lettres (A→10 … Z→35), insensible casse
        return idx < nchars ? idx : -1;                        // glyphe absent (ex. ' dans bigfont) → vide
    }

    // --- rendu ---

    /** Niveau de teinte (1..max) → couleur $0RGB assombrie depuis `color` (biseau). */
    private static int shade(int color, int level, int maxLevel) {
        if (level >= maxLevel) return color;
        int r = (color >> 8) & 15, g = (color >> 4) & 15, b = color & 15;
        r = r * level / maxLevel; g = g * level / maxLevel; b = b * level / maxLevel;
        return (r << 8) | (g << 4) | b;
    }

    private static void drawWith(Glyphs gf, int fb, int fbW, int fbH, int x, int y, String s, int color) {
        for (int i = 0; i < s.length(); i++) {
            int gi = glyphIndex(s.charAt(i), gf.glyph.length);
            if (gi >= 0 && gf.glyph[gi] != null) {
                byte[] px = gf.glyph[gi];
                for (int gy = 0; gy < gf.h; gy++) {
                    int py = y + gy;
                    if (py < 0 || py >= fbH) continue;
                    for (int gx = 0; gx < gf.w; gx++) {
                        int lvl = px[gy * gf.w + gx] & 0xff;
                        if (lvl == 0) continue;                // transparent
                        int pxx = x + gx;
                        if (pxx < 0 || pxx >= fbW) continue;
                        Mem.ww(fb + (py * fbW + pxx) * 2, shade(color, lvl, gf.maxLevel));
                    }
                }
            }
            x += gf.advance;
        }
    }

    /** Petite fonte (6×8) — HUD, textes secondaires. */
    public static void draw(int fb, int fbW, int fbH, int x, int y, String s, int color) {
        drawWith(SMALL, fb, fbW, fbH, x, y, s, color);
    }

    /** Grande fonte authentique 8×10 (menus, écrans d'histoire). */
    public static void drawBig(int fb, int fbW, int fbH, int x, int y, String s, int color) {
        drawWith(BIG, fb, fbW, fbH, x, y, s, color);
    }

    public static void drawCentered(int fb, int fbW, int fbH, int y, String s, int color) {
        draw(fb, fbW, fbH, (fbW - s.length() * SMALL.advance) / 2, y, s, color);
    }

    public static void drawCenteredBig(int fb, int fbW, int fbH, int y, String s, int color) {
        drawBig(fb, fbW, fbH, (fbW - s.length() * BIG.advance) / 2, y, s, color);
    }

    public static int width(String s)    { return s.length() * SMALL.advance; }
    public static int widthBig(String s) { return s.length() * BIG.advance; }
}
