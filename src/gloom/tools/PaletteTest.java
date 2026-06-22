package gloom.tools;

import gloom.Mem;
import gloom.Palette;
import gloom.Vars;
import gloom.data.Tables;

/**
 * Harnais de cohérence du sous-système 03 (palettes & ombrage).
 *
 *  - initdarktable : comparé au calcul indépendant ((sqr[z*8]>>3)^15) depuis la
 *    table sqr réellement chargée ;
 *  - calcpalettes : palette de base synthétique → vérifie palettes[0]=base et les
 *    15 rampes assombries (canal-k borné, bit $8000) recalculées à la main ;
 *  - makeapal/makepalettes : vérifie les flashs blanc ($ffff/0) et rouge ($ff00/16).
 *
 * Doit afficher « TOUT OK ». Lancé par `gradle paletteTest`.
 */
public final class PaletteTest {

    private static int failures = 0;
    private static final int NCOLS = 16;   // palette de base synthétique

    public static void main(String[] args) {
        testDarktable();
        testCalcPalettes();
        testMakePalettes();

        if (failures == 0) {
            System.out.println("TOUT OK");
        } else {
            System.out.println(failures + " ECHEC(S)");
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------

    private static void testDarktable() {
        Palette.initdarktable();
        int sqr = Mem.l(Tables.sqr);
        int dt = Mem.l(Vars.darktable);
        // L'original remplit darktable à l'envers : a1 monte de 0 pendant que d2
        // descend de maxz-1 → darktable[z] = f(sqr[(maxz-1-z)*8]).
        for (int z : new int[]{0, 1, 2, 50, 100, 1000, Vars.maxz - 1}) {
            int src = (Vars.maxz - 1 - z) * 8;
            int expected = ((Mem.uw(sqr + src) >>> 3) ^ 15) & 0xffff;  // recalcul indépendant
            check("darktable[" + z + "]", Mem.uw(dt + z * 2), expected);
        }
    }

    // Palette de base synthétique : couleur c = ($0RGB) avec r=c, g=15-c, b=(c*3)&15.
    private static int baseColour(int c) {
        int r = c & 15;
        int g = (15 - c) & 15;
        int b = (c * 3) & 15;
        return (r << 8) | (g << 4) | b;
    }

    private static void setupBasePalette() {
        // bufA : base (NCOLS) + 15 versions assombries = 16*NCOLS mots.
        int bufA = Mem.alloc(16 * NCOLS * 2);
        int bufW = Mem.alloc(16 * NCOLS * 2);
        int bufR = Mem.alloc(16 * NCOLS * 2);
        for (int c = 0; c < NCOLS; c++) {
            Mem.ww(bufA + c * 2, baseColour(c));
        }
        Mem.wl(Vars.map_rgbs, bufA);
        Mem.wl(Vars.map_rgbsat, bufA + NCOLS * 2);   // fin de la base
        Mem.wl(Vars.map_rgbsw, bufW);
        Mem.wl(Vars.map_rgbsr, bufR);
    }

    private static void testCalcPalettes() {
        setupBasePalette();
        Palette.calcpalettes();

        int base = Mem.l(Vars.map_rgbs);
        // palettes[0] == map_rgbs
        check("palettes[0]=base", Mem.l(Vars.palettes), base);
        // map_rgbsend = base + 16*NCOLS mots
        check("map_rgbsend", Mem.l(Vars.map_rgbsend), base + 16 * NCOLS * 2);

        for (int k = 0; k < 16; k++) {
            int pal = Mem.l(Vars.palettes + k * 4);
            for (int c = 0; c < NCOLS; c++) {
                int got = Mem.uw(pal + c * 2);
                int expected = expectedShade(baseColour(c), k);
                check("pal[" + k + "][" + c + "]", got, expected);
            }
        }
    }

    /** Recalcul indépendant d'une couleur assombrie de k crans (k=0 → base). */
    private static int expectedShade(int col, int k) {
        if (k == 0) return col;   // palettes[0] pointe sur la base telle quelle
        int r = Math.max(0, ((col & 0xf00) >>> 8) - k);
        int g = Math.max(0, ((col & 0x0f0) >>> 4) - k);
        int b = Math.max(0, (col & 0x00f) - k);
        return ((r << 8) | (g << 4) | b) | 0x8000;
    }

    private static void testMakePalettes() {
        // calcpalettes a déjà tourné (map_rgbsend posé). Construit blanc/rouge.
        Palette.makepalettes();

        int base = Mem.l(Vars.map_rgbs);
        int rgbsw = Mem.l(Vars.map_rgbsw);
        int rgbsr = Mem.l(Vars.map_rgbsr);

        // palettesw[k] = map_rgbsw + (palettes[k]-map_rgbs)
        for (int k = 0; k < 16; k++) {
            int off = Mem.l(Vars.palettes + k * 4) - base;
            check("palettesw[" + k + "]", Mem.l(Vars.palettesw + k * 4), rgbsw + off);
            check("palettesr[" + k + "]", Mem.l(Vars.palettesr + k * 4), rgbsr + off);
        }

        // Vérifie le contenu pour quelques couleurs de base (blanc et rouge).
        for (int c = 0; c < NCOLS; c++) {
            int col = baseColour(c);
            check("white[" + c + "]", Mem.uw(rgbsw + c * 2), expectedGrey(col, 0xffff, 0));
            check("red[" + c + "]",   Mem.uw(rgbsr + c * 2), expectedGrey(col, 0xff00, 16));
        }
    }

    /** Recalcul indépendant de makeapal pour une couleur. */
    private static int expectedGrey(int col, int mask, int gamma) {
        int r = (col & 0xf00) >>> 8;
        int g = (col & 0x0f0) >>> 4;
        int b = (col & 0x00f);
        int sum = r + g + b + gamma;
        if (sum >= 48) sum = 47;
        int br = sum / 3;
        return ((br << 8) | (br << 4) | br) & mask;
    }

    // ------------------------------------------------------------------

    private static void check(String name, int actual, int expected) {
        if (actual != expected) {
            System.out.println("ECHEC " + name + " : attendu " + expected + " (0x" + Integer.toHexString(expected)
                    + "), obtenu " + actual + " (0x" + Integer.toHexString(actual) + ")");
            failures++;
        }
    }
}
