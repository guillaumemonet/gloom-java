package gloom;

/**
 * Sous-système 02 — Listes chaînées poolées.
 *
 * Traduction littérale des macros de liste de gloom.s (alloclist/k_alloclist,
 * addfirst/addlast/addnext, killitem, clearlist, zerolist — gloom.s:421-551).
 *
 * C'est une liste doublement chaînée à la mode Exec (MinList/MinNode) doublée
 * d'une free-list pour une allocation poolée sans appel système. Toute la logique
 * de jeu l'utilise : objects, doors, blood, gore, rotpolys, sfx.
 *
 * Disposition de l'EN-TÊTE de liste (4 longs, alloué en Mem) — le label \1 de la
 * macro pointe ici :
 *   +0  head      (lh_Head)     : 1er nœud, ou &tail (lst+4) si vide
 *   +4  tail      (lh_Tail \1_last) : sentinelle, toujours .succ=0
 *   +8  tailpred  (lh_TailPred) : dernier nœud, ou &head (lst) si vide
 *   +12 free      (\1_free)     : tête de la free-list
 *
 * Disposition d'un NŒUD :
 *   +0  succ (next)   +4  pred (prev)   +8.. données
 *
 * Les méthodes prennent l'adresse `lst` de l'en-tête. addfirst/addlast/addnext
 * renvoient l'adresse du nœud inséré, ou 0 si la free-list est vide (beq d'origine).
 */
public final class Lists {

    /** Taille d'un en-tête de liste, en octets (4 longs). */
    public static final int HEADER_SIZE = 16;

    private Lists() {
    }

    /** Alloue et initialise un en-tête de liste en Mem ; renvoie son adresse. */
    public static int allocHeader() {
        return Mem.alloc(HEADER_SIZE);
    }

    /**
     * alloclist / k_alloclist (gloom.s:421/436) : initialise l'en-tête `lst` et
     * alloue un pool de `maxitems` items de `itemsize` octets, chaînés en free-list.
     */
    public static void alloclist(int lst, int maxitems, int itemsize) {
        int a2 = lst;                         // lea \1(pc),a2
        Mem.wl(a2 + 8, a2);                   // move.l a2,8(a2)   ; tailpred = &head
        int a0 = a2 + 4;                      // lea 4(a2),a0      ; &tail
        Mem.wl(a0, 0);                        // clr.l (a0)        ; tail.succ = 0
        Mem.wl(a2, a0);                       // move.l a0,(a2)    ; head = &tail
        int d0 = maxitems;                    // (d0/d1 sauvés/restaurés autour de allocmem)
        int d1 = itemsize;
        int total = M68k.mulu(d0, d1);        // mulu d1,d0
        int blk = Alloc.allocmem_(total, 0x10001, 0); // move.l #$10001,d1 ; allocmem alloclist
        a0 = blk;                             // move.l d0,a0
        a2 = lst + 12;                        // lea 12(a2),a2     ; &free
        d0 = maxitems - 1;                    // subq #1,d0
        do {                                  // .loop
            Mem.wl(a2, a0);                   // move.l a0,(a2)
            a2 = a0;                          // move.l a0,a2
            a0 += d1;                         // add d1,a0
        } while (d0-- != 0);                  // dbf d0,.loop  (maxitems itérations)
        // le dernier nœud de la free-list garde .succ=0 (Mem zéroïsé) → terminaison
    }

    /** addfirst (gloom.s:475) : insère en tête. Renvoie le nœud ou 0. */
    public static int addfirst(int lst) {
        int d0 = Mem.l(lst + 12);             // move.l \1_free,d0
        if (d0 == 0) return 0;                // beq.s .afskip
        int a0 = d0;                          // move.l d0,a0
        Mem.wl(lst + 12, Mem.l(a0));          // move.l (a0),\1_free
        int a1 = Mem.l(lst);                  // move.l \1,a1   ; current first
        Mem.wl(a0, a1);                       // move.l a1,(a0)
        Mem.wl(a1 + 4, a0);                   // move.l a0,4(a1)
        Mem.wl(lst, a0);                      // move.l a0,\1
        Mem.wl(a0 + 4, lst);                  // move.l #\1,4(a0)
        return a0;
    }

    /** addlast (gloom.s:492) : insère en queue. Renvoie le nœud ou 0. */
    public static int addlast(int lst) {
        int d0 = Mem.l(lst + 12);             // move.l \1_free,d0
        if (d0 == 0) return 0;                // beq.s .alskip
        int a0 = d0;                          // move.l d0,a0
        Mem.wl(lst + 12, Mem.l(a0));          // move.l (a0),\1_free
        int a1 = Mem.l(lst + 8);              // move.l \1_last+4,a1  ; current last
        Mem.wl(a1, a0);                       // move.l a0,(a1)
        Mem.wl(a0 + 4, a1);                   // move.l a1,4(a0)
        Mem.wl(lst + 8, a0);                  // move.l a0,\1_last+4
        Mem.wl(a0, lst + 4);                  // move.l #\1_last,(a0)
        return a0;
    }

    /** addnext (gloom.s:457) : insère après le nœud a5. Renvoie le nœud ou 0. */
    public static int addnext(int lst, int a5) {
        int d0 = Mem.l(lst + 12);             // move.l \1_free,d0
        if (d0 == 0) return 0;                // beq.s .anskip
        int a0 = d0;                          // move.l d0,a0
        Mem.wl(lst + 12, Mem.l(a0));          // move.l (a0),\1_free
        int a1 = Mem.l(a5);                   // move.l (a5),a1
        Mem.wl(a0, a1);                       // move.l a1,(a0)
        Mem.wl(a1 + 4, a0);                   // move.l a0,4(a1)
        Mem.wl(a5, a0);                       // move.l a0,(a5)
        Mem.wl(a0 + 4, a5);                   // move.l a5,4(a0)
        return a0;
    }

    /** killitem (gloom.s:510) : retire le nœud a0, le rend à la free-list. Renvoie le précédent. */
    public static int killitem(int lst, int a0) {
        int a1 = Mem.l(a0);                   // move.l (a0),a1    ; next of me
        Mem.wl(a1 + 4, Mem.l(a0 + 4));        // move.l 4(a0),4(a1)
        a1 = Mem.l(a0 + 4);                   // move.l 4(a0),a1   ; prev of me
        Mem.wl(a1, Mem.l(a0));                // move.l (a0),(a1)
        Mem.wl(a0, Mem.l(lst + 12));          // move.l \1_free,(a0)
        Mem.wl(lst + 12, a0);                 // move.l a0,\1_free
        return a1;                            // move.l a1,a0
    }

    /** clearlist (gloom.s:524) : vide la liste (tous les nœuds reviennent à la free-list). */
    public static void clearlist(int lst) {
        while (true) {                        // .clloop
            int a0 = Mem.l(lst);              // move.l \1,a0
            if (Mem.l(a0) == 0) return;       // tst.l (a0) ; beq .cldone
            killitem(lst, a0);                // killitem \1
        }
    }

    /** zerolist (gloom.s:536) : met à 0 la partie données (offset 8..) de tous les items. */
    public static void zerolist(int lst, int itemsize) {
        clearlist(lst);                       // clearlist \1
        int a0;
        while ((a0 = addlast(lst)) != 0) {    // .zlloop addlast \1 ; beq .zlskip
            int a1 = a0 + 8;                  // lea 8(a0),a1
            int d1 = (itemsize - 8) / 2 - 1;  // move #(\2-8)/2-1,d1
            do {                              // .zlloop2
                Mem.ww(a1, 0); a1 += 2;       // move d0,(a1)+
            } while (d1-- != 0);              // dbf d1,.zlloop2
        }
        clearlist(lst);                       // .zlskip clearlist \1
    }

    // ------------------------------------------------------------------
    // Accès / itération (idiome `move.l list,a0 ; .loop ... move.l (a0),a0`)
    // ------------------------------------------------------------------

    /** Tête de liste (1er nœud, ou &tail si vide). */
    public static int head(int lst) {
        return Mem.l(lst);
    }

    /** Adresse de la sentinelle de fin (un nœud dont .succ vaut 0). */
    public static int tail(int lst) {
        return lst + 4;
    }

    /** Successeur d'un nœud. */
    public static int next(int node) {
        return Mem.l(node);
    }
}
