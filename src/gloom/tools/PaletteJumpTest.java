package gloom.tools;

import gloom.Defs;
import gloom.Mem;
import gloom.Vars;
import gloom.data.ObjInfo;
import gloom.host.LevelScene;

/**
 * Harnais : la palette des objets ne doit pas « sauter » entre niveaux.
 *
 * On charge la MÊME map sous deux tuiles différentes (txts/*1 puis *2) — donc deux palettes
 * de tuile de tailles différentes, ce qui décale les index de palette des monstres. Un sprite
 * de monstre mis en cache garderait des index obsolètes (= saut de palette). Avec le rechargement
 * par niveau (ObjInfo.clearMonsterShapes), le marine est re-remappé : sa COULEUR réelle résolue
 * via map_rgbs doit être identique sous les deux tuiles. `gradle paletteJumpTest`.
 */
public final class PaletteJumpTest {
    private static int failures = 0;

    public static void main(String[] args) {
        int colorTile1 = loadAndSampleMarine("1");
        int descA = Mem.l(ObjInfo.marine);
        int colorTile2 = loadAndSampleMarine("2");
        int descB = Mem.l(ObjInfo.marine);

        System.out.printf("[info] marine couleur tuile1=$%03x tuile2=$%03x ; descA=%d descB=%d%n",
                colorTile1, colorTile2, descA, descB);
        check("marine rechargé entre niveaux (descripteur différent)", descA != descB);
        check("couleur réelle du marine identique sous 2 tuiles (pas de saut)", colorTile1 == colorTile2);

        if (failures == 0) System.out.println("TOUT OK");
        else { System.out.println(failures + " ECHEC(S)"); System.exit(1); }
    }

    /** Charge la map sous la tuile donnée, prend le 1er pixel non nul de la frame 0 du marine,
     *  et renvoie sa couleur RÉSOLUE (map_rgbs[index]). */
    private static int loadAndSampleMarine(String tile) {
        LevelScene scene = new LevelScene();
        scene.init(224, 160, "com1_1", tile);
        gloom.Objects.loadanobj(10);                  // marine (type 10) : chargé+remappé ce niveau

        int a6 = Mem.l(ObjInfo.marine);               // anim chargé+remappé
        int frame0 = a6 + Mem.l(a6 + Defs.an_size);   // offset de la frame 0
        int px = frame0 + 8;                          // saute handles(4) + w/h(4) → pixels
        int w = Mem.uw(frame0 + 4), h = Mem.uw(frame0 + 6);
        int rgbs = Mem.l(Vars.map_rgbs);
        for (int i = 0; i < w * h; i++) {
            int idx = Mem.ub(px + i);
            if (idx != 0) return Mem.uw(rgbs + idx * 2);   // couleur résolue du 1er pixel opaque
        }
        check("marine a des pixels", false);
        return -1;
    }

    private static void check(String name, boolean cond) {
        if (!cond) { System.out.println("ECHEC " + name); failures++; }
    }
}
