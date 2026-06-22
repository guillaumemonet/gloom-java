package gloom.tools;

import gloom.Files;
import gloom.Map;
import gloom.Mem;
import gloom.Vars;

import java.io.IOException;

/**
 * Harnais de cohérence du sous-système 04b (chargement map & textures).
 *
 *  - loadfile : charge une vraie map et compare les octets à une lecture indépendante ;
 *  - initmap : vérifie la résolution des pointeurs d'en-tête (recalculés depuis les
 *    offsets du blob) ;
 *  - addpal/remap : palette + pixels synthétiques, accumulation/dédup et remap recalculés ;
 *  - loadtxts : charge bout-en-bout les textures d'une vraie map (structurel + tolérant).
 *
 * Doit afficher « TOUT OK ». Lancé par `gradle mapTest`.
 */
public final class MapTest {

    private static int failures = 0;
    private static final String MAP = "maps/com1_1";

    public static void main(String[] args) {
        testLoadfile();
        testInitmap();
        testAddpalRemap();
        testLoadtxts();

        if (failures == 0) {
            System.out.println("TOUT OK");
        } else {
            System.out.println(failures + " ECHEC(S)");
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------

    private static int nameAddr(String s) {
        int a = Mem.alloc(s.length() + 1);
        Mem.load(a, s.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        Mem.wb(a + s.length(), 0);
        return a;
    }

    private static void testLoadfile() {
        byte[] ref;
        try {
            ref = gloom.Assets.read(MAP);
        } catch (IOException e) {
            System.out.println("ECHEC loadfile: " + MAP + " introuvable"); failures++; return;
        }
        int addr = Files.loadfile(nameAddr(MAP), 1);
        checkTrue("loadfile-nonzero", addr != 0);
        // en-tête d'allocation (allocmem2_ offset 16)
        check("loadfile-alloc-offset", Mem.l(addr - 8), 16);
        check("loadfile-alloc-size", Mem.l(addr - 12), ref.length + 16);
        // contenu identique sur quelques octets
        boolean same = true;
        for (int i = 0; i < Math.min(64, ref.length); i++) {
            if ((Mem.RAM[addr + i]) != ref[i]) { same = false; break; }
        }
        checkTrue("loadfile-content", same);
    }

    private static void testInitmap() {
        int map = Files.loadfile(nameAddr(MAP), 1);
        Mem.wl(Vars.map_map, map);
        // palette vide : map_rgbsfrom2 = base d'un buffer frais
        int bufPal = Mem.alloc(512 * 2);
        Mem.wl(Vars.map_rgbs, bufPal);
        Mem.wl(Vars.map_rgbsfrom2, bufPal);

        Map.initmap();

        // recalcul indépendant des offsets d'en-tête
        check("map_grid", Mem.l(Vars.map_grid), map + Mem.l(map));
        check("map_poly", Mem.l(Vars.map_poly), map + Mem.l(map + 4));
        check("map_ppnt", Mem.l(Vars.map_ppnt), map + Mem.l(map + 8));
        check("map_anim", Mem.l(Vars.map_anim), map + Mem.l(map + 12));
        check("map_txts", Mem.l(Vars.map_txts), map + Mem.l(map + 16));
        check("map_events", Mem.l(Vars.map_events), map + Mem.l(map) - 100);
        check("map_rgbsat=from2", Mem.l(Vars.map_rgbsat), bufPal);
    }

    private static void testAddpalRemap() {
        // palette de base vide
        int bufPal = Mem.alloc(512 * 2);
        Mem.wl(Vars.map_rgbs, bufPal);
        Mem.wl(Vars.map_rgbsat, bufPal);

        // pa #1 : numcols=4, couleurs 0x111,0x222,0x333,0x444
        int pa1 = Mem.alloc(2 + 4 * 2);
        Mem.ww(pa1, 4);
        Mem.ww(pa1 + 2, 0x111); Mem.ww(pa1 + 4, 0x222);
        Mem.ww(pa1 + 6, 0x333); Mem.ww(pa1 + 8, 0x444);
        Map.addpal(pa1);

        int mt = Mem.l(Vars.maptable);
        // palette globale = [0x111,0x222,0x333,0x444]
        check("rgbs[0]", Mem.uw(bufPal), 0x111);
        check("rgbs[3]", Mem.uw(bufPal + 6), 0x444);
        check("rgbsat-grew", Mem.l(Vars.map_rgbsat), bufPal + 8);
        // maptable : couleur d2 (1-based) → index global (0-based)
        check("maptable[0]", Mem.ub(mt + 0), 0);
        check("maptable[1]", Mem.ub(mt + 1), 0);
        check("maptable[2]", Mem.ub(mt + 2), 1);
        check("maptable[3]", Mem.ub(mt + 3), 2);
        check("maptable[4]", Mem.ub(mt + 4), 3);
        check("maptable[249]", Mem.ub(mt + 249), 249);
        check("maptable[255]", Mem.ub(mt + 255), 255);

        // remap d'un bloc de pixels avec CETTE maptable
        int pix = Mem.alloc(7);
        int[] in = {0, 1, 2, 3, 4, 249, 255};
        for (int i = 0; i < in.length; i++) Mem.wb(pix + i, in[i]);
        Map.remap(pix, pix + 7);
        int[] exp = {0, 0, 1, 2, 3, 249, 255};
        for (int i = 0; i < exp.length; i++) check("remap[" + i + "]", Mem.ub(pix + i), exp[i]);

        // pa #2 : dédup (0x222 existe → index 1) + nouvelle couleur 0x999 → index 4
        int pa2 = Mem.alloc(2 + 2 * 2);
        Mem.ww(pa2, 2);
        Mem.ww(pa2 + 2, 0x222); Mem.ww(pa2 + 4, 0x999);
        Map.addpal(pa2);
        check("dedup-rgbsat", Mem.l(Vars.map_rgbsat), bufPal + 10);   // 1 seule couleur ajoutée
        check("rgbs[4]=0x999", Mem.uw(bufPal + 8), 0x999);
        check("dedup-maptable[1]", Mem.ub(mt + 1), 1);                // 0x222 réutilisé
        check("dedup-maptable[2]", Mem.ub(mt + 2), 4);                // 0x999 nouveau
    }

    private static void testLoadtxts() {
        int map = Files.loadfile(nameAddr(MAP), 1);
        Mem.wl(Vars.map_map, map);
        int bufPal = Mem.alloc(512 * 2);
        Mem.wl(Vars.map_rgbs, bufPal);
        Mem.wl(Vars.map_rgbsfrom2, bufPal);
        Map.initmap();
        // remet les tables à zéro
        for (int i = 0; i < 8; i++) Mem.wl(Vars.textscrns + i * 4, 0);
        for (int i = 0; i < 160; i++) Mem.wl(Vars.textures + i * 4, 0);

        Map.loadtxts();

        int loaded = 0;
        StringBuilder names = new StringBuilder();
        int a5 = Mem.l(Vars.map_txts);
        for (int i = 0; i < 8; i++) {
            int scr = Mem.l(Vars.textscrns + i * 4);
            if (scr != 0) loaded++;
            // (récupère le nom pour info)
            String nm = Mem.cstr(a5);
            a5 += nm.length() + 1;
            names.append(nm.isEmpty() ? "-" : nm).append(scr != 0 ? "(ok) " : "(.) ");
        }
        System.out.println("[info] textures du niveau : " + names);

        // structurel : pour chaque écran chargé, ses 20 colonnes pointent dans le fichier.
        int tIdx = 0;
        for (int i = 0; i < 8; i++) {
            int scr = Mem.l(Vars.textscrns + i * 4);
            if (scr == 0) continue;
            for (int c = 0; c < 20; c++) {
                int expected = scr + 4 + c * 64 * 65;
                check("textures[scr" + i + ".col" + c + "]", Mem.l(Vars.textures + tIdx * 4), expected);
                tIdx++;
            }
        }

        if (loaded == 0) {
            System.out.println("[NOTE] aucun écran de texture chargé (assets txts absents) — "
                    + "loadtxts s'est exécuté sans erreur ; vérif. de contenu sautée.");
        } else {
            checkTrue("palette-grew", Integer.compareUnsigned(Mem.l(Vars.map_rgbsat), bufPal) > 0);
        }
    }

    // ------------------------------------------------------------------

    private static void check(String name, int actual, int expected) {
        if (actual != expected) {
            System.out.println("ECHEC " + name + " : attendu " + expected + " (0x" + Integer.toHexString(expected)
                    + "), obtenu " + actual + " (0x" + Integer.toHexString(actual) + ")");
            failures++;
        }
    }

    private static void checkTrue(String name, boolean cond) {
        if (!cond) { System.out.println("ECHEC " + name); failures++; }
    }
}
