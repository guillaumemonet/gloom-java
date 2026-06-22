package gloom.tools;

import gloom.Defs;
import gloom.Mem;
import gloom.Objects;
import gloom.Vars;
import gloom.data.ObjInfo;
import gloom.host.LevelScene;

/**
 * Harnais : powerups. Spawne chaque pickup sur le joueur, exécute obj_loop, et vérifie que l'effet
 * est appliqué (thermo, invisibilité, bouncy, invincibilité/hyper, changement d'arme). `gradle powerupTest`.
 */
public final class PowerupTest {
    private static int failures = 0;

    public static void main(String[] args) {
        LevelScene scene = new LevelScene();
        scene.init(224, 160, System.getProperty("map", "com1_1"), System.getProperty("tile", "2"));
        int p = scene.player;
        int px = Mem.w(p + Defs.ob_x), pz = Mem.w(p + Defs.ob_z);

        // thermo (type 4) → ob_thermo > 0
        Mem.ww(p + Defs.ob_thermo, 0);
        pickup(4, px, pz);
        System.out.println("[info] thermo = " + Mem.w(p + Defs.ob_thermo));
        checkTrue("thermo glasses → ob_thermo > 0", Mem.w(p + Defs.ob_thermo) > 0);

        // invisibilité (type 6) → ob_invisible > 0
        Mem.ww(p + Defs.ob_invisible, 0);
        pickup(6, px, pz);
        System.out.println("[info] invisible = " + Mem.w(p + Defs.ob_invisible));
        checkTrue("invisibilité → ob_invisible > 0", Mem.w(p + Defs.ob_invisible) > 0);

        // bouncy (type 9) → ob_bouncecnt incrémenté
        Mem.ww(p + Defs.ob_bouncecnt, 0);
        pickup(9, px, pz);
        System.out.println("[info] bouncecnt = " + Mem.w(p + Defs.ob_bouncecnt));
        checkTrue("balles rebondissantes → ob_bouncecnt = 1", Mem.w(p + Defs.ob_bouncecnt) == 1);

        // invincibilité/hyper (type 7) → ob_hyper != 0
        Mem.ww(p + Defs.ob_hyper, 0);
        pickup(7, px, pz);
        System.out.println("[info] hyper = " + Mem.w(p + Defs.ob_hyper));
        checkTrue("invincibilité → ob_hyper activé", Mem.w(p + Defs.ob_hyper) != 0);

        // arme (type 16 = weapon1) → ob_weapon change
        Mem.ww(p + Defs.ob_weapon, 3);
        pickup(16, px, pz);
        System.out.println("[info] weapon = " + Mem.w(p + Defs.ob_weapon) + " (était 3)");
        checkTrue("pickup d'arme → ob_weapon change", Mem.w(p + Defs.ob_weapon) != 3);

        if (failures == 0) System.out.println("TOUT OK");
        else { System.out.println(failures + " ECHEC(S)"); System.exit(1); }
    }

    /** Spawne un pickup de `type` à (x,z) et exécute une frame de logique (ramassage). */
    private static void pickup(int type, int x, int z) {
        Objects.loadanobj(type);
        Mem.wl(ObjInfo.dummy, 0);
        int ev = Mem.alloc(12);
        Mem.ww(ev, type);
        Mem.ww(ev + 2, x); Mem.ww(ev + 4, 0); Mem.ww(ev + 6, z); Mem.ww(ev + 8, 0);
        Objects.exec_addobj(ev);
        Mem.wl(scenePlayerWashit(), 0);     // évite le verrou « déjà touché »
        Objects.obj_loop();
    }

    // le joueur est player1 ; on remet ob_washit à 0 pour autoriser la collision
    private static int scenePlayerWashit() {
        return Mem.l(ObjInfo.player1) + Defs.ob_washit;
    }

    private static void checkTrue(String name, boolean cond) {
        if (!cond) { System.out.println("ECHEC " + name); failures++; }
    }
}
