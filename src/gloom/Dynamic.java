package gloom;

import gloom.data.Tables;

/**
 * Sous-système 04c — Géométrie dynamique de la map (par frame).
 *
 * Traduction littérale de gloom.s :
 *  - doanims (gloom.s:1942) : cycle des frames d'animation de texture ;
 *  - dorots (gloom.s:1964) + rotter (gloom.s:2119) : rotation des rotpolys ;
 *  - morph (gloom.s:2010) + calcnormvec (gloom.s:2057) : morphing + recalcul des normales ;
 *  - dodoors (gloom.s:2137) : interpolation d'ouverture/fermeture des portes.
 *
 * Consomme gloom.Lists (rotpolys/doors), gloom.Defs (offsets), gloom.data.Tables
 * (camrots2, sqr), gloom.Vars (map_anim, textures, listes).
 *
 * Convention : rotter et calcnormvec renvoient leur vecteur résultat (les "registres"
 * d0,d1 de sortie) dans les champs statiques RX/RZ.
 */
public final class Dynamic {

    /** Sorties d0,d1 de rotter / calcnormvec. */
    public static int RX, RZ;

    private Dynamic() {
    }

    // ==================================================================
    // doanims (gloom.s:1942)
    // ==================================================================

    public static void doanims() {
        int a0 = Mem.l(Vars.map_anim);                // move.l map_anim,a0
        int a1 = Vars.textures;                       // lea textures,a1
        while (true) {                                // .loop
            int d0 = Mem.uw(a0); a0 += 2;             // move (a0)+,d0  ; how many frames
            if (d0 == 0) return;                      // beq .done
            int d1 = Mem.uw(a0);                      // movem (a0)+,d1-d2 : first
            int d2 = Mem.uw(a0 + 2);                  //                    delay
            a0 += 4;
            int cnt = M68k.w(Mem.uw(a0) - 1);         // subq #1,(a0)+
            Mem.ww(a0, cnt); a0 += 2;
            if (cnt > 0) continue;                    // bgt .loop  (compte encore)
            Mem.ww(a0 - 2, d2);                       // move d2,-2(a0)  ; counter = delay
            int a2 = a1 + d1 * 4;                     // lea 0(a1,d1*4),a2
            // rotation à gauche des d0 (frames) pointeurs (subq #2,d0 + dbf = frames-1 décalages)
            int save = Mem.l(a2);                     // move.l (a2),d2
            for (int i = 0; i < d0 - 1; i++) {        // .loop2 move.l 4(a2),(a2)+
                Mem.wl(a2 + i * 4, Mem.l(a2 + (i + 1) * 4));
            }
            Mem.wl(a2 + (d0 - 1) * 4, save);          // move.l d2,(a2)
        }
    }

    // ==================================================================
    // dorots (gloom.s:1964) + rotter (gloom.s:2119)
    // ==================================================================

    public static void dorots() {
        int a6 = Mem.l(Tables.camrots2);              // move.l camrots2,a6
        int a5 = Vars.rotpolys;                       // lea rotpolys,a5  (header)
        while (true) {                                // rotloop
            a5 = Mem.l(a5);                           // move.l (a5),a5
            if (Mem.l(a5) == 0) return;               // tst.l (a5) ; beq .done
            int d0 = Mem.w(a5 + Defs.rp_speed);       // move rp_speed,d0
            if (d0 == 0) continue;                     // beq rotloop
            Mem.ww(a5 + Defs.rp_rot, Mem.uw(a5 + Defs.rp_rot) + d0); // add d0,rp_rot
            d0 = Mem.w(a5 + Defs.rp_rot);             // move rp_rot,d0
            int a2 = Mem.l(a5 + Defs.rp_first);       // first
            int d5 = Mem.uw(a5 + Defs.rp_num) - 1;    // rp_num-1
            int a1 = a2 + ((d5 << 5) & 0xffff);       // lea 0(a2,d4),a1  (previous = dernier)
            int a3 = a5 + Defs.rp_lx;                 // lea rp_lx(a5),a3

            if ((Mem.ub(a5 + Defs.rp_flags + 1) & 1) != 0) { // btst #0,rp_flags+1
                morph(a5, a2, a1, a3, d0, d5);
                continue;                             // morph → bra rotloop
            }
            // .rot
            int d6 = Mem.w(a5 + Defs.rp_cx);          // movem rp_cx,d6-d7
            int d7 = Mem.w(a5 + Defs.rp_cz);
            d0 &= 1023;                               // and #1023,d0
            int a4 = a6 + d0 * 8;                     // lea 0(a6,d0*8),a4
            int cnt = d5;
            do {                                      // .loop
                a3 = rotter(a3, a4);                  // bsr rotter
                int rx = M68k.addw(RX, d6);           // add d6,d0
                int rz = M68k.addw(RZ, d7);           // add d7,d1
                Mem.ww(a2 + Defs.zo_lx, rx); Mem.ww(a2 + Defs.zo_lz, rz); // movem d0-d1,zo_lx(a2)
                Mem.ww(a1 + Defs.zo_rx, rx); Mem.ww(a1 + Defs.zo_rz, rz); // movem d0-d1,zo_rx(a1)
                a3 = rotter(a3, a4);                  // bsr rotter  (normale)
                Mem.ww(a2 + Defs.zo_na, RX); Mem.ww(a2 + Defs.zo_nb, RZ); // movem d0-d1,zo_na(a2)
                Mem.ww(a2 + Defs.zo_a, M68k.negw(RZ)); Mem.ww(a2 + Defs.zo_b, RX); // exg/neg ; zo_a
                a1 = a2;                              // move.l a2,a1
                a2 += 32;                             // lea 32(a2),a2
            } while (cnt-- != 0);                     // dbf d5,.loop
        }
    }

    /** rotter (gloom.s:2119) : rote le vertex (a3)+ par la matrice a4 → RX/RZ ; renvoie a3 avancé. */
    public static int rotter(int a3, int a4) {
        int d0 = Mem.w(a3);                           // movem (a3)+,d0-d1
        int d1 = Mem.w(a3 + 2);
        a3 += 4;
        int d2 = d0;                                  // move d0,d2
        int d3 = d1;                                  // move d1,d3
        d0 = M68k.muls(d0, Mem.w(a4));                // muls (a4),d0
        d3 = M68k.muls(d3, Mem.w(a4 + 2));            // muls 2(a4),d3
        d0 = d0 + d3;                                 // add.l d3,d0
        d0 = d0 + d0;                                 // add.l d0,d0
        d0 = M68k.swap(d0);                           // swap d0
        d2 = M68k.muls(d2, Mem.w(a4 + 4));            // muls 4(a4),d2
        d1 = M68k.muls(d1, Mem.w(a4 + 6));            // muls 6(a4),d1
        d1 = d2 + d1;                                 // add.l d2,d1
        d1 = d1 + d1;                                 // add.l d1,d1
        d1 = M68k.swap(d1);                           // swap d1
        RX = d0; RZ = d1;
        return a3;
    }

    // ==================================================================
    // morph (gloom.s:2010)
    // ==================================================================

    private static void morph(int a5, int a2, int a1, int a3, int d0, int d5) {
        if ((short) d0 <= 0) {                        // tst d0 ; bgt .dp
            d0 = 0;                                    // moveq #0,d0
            negSpeed(a5);                              // .neg neg rp_speed
            Mem.ww(a5 + Defs.rp_rot, d0);             // .skip2 move d0,rp_rot
        } else if ((short) d0 < 0x4000) {             // cmp #$4000,d0 ; blt .skip
            // .skip : rp_rot inchangé (déjà incrémenté par dorots)
        } else {
            d0 = 0x4000;                              // move #$4000,d0
            if ((Mem.ub(a5 + Defs.rp_flags + 1) & 2) != 0) { // btst #1,rp_flags+1
                negSpeed(a5);                          // .neg
            } else {
                Mem.ww(a5 + Defs.rp_speed, 0);        // clr rp_speed
            }
            Mem.ww(a5 + Defs.rp_rot, d0);             // .skip2
        }
        // .skip
        int d4 = d0;                                  // move d0,d4
        int a2save = a2, d5save = d5;                 // movem.l a2/d5,-(a7)
        int cnt = d5;
        do {                                          // .loop  (interpolation des vertices)
            int v0 = Mem.w(a3);                       // movem (a3)+,d0-d3
            int v1 = Mem.w(a3 + 2);
            int v2 = Mem.w(a3 + 4);
            int v3 = Mem.w(a3 + 6);
            a3 += 8;
            int dd0 = M68k.muls(v0, d4);              // muls d4,d0
            dd0 = dd0 << 2;                           // lsl.l #2,d0
            dd0 = M68k.swap(dd0);                     // swap d0
            dd0 = M68k.addw(dd0, v2);                 // add d2,d0
            int dd1 = M68k.muls(v1, d4);              // muls d4,d1
            dd1 = dd1 << 2;                           // lsl.l #2,d1
            dd1 = M68k.swap(dd1);                     // swap d1
            dd1 = M68k.addw(dd1, v3);                 // add d3,d1
            Mem.ww(a2 + Defs.zo_lx, dd0); Mem.ww(a2 + Defs.zo_lz, dd1); // movem d0-d1,zo_lx(a2)
            Mem.ww(a1 + Defs.zo_rx, dd0); Mem.ww(a1 + Defs.zo_rz, dd1); // movem d0-d1,zo_rx(a1)
            a1 = a2;                                  // move.l a2,a1
            a2 += 32;                                 // lea 32(a2),a2
        } while (cnt-- != 0);                         // dbf d5,.loop
        a2 = a2save; d5 = d5save;                     // movem.l (a7)+,a2/d5
        cnt = d5;
        do {                                          // .loop2  (recalcul des normales)
            int nx = (short) (Mem.w(a2 + Defs.zo_rx) - Mem.w(a2 + Defs.zo_lx)); // move zo_rx ; sub zo_lx
            int nz = (short) (Mem.w(a2 + Defs.zo_rz) - Mem.w(a2 + Defs.zo_lz));
            calcnormvec(nx, nz);                      // bsr calcnormvec → RX,RZ
            Mem.ww(a2 + Defs.zo_na, RX); Mem.ww(a2 + Defs.zo_nb, RZ); // movem d0-d1,zo_na
            Mem.ww(a2 + Defs.zo_a, M68k.negw(RZ)); Mem.ww(a2 + Defs.zo_b, RX); // exg/neg ; zo_a
            a2 += 32;                                 // lea 32(a2),a2
        } while (cnt-- != 0);                         // dbf d5,.loop2
    }

    private static void negSpeed(int a5) {
        Mem.ww(a5 + Defs.rp_speed, M68k.negw(Mem.uw(a5 + Defs.rp_speed)));
    }

    // ==================================================================
    // calcnormvec (gloom.s:2057) : normalise (d0,d1) → RX,RZ (≈ vecteur × 32766)
    // ==================================================================

    public static void calcnormvec(int d0, int d1) {
        int d2 = M68k.muls(d0, d0);                   // move d0,d2 ; muls d2,d2
        int d3 = M68k.muls(d1, d1);                   // move d1,d3 ; muls d3,d3
        d2 = d2 + d3;                                 // add.l d3,d2  ; |v|^2
        d3 = 0x10000;                                 // move.l #$10000,d3
        while (Integer.compareUnsigned(d2, 16384) >= 0) { // .fitit cmp.l #16384,d2 ; bcs .ok
            d2 = d2 >> 1;                             // asr.l #1,d2
            d3 = (int) (((d3 & 0xffffffffL) * 92681L) >> 16); // mulu.l #92681 ; move d4,d3 ; swap d3  (×√2)
        }
        // .ok
        int a0 = Mem.l(Tables.sqr);                   // move.l sqr,a0  (table racine carrée)
        d2 = d2 & 0xfffe;                             // and #$fffe,d2
        d2 = Mem.w(a0 + d2);                          // movem 0(a0,d2),d2  (movem.w sign-étendu)
        d2 = (int) ((d2 & 0xffffffffL) * (d3 & 0xffffffffL)); // mulu.l d3,d2  (32 bits faibles)
        d2 = M68k.swap(d2);                           // swap d2  → length.w

        int absx = Math.abs((int) (short) d0);        // move d0,d3 ; bpl ; neg d3
        int absz = Math.abs((int) (short) d1);        // move d1,d4 ; bpl ; neg d4
        int big = Math.max(absx, absz);               // cmp d4,d3 ; bcc ; exg → d3=max
        int len = (short) d2;                         // d2.w
        if (Integer.compareUnsigned(len & 0xffff, big & 0xffff) < 0) { // cmp d3,d2 ; bcc .lo ; move d3,d2
            len = big;
        }
        int d2l = (short) len;                        // ext.l d2

        int rx = (d0 & 0xffff) << 16;                 // swap d0 ; clr d0
        rx = rx / d2l;                                // divs.l d2,d0
        rx = rx * 32766;                              // muls.l #32766,d0
        rx = M68k.swap(rx);                           // swap d0
        int rz = (d1 & 0xffff) << 16;                 // swap d1 ; clr d1
        rz = rz / d2l;                                // divs.l d2,d1
        rz = rz * 32766;                              // muls.l #32766,d1
        rz = M68k.swap(rz);                           // swap d1
        RX = rx; RZ = rz;
    }

    // ==================================================================
    // dodoors (gloom.s:2137)
    // ==================================================================

    public static void dodoors() {
        int a5 = Vars.doors;                          // lea doors,a5
        while (true) {                                // .loop
            a5 = Mem.l(a5);                           // move.l (a5),a5
            if (Mem.l(a5) == 0) return;               // tst.l (a5) ; beq .done
            int a0 = Mem.l(a5 + Defs.do_poly);        // do_poly
            int d0 = Mem.w(a5 + Defs.do_fracadd);     // do_fracadd
            Mem.ww(a5 + Defs.do_frac, Mem.uw(a5 + Defs.do_frac) + d0); // add d0,do_frac
            d0 = Mem.w(a5 + Defs.do_frac);            // move do_frac,d0
            int d1 = (d0 + d0) & 0xffff;              // move d0,d1 ; add d1,d1
            Mem.ww(a0 + Defs.zo_open, d1);            // move d1,zo_open(a0)  ; copy frac

            // interpolation X
            int dx = (short) (Mem.w(a5 + Defs.do_rx) - Mem.w(a5 + Defs.do_lx)); // width
            int d2 = M68k.muls(dx, d0);               // muls d0,d2
            d2 = M68k.swap(d2 << 2);                  // lsl.l #2,d2 ; swap d2
            int d3 = (short) (Mem.w(a5 + Defs.do_lx) - (short) d2);            // do_lx - d2
            Mem.ww(a0 + Defs.zo_lx, d3);              // move d3,zo_lx(a0)
            d3 = (short) (d3 + dx);                   // add d1,d3
            Mem.ww(a0 + Defs.zo_rx, d3);              // move d3,zo_rx(a0)

            // interpolation Z
            int dz = (short) (Mem.w(a5 + Defs.do_rz) - Mem.w(a5 + Defs.do_lz));
            int d2z = M68k.swap(M68k.muls(dz, d0) << 2);
            int d3z = (short) (Mem.w(a5 + Defs.do_lz) - (short) d2z);
            Mem.ww(a0 + Defs.zo_lz, d3z);
            d3z = (short) (d3z + dz);
            Mem.ww(a0 + Defs.zo_rz, d3z);

            // kill quand frac == 0 (fermée) ou $4000 (ouverte)
            if (d0 == 0 || d0 == 0x4000) {            // tst d0 ; beq .kill ; cmp #$4000 ; bne .loop
                a5 = Lists.killitem(Vars.doors, a5);  // killitem doors ; a5=prev
            }
        }
    }
}
