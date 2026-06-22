package gloom;

/**
 * Sous-système 01 — Math fondamental.
 *
 * Traduction littérale des routines mathématiques pures de gloom.s :
 *  - RNG additif (lagged-Fibonacci, lags 24/55) : seedrnd/rndw/rndn/rndl
 *    et la copie seedrnd2/rndw2/rndn2/rndl2 (deuxième générateur indépendant) ;
 *  - calcangle_ : vecteur (x,y) → angle 8 bits (0..255), par octants + arctan
 *    précalculé (arc.bin) ;
 *  - findanglesign : sens de rotation le plus court entre deux angles 8 bits.
 *
 * Ces routines ne dépendent ni de l'affichage ni des structures de jeu : c'est la
 * fondation testable en isolation (voir gloom.tools.MathTest et docs/PORT_SUBSYS_01_MATH.md).
 *
 * Les wrappers calcangle/calcangle2 (gloom.s:2570-2583) et calcvecs/findsegdist
 * (gloom.s:4431/5831) accèdent aux structures ob_/zo_ et à la caméra : ils seront
 * portés avec leurs sous-systèmes respectifs.
 *
 * Registres dN/aN = int. Données/tables = adresses dans Mem.
 */
public final class Maths {

    // ------------------------------------------------------------------
    // Données (gloom.s:2502-2504, 2553-2555, 2620-2622)
    // ------------------------------------------------------------------

    /** rndtable : ds.w 55 (gloom.s:2502) */
    private static final int rndtable;
    /** k_index : dc.l 0 (gloom.s:2503) */
    private static final int k_index;
    /** j_index : dc.l 0 (gloom.s:2504) */
    private static final int j_index;

    /** rndtable2 : ds.w 55 (gloom.s:2553) */
    private static final int rndtable2;
    private static final int k_index2;
    private static final int j_index2;

    /** .oct : dc 0,0,$4000,-1,... (gloom.s:2620-2621) — 8 longs indexés par d2 (offset octet). */
    private static final int oct;
    /** .arc : incbin arc.bin (gloom.s:2622) — table arctan, 512 mots. */
    private static final int arc;

    static {
        rndtable = Mem.alloc(55 * 2);   // ds.w 55
        k_index = Mem.alloc(4);         // dc.l 0
        j_index = Mem.alloc(4);         // dc.l 0

        rndtable2 = Mem.alloc(55 * 2);
        k_index2 = Mem.alloc(4);
        j_index2 = Mem.alloc(4);

        // .oct dc 0,0,$4000,-1,0,-1,$c000,0 / dc $8000,-1,$4000,0,$8000,0,$c000,-1
        oct = Mem.dcW(0, 0, 0x4000, -1, 0, -1, 0xc000, 0,
                      0x8000, -1, 0x4000, 0, 0x8000, 0, 0xc000, -1);

        arc = Assets.incbin("arc.bin");
    }

    private Maths() {
    }

    // ==================================================================
    // RNG #1 (gloom.s:2468-2517)
    // ==================================================================

    /** seedrnd : seed number in d0.w (gloom.s:2468) */
    public static void seedrnd(int d0) {
        int d1 = 54;                                 // moveq #54,d1
        int a0 = rndtable;                           // lea rndtable(pc),a0
        do {                                         // .loop
            Mem.ww(a0, d0); a0 += 2;                 // move d0,(a0)+
            d0 = M68k.mulu(d0, 0x1efd);              // mulu #$1efd,d0
            d0 = M68k.addw(d0, 0xdff);               // add #$dff,d0
        } while (d1-- != 0);                         // dbf d1,.loop  (55 itérations)
        Mem.wl(k_index, a0);                         // move.l a0,k_index
        Mem.wl(j_index, rndtable + 48);              // move.l #rndtable+48,j_index
    }

    /** rndw : return rnd number 0...65535 in d0.w (gloom.s:2482) */
    public static int rndw() {
        int a1 = rndtable;                           // lea rndtable(pc),a1
        int a0 = Mem.l(j_index);                     // move.l j_index(pc),a0
        a0 -= 2; int d0 = Mem.uw(a0);                // move -(a0),d0
        if (a0 == a1) {                              // cmp.l a0,a1 ; bne.s .skip
            a0 = rndtable + 110;                     // lea rndtable+110(pc),a0
        }
        Mem.wl(j_index, a0);                         // .skip move.l a0,j_index
        a0 = Mem.l(k_index);                         // move.l k_index(pc),a0
        a0 -= 2; d0 = (d0 + Mem.uw(a0)) & 0xffff;    // add -(a0),d0
        Mem.ww(a0, d0);                              // move d0,(a0)
        if (a0 == a1) {                              // cmp.l a0,a1 ; bne.s .skip2
            a0 = rndtable + 110;                     // lea rndtable+110(pc),a0
        }
        Mem.wl(k_index, a0);                         // .skip2 move.l a0,k_index
        return d0;                                   // d0.w
    }

    /** rndl : 32-bit random (gloom.s:2506) */
    public static int rndl() {
        int d0 = M68k.setw(0, rndw());               // bsr rndw
        int d1 = M68k.setw(0, d0);                   // move d0,d1
        d0 = M68k.setw(0, rndw());                   // bsr rndw
        d0 = M68k.swap(d0);                          // swap d0
        d0 = M68k.setw(d0, d1);                      // move d1,d0
        return d0;
    }

    /** rndn : random 0..d0.w-1 (scaled) (gloom.s:2513) */
    public static int rndn(int d0) {
        int d1 = d0;                                 // move d0,d1
        d0 = M68k.setw(d0, rndw());                  // bsr rndw
        d0 = M68k.mulu(d1, d0);                      // mulu d1,d0
        d0 = M68k.swap(d0);                          // swap d0
        return d0;                                   // d0.w
    }

    // ==================================================================
    // RNG #2 — copie indépendante (gloom.s:2519-2568)
    // ==================================================================

    /** seedrnd2 (gloom.s:2519) */
    public static void seedrnd2(int d0) {
        int d1 = 54;
        int a0 = rndtable2;
        do {
            Mem.ww(a0, d0); a0 += 2;
            d0 = M68k.mulu(d0, 0x1efd);
            d0 = M68k.addw(d0, 0xdff);
        } while (d1-- != 0);
        Mem.wl(k_index2, a0);
        Mem.wl(j_index2, rndtable2 + 48);
    }

    /** rndw2 (gloom.s:2533) */
    public static int rndw2() {
        int a1 = rndtable2;
        int a0 = Mem.l(j_index2);
        a0 -= 2; int d0 = Mem.uw(a0);
        if (a0 == a1) {
            a0 = rndtable2 + 110;
        }
        Mem.wl(j_index2, a0);
        a0 = Mem.l(k_index2);
        a0 -= 2; d0 = (d0 + Mem.uw(a0)) & 0xffff;
        Mem.ww(a0, d0);
        if (a0 == a1) {
            a0 = rndtable2 + 110;
        }
        Mem.wl(k_index2, a0);
        return d0;
    }

    /** rndl2 (gloom.s:2557) */
    public static int rndl2() {
        int d0 = M68k.setw(0, rndw2());
        int d1 = M68k.setw(0, d0);
        d0 = M68k.setw(0, rndw2());
        d0 = M68k.swap(d0);
        d0 = M68k.setw(d0, d1);
        return d0;
    }

    /** rndn2 (gloom.s:2564) */
    public static int rndn2(int d0) {
        int d1 = d0;
        d0 = M68k.setw(d0, rndw2());
        d0 = M68k.mulu(d1, d0);
        d0 = M68k.swap(d0);
        return d0;
    }

    // ==================================================================
    // calcangle_ (gloom.s:2585) : d0.w=x d1.w=y (dest-src) -> angle 0..255 dans d0.w
    // ==================================================================

    public static int calcangle_(int d0, int d1) {
        int d2 = 0;                                  // moveq #0,d2
        if ((short) d1 < 0) {                        // tst d1 ; bpl.s .hpos
            d2 = 16;                                 // moveq #16,d2
            d1 = M68k.negw(d1);                      // neg d1
        }
        // .hpos
        if ((short) d0 < 0) {                        // tst d0 ; bpl.s .wpos
            d2 ^= 8;                                 // eor #8,d2
            d0 = M68k.negw(d0);                      // neg d0
        }
        // .wpos
        if ((short) d0 < (short) d1) {               // cmp d1,d0 ; bmi.s .notsteep
            // d0 < d1 : tombe dans .notsteep
        } else if ((short) d0 != (short) d1) {       // bne.s .neq
            d2 ^= 4;                                 // .neq eor #4,d2
            int t = d0; d0 = d1; d1 = t;             // exg d1,d0
        } else {                                     // d0 == d1
            d1 = M68k.setw(d1, 0x2000);              // move #$2000,d1
            return flow(d1, d2);                     // bra.s .flow
        }
        // .notsteep
        if ((short) d1 == 0) {                       // tst d1 ; bne.s .noflow
            d1 = M68k.setw(d1, 0);                   // moveq #0,d1
            return flow(d1, d2);                     // bra.s .flow
        }
        // .noflow
        d0 = M68k.extl(d0);                          // ext.l d0
        d0 = M68k.swap(d0);                          // swap d0
        d0 = M68k.divu(d0, d1);                      // divu d1,d0
        d0 = M68k.lsrw(d0, 6);                       // lsr #6,d0
        d0 = M68k.setw(d0, d0 & 1022);               // and #1022,d0
        d1 = M68k.setw(d1, Mem.uw(arc + M68k.uw(d0)));// move .arc(pc,d0),d1
        return flow(d1, d2);                         // (chute dans .flow)
    }

    /** .flow (gloom.s:2613) */
    private static int flow(int d1, int d2) {
        int d0 = Mem.l(oct + M68k.uw(d2));           // move.l .oct(pc,d2),d0
        d1 = M68k.setw(d1, d1 ^ d0);                 // eor d0,d1
        d0 = M68k.swap(d0);                          // swap d0
        d0 = M68k.addw(d0, d1);                      // add d1,d0
        d0 = M68k.lsrw(d0, 8);                       // lsr #8,d0
        return d0;                                   // angle 0..255 dans d0.w
    }

    // ==================================================================
    // findanglesign (gloom.s:7969)
    //  d1 = angle où je suis ; d0 = angle voulu ; renvoie le signe (±1) de
    //  l'incrément à ajouter pour y aller par le plus court chemin.
    // ==================================================================

    public static int findanglesign(int d0, int d1) {
        d0 &= 255;                                   // and #255,d0
        d1 &= 255;                                   // and #255,d1
        d1 = M68k.subw(d1, d0);                      // sub d0,d1
        if ((short) d1 >= 0) {                       // bpl.s .plus
            d0 = 1;                                  // .plus moveq #1,d0
            if ((short) d1 >= 128) {                 // cmp #128,d1 ; bge.s .rts2
                return -d0;                          // .rts2 neg d0
            }
            d0 = -d0;                                // neg d0
            return -d0;                              // .rts2 neg d0
        }
        d0 = 1;                                      // moveq #1,d0
        if ((short) d1 > -128) {                     // cmp #-128,d1 ; bgt.s .rts
            return -d0;                              // .rts neg d0
        }
        d0 = -d0;                                    // neg d0
        return -d0;                                  // .rts neg d0
    }
}
