package gloom.tools;

import gloom.Defs;
import gloom.Mem;
import gloom.Vars;
import gloom.host.LevelScene;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Harnais d'intégration (boucle hôte, sans fenêtre) : charge une VRAIE map Gloom,
 * rend une frame complète (murs + sol + plafond) et l'exporte en PNG.
 *
 * `gradle levelRenderTest` (options -Dmap=com1_1 -Dtile=1 -Dframes=...). → level.png
 */
public final class LevelRenderTest {

    private static int W = 224, H = 160;

    public static void main(String[] args) throws Exception {
        W = Integer.getInteger("w", 224);
        H = Integer.getInteger("h", 160);
        String map = System.getProperty("map", "com1_1");
        String tile = System.getProperty("tile", "1");

        LevelScene scene = new LevelScene();
        scene.init(W, H, map, tile);

        int px = Mem.l(scene.player + Defs.ob_x) >> 16;
        int py = Mem.l(scene.player + Defs.ob_y) >> 16;
        int pz = Mem.l(scene.player + Defs.ob_z) >> 16;
        int pr = (Mem.l(scene.player + Defs.ob_rot) >> 16) & 0xff;
        System.out.println("[info] map=" + map + " tile=" + tile
                + " start x=" + px + " y=" + py + " z=" + pz + " rot=" + pr);

        // inventaire des objets spawnés + recherche du plus proche (hors joueur)
        int count = 0, nearest = 0; long nd = Long.MAX_VALUE;
        int o = Mem.l(Vars.objects);
        while (Mem.l(o) != 0) {
            if (o != scene.player) {
                count++;
                int ox = Mem.l(o + Defs.ob_x) >> 16, oz = Mem.l(o + Defs.ob_z) >> 16;
                long d = (long) (ox - px) * (ox - px) + (long) (oz - pz) * (oz - pz);
                if (d < nd) { nd = d; nearest = o; }
            }
            o = Mem.l(o);
        }
        System.out.println("[info] objets spawnés (hors joueur) = " + count);

        // approche : place la caméra plein sud de l'objet, face nord (rot=angle vers +z)
        if (nearest != 0) {
            int ox = Mem.l(nearest + Defs.ob_x) >> 16, oz = Mem.l(nearest + Defs.ob_z) >> 16;
            int cxn = ox, czn = oz - 520;
            int ang = gloom.Maths.calcangle_(ox - cxn, oz - czn) & 0xff;   // calcangle_(0,+) = nord
            Mem.wl(scene.player + Defs.ob_x, cxn << 16);
            Mem.wl(scene.player + Defs.ob_z, czn << 16);
            Mem.wl(scene.player + Defs.ob_rot, ang << 16);
            System.out.println("[info] approche objet @(" + ox + "," + oz + ") depuis (" + cxn + "," + czn
                    + ") angle " + ang);
        }

        // --- diagnostic objets ---
        if (nearest != 0) {
            System.out.println("[dbg] nearest ob_x=" + (Mem.l(nearest + Defs.ob_x) >> 16)
                    + " ob_y=" + (short) Mem.w(nearest + Defs.ob_y)
                    + " ob_z=" + (Mem.l(nearest + Defs.ob_z) >> 16)
                    + " ob_shape=" + Mem.l(nearest + Defs.ob_shape)
                    + " ob_frame=" + Mem.uw(nearest + Defs.ob_frame)
                    + " ob_render=" + Mem.l(nearest + Defs.ob_render)
                    + " ob_scale=" + Mem.uw(nearest + Defs.ob_scale));
        }
        // compte les entrées shapelist après calcscene
        Mem.wl(Vars.memat, Mem.l(Vars.memory));
        gloom.Objects.calcscene(scene.player);
        int sprites = 0, strips = 0, sl = Mem.l(Vars.shapelist);
        while (sl != 0) {
            if (Mem.l(sl + Defs.sh_shape) != 0) sprites++; else strips++;
            sl = Mem.l(sl);
        }
        System.out.println("[dbg] shapelist: sprites=" + sprites + " strips=" + strips);
        // détail de la frame + de l'entrée sh
        if (nearest != 0) {
            int anset = Mem.l(nearest + Defs.ob_shape);
            int f0 = anset + Mem.l(anset + 12);
            System.out.println("[dbg] anset maxw=" + Mem.uw(anset + 4) + " maxh=" + Mem.uw(anset + 6)
                    + " frame0off=" + Mem.l(anset + 12)
                    + " | frame xh=" + Mem.w(f0) + " yh=" + Mem.w(f0 + 2)
                    + " w=" + Mem.uw(f0 + 4) + " h=" + Mem.uw(f0 + 6));
            int s = Mem.l(Vars.shapelist);
            while (s != 0 && Mem.l(s + Defs.sh_shape) == 0) s = Mem.l(s);
            if (s != 0) System.out.println("[dbg] sh_x=" + (short) Mem.w(s + Defs.sh_x)
                    + " sh_y=" + (short) Mem.w(s + Defs.sh_y) + " sh_z=" + (short) Mem.w(s + Defs.sh_z)
                    + " sh_scale=" + Mem.uw(s + Defs.sh_scale) + " sh_render=" + Mem.l(s + Defs.sh_render)
                    + " | midx=" + Mem.w(Vars.midx) + " midy=" + Mem.w(Vars.midy));
        }

        scene.renderFrame();

        int fb = Mem.l(Vars.cop);
        int painted = 0;
        for (int i = 0; i < W * H; i++) if (Mem.uw(fb + i * 2) != 0) painted++;
        System.out.println("[info] pixels non-noirs = " + painted + " / " + (W * H));

        writePng(fb, "level.png");
        System.out.println("[info] PNG écrit : level.png (" + W + "x" + H + ")");

        if (painted > W * H / 10) System.out.println("TOUT OK");
        else { System.out.println("ECHEC : image quasi vide (caméra hors géométrie ?)"); System.exit(1); }
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
}
