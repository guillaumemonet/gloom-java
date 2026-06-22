package gloom.tools;

import gloom.Defs;
import gloom.Lists;
import gloom.Mem;
import gloom.Objects;
import gloom.Vars;
import gloom.data.ObjInfo;
import gloom.host.LevelScene;

/**
 * Harnais : comportements SPÉCIAUX des boss.
 *  - dragon : à la mort (dragondead), compte à rebours → finished=3 (victoire) ;
 *  - deathhead : aspiration d'âme (deathsuck) → particules d'âme + sucking, puis restauration.
 * `gradle bossTest`.
 */
public final class BossTest {
    private static int failures = 0;

    public static void main(String[] args) {
        LevelScene scene = new LevelScene();
        scene.init(224, 160, "com1_1", "2");
        int player = scene.player;

        testDragonDeath(scene);
        testSoulSuck(scene, player);

        if (failures == 0) System.out.println("TOUT OK");
        else { System.out.println(failures + " ECHEC(S)"); System.exit(1); }
    }

    /** Le dragon mort (dragondead) doit, après son délai, poser finished=3 (fin de partie gagnée). */
    private static void testDragonDeath(LevelScene scene) {
        int d = spawn(scene, 8);                                   // dragon
        if (d == 0) { check("dragon spawné", false); return; }
        Mem.wl(d + Defs.ob_logic, Objects.L_DRAGONDEAD);
        Mem.ww(d + Defs.ob_delay, 3);
        Mem.ww(Vars.finished, 0);
        for (int t = 0; t < 5; t++) Objects.dragondead(d);
        System.out.println("[info] dragon mort → finished=" + Mem.w(Vars.finished));
        check("dragondead pose finished=3 (victoire)", Mem.w(Vars.finished) == 3);
        if (alive(d)) Lists.killitem(Vars.objects, d);
        Mem.ww(Vars.finished, 0);
    }

    /** La deathhead en aspiration (deathsuck) crée des particules d'âme et arme sucking ; à la fin, le coupe. */
    private static void testSoulSuck(LevelScene scene, int player) {
        int dh = spawn(scene, 22);                                 // deathhead
        if (dh == 0) { check("deathhead spawné", false); return; }
        // arme l'aspiration manuellement (hurtdeath est déclenché par une touche en jeu)
        Mem.wl(Vars.sucking, player);
        Mem.wl(Vars.sucker, dh);
        Mem.wl(dh + Defs.ob_oldlogic, Objects.L_DEATHHEAD);
        Mem.ww(dh + Defs.ob_oldrot, 0);
        Mem.wl(dh + Defs.ob_logic, Objects.L_DEATHSUCK);
        Mem.ww(dh + Defs.ob_delay, 8);

        int before = count(Vars.blood);
        Objects.deathsuck(dh);                                     // un tick d'aspiration
        int after = count(Vars.blood);
        System.out.println("[info] particules d'âme : " + before + " → " + after);
        check("deathsuck crée des particules d'âme", after > before);

        for (int t = 0; t < 12; t++) Objects.deathsuck(dh);        // jusqu'à expiration du délai
        System.out.println("[info] après aspiration : sucking=" + Mem.l(Vars.sucking));
        check("fin d'aspiration → sucking libéré", Mem.l(Vars.sucking) == 0);
        if (alive(dh)) Lists.killitem(Vars.objects, dh);
    }

    private static int spawn(LevelScene scene, int type) {
        Objects.loadanobj(type);
        Mem.wl(ObjInfo.dummy, 0);
        int px = Mem.l(scene.player + Defs.ob_x) >> 16, pz = Mem.l(scene.player + Defs.ob_z) >> 16;
        int ev = Mem.alloc(12);
        Mem.ww(ev, type);
        Mem.ww(ev + 2, px); Mem.ww(ev + 4, 0); Mem.ww(ev + 6, pz + 400); Mem.ww(ev + 8, 0);
        Objects.exec_addobj(ev);
        return Mem.l(ObjInfo.dummy);
    }

    private static int count(int listHead) {
        int n = 0, o = Mem.l(listHead);
        while (Mem.l(o) != 0) { n++; o = Mem.l(o); }
        return n;
    }

    private static boolean alive(int obj) {
        int o = Mem.l(Vars.objects);
        while (Mem.l(o) != 0) { if (o == obj) return true; o = Mem.l(o); }
        return false;
    }

    private static void check(String name, boolean cond) {
        if (!cond) { System.out.println("ECHEC " + name); failures++; }
    }
}
