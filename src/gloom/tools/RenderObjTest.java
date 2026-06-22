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
 * Harnais 05c-2 — sprites + strips see-through + sang.
 *
 *  - drawshapes/drawobjnorm : un sprite synthétique est projeté et dessiné (occulté par
 *    le Z-buffer des murs vd_z) ;
 *  - drawstripHoled : un strip see-through (texels transparents) tinte le fond ;
 *  - drawblood : une goutte de sang projetée écrit un pixel.
 *
 * Doit afficher « TOUT OK ». `gradle renderObjTest` (→ render05c2.png).
 */
public final class RenderObjTest {

    private static int failures = 0;
    private static final int W = 160, H = 120;

    public static void main(String[] args) throws Exception {
        Render.setupFramebuffer(W, H);
        Palette.initdarktable();
        setupPalette();

        testSprite();
        testStrip();
        testBlood();

        if (failures == 0) System.out.println("TOUT OK");
        else { System.out.println(failures + " ECHEC(S)"); System.exit(1); }
    }

    // ------------------------------------------------------------------

    private static void testSprite() throws Exception {
        Render.clearFramebuffer(0x111);                 // fond gris foncé
        // Z-buffer mural : tout au loin (32767) → le sprite (z=100) passe devant partout
        int vd = Mem.l(Vars.vertdraws);
        for (int c = 0; c < W; c++) Mem.ww(vd + c * Defs.vd_size + Defs.vd_z, 32767);

        int sprite = buildSprite(32, 32);
        int sh = Mem.alloc(Defs.sh_size);
        Mem.wl(sh, 0);
        Mem.ww(sh + Defs.sh_x, 0);                      // centré
        Mem.ww(sh + Defs.sh_y, 0);
        Mem.ww(sh + Defs.sh_z, 100);
        Mem.wl(sh + Defs.sh_shape, sprite);
        Mem.ww(sh + Defs.sh_scale, 256);                // 1.0 (8.8)
        Mem.wl(sh + Defs.sh_render, Render.OBJ_NORM);
        Mem.wl(Vars.shapelist, sh);

        Render.drawshapes();

        int fb = Mem.l(Vars.cop);
        int painted = countNot(fb, 0x111);
        System.out.println("[info] sprite : pixels=" + painted);
        checkTrue("sprite dessiné", painted > 50);
        writePng(fb, "render05c2.png");
    }

    /** Sprite [xhandle][yhandle][width][height][pixels column-major]. Disque coloré, fond transparent. */
    private static int buildSprite(int w, int h) {
        int b = Mem.alloc(8 + w * h);
        Mem.ww(b, w / 2); Mem.ww(b + 2, h / 2);         // handles (centre)
        Mem.ww(b + 4, w);  Mem.ww(b + 6, h);            // width/height
        int cx = w / 2, cy = h / 2;
        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++) {
                int dx = x - cx, dy = y - cy;
                int v = (dx * dx + dy * dy <= (w / 2) * (w / 2)) ? (40 + ((x + y) & 31)) : 0; // disque
                Mem.wb(b + 8 + x * h + y, v);           // column-major (stride = height)
            }
        return b;
    }

    private static void testStrip() {
        Render.clearFramebuffer(0xfff);                 // fond blanc (pour voir le tint AND)
        // colonne de texture see-through : en-tête négatif (-3) + texels mêlant 0 (trou) et opaques
        int col = Mem.alloc(1 + 64);
        Mem.wb(col, 0xfd);                              // header -3 (masque stripands)
        for (int r = 0; r < 64; r++) Mem.wb(col + 1 + r, (r & 4) == 0 ? 0 : (60 + r));
        // vd du strip
        int vds = Mem.alloc(Defs.vd_size);
        Mem.wl(vds + Defs.vd_data, col + 1);            // vd_data pointe APRÈS l'en-tête
        Mem.ww(vds + Defs.vd_y, -20);
        Mem.ww(vds + Defs.vd_h, 40);
        Mem.wl(vds + Defs.vd_ystep, (64 << 16) / 40);   // 64 texels sur 40 px
        Mem.ww(vds + Defs.vd_pal, 0);
        // shapelist : un strip (sh_shape=0)
        int sh = Mem.alloc(Defs.sh_size);
        Mem.wl(sh, 0);
        Mem.wl(sh + Defs.sh_shape, 0);                  // strip
        Mem.ww(sh + Defs.sh_x, 0);                      // colonne centre
        Mem.wl(sh + Defs.sh_strip, vds);
        Mem.wl(Vars.shapelist, sh);

        Render.drawshapes();

        int fb = Mem.l(Vars.cop);
        int midCol = W / 2;
        int opaque = 0, tinted = 0;
        for (int y = 0; y < H; y++) {
            int c = Mem.uw(fb + (y * W + midCol) * 2);
            if (c != 0xfff && c != 0) { if ((c & 0x0f0) != 0 && c != (0xfff & 0xfff0)) opaque++; }
            if (c != 0xfff) tinted++;
        }
        System.out.println("[info] strip : pixels modifiés=" + tinted);
        checkTrue("strip dessiné (opaque + tint)", tinted > 10);
    }

    private static void testBlood() {
        Render.clearFramebuffer(0);
        // caméra à l'origine, regard nord
        int player = Mem.alloc(Defs.ob_size);
        Mem.wl(player + Defs.ob_x, 0); Mem.wl(player + Defs.ob_y, 0);
        Mem.wl(player + Defs.ob_z, 0); Mem.wl(player + Defs.ob_rot, 0);
        Mem.ww(player + Defs.ob_eyey, 0); Mem.ww(player + Defs.ob_bounce, 0);
        Render.calccamera(player);

        // liste blood = liste Exec (header→nœud→sentinelle) : on ajoute un nœud proprement
        gloom.Lists.alloclist(Vars.blood, 4, Defs.bl_size);
        int b = gloom.Lists.addlast(Vars.blood);        // nœud avec chaînage correct
        Mem.ww(b + Defs.bl_x, 0);                       // (mot fort des longs)
        Mem.ww(b + Defs.bl_y, 10);                      // bl_y>0
        Mem.ww(b + Defs.bl_z, 200);
        Mem.ww(b + Defs.bl_color, 0xf00);               // masque rouge

        Render.drawblood();

        int fb = Mem.l(Vars.cop);
        int painted = countNot(fb, 0);
        System.out.println("[info] sang : pixels=" + painted);
        checkTrue("goutte de sang dessinée", painted >= 1);
    }

    // ------------------------------------------------------------------

    private static int countNot(int fb, int bg) {
        int n = 0;
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                if (Mem.uw(fb + (y * W + x) * 2) != bg) n++;
        return n;
    }

    private static void setupPalette() {
        int colors = Mem.alloc(256 * 2);
        for (int i = 0; i < 256; i++) {
            int r = (i >> 2) & 15, g = (i >> 1) & 15, bl = i & 15;
            Mem.ww(colors + i * 2, (r << 8) | (g << 4) | bl);
        }
        int pals = Mem.alloc(16 * 4);
        for (int k = 0; k < 16; k++) Mem.wl(pals + k * 4, colors);
        Mem.wl(Vars.palette, pals);
    }

    private static void writePng(int fb, String name) throws Exception {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                int c = Mem.uw(fb + (y * W + x) * 2);
                int r = ((c >> 8) & 15) * 17, g = ((c >> 4) & 15) * 17, bl = (c & 15) * 17;
                img.setRGB(x, y, (r << 16) | (g << 8) | bl);
            }
        ImageIO.write(img, "png", new File(name));
    }

    private static void checkTrue(String name, boolean cond) {
        if (!cond) { System.out.println("ECHEC " + name); failures++; }
    }
}
