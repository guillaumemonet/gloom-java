package gloom.tools;

import gloom.Defs;
import gloom.Maths;
import gloom.Mem;
import gloom.Objects;
import gloom.Vars;
import gloom.data.ObjInfo;
import gloom.host.LevelScene;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Harnais « gore/sparks » (08c) : valide les étincelles d'impact (makesparks via calcbounce),
 * la gerbe de sang + les gibs à la mort (blowobject→bloodymess2/blowchunx), la physique du sang
 * (moveblood) et les décals de gore au sol (chunklogic, mode≠0).
 *
 * Sur com1_1 : on tire dans les murs (étincelles), on tue un marine (sang + gibs qui retombent),
 * puis avec `mode=1` les gibs laissent des décals dans la liste `gore`. Écrit gore.png. `gradle goreTest`.
 */
public final class GoreTest {

    private static int failures = 0;
    private static final int W = 224, H = 160;

    public static void main(String[] args) throws Exception {
        LevelScene scene = new LevelScene();
        scene.init(W, H, System.getProperty("map", "com1_1"), System.getProperty("tile", "2"));
        int p = scene.player;
        Mem.ww(p + Defs.ob_weapon, 0);

        // ---- Phase S : étincelles à l'impact d'une balle sur un mur -----------------------
        boolean sawSparks = false;
        for (int dir = 0; dir < 256 && !sawSparks; dir += 16) {
            Mem.ww(p + Defs.ob_rot, dir);
            Mem.wl(p + Defs.ob_rotspeed, 0);
            Mem.wb(p + Defs.ob_reloadcnt, 0);
            scene.setInput(0, 0, -1, 0); scene.tick(); scene.tick();
            scene.setInput(0, 0, 0, 0);
            for (int k = 0; k < 30 && !sawSparks; k++) {
                scene.tick();
                if (countLogic(Objects.L_SPARKS) > 0) sawSparks = true;
            }
        }
        System.out.println("[info] étincelles d'impact mur = " + sawSparks);
        checkTrue("la balle produit des étincelles sur un mur (makesparks)", sawSparks);

        // ---- Phase B : sang + gibs à la mort d'un marine ----------------------------------
        int px = Mem.l(p + Defs.ob_x) >> 16, pz = Mem.l(p + Defs.ob_z) >> 16;
        int marine = spawnMarine(px + 250, pz - 80);
        checkTrue("marine spawné", marine != 0);
        int chunks = Mem.l(marine + Defs.ob_chunks);
        System.out.println("[info] marine ob_chunks = " + (chunks != 0 ? "présent" : "absent"));

        killTarget(scene, p, marine, 600);
        checkTrue("le marine est mort", !alive(marine));
        int blood = listSize(Vars.blood);
        int gibs = countLogic(Objects.L_CHUNK);
        System.out.println("[info] à la mort : gouttes de sang = " + blood + ", gibs = " + gibs);
        checkTrue("la mort projette du sang", blood > 0);
        checkTrue("la mort projette des gibs", gibs > 0);

        // physique du sang : les gouttes retombent et disparaissent
        scene.setInput(0, 0, 0, 0);
        for (int i = 0; i < 160; i++) scene.tick();
        int bloodAfter = listSize(Vars.blood);
        System.out.println("[info] sang après chute : " + blood + " → " + bloodAfter);
        checkTrue("le sang retombe et se résorbe (moveblood)", bloodAfter < blood);

        // ---- Phase G : décals de gore au sol (mode=1) -------------------------------------
        Mem.ww(Vars.mode, 1);                                   // active les décals (chunklogic)
        int px2 = Mem.l(p + Defs.ob_x) >> 16, pz2 = Mem.l(p + Defs.ob_z) >> 16;
        int marine2 = spawnMarine(px2 + 250, pz2 - 80);
        checkTrue("marine2 spawné", marine2 != 0);
        int goreBefore = listSize(Vars.gore);
        killTarget(scene, p, marine2, 600);
        // laisse les gibs retomber au sol → décals de gore
        scene.setInput(0, 0, 0, 0);
        for (int i = 0; i < 200; i++) scene.tick();
        int goreAfter = listSize(Vars.gore);
        System.out.println("[info] décals de gore : " + goreBefore + " → " + goreAfter);
        checkTrue("les gibs laissent des décals de gore au sol", goreAfter > goreBefore);

        scene.renderFrame();
        dumpPng(Mem.l(Vars.cop), "gore.png");
        System.out.println("[info] PNG : gore.png");

        if (failures == 0) System.out.println("TOUT OK");
        else { System.out.println(failures + " ECHEC(S)"); System.exit(1); }
    }

    /** Tire jusqu'à tuer la cible (rechargement forcé à 0 pour enchaîner les tirs). */
    private static void killTarget(LevelScene scene, int p, int target, int maxTicks) {
        for (int i = 0; i < maxTicks && alive(target); i += 4) {
            aimAt(p, target);
            Mem.wb(p + Defs.ob_reloadcnt, 0);
            scene.setInput(0, 0, -1, 0); scene.tick(); scene.tick();
            scene.setInput(0, 0, 0, 0);  scene.tick(); scene.tick();
        }
    }

    private static void aimAt(int s, int t) {
        int dx = Mem.w(t + Defs.ob_x) - Mem.w(s + Defs.ob_x);
        int dz = Mem.w(t + Defs.ob_z) - Mem.w(s + Defs.ob_z);
        Mem.ww(s + Defs.ob_rot, Maths.calcangle_(dx, dz) & 0xff);
        Mem.wl(s + Defs.ob_rotspeed, 0);
    }

    private static int spawnMarine(int x, int z) {
        Objects.loadanobj(10);
        Mem.wl(ObjInfo.dummy, 0);
        int ev = Mem.alloc(12);
        Mem.ww(ev, 10);
        Mem.ww(ev + 2, x); Mem.ww(ev + 4, 0); Mem.ww(ev + 6, z); Mem.ww(ev + 8, 0);
        Objects.exec_addobj(ev);
        return Mem.l(ObjInfo.dummy);
    }

    private static boolean alive(int obj) {
        int o = Mem.l(Vars.objects);
        while (Mem.l(o) != 0) { if (o == obj) return true; o = Mem.l(o); }
        return false;
    }

    private static int countLogic(int logicId) {
        int n = 0, o = Mem.l(Vars.objects);
        while (Mem.l(o) != 0) { if (Mem.l(o + Defs.ob_logic) == logicId) n++; o = Mem.l(o); }
        return n;
    }

    private static int listSize(int header) {
        int n = 0, o = Mem.l(header);
        while (Mem.l(o) != 0) { n++; o = Mem.l(o); }
        return n;
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
