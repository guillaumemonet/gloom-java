package gloom.tools;

import gloom.Maths;

/**
 * Harnais de cohérence du sous-système 01 (Math fondamental).
 *
 * NB sur la « parité » : sans le binaire Amiga d'origine en exécution, on ne
 * compare pas bit-à-bit contre la machine réelle. On vérifie ici :
 *  - le déterminisme et les invariants du RNG (re-seed → même suite, plages,
 *    indépendance des deux générateurs) ;
 *  - calcangle_ contre une référence atan2 indépendante (≈, tolérance liée à la
 *    discrétisation de arc.bin) + bornes + axes cardinaux ;
 *  - findanglesign contre des cas tracés à la main depuis l'assembleur.
 *
 * Doit afficher « TOUT OK ». Lancé par `gradle mathTest`.
 */
public final class MathTest {

    private static int failures = 0;

    public static void main(String[] args) {
        testRngDeterminism();
        testRngRanges();
        testRngIndependent();
        testCalcAngleBounds();
        testCalcAngleCardinals();
        testCalcAngleVsAtan2();
        testFindAngleSign();

        if (failures == 0) {
            System.out.println("TOUT OK");
        } else {
            System.out.println(failures + " ECHEC(S)");
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------

    private static void testRngDeterminism() {
        Maths.seedrnd(12345);
        int[] a = new int[200];
        for (int i = 0; i < a.length; i++) a[i] = Maths.rndw();
        Maths.seedrnd(12345);
        for (int i = 0; i < a.length; i++) {
            check("rng-determinism[" + i + "]", a[i], Maths.rndw());
        }
        // Une graine différente doit (quasi sûrement) produire une autre suite.
        Maths.seedrnd(99999);
        boolean differs = false;
        for (int i = 0; i < a.length; i++) if (Maths.rndw() != a[i]) { differs = true; break; }
        checkTrue("rng-seed-sensitivity", differs);
    }

    private static void testRngRanges() {
        Maths.seedrnd(7);
        for (int i = 0; i < 10000; i++) {
            int w = Maths.rndw();
            checkTrue("rndw-range", w >= 0 && w <= 0xffff);
            int n = Maths.rndn(100) & 0xffff;   // le caller asm n'utilise que d0.w
            checkTrue("rndn-range", n >= 0 && n < 100);
        }
        // rndl : doit utiliser les 32 bits (mot fort non nul au moins une fois).
        boolean highSet = false;
        for (int i = 0; i < 100; i++) if ((Maths.rndl() & 0xffff0000) != 0) { highSet = true; break; }
        checkTrue("rndl-32bit", highSet);
    }

    private static void testRngIndependent() {
        // Les deux générateurs ont des tables séparées : même graine → même suite
        // chacun de son côté, mais ils n'interfèrent pas.
        Maths.seedrnd(42);
        Maths.seedrnd2(42);
        int[] g1 = new int[50];
        int[] g2 = new int[50];
        for (int i = 0; i < 50; i++) { g1[i] = Maths.rndw(); g2[i] = Maths.rndw2(); }
        // rndw et rndw2 partagent l'algorithme → même graine donne la même suite.
        for (int i = 0; i < 50; i++) check("rng12-same-algo[" + i + "]", g1[i], g2[i]);
        // Et tirer dans g2 n'a pas perturbé g1 (re-seed g1 et compare).
        Maths.seedrnd(42);
        for (int i = 0; i < 50; i++) check("rng1-isolated[" + i + "]", g1[i], Maths.rndw());
    }

    /** calcangle_ renvoie le registre d0 ; seul d0.w (l'angle 0..255) est utilisé par les callers. */
    private static int ca(int x, int y) {
        return Maths.calcangle_(x, y) & 0xffff;
    }

    private static void testCalcAngleBounds() {
        for (int y = -300; y <= 300; y += 17) {
            for (int x = -300; x <= 300; x += 17) {
                int a = ca(x, y);
                checkTrue("calcangle-bounds(" + x + "," + y + ")=" + a, a >= 0 && a <= 255);
            }
        }
    }

    private static void testCalcAngleCardinals() {
        // 0 = +y (nord), puis quart de tour tous les 64. Tolérance ±2 (discrétisation).
        checkNear("calcangle(0,+y)", ca(0, 100), 0, 2);
        checkNear("calcangle(+x,0)", ca(100, 0), 64, 2);
        checkNear("calcangle(0,-y)", ca(0, -100), 128, 2);
        checkNear("calcangle(-x,0)", ca(-100, 0), 192, 2);
    }

    private static void testCalcAngleVsAtan2() {
        // Référence indépendante : 0 = +y, sens des angles croissants déduit des axes.
        for (int deg = 0; deg < 360; deg += 5) {
            double rad = Math.toRadians(deg);
            int x = (int) Math.round(Math.sin(rad) * 1000);
            int y = (int) Math.round(Math.cos(rad) * 1000);
            if (x == 0 && y == 0) continue;
            int got = ca(x, y);
            int expected = (int) Math.round(deg / 360.0 * 256.0) & 255;
            int diff = circDiff(got, expected, 256);
            checkTrue("calcangle~atan2 deg=" + deg + " got=" + got + " exp=" + expected + " d=" + diff,
                    diff <= 3);
        }
    }

    private static void testFindAngleSign() {
        // Cas tracés à la main depuis gloom.s:7969 (findanglesign(d0=target, d1=current)).
        check("fas(0,0)",     Maths.findanglesign(0, 0),     1);
        check("fas(10,0)",    Maths.findanglesign(10, 0),    -1);
        check("fas(0,10)",    Maths.findanglesign(0, 10),    1);
        check("fas(200,0)",   Maths.findanglesign(200, 0),   1);
        check("fas(0,200)",   Maths.findanglesign(0, 200),   -1);
    }

    // ------------------------------------------------------------------

    private static int circDiff(int a, int b, int mod) {
        int d = Math.abs(a - b) % mod;
        return Math.min(d, mod - d);
    }

    private static void check(String name, int expected, int actual) {
        if (expected != actual) {
            System.out.println("ECHEC " + name + " : attendu= " + expected + ", obtenu " + actual);
            failures++;
        }
    }

    private static void checkNear(String name, int actual, int expected, int tol) {
        if (Math.abs(actual - expected) > tol) {
            System.out.println("ECHEC " + name + " : attendu= ~" + expected + " (±" + tol + "), obtenu " + actual);
            failures++;
        }
    }

    private static void checkTrue(String name, boolean cond) {
        if (!cond) {
            System.out.println("ECHEC " + name);
            failures++;
        }
    }
}
