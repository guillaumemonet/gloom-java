package gloom.tools;

import gloom.Defs;
import gloom.Mem;
import gloom.Player;
import gloom.Vars;
import gloom.host.LevelScene;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Harnais 07/08b : déplacement du joueur (avec collision murs) + mouvement des monstres.
 *
 * Avance le joueur N frames, vérifie qu'il s'est déplacé sans rester coincé dans un mur, et
 * que des objets (monstres) ont bougé via leur IA. Écrit move.png. `gradle moveTest`.
 */
public final class MoveTest {

    private static int failures = 0;
    private static final int W = 224, H = 160;

    public static void main(String[] args) throws Exception {
        LevelScene scene = new LevelScene();
        scene.init(W, H, System.getProperty("map", "com1_1"), System.getProperty("tile", "2"));
        int p = scene.player;

        int x0 = Mem.l(p + Defs.ob_x) >> 16, z0 = Mem.l(p + Defs.ob_z) >> 16;
        Map<Integer, int[]> before = snapshot(p);

        // avance tout droit (joyy=-1) pendant 240 frames d'affichage (120 frames de logique)
        scene.setInput(0, -1, 0, 0);
        for (int i = 0; i < 240; i++) scene.tick();

        int x1 = Mem.l(p + Defs.ob_x) >> 16, z1 = Mem.l(p + Defs.ob_z) >> 16;
        int dist = (int) Math.sqrt((double) (x1 - x0) * (x1 - x0) + (double) (z1 - z0) * (z1 - z0));
        System.out.println("[info] joueur (" + x0 + "," + z0 + ") → (" + x1 + "," + z1 + ") dist=" + dist);

        // joueur pas coincé dans un mur ?
        Player.d6 = Mem.l(p + Defs.ob_x);
        Player.d7 = Mem.l(p + Defs.ob_z);
        boolean stuck = Player.checknewslow(p);
        checkTrue("joueur pas dans un mur", !stuck);
        checkTrue("joueur a avancé", dist > 8);

        // le joueur a pu ramasser un powerup d'invisibilité en avançant (08 powerups) — on le retire
        // pour tester la traque pure (joueur invisible → visée des monstres imprécise, cf. pickcalc).
        Mem.ww(p + Defs.ob_invisible, 0);

        // les objets de com1_1 sont des items (immobiles) → on spawne un marine pour tester l'IA.
        // On le place dans le couloir parcouru par le joueur (même X, au nord) : chemin dégagé,
        // donc la traque est robuste (pas sensible au pathing/RNG d'un spawn coincé derrière un mur).
        gloom.Objects.loadanobj(10);                     // sprite marine
        Mem.wl(gloom.data.ObjInfo.dummy, 0);             // libère le slot de spawn
        int ev = Mem.alloc(12);
        Mem.ww(ev, 10);                                  // type = marine
        Mem.ww(ev + 2, x1); Mem.ww(ev + 4, 0); Mem.ww(ev + 6, z1 + 400); Mem.ww(ev + 8, 0);
        gloom.Objects.exec_addobj(ev);
        int marine = Mem.l(gloom.data.ObjInfo.dummy);
        checkTrue("marine spawné", marine != 0);
        int mx0 = Mem.l(marine + Defs.ob_x) >> 16, mz0 = Mem.l(marine + Defs.ob_z) >> 16;
        long d0p = dist2(mx0, mz0, x1, z1);
        scene.setInput(0, 0, 0, 0);                      // joueur immobile
        for (int i = 0; i < 200; i++) scene.tick();
        int mx1 = Mem.l(marine + Defs.ob_x) >> 16, mz1 = Mem.l(marine + Defs.ob_z) >> 16;
        long d1p = dist2(mx1, mz1, Mem.l(p + Defs.ob_x) >> 16, Mem.l(p + Defs.ob_z) >> 16);
        int mdist = (int) Math.sqrt((double) dist2(mx0, mz0, mx1, mz1));
        System.out.println("[info] marine (" + mx0 + "," + mz0 + ") → (" + mx1 + "," + mz1
                + ") dist=" + mdist + " | dist au joueur " + (int) Math.sqrt(d0p) + "→" + (int) Math.sqrt(d1p));
        checkTrue("le marine a bougé", mdist > 8);
        checkTrue("le marine s'est rapproché du joueur", d1p < d0p);

        scene.renderFrame();
        dumpPng(Mem.l(Vars.cop), "move.png");
        System.out.println("[info] PNG : move.png");

        if (failures == 0) System.out.println("TOUT OK");
        else { System.out.println(failures + " ECHEC(S)"); System.exit(1); }
    }

    private static long dist2(int ax, int az, int bx, int bz) {
        return (long) (ax - bx) * (ax - bx) + (long) (az - bz) * (az - bz);
    }

    /** Positions (x,z entiers) de tous les objets sauf le joueur. */
    private static Map<Integer, int[]> snapshot(int player) {
        Map<Integer, int[]> m = new HashMap<>();
        int o = Mem.l(Vars.objects);
        while (Mem.l(o) != 0) {
            if (o != player) m.put(o, new int[]{Mem.l(o + Defs.ob_x) >> 16, Mem.l(o + Defs.ob_z) >> 16});
            o = Mem.l(o);
        }
        return m;
    }

    private static void dumpPng(int fb, String name) throws Exception {
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
