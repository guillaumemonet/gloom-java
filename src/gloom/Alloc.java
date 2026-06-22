package gloom;

/**
 * Sous-système 02 — Allocateur mémoire tracké.
 *
 * Traduction littérale de allocmem_/allocmem2_/freememlist (gloom.s:12747-12818)
 * et de la variable memlist (gloom.s:7654).
 *
 * L'original réserve, devant chaque bloc utilisateur, un en-tête de 16 octets
 * chaîné dans `memlist` pour pouvoir tout libérer d'un coup à la sortie :
 *   00.l next        (chaînage memlist)
 *   04.l real size   (taille passée à AllocMem, en-tête inclus)
 *   08.l offset      (décalage vers la mémoire utilisateur ; normalement 16)
 *   12.l text field  (libellé de debug)
 *
 * Portage : Exec AllocMem (-198) → Mem.alloc (qui zéro-initialise, satisfaisant
 * MEMF_CLEAR). Exec FreeMem (-210) n'a pas d'équivalent (Mem est un bump-allocator
 * sans reclaim) : freememlist se contente donc de vider la liste (memlist=0).
 * Limitation documentée — à revoir pour les transitions de niveau (cf. ARCHITECTURE §11).
 */
public final class Alloc {

    /** memlist : dc.l 0 (gloom.s:7654) — tête de la liste des blocs alloués. */
    public static final int memlist = Mem.alloc(4);

    private Alloc() {
    }

    /**
     * allocmem_ : d0=size, d1=requirements, a0=text field → renvoie le pointeur
     * utilisateur (d0). (gloom.s:12755)
     */
    public static int allocmem_(int d0, int d1, int a0) {
        int d3 = 16;                          // moveq #16,d3 ; offset
        return amem_(d0, d1, d3, a0);         // bra.s amem_
    }

    /**
     * allocmem2_ : comme allocmem_ mais d2.l = mémoire supplémentaire réservée en
     * tête (l'offset utilisateur devient 16+d2). (gloom.s:12747)
     */
    public static int allocmem2_(int d0, int d1, int d2, int a0) {
        int d3 = 16;                          // moveq #16,d3
        d3 += d2;                             // add.l d2,d3
        return amem_(d0, d1, d3, a0);         // bra.s amem_
    }

    /** amem_ (gloom.s:12768) : d0=size, d3=offset, a0=text. */
    private static int amem_(int d0, int d1, int d3, int a0) {
        int d4 = a0;                          // move.l a0,d4 (text)
        d0 = d0 + d3;                         // add.l d3,d0
        int d2 = d0;                          // move.l d0,d2 (len)
        // move.l 4.w,a6 ; jsr -198(a6)  → AllocMem(d0 octets, d1) ; MEMF_CLEAR ⇒ Mem zéroise
        int blk = Mem.alloc(d0);              // (échec → OutOfMemoryError, équivalent du warn #$f00)
        a0 = blk;                             // move.l d0,a0
        Mem.wl(a0, Mem.l(memlist));           // move.l memlist,(a0)   ; next
        Mem.wl(memlist, a0);                  // move.l a0,memlist
        Mem.wl(a0 + 4, d2);                   // movem.l d2-d4,4(a0)   ; real size
        Mem.wl(a0 + 8, d3);                   //                       ; offset
        Mem.wl(a0 + 12, d4);                  //                       ; text field
        a0 = a0 + d3;                         // add.l d3,a0
        return a0;                            // move.l a0,d0
    }

    /**
     * freememlist (gloom.s:12790) : libère tous les blocs de memlist.
     * Mem ne sait pas reclaim → on vide simplement la liste (effet observable
     * identique côté programme : memlist=0).
     */
    public static void freememlist() {
        Mem.wl(memlist, 0);                   // (FreeMem en boucle non reproductible)
    }
}
