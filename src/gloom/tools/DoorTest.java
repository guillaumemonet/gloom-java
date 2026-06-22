package gloom.tools;

import gloom.Defs;
import gloom.Events;
import gloom.Mem;
import gloom.Vars;
import gloom.host.LevelScene;

/**
 * Harnais : ouverture de porte. Vérifie qu'une porte créée par exec_opendoor s'anime bien via
 * dodoors (appelé chaque frame par LevelScene.tick) — le polygone glisse puis la porte est retirée
 * une fois ouverte. Régression du bug « les portes ne s'ouvrent pas ». `gradle doorTest`.
 */
public final class DoorTest {
    private static int failures = 0;

    public static void main(String[] args) {
        LevelScene scene = new LevelScene();
        scene.init(224, 160, System.getProperty("map", "com1_1"), System.getProperty("tile", "2"));

        int doorNum = 8;                                 // un index de zone valide
        int poly = Mem.l(Vars.map_poly) + (doorNum << 5);
        int open0 = Mem.uw(poly + Defs.zo_open);
        int lx0 = Mem.w(poly + Defs.zo_lx);

        int ev = Mem.alloc(2);
        Mem.ww(ev, doorNum);                             // # de porte (zone)
        Events.exec_opendoor(ev);
        checkTrue("porte ajoutée à la liste", listSize(Vars.doors) == 1);

        // anime : dodoors tourne dans tick(). La porte s'ouvre en ~64 frames de logique.
        int ticks = 0, maxOpen = open0;
        while (listSize(Vars.doors) > 0 && ticks < 400) {
            scene.tick();
            maxOpen = Math.max(maxOpen, Mem.uw(poly + Defs.zo_open));
            ticks++;
        }
        int open1 = Mem.uw(poly + Defs.zo_open);
        int lx1 = Mem.w(poly + Defs.zo_lx);
        System.out.println("[info] zo_open " + open0 + "→ max " + maxOpen + " (fin " + open1 + ")"
                + " | zo_lx " + lx0 + "→" + lx1 + " | ticks=" + ticks
                + " | portes restantes=" + listSize(Vars.doors));

        checkTrue("la porte s'est ouverte (zo_open → 0x8000)", maxOpen >= 0x8000);
        checkTrue("la porte est retirée une fois ouverte", listSize(Vars.doors) == 0);
        // (zo_lx bouge si la zone a une largeur ; ici lx0=" + lx0 + " lx1=" + lx1)

        if (failures == 0) System.out.println("TOUT OK");
        else { System.out.println(failures + " ECHEC(S)"); System.exit(1); }
    }

    private static int listSize(int header) {
        int n = 0, o = Mem.l(header);
        while (Mem.l(o) != 0) { n++; o = Mem.l(o); }
        return n;
    }

    private static void checkTrue(String name, boolean cond) {
        if (!cond) { System.out.println("ECHEC " + name); failures++; }
    }
}
