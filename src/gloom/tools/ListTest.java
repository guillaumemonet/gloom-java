package gloom.tools;

import gloom.Alloc;
import gloom.Lists;
import gloom.Mem;
import gloom.data.Tables;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Harnais de cohérence du sous-système 02 (allocateur + listes chaînées + tables).
 *
 * Vérifie le comportement des primitives de liste (alloclist/addfirst/addlast/
 * addnext/killitem/clearlist/zerolist) et de l'allocateur tracké, plus les
 * invariants structurels des tables précalculées (pointeurs sqr/castrots/camrots,
 * table weapon, présence des assets).
 *
 * Doit afficher « TOUT OK ». Lancé par `gradle listTest`.
 */
public final class ListTest {

    private static int failures = 0;
    private static final int ITEM = 16;   // nœud : 8 (succ/pred) + 8 (données)

    public static void main(String[] args) {
        testFreePool();
        testOrderAndExhaustion();
        testKillAndReuse();
        testAddNext();
        testClearList();
        testZeroList();
        testAllocTracking();
        testTables();

        if (failures == 0) {
            System.out.println("TOUT OK");
        } else {
            System.out.println(failures + " ECHEC(S)");
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------

    private static void testFreePool() {
        int lst = newList(5);
        check("free-pool-init", countFree(lst), 5);
        check("list-empty-init", iterate(lst).size(), 0);
    }

    private static void testOrderAndExhaustion() {
        int lst = newList(4);
        tag(Lists.addlast(lst), 100);
        tag(Lists.addlast(lst), 101);
        tag(Lists.addlast(lst), 102);
        tag(Lists.addfirst(lst), 99);
        checkList("order-addlast/first", iterate(lst), 99, 100, 101, 102);
        check("pool-exhausted-free", countFree(lst), 0);
        check("addlast-exhausted", Lists.addlast(lst), 0);
        check("addfirst-exhausted", Lists.addfirst(lst), 0);
    }

    private static void testKillAndReuse() {
        int lst = newList(4);
        int a = Lists.addlast(lst); tag(a, 10);
        int b = Lists.addlast(lst); tag(b, 20);
        int c = Lists.addlast(lst); tag(c, 30);
        // killitem renvoie le précédent : tuer b doit renvoyer a.
        int prev = Lists.killitem(lst, b);
        check("killitem-returns-prev", prev, a);
        checkList("after-kill", iterate(lst), 10, 30);
        check("free-after-kill", countFree(lst), 2);   // 1 restant + 1 recyclé
        // réutilisation immédiate du nœud recyclé.
        int reused = Lists.addlast(lst); tag(reused, 40);
        check("reuse-node", reused, b);                 // free-list LIFO
        checkList("after-reuse", iterate(lst), 10, 30, 40);
    }

    private static void testAddNext() {
        int lst = newList(4);
        int a = Lists.addlast(lst); tag(a, 1);
        int c = Lists.addlast(lst); tag(c, 3);
        int b = Lists.addnext(lst, a); tag(b, 2);       // insère 2 après 1
        checkList("addnext", iterate(lst), 1, 2, 3);
    }

    private static void testClearList() {
        int lst = newList(4);
        Lists.addlast(lst); Lists.addlast(lst); Lists.addfirst(lst);
        Lists.clearlist(lst);
        check("clearlist-empty", iterate(lst).size(), 0);
        check("clearlist-free-restored", countFree(lst), 4);
    }

    private static void testZeroList() {
        int lst = newList(3);
        // salit les données de tous les items.
        int x;
        while ((x = Lists.addlast(lst)) != 0) { Mem.wl(x + 8, 0x7777); Mem.wl(x + 12, 0x8888); }
        Lists.zerolist(lst, ITEM);
        check("zerolist-empty", iterate(lst).size(), 0);
        check("zerolist-free", countFree(lst), 3);
        int n = Lists.addlast(lst);
        check("zerolist-data0-a", Mem.l(n + 8), 0);
        check("zerolist-data0-b", Mem.l(n + 12), 0);
    }

    private static void testAllocTracking() {
        Alloc.freememlist();                            // repart d'une liste vide
        int before = Mem.l(Alloc.memlist);
        int p = Alloc.allocmem_(64, 0x10001, 0);
        // le pointeur utilisateur est 16 octets après l'en-tête de bloc.
        check("allocmem-header-size", Mem.l(p - 8), 16);          // offset
        check("allocmem-real-size", Mem.l(p - 12), 64 + 16);      // real size
        check("allocmem-linked", Mem.l(Alloc.memlist), p - 16);   // memlist = bloc
        check("allocmem-next", Mem.l(p - 16), before);            // next = ancien memlist
        // allocmem2_ : 32 octets supplémentaires en tête → offset 48.
        int q = Alloc.allocmem2_(64, 0x10001, 32, 0);
        check("allocmem2-offset", Mem.l(q - 8 - 32), 16 + 32);
    }

    private static void testTables() {
        // Invariants des pointeurs (gloom.s:7570/7630/7631).
        check("sqr->sqrinc", Mem.l(Tables.sqr), Tables.sqrinc);
        check("castrots-offset", Mem.l(Tables.castrots), Tables.castrotsinc + 8 * 160);
        check("camrots->camrotsinc", Mem.l(Tables.camrots), Tables.camrotsinc);
        // Table weapon : paires (bullet,sparks).
        check("weapon1-bullet", Mem.l(Tables.weapons + 0), Tables.bullet1);
        check("weapon1-sparks", Mem.l(Tables.weapons + 4), Tables.sparks1);
        check("weapon3-bullet", Mem.l(Tables.weapons + 16), Tables.bullet3);
        check("weapon5-sparks", Mem.l(Tables.weapons + 36), Tables.sparks5);
        // Présence/format des assets clés (sanity, indépendant du port).
        checkAssetSize("camrots.bin", 256 * 8);         // 256 angles × 8 octets
        checkTrue("gridoffs4.bin présent", assetSize("gridoffs4.bin") > 0);
        checkTrue("sqr.bin présent", assetSize("sqr.bin") > 0);
        checkTrue("castrots64.bin présent", assetSize("castrots64.bin") > 0);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static int newList(int n) {
        int lst = Lists.allocHeader();
        Lists.alloclist(lst, n, ITEM);
        return lst;
    }

    private static void tag(int node, int value) {
        if (node == 0) { failures++; System.out.println("ECHEC tag: nœud nul"); return; }
        Mem.wl(node + 8, value);
    }

    private static List<Integer> iterate(int lst) {
        List<Integer> out = new ArrayList<>();
        int node = Lists.head(lst);
        int tail = Lists.tail(lst);
        int guard = 0;
        while (node != tail && guard++ < 100000) {
            out.add(Mem.l(node + 8));
            node = Lists.next(node);
        }
        return out;
    }

    private static int countFree(int lst) {
        int f = Mem.l(lst + 12);
        int n = 0;
        while (f != 0 && n < 100000) { n++; f = Mem.l(f); }
        return n;
    }

    private static long assetSize(String name) {
        var p = gloom.Assets.resolve(name);
        try {
            return (p == null) ? -1 : Files.size(p);
        } catch (Exception e) {
            return -1;
        }
    }

    private static void checkAssetSize(String name, long expected) {
        long s = assetSize(name);
        if (s != expected) {
            System.out.println("ECHEC asset " + name + " : taille attendue " + expected + ", obtenue " + s);
            failures++;
        }
    }

    private static void checkList(String name, List<Integer> got, int... expected) {
        boolean ok = got.size() == expected.length;
        for (int i = 0; ok && i < expected.length; i++) ok = got.get(i) == expected[i];
        if (!ok) {
            System.out.println("ECHEC " + name + " : attendu " + java.util.Arrays.toString(expected) + ", obtenu " + got);
            failures++;
        }
    }

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
