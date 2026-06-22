package gloom.tools;

import gloom.Defs;
import gloom.Mem;
import gloom.Palette;
import gloom.Render;
import gloom.Vars;
import gloom.data.Tables;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Harnais 05b — rasterisation des murs vers framebuffer.
 *
 * Pipeline complet sur une scène synthétique (1 mur texturé devant la caméra) :
 * makewalls → castwalls → renderwalls. Vérifie qu'une bande verticale de pixels
 * non-fond est dessinée au centre, et écrit un PNG (render05b.png) pour contrôle visuel.
 *
 * Doit afficher « TOUT OK ». `gradle renderWallTest`.
 */
public final class RenderWallTest {

    private static int failures = 0;
    private static final int W = 224, H = 160;

    public static void main(String[] args) throws Exception {
        Render.setupFramebuffer(W, H);
        Palette.initdarktable();
        int tex = setupTexture();
        setupPalette();

        // --- map synthétique : grille + 1 zone devant la caméra ---
        int grid = Mem.alloc(32 * 32 * 8);
        int ppnt = Mem.alloc(64);
        int poly = Mem.alloc(4 * Defs.zo_size);
        Mem.wl(Vars.map_grid, grid);
        Mem.wl(Vars.map_ppnt, ppnt);
        Mem.wl(Vars.map_poly, poly);

        // arène mémoire de frame (makewalls/castwalls y allouent wl/vd/sh)
        int arena = Mem.alloc(1 << 16);
        Mem.wl(Vars.memory, arena);
        Mem.wl(Vars.memat, arena);
        Mem.wl(Vars.shapelist, 0);

        int cx = 16 * 256 + 128;                 // caméra en cellule (16,16)
        int player = newPlayer(cx, 0, cx, 0);

        int z0 = poly;
        // mur orienté vers la caméra (sera inversé si back-face)
        setWallZone(z0, cx + 100, cx + 500, cx - 100, cx + 500, tex);
        int cell = grid + (16 * 32 + 16) * 8;
        Mem.ww(cell, 0);          // 1 poly (count-1=0)
        Mem.ww(cell + 2, 0);      // offset ppnt
        Mem.ww(ppnt, 0);          // poly #0

        Render.calccamera(player);
        Render.makewalls();
        if (Mem.l(Vars.outlist) == 0) {           // back-face → inverse le winding
            setWallZone(z0, cx - 100, cx + 500, cx + 100, cx + 500, tex);
            Mem.ww(z0 + Defs.zo_done, 0);
            Render.calccamera(player);
            Render.makewalls();
        }
        checkTrue("outlist non vide", Mem.l(Vars.outlist) != 0);
        dumpWall();

        Render.clearFramebuffer(0x004);           // fond bleu nuit
        Render.castwalls();
        Render.renderwalls();

        // compte les pixels non-fond
        int fb = Mem.l(Vars.cop);
        int painted = 0, cols = 0;
        for (int x = 0; x < W; x++) {
            boolean colPainted = false;
            for (int y = 0; y < H; y++) {
                if (Mem.uw(fb + (y * W + x) * 2) != 0x004) { painted++; colPainted = true; }
            }
            if (colPainted) cols++;
        }
        System.out.println("[info] pixels peints=" + painted + " sur " + cols + " colonnes");
        checkTrue("des pixels de mur dessinés", painted > 0);
        checkTrue("plusieurs colonnes peintes", cols >= 3);

        writePng(fb, "render05b.png");
        System.out.println("[info] PNG écrit : render05b.png (" + W + "x" + H + ")");

        if (failures == 0) System.out.println("TOUT OK");
        else { System.out.println(failures + " ECHEC(S)"); System.exit(1); }
    }

    // ------------------------------------------------------------------

    /** Texture 64 colonnes × (1 octet en-tête + 64 texels). Renvoie l'index dans textures[]. */
    private static int setupTexture() {
        int t = Mem.alloc(64 * 65);
        for (int col = 0; col < 64; col++) {
            int base = t + col * 65;
            Mem.wb(base, 0);                      // en-tête = 0 → colonne solide
            for (int row = 0; row < 64; row++) {
                // damier + dégradé pour un visuel lisible (indices 1..255)
                int v = (((col >> 3) ^ (row >> 3)) & 1) == 0 ? (1 + row * 3) : (1 + col * 3);
                Mem.wb(base + 1 + row, v & 0xff);
            }
        }
        Mem.wl(Vars.textures + 0, t);             // textures[0]
        return 0;
    }

    /** Palette : une table de 256 couleurs $0RGB ; palettes[0..15] pointent dessus. */
    private static void setupPalette() {
        int colors = Mem.alloc(256 * 2);
        for (int i = 0; i < 256; i++) {
            int r = (i >> 2) & 15, g = (i >> 1) & 15, b = i & 15;
            Mem.ww(colors + i * 2, (r << 8) | (g << 4) | b);
        }
        int pals = Mem.alloc(16 * 4);
        for (int k = 0; k < 16; k++) Mem.wl(pals + k * 4, colors);
        Mem.wl(Vars.palette, pals);
    }

    private static int newPlayer(int x, int y, int z, int rot) {
        int o = Mem.alloc(Defs.ob_size);
        Mem.wl(o + Defs.ob_x, x << 16);
        Mem.wl(o + Defs.ob_y, y << 16);
        Mem.wl(o + Defs.ob_z, z << 16);
        Mem.wl(o + Defs.ob_rot, rot << 16);
        Mem.ww(o + Defs.ob_eyey, 110);
        Mem.ww(o + Defs.ob_bounce, 0);
        return o;
    }

    private static void setWallZone(int z, int lx, int lz, int rx, int rz, int texIdx) {
        Mem.ww(z + Defs.zo_lx, lx); Mem.ww(z + Defs.zo_lz, lz);
        Mem.ww(z + Defs.zo_rx, rx); Mem.ww(z + Defs.zo_rz, rz);
        Mem.ww(z + Defs.zo_done, 0); Mem.ww(z + Defs.zo_open, 0);
        Mem.ww(z + Defs.zo_sc, 1);
        for (int i = 0; i < 8; i++) Mem.wb(z + Defs.zo_t + i, texIdx);
    }

    private static void dumpWall() {
        int wl = Mem.l(Vars.outlist);
        if (wl == 0) return;
        System.out.println("[info] wl lsx=" + (short) Mem.w(wl + Defs.wl_lsx)
                + " rsx=" + (short) Mem.w(wl + Defs.wl_rsx)
                + " nz=" + (short) Mem.w(wl + Defs.wl_nz)
                + " fz=" + (short) Mem.w(wl + Defs.wl_fz));
    }

    private static void writePng(int fb, String name) throws Exception {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int c = Mem.uw(fb + (y * W + x) * 2);
                int r = ((c >> 8) & 15) * 17, g = ((c >> 4) & 15) * 17, b = (c & 15) * 17;
                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        ImageIO.write(img, "png", new File(name));
    }

    private static void checkTrue(String name, boolean cond) {
        if (!cond) { System.out.println("ECHEC " + name); failures++; }
    }
}
