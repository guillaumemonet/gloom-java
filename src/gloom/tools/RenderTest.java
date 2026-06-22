package gloom.tools;

import gloom.Defs;
import gloom.Mem;
import gloom.Render;
import gloom.Vars;
import gloom.data.Tables;

/**
 * Harnais de cohérence du sous-système 05a (caméra + collecte des murs visibles).
 *
 *  - calccamera : pose camx/y/z/r + matrice cm depuis camrots ;
 *  - dothezone : un mur placé devant la caméra est transformé, projeté et collecté ;
 *  - makeoutlist : tri par occultation (le mur proche sort en premier) ;
 *  - makewalls : grille synthétique → un mur traverse toute la chaîne jusqu'à outlist.
 *
 * Géométrie pure (pas d'affichage). Doit afficher « TOUT OK ». `gradle renderTest`.
 */
public final class RenderTest {

    private static int failures = 0;

    public static void main(String[] args) {
        setupScreen();
        testCalccamera();
        testDothezone();
        testMakeoutlist();
        testMakewalls();

        if (failures == 0) {
            System.out.println("TOUT OK");
        } else {
            System.out.println(failures + " ECHEC(S)");
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------

    private static void setupScreen() {
        Mem.ww(Vars.minx, 0);
        Mem.ww(Vars.maxx, 319);
        Mem.ww(Vars.miny, 0);
        Mem.ww(Vars.maxy, 199);
    }

    private static void resetFrameArena() {
        int arena = Mem.alloc(1 << 16);   // 64 Ko pour les wl de frame
        Mem.wl(Vars.memory, arena);
        Mem.wl(Vars.memat, arena);
        Mem.wl(Vars.inlist, 0);
        Mem.wl(Vars.inlistf, Vars.inlist);
        Mem.ww(Vars.frame, 1);
    }

    private static int newPlayer(int x, int y, int z, int rot) {
        int o = Mem.alloc(Defs.ob_size);
        Mem.wl(o + Defs.ob_x, x << 16);   // 16.16 : mot fort = entier
        Mem.wl(o + Defs.ob_y, y << 16);
        Mem.wl(o + Defs.ob_z, z << 16);
        Mem.wl(o + Defs.ob_rot, rot << 16);
        Mem.ww(o + Defs.ob_eyey, 110);
        Mem.ww(o + Defs.ob_bounce, 0);
        return o;
    }

    private static int newZone(int lx, int lz, int rx, int rz) {
        int z = Mem.alloc(Defs.zo_size);
        Mem.ww(z + Defs.zo_lx, lx); Mem.ww(z + Defs.zo_lz, lz);
        Mem.ww(z + Defs.zo_rx, rx); Mem.ww(z + Defs.zo_rz, rz);
        Mem.ww(z + Defs.zo_done, 0);
        Mem.ww(z + Defs.zo_open, 0);
        Mem.ww(z + Defs.zo_sc, 1);
        return z;
    }

    // ------------------------------------------------------------------

    private static void testCalccamera() {
        int p = newPlayer(1000, 50, 2000, 0);
        Render.calccamera(p);
        check("camx", (short) Mem.w(Vars.camx), 1000);
        check("camz", (short) Mem.w(Vars.camz), 2000);
        check("camr", Mem.uw(Vars.camr), 0);
        check("camy", (short) Mem.w(Vars.camy), 160);   // 50 + eyey(110) + bounce(0)
        // cm = camrots[0] : matrice ≈ identité (cm1 grand, cm2≈0)
        int c0 = Mem.l(Tables.camrots) + 0;
        check("cm1=camrots[0]", Mem.w(Vars.cm1), Mem.w(c0));
        check("cm2=camrots[0]+2", Mem.w(Vars.cm2), Mem.w(c0 + 2));
        check("cm4=camrots[0]+6", Mem.w(Vars.cm4), Mem.w(c0 + 6));
        checkTrue("cm1 non nul (≈identité)", Math.abs((short) Mem.w(Vars.cm1)) > 0x4000);
    }

    private static void testDothezone() {
        resetFrameArena();
        Render.calccamera(newPlayer(0, 0, 0, 0));        // caméra à l'origine, regard nord (+z)
        // mur 200 de large, à z=500, devant la caméra (orienté vers elle)
        int zone = newZone(100, 500, -100, 500);
        Render.dothezone(zone);
        int wl = Mem.l(Vars.inlist);
        if (wl == 0) {
            // mauvais winding (back-face) → on inverse les extrémités
            resetFrameArena();
            Render.calccamera(newPlayer(0, 0, 0, 0));
            zone = newZone(-100, 500, 100, 500);
            Mem.ww(zone + Defs.zo_done, 0);
            Render.dothezone(zone);
            wl = Mem.l(Vars.inlist);
        }
        checkTrue("dothezone-collecte", wl != 0);
        if (wl != 0) {
            int lsx = (short) Mem.w(wl + Defs.wl_lsx);
            int rsx = (short) Mem.w(wl + Defs.wl_rsx);
            int nz = (short) Mem.w(wl + Defs.wl_nz);
            int fz = (short) Mem.w(wl + Defs.wl_fz);
            checkTrue("wl_lsx<wl_rsx (" + lsx + "<" + rsx + ")", lsx < rsx);
            checkTrue("wl_nz>0 (" + nz + ")", nz > 0);
            checkTrue("wl_nz<=wl_fz", nz <= fz);
            // projection raisonnable : à z=500, x=±100, focale 64 → ~±12 (centré sur 0)
            checkTrue("|lsx| raisonnable", Math.abs(lsx) < 64);
            checkTrue("|rsx| raisonnable", Math.abs(rsx) < 64);
        }
    }

    private static void testMakeoutlist() {
        // 2 murs, plages Z disjointes : A proche, B lointain (chevauchent en X).
        int A = Mem.alloc(Defs.wl_size);
        int B = Mem.alloc(Defs.wl_size);
        setWall(A, /*lsx*/ 0, /*rsx*/ 100, /*nz*/ 100, /*fz*/ 120);
        setWall(B, 0, 100, 200, 220);
        // inlist → B → A → 0  (ordre quelconque ; makeoutlist doit trier)
        Mem.wl(Vars.inlist, B);
        Mem.wl(B, A);
        Mem.wl(A, 0);

        Render.makeoutlist();

        check("outlist[0]=A (proche)", Mem.l(Vars.outlist), A);
        check("outlist[1]=B (lointain)", Mem.l(A), B);
        check("outlist-fin", Mem.l(B), 0);
    }

    private static void setWall(int w, int lsx, int rsx, int nz, int fz) {
        Mem.ww(w + Defs.wl_lsx, lsx); Mem.ww(w + Defs.wl_rsx, rsx);
        Mem.ww(w + Defs.wl_nz, nz);   Mem.ww(w + Defs.wl_fz, fz);
        Mem.ww(w + Defs.wl_open, 0);
        Mem.wl(w, 0);
    }

    private static void testMakewalls() {
        resetFrameArena();
        // grille 32×32 (8 octets/cellule) + map_ppnt + map_poly (1 zone)
        int grid = Mem.alloc(32 * 32 * 8);
        int ppnt = Mem.alloc(64);
        int poly = Mem.alloc(4 * Defs.zo_size);
        Mem.wl(Vars.map_grid, grid);
        Mem.wl(Vars.map_ppnt, ppnt);
        Mem.wl(Vars.map_poly, poly);

        // caméra en cellule (16,16) ; coord = 16*256 + 128 = 4224
        int cx = 16 * 256 + 128;
        Render.calccamera(newPlayer(cx, 0, cx, 0));

        // une zone devant la caméra (au nord, +z), murs orientés vers elle
        int z0 = poly;                         // zone #0
        Mem.ww(z0 + Defs.zo_lx, cx - 100); Mem.ww(z0 + Defs.zo_lz, cx + 500);
        Mem.ww(z0 + Defs.zo_rx, cx + 100); Mem.ww(z0 + Defs.zo_rz, cx + 500);
        Mem.ww(z0 + Defs.zo_done, 0); Mem.ww(z0 + Defs.zo_open, 0);

        // cellule (16,16) référence le poly #0
        int cell = grid + (16 * 32 + 16) * 8;
        Mem.ww(cell, 0);          // nb polys - 1 = 0 (→ 1 poly, dbf)
        Mem.ww(cell + 2, 0);      // offset dans map_ppnt
        Mem.ww(ppnt, 0);          // map_ppnt[0] = poly #0

        Render.makewalls();

        int out = Mem.l(Vars.outlist);
        // si back-face, on inverse et on recommence
        if (out == 0) {
            resetFrameArena();
            Render.calccamera(newPlayer(cx, 0, cx, 0));
            Mem.ww(z0 + Defs.zo_lx, cx + 100); Mem.ww(z0 + Defs.zo_lz, cx + 500);
            Mem.ww(z0 + Defs.zo_rx, cx - 100); Mem.ww(z0 + Defs.zo_rz, cx + 500);
            Mem.ww(z0 + Defs.zo_done, 0);
            Render.makewalls();
            out = Mem.l(Vars.outlist);
        }
        checkTrue("makewalls→outlist non vide", out != 0);
        if (out != 0) {
            checkTrue("makewalls wl_nz>0", (short) Mem.w(out + Defs.wl_nz) > 0);
        }
    }

    // ------------------------------------------------------------------

    private static void check(String name, int actual, int expected) {
        if (actual != expected) {
            System.out.println("ECHEC " + name + " : attendu " + expected + ", obtenu " + actual);
            failures++;
        }
    }

    private static void checkTrue(String name, boolean cond) {
        if (!cond) { System.out.println("ECHEC " + name); failures++; }
    }
}
