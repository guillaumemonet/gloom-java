package gloom.tools;

import gloom.Defs;
import gloom.Mem;
import gloom.Palette;
import gloom.Render;
import gloom.Vars;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Harnais 05c-1 — scène complète : murs texturés + sol + plafond (perspective).
 *
 * Pipeline : makewalls → castwalls → renderwalls → flat(sol) → flat(plafond).
 * Vérifie qu'on a des pixels de mur (centre), de sol (bas) et de plafond (haut),
 * et écrit render05c.png. La caméra a un œil au-dessus du sol (camy négatif).
 *
 * Doit afficher « TOUT OK ». `gradle renderSceneTest`.
 */
public final class RenderSceneTest {

    private static int failures = 0;
    private static final int W = 224, H = 160;

    public static void main(String[] args) throws Exception {
        Render.setupFramebuffer(W, H);
        Palette.initdarktable();
        int wallTex = setupWallTexture();
        int floorTex = setup128("floor");
        int roofTex = setup128("roof");
        setupPalette();
        Mem.wl(Vars.floor, floorTex);
        Mem.wl(Vars.roof, roofTex);
        Mem.ww(Vars.floorflag, 1);
        Mem.ww(Vars.roofflag, 1);

        // arène frame
        int arena = Mem.alloc(1 << 16);
        Mem.wl(Vars.memory, arena);
        Mem.wl(Vars.memat, arena);
        Mem.wl(Vars.shapelist, 0);

        // map synthétique : grille + 1 mur devant la caméra
        int grid = Mem.alloc(32 * 32 * 8);
        int ppnt = Mem.alloc(64);
        int poly = Mem.alloc(4 * Defs.zo_size);
        Mem.wl(Vars.map_grid, grid);
        Mem.wl(Vars.map_ppnt, ppnt);
        Mem.wl(Vars.map_poly, poly);

        int cx = 16 * 256 + 128;
        // œil au-dessus du sol : ob_y=-220, eyey=110 → camy=-110 (Y croît vers le bas)
        int player = newPlayer(cx, -220, cx, 0);

        setWall(poly, cx - 100, cx + 400, cx + 100, cx + 400, wallTex);
        int cell = grid + (16 * 32 + 16) * 8;
        Mem.ww(cell, 0); Mem.ww(cell + 2, 0); Mem.ww(ppnt, 0);

        Render.calccamera(player);
        Render.makewalls();
        if (Mem.l(Vars.outlist) == 0) {            // back-face → inverse
            setWall(poly, cx + 100, cx + 400, cx - 100, cx + 400, wallTex);
            Mem.ww(poly + Defs.zo_done, 0);
            Render.calccamera(player);
            Render.makewalls();
        }
        checkTrue("outlist non vide", Mem.l(Vars.outlist) != 0);

        Render.clearFramebuffer(0);                // fond 0 (flat ne remplit que les 0)
        Render.castwalls();
        Render.renderwalls();

        // sol : -camy, yadd -1, maxy-1 ; plafond : -255-camy, yadd +1, miny
        int camy = (short) Mem.w(Vars.camy);
        Render.flat(-camy, -1, Mem.w(Vars.maxy) - 1, floorTex);
        Render.flat(-255 - camy, 1, Mem.w(Vars.miny), roofTex);

        // stats par bandes
        int fb = Mem.l(Vars.cop);
        int top = countNonZero(fb, 0, H / 3);
        int mid = countNonZero(fb, H / 3, 2 * H / 3);
        int bot = countNonZero(fb, 2 * H / 3, H);
        System.out.println("[info] camy=" + camy + " pixels haut=" + top + " milieu=" + mid + " bas=" + bot);
        checkTrue("plafond (haut) rempli", top > W);     // > 1 ligne
        checkTrue("sol (bas) rempli", bot > W);
        checkTrue("scène (milieu) remplie", mid > 0);

        writePng(fb, "render05c.png");
        System.out.println("[info] PNG écrit : render05c.png (" + W + "x" + H + ")");

        if (failures == 0) System.out.println("TOUT OK");
        else { System.out.println(failures + " ECHEC(S)"); System.exit(1); }
    }

    // ------------------------------------------------------------------

    private static int countNonZero(int fb, int y0, int y1) {
        int n = 0;
        for (int y = y0; y < y1; y++)
            for (int x = 0; x < W; x++)
                if (Mem.uw(fb + (y * W + x) * 2) != 0) n++;
        return n;
    }

    private static int setupWallTexture() {
        int t = Mem.alloc(64 * 65);
        for (int col = 0; col < 64; col++) {
            int base = t + col * 65;
            Mem.wb(base, 0);
            for (int row = 0; row < 64; row++) {
                int v = (((col >> 3) ^ (row >> 3)) & 1) == 0 ? (1 + row * 3) : (1 + col * 3);
                Mem.wb(base + 1 + row, v & 0xff);
            }
        }
        Mem.wl(Vars.textures + 0, t);
        return 0;
    }

    /** Texture 128×128 (sol/plafond). Renvoie l'adresse. `kind` choisit le motif. */
    private static int setup128(String kind) {
        int t = Mem.alloc(128 * 128);
        boolean floor = kind.equals("floor");
        for (int x = 0; x < 128; x++) {
            for (int z = 0; z < 128; z++) {
                int chk = (((x >> 4) ^ (z >> 4)) & 1);
                // sol : verts (indices ~64-96) ; plafond : bleus (indices ~32-48)
                int v = floor ? (chk == 0 ? 70 : 90) : (chk == 0 ? 34 : 40);
                Mem.wb(t + (x << 7) + z, v);
            }
        }
        return t;
    }

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

    private static void setWall(int z, int lx, int lz, int rx, int rz, int tex) {
        Mem.ww(z + Defs.zo_lx, lx); Mem.ww(z + Defs.zo_lz, lz);
        Mem.ww(z + Defs.zo_rx, rx); Mem.ww(z + Defs.zo_rz, rz);
        Mem.ww(z + Defs.zo_done, 0); Mem.ww(z + Defs.zo_open, 0);
        Mem.ww(z + Defs.zo_sc, 1);
        for (int i = 0; i < 8; i++) Mem.wb(z + Defs.zo_t + i, tex);
    }

    private static void writePng(int fb, String name) throws Exception {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                int c = Mem.uw(fb + (y * W + x) * 2);
                int r = ((c >> 8) & 15) * 17, g = ((c >> 4) & 15) * 17, b = (c & 15) * 17;
                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        ImageIO.write(img, "png", new File(name));
    }

    private static void checkTrue(String name, boolean cond) {
        if (!cond) { System.out.println("ECHEC " + name); failures++; }
    }
}
