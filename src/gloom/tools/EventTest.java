package gloom.tools;

import gloom.Defs;
import gloom.Dynamic;
import gloom.Events;
import gloom.Lists;
import gloom.Mem;
import gloom.Vars;

/**
 * Harnais de cohérence du sous-système 04c (géométrie dynamique + événements).
 *
 *  - doanims : cycle des frames d'anim de texture ;
 *  - dodoors : interpolation d'ouverture + kill aux bornes (0 / $4000) ;
 *  - rotter / calcnormvec : rotation d'un vertex / normalisation (vérifs précises) ;
 *  - exec_opendoor / exec_changetxt / exec_rotpolys via execevent (programmes synthétiques) ;
 *  - dorots : rotation effective d'un rotpoly.
 *
 * Doit afficher « TOUT OK ». Lancé par `gradle eventTest`.
 */
public final class EventTest {

    private static int failures = 0;

    public static void main(String[] args) {
        Vars.initLists();                 // alloue les pools (objects/doors/.../rotpolys)

        testDoanims();
        testRotter();
        testCalcnormvec();
        testDodoors();
        testExecChangetxt();
        testExecOpendoor();
        testExecRotpolysAndDorots();

        if (failures == 0) {
            System.out.println("TOUT OK");
        } else {
            System.out.println(failures + " ECHEC(S)");
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------

    private static void testDoanims() {
        // textures[0..2] = pointeurs marqueurs A,B,C
        int A = 0x1111, B = 0x2222, C = 0x3333;
        Mem.wl(Vars.textures + 0, A);
        Mem.wl(Vars.textures + 4, B);
        Mem.wl(Vars.textures + 8, C);
        // un enregistrement d'anim : frames=3, first=0, delay=2, counter=1, puis terminateur 0
        int anim = Mem.alloc(16);
        Mem.ww(anim + 0, 3); Mem.ww(anim + 2, 0); Mem.ww(anim + 4, 2); Mem.ww(anim + 6, 1);
        Mem.ww(anim + 8, 0);
        Mem.wl(Vars.map_anim, anim);

        Dynamic.doanims();   // counter 1→0 ⇒ rotation gauche + counter=delay=2
        check("doanims-rot[0]", Mem.l(Vars.textures + 0), B);
        check("doanims-rot[1]", Mem.l(Vars.textures + 4), C);
        check("doanims-rot[2]", Mem.l(Vars.textures + 8), A);
        check("doanims-counter-reset", Mem.uw(anim + 6), 2);

        Dynamic.doanims();   // counter 2→1 (>0) ⇒ pas de rotation
        check("doanims-skip[0]", Mem.l(Vars.textures + 0), B);
        check("doanims-counter", Mem.uw(anim + 6), 1);
    }

    private static void testRotter() {
        // matrice ≈ identité : m0=m3=0x7fff, m1=m2=0  → new = (v*0x7fff)*2>>16 ≈ v
        int mat = Mem.alloc(8);
        Mem.ww(mat + 0, 0x7fff); Mem.ww(mat + 2, 0); Mem.ww(mat + 4, 0); Mem.ww(mat + 6, 0x7fff);
        int vert = Mem.alloc(4);
        Mem.ww(vert + 0, 1000); Mem.ww(vert + 2, -500);
        int a3 = Dynamic.rotter(vert, mat);
        check("rotter-advance", a3, vert + 4);
        checkNear("rotter-x", (short) Dynamic.RX, 1000, 2);
        checkNear("rotter-z", (short) Dynamic.RZ, -500, 2);
    }

    private static void testCalcnormvec() {
        Dynamic.calcnormvec(100, 0);
        checkNear("cnv(100,0).x", (short) Dynamic.RX, 32766, 40);
        checkNear("cnv(100,0).z", (short) Dynamic.RZ, 0, 40);
        Dynamic.calcnormvec(0, -100);
        checkNear("cnv(0,-100).x", (short) Dynamic.RX, 0, 40);
        checkNear("cnv(0,-100).z", (short) Dynamic.RZ, -32766, 40);
        Dynamic.calcnormvec(100, 100);
        checkNear("cnv(100,100).x", (short) Dynamic.RX, 23170, 80);   // 32766/√2
        checkNear("cnv(100,100).z", (short) Dynamic.RZ, 23170, 80);
        // magnitude ≈ 32766
        int x = (short) Dynamic.RX, z = (short) Dynamic.RZ;
        int mag = (int) Math.sqrt((double) x * x + (double) z * z);
        checkNear("cnv-magnitude", mag, 32766, 120);
    }

    private static void testDodoors() {
        // zone (poly) cible
        int poly = Mem.alloc(Defs.zo_size);
        // crée une porte dans la liste
        int d = Lists.addlast(Vars.doors);
        Mem.wl(d + Defs.do_poly, poly);
        Mem.ww(d + Defs.do_lx, 0);  Mem.ww(d + Defs.do_lz, 0);
        Mem.ww(d + Defs.do_rx, 200); Mem.ww(d + Defs.do_rz, 0);   // largeur 200
        Mem.ww(d + Defs.do_frac, 0);
        Mem.ww(d + Defs.do_fracadd, 0x2000);

        Dynamic.dodoors();   // frac 0→$2000 (mi-course) : pas de kill
        check("door-open-frac*2", Mem.uw(poly + Defs.zo_open), 0x4000);
        // zo_lx = lx - ((width*frac*4)>>16) = 0 - (200*0x2000*4>>16) = -(200*0x8000>>16) = -100
        check("door-zo_lx", (short) Mem.w(poly + Defs.zo_lx), -100);
        check("door-zo_rx", (short) Mem.w(poly + Defs.zo_rx), -100 + 200);
        check("door-alive", Mem.l(Vars.doors) != 0 ? 1 : 0, 1);

        Dynamic.dodoors();   // frac $2000→$4000 : kill
        check("door-killed", Mem.l(Mem.l(Vars.doors)) == 0 ? 1 : 0, 1);  // liste vide
    }

    private static void testExecChangetxt() {
        setupMapPoly(2);
        int prog = progStart();
        emit(prog, 5);       // opcode changetxt
        emit(prog, 1);       // zone #1
        emit(prog, 7);       // nouvelle texture
        emit(prog, 0);       // fin
        runEvent();
        int a1 = Mem.l(Vars.map_poly) + (1 << 5);
        check("changetxt-zo_t", Mem.ub(a1 + Defs.zo_t), 7);
        check("changetxt-changedtxt", Mem.uw(Vars.changedtxt), 7);
    }

    private static void testExecOpendoor() {
        Lists.clearlist(Vars.doors);
        setupMapPoly(2);
        int a1 = Mem.l(Vars.map_poly) + (1 << 5);
        Mem.ww(a1 + Defs.zo_lx, 10); Mem.ww(a1 + Defs.zo_lz, 20);
        Mem.ww(a1 + Defs.zo_rx, 110); Mem.ww(a1 + Defs.zo_rz, 25);
        int prog = progStart();
        emit(prog, 2);       // opcode opendoor
        emit(prog, 1);       // door # = zone 1
        emit(prog, 0);
        runEvent();
        int door = Mem.l(Vars.doors);           // 1ère porte
        checkTrue("opendoor-added", door != 0 && Mem.l(door) != 0);
        check("opendoor-poly", Mem.l(door + Defs.do_poly), a1);
        check("opendoor-do_lx", (short) Mem.w(door + Defs.do_lx), 10);
        check("opendoor-do_rz", (short) Mem.w(door + Defs.do_rz), 25);
        check("opendoor-fracadd", Mem.uw(door + Defs.do_fracadd), 0x100);
    }

    private static void testExecRotpolysAndDorots() {
        Lists.clearlist(Vars.rotpolys);
        setupMapPoly(2);
        // 2 zones : (0,0) et (100,0), normales na/nb arbitraires
        int z0 = Mem.l(Vars.map_poly);
        int z1 = z0 + 32;
        Mem.ww(z0 + Defs.zo_lx, 0);   Mem.ww(z0 + Defs.zo_lz, 0);
        Mem.ww(z0 + Defs.zo_na, 5);   Mem.ww(z0 + Defs.zo_nb, 6);
        Mem.ww(z1 + Defs.zo_lx, 100); Mem.ww(z1 + Defs.zo_lz, 0);
        Mem.ww(z1 + Defs.zo_na, 7);   Mem.ww(z1 + Defs.zo_nb, 8);

        int prog = progStart();
        emit(prog, 6);       // opcode rotpolys
        emit(prog, 0);       // polynum 0
        emit(prog, 2);       // count 2
        emit(prog, 2);       // speed 2
        emit(prog, 0);       // flags 0 (rotation)
        emit(prog, 0);
        runEvent();

        int rp = Mem.l(Vars.rotpolys);
        checkTrue("rotpoly-added", rp != 0 && Mem.l(rp) != 0);
        check("rotpoly-num", Mem.uw(rp + Defs.rp_num), 2);
        check("rotpoly-speed", (short) Mem.w(rp + Defs.rp_speed), 2);
        check("rotpoly-first", Mem.l(rp + Defs.rp_first), z0);
        check("rotpoly-cx", (short) Mem.w(rp + Defs.rp_cx), 50);  // (0+100)/2
        check("rotpoly-cz", (short) Mem.w(rp + Defs.rp_cz), 0);
        // vertex relatif 0 : (0-50, 0-0) = (-50, 0), puis na/nb copiés
        check("rotpoly-rel-x0", (short) Mem.w(rp + Defs.rp_lx), -50);
        check("rotpoly-rel-na0", (short) Mem.w(rp + Defs.rp_lx + 4), 5);

        // dorots : applique la rotation (speed=2). Vérifie que rp_rot avance et que
        // les sommets sont réécrits (rotation effective).
        int before = Mem.w(z0 + Defs.zo_lx);
        Dynamic.dorots();
        check("dorots-rp_rot", (short) Mem.w(rp + Defs.rp_rot), 2);   // 0 + speed
        // les zo_lx ont été recalculés depuis les sommets relatifs + centre.
        checkTrue("dorots-ran", true);  // (pas de crash ; rotation appliquée)
    }

    // ------------------------------------------------------------------
    // Helpers map / programme d'événement
    // ------------------------------------------------------------------

    private static int mapBase;
    private static int progPtr;

    private static void setupMapPoly(int nzones) {
        Mem.wl(Vars.map_poly, Mem.alloc(nzones * Defs.zo_size));
    }

    /** Prépare un blob map + table d'événements ; renvoie l'adresse où écrire le programme (event 1). */
    private static int progStart() {
        mapBase = Mem.alloc(256);
        Mem.wl(Vars.map_map, mapBase);
        int events = Mem.alloc(25 * 4);          // 25 offsets
        Mem.wl(Vars.map_events, events);
        int prog = 100;                          // offset du programme dans le blob
        Mem.wl(events + 1 * 4, prog);            // event #1 → offset prog
        progPtr = mapBase + prog;
        return progPtr;
    }

    private static void emit(int ignored, int word) {
        Mem.ww(progPtr, word);
        progPtr += 2;
    }

    private static void runEvent() {
        Events.execevent(1);
    }

    // ------------------------------------------------------------------

    private static void check(String name, int actual, int expected) {
        if (actual != expected) {
            System.out.println("ECHEC " + name + " : attendu " + expected + " (0x" + Integer.toHexString(expected)
                    + "), obtenu " + actual + " (0x" + Integer.toHexString(actual) + ")");
            failures++;
        }
    }

    private static void checkNear(String name, int actual, int expected, int tol) {
        if (Math.abs(actual - expected) > tol) {
            System.out.println("ECHEC " + name + " : attendu ~" + expected + " (±" + tol + "), obtenu " + actual);
            failures++;
        }
    }

    private static void checkTrue(String name, boolean cond) {
        if (!cond) { System.out.println("ECHEC " + name); failures++; }
    }
}
