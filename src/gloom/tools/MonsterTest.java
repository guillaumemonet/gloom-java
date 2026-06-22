package gloom.tools;

import gloom.Defs;
import gloom.Mem;
import gloom.Objects;
import gloom.Vars;
import gloom.data.ObjInfo;
import gloom.host.LevelScene;

/**
 * Harnais : IA spécifiques des monstres. Spawne chaque type près du joueur, fait tourner la logique,
 * et vérifie que le monstre est ACTIF (se déplace et/ou tire), sans planter. `gradle monsterTest`.
 */
public final class MonsterTest {
    private static int failures = 0;
    private static final String[] NAME = {"baldy", "terra", "ghoul", "phantom", "demon", "lizard", "troll", "dragon", "deathhead"};
    private static final int[] TYPE  = {11,      12,      13,      14,        15,      21,       23,      8,        22};

    public static void main(String[] args) {
        // un seul niveau (réaliste : les sprites sont chargés une fois et mis en cache).
        LevelScene scene = new LevelScene();
        scene.init(224, 160, "com1_1", "2");
        for (int i = 0; i < TYPE.length; i++) test(scene, NAME[i], TYPE[i]);
        if (failures == 0) System.out.println("TOUT OK");
        else { System.out.println(failures + " ECHEC(S)"); System.exit(1); }
    }

    private static void test(LevelScene scene, String name, int type) {
        int p = scene.player;
        Mem.ww(p + Defs.ob_invisible, 0);
        int px = Mem.l(p + Defs.ob_x) >> 16, pz = Mem.l(p + Defs.ob_z) >> 16;

        Objects.loadanobj(type);
        Mem.wl(ObjInfo.dummy, 0);
        int ev = Mem.alloc(12);
        Mem.ww(ev, type);
        Mem.ww(ev + 2, px); Mem.ww(ev + 4, 0); Mem.ww(ev + 6, pz + 400); Mem.ww(ev + 8, 0);
        Objects.exec_addobj(ev);
        int m = Mem.l(ObjInfo.dummy);
        if (m == 0) { checkTrue(name + " spawné", false); return; }
        int mx0 = Mem.l(m + Defs.ob_x) >> 16, mz0 = Mem.l(m + Defs.ob_z) >> 16;

        scene.setInput(0, 0, 0, 0);
        boolean fired = false;
        for (int t = 0; t < 240; t++) {
            scene.tick();
            if (monsterBullet()) fired = true;
        }
        int mx1 = Mem.l(m + Defs.ob_x) >> 16, mz1 = Mem.l(m + Defs.ob_z) >> 16;
        int moved = (int) Math.sqrt((double) (mx1 - mx0) * (mx1 - mx0) + (double) (mz1 - mz0) * (mz1 - mz0));
        System.out.println("[info] " + name + " : bougé=" + moved + " a tiré=" + fired);
        checkTrue(name + " : IA active (se déplace ou tire)", moved > 4 || fired);
        if (alive(m)) gloom.Lists.killitem(Vars.objects, m);     // retire avant le type suivant
    }

    private static boolean alive(int obj) {
        int o = Mem.l(Vars.objects);
        while (Mem.l(o) != 0) { if (o == obj) return true; o = Mem.l(o); }
        return false;
    }

    private static boolean monsterBullet() {
        int o = Mem.l(Vars.objects);
        while (Mem.l(o) != 0) {
            int lg = Mem.l(o + Defs.ob_logic);
            if (lg == Objects.L_FIRE && Mem.uw(o + Defs.ob_colltype) == 4) return true;
            if (lg == Objects.L_HOMEIN) return true;              // missile à tête chercheuse (dragon)
            o = Mem.l(o);
        }
        return false;
    }

    private static void checkTrue(String name, boolean cond) {
        if (!cond) { System.out.println("ECHEC " + name); failures++; }
    }
}
