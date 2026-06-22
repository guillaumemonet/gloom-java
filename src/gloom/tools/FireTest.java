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
 * Harnais « tir » : valide le tir du joueur (checkfire→shoot→firelogic), les dégâts/mort par
 * balle (collision balle↔monstre dans obj_loop) et le tir des monstres (fire1).
 *
 * Sur com1_1 : on amène le joueur dans une zone dégagée, on spawne un marine, on vise et on
 * tire — la balle apparaît, vole, touche le marine et le tue. On vérifie aussi qu'un marine
 * laissé tranquille finit par tirer (balle de type monstre). Écrit fire.png. `gradle fireTest`.
 */
public final class FireTest {

    private static int failures = 0;
    private static final int W = 224, H = 160;

    public static void main(String[] args) throws Exception {
        LevelScene scene = new LevelScene();
        scene.init(W, H, System.getProperty("map", "com1_1"), System.getProperty("tile", "2"));
        int p = scene.player;

        // amène le joueur dans une zone dégagée (comme MoveTest)
        scene.setInput(0, -1, 0, 0);
        for (int i = 0; i < 240; i++) scene.tick();
        scene.setInput(0, 0, 0, 0);

        int px = Mem.l(p + Defs.ob_x) >> 16, pz = Mem.l(p + Defs.ob_z) >> 16;

        // ---- Phase A : le tir du joueur crée une balle ----------------------------------
        int marine = spawnMarine(px + 300, pz - 100);
        checkTrue("marine spawné", marine != 0);
        aimAt(p, marine);
        Mem.ww(p + Defs.ob_weapon, 0);                          // arme 0 (dégât 1)

        int before = countBullets(-1);
        fireOneShot(scene, p, marine);                          // un appui = une balle
        int after = countBullets(-1);
        System.out.println("[info] balles : " + before + " → " + after);
        checkTrue("le tir a créé une balle", after > before);

        // la balle est de type joueur (colltype 1), pas une balle de monstre (4)
        checkTrue("balle = type joueur (colltype 1)", findBullet(1) != 0);

        // ---- Phase B : la balle touche et tue le marine ---------------------------------
        int hp0 = Mem.w(marine + Defs.ob_hitpoints);
        System.out.println("[info] marine PV initiaux = " + hp0);
        boolean dead = false;
        int minHp = hp0;
        for (int i = 0; i < 400 && !dead; i++) {
            aimAt(p, marine);
            fireOneShot(scene, p, marine);                      // appui/relâche → une balle par cycle
            if (!alive(marine)) { dead = true; break; }
            minHp = Math.min(minHp, Mem.w(marine + Defs.ob_hitpoints));
        }
        System.out.println("[info] marine PV mini observés = " + minHp + " | mort = " + dead);
        checkTrue("la balle a infligé des dégâts au marine", minHp < hp0 || dead);
        checkTrue("le marine a fini par mourir", dead);

        // ---- Phase C : un monstre tranquille tire (fire1 → balle de type monstre) --------
        // marine placé loin pour que l'événement détecté soit bien une BALLE (pas du corps-à-corps).
        int px2 = Mem.l(p + Defs.ob_x) >> 16, pz2 = Mem.l(p + Defs.ob_z) >> 16;
        int marine2 = spawnMarine(px2 + 600, pz2 - 120);
        checkTrue("marine2 spawné", marine2 != 0);
        scene.setInput(0, 0, 0, 0);                             // joueur immobile, ne tire pas
        int monBullet = 0;
        for (int i = 0; i < 300 && monBullet == 0; i++) {
            scene.tick();
            monBullet = findBullet(4);                          // balle de type monstre (colltype 4)
        }
        System.out.println("[info] balle de monstre (colltype 4) repérée = " + (monBullet != 0));
        checkTrue("le monstre a tiré une balle (fire1)", monBullet != 0);

        scene.renderFrame();
        dumpPng(Mem.l(Vars.cop), "fire.png");
        System.out.println("[info] PNG : fire.png");

        if (failures == 0) System.out.println("TOUT OK");
        else { System.out.println(failures + " ECHEC(S)"); System.exit(1); }
    }

    /** Vise l'objet `t` depuis `s` : ob_rot = calcangle, rotspeed annulé pour neutraliser rotplayer. */
    private static void aimAt(int s, int t) {
        int dx = Mem.w(t + Defs.ob_x) - Mem.w(s + Defs.ob_x);
        int dz = Mem.w(t + Defs.ob_z) - Mem.w(s + Defs.ob_z);
        Mem.ww(s + Defs.ob_rot, Maths.calcangle_(dx, dz) & 0xff);
        Mem.wl(s + Defs.ob_rotspeed, 0);
    }

    /** Un appui (joyb=-1) puis un relâché (joyb=0) — un seul tir grâce à checkfireb (front montant). */
    private static void fireOneShot(LevelScene scene, int p, int marine) {
        aimAt(p, marine);
        scene.setInput(0, 0, -1, 0); scene.tick(); scene.tick(); // appui (2 ticks = 1 frame logique)
        scene.setInput(0, 0, 0, 0);  scene.tick(); scene.tick(); // relâché → laisse la balle voler
    }

    private static int spawnMarine(int x, int z) {
        Objects.loadanobj(10);                                  // sprite marine
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

    /** Nombre de balles (ob_logic == L_FIRE) ; colltype<0 = toutes. */
    private static int countBullets(int colltype) {
        int n = 0, o = Mem.l(Vars.objects);
        while (Mem.l(o) != 0) {
            if (Mem.l(o + Defs.ob_logic) == Objects.L_FIRE
                    && (colltype < 0 || Mem.uw(o + Defs.ob_colltype) == colltype)) n++;
            o = Mem.l(o);
        }
        return n;
    }

    /** Première balle de colltype donné (0 si aucune). */
    private static int findBullet(int colltype) {
        int o = Mem.l(Vars.objects);
        while (Mem.l(o) != 0) {
            if (Mem.l(o + Defs.ob_logic) == Objects.L_FIRE && Mem.uw(o + Defs.ob_colltype) == colltype) return o;
            o = Mem.l(o);
        }
        return 0;
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
