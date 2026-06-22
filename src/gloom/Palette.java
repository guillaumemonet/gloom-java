package gloom;

import gloom.data.Tables;

/**
 * Sous-système 03 — Palettes & table d'ombrage.
 *
 * Traduction littérale de gloom.s :
 *  - initdarktable (gloom.s:11697) : table profondeur→luminosité (via sqr) ;
 *  - calcpalettes (gloom.s:12049) : à partir de la palette de base, génère 15
 *    versions assombries (rampe de luminosité) et remplit palettes[0..15] ;
 *  - makeapal (gloom.s:12014) : version « monochrome » (luminosité) d'une palette,
 *    avec masque couleur et gamma ;
 *  - makepalettes (gloom.s:11973) : construit les palettes « flash blanc » et
 *    « flash rouge » (pointeurs palettesw/palettesr + buffers via makeapal).
 *
 * Couleur Amiga = $0RGB (4 bits/canal). Les buffers map_rgbs* et les tableaux
 * palettes/palettesw/palettesr sont dans gloom.Vars ; la table sqr dans
 * gloom.data.Tables. Registres dN/aN = int.
 *
 * REPORTÉ : initbmappal (gloom.s:11672) écrit dans la copperlist (cols1..cols4) →
 * sous-système display. remap/addpal (remapping de textures) → sous-système 04.
 */
public final class Palette {

    private Palette() {
    }

    /** initdarktable (gloom.s:11697) : darktable[z] = (sqr[z*8] >> 3) ^ 15, z=0..maxz-1. */
    public static void initdarktable() {
        int d2 = Vars.maxz - 1;                       // move #maxz-1,d2
        int a0 = Mem.l(Tables.sqr);                   // move.l sqr(pc),a0
        int a1 = Mem.l(Vars.darktable);               // move.l darktable(pc),a1
        do {                                          // .loop
            int d3 = d2;                              // move d2,d3
            d3 = M68k.lslw(d3, 3);                    // lsl #3,d3
            d3 = M68k.setw(d3, Mem.uw(a0 + M68k.uw(d3))); // move 0(a0,d3),d3
            d3 = M68k.lsrw(d3, 3);                    // lsr #3,d3
            d3 = M68k.setw(d3, d3 ^ 15);              // eor #15,d3
            Mem.ww(a1, d3); a1 += 2;                  // move d3,(a1)+
        } while (d2-- != 0);                          // dbf d2,.loop
    }

    /**
     * calcpalettes (gloom.s:12049) : la palette de base occupe [map_rgbs, map_rgbsat).
     * Ajoute 15 versions assombries (chaque canal -k, borné à 0, bit $8000 posé) à
     * partir de map_rgbsat, et remplit palettes[0..15] (0 = base). Pose map_rgbsend.
     */
    public static void calcpalettes() {
        int a2 = Vars.palettes;                       // lea palettes(pc),a2
        Mem.wl(a2, Mem.l(Vars.map_rgbs)); a2 += 4;    // move.l map_rgbs,(a2)+  ; palettes[0]
        int a1 = Mem.l(Vars.map_rgbsat);              // move.l map_rgbsat,a1
        int a3 = a1;                                  // move.l a1,a3
        int d0 = 1;                                   // moveq #1,d0  ; rgb subtract
        do {                                          // .loop
            int a0 = Mem.l(Vars.map_rgbs);            // move.l map_rgbs,a0
            Mem.wl(a2, a1); a2 += 4;                  // move.l a1,(a2)+  ; palettes[d0]
            do {                                      // .loop2
                int d1 = Mem.uw(a0); a0 += 2;         // move (a0)+,d1
                int d2 = d1;                          // move d1,d2
                int d3 = d1;                          // move d1,d3
                d1 = (d1 & 0xf00) >>> 8; d1 -= d0; if (d1 < 0) d1 = 0;  // r : and/lsr/sub/clamp
                d2 = (d2 & 0x0f0) >>> 4; d2 -= d0; if (d2 < 0) d2 = 0;  // g
                d3 = (d3 & 0x00f);       d3 -= d0; if (d3 < 0) d3 = 0;  // b
                d1 <<= 8;                             // lsl #8,d1
                d2 <<= 4;                             // lsl #4,d2
                d2 |= d3;                             // or d3,d2
                d1 |= d2;                             // or d2,d1
                d1 |= 0x8000;                         // or #$8000,d1
                Mem.ww(a1, d1); a1 += 2;              // move d1,(a1)+
            } while (Integer.compareUnsigned(a0, a3) < 0); // cmp.l a3,a0 ; bcs.s .loop2
            d0++;                                     // addq #1,d0
        } while (d0 < 16);                            // cmp #16,d0 ; bcs.s .loop
        Mem.wl(Vars.map_rgbsend, a1);                 // move.l a1,map_rgbsend
    }

    /**
     * makeapal (gloom.s:12014) : a1=dest, d1=masque couleur, d2=gamma. Pour chaque
     * couleur de [map_rgbs, map_rgbsend), calcule la luminosité (r+g+b+gamma)/3, la
     * réplique sur les 3 canaux, applique le masque, et écrit dans a1.
     */
    public static void makeapal(int a1, int d1, int d2) {
        int a0 = Mem.l(Vars.map_rgbs);                // move.l map_rgbs(pc),a0
        do {                                          // .loop
            int d4 = Mem.uw(a0); a0 += 2;             // move (a0)+,d4
            int d5 = d4;                              // move d4,d5
            int d6 = d4;                              // move d4,d6
            d4 = (d4 & 0xf00) >>> 8;                  // and #$f00,d4 ; lsr #8,d4  (r)
            d5 = (d5 & 0x0f0) >>> 4;                  // and #$0f0,d5 ; lsr #4,d5  (g)
            d6 = (d6 & 0x00f);                        // and #$00f,d6              (b)
            d5 += d6;                                 // add d6,d5
            d4 += d5;                                 // add d5,d4
            d4 += d2;                                 // add d2,d4  ; +gamma
            if (Integer.compareUnsigned(d4 & 0xffff, 16 * 3) >= 0) d4 = 16 * 3 - 1; // cmp #48 ; bcs .bok
            d4 = (d4 & 0xffff) / 3;                   // ext.l d4 ; divu #3,d4  (quotient)
            d5 = d4;                                  // move d4,d5
            d6 = d4;                                  // move d4,d6
            d4 <<= 8;                                 // lsl #8,d4
            d5 <<= 4;                                 // lsl #4,d5
            d5 |= d6;                                 // or d6,d5
            d4 |= d5;                                 // or d5,d4  ; RGB
            d4 &= d1;                                 // and d1,d4
            Mem.ww(a1, d4); a1 += 2;                  // move d4,(a1)+
        } while (Integer.compareUnsigned(a0, Mem.l(Vars.map_rgbsend)) < 0); // cmp.l map_rgbsend,a0 ; bcs
    }

    /**
     * makepalettes (gloom.s:11973) : construit palettesw/palettesr (pointeurs rebasés
     * sur map_rgbsw/map_rgbsr) puis remplit ces buffers via makeapal (blanc $ffff/0,
     * rouge $ff00/16).
     */
    public static void makepalettes() {
        int a0 = Vars.palettes;                       // lea palettes(pc),a0
        int d0 = Mem.l(Vars.map_rgbs);                // move.l map_rgbs(pc),d0
        int a1 = Vars.palettesw;                      // lea palettesw(pc),a1
        int d1 = Mem.l(Vars.map_rgbsw);               // move.l map_rgbsw(pc),d1
        int a2 = Vars.palettesr;                      // lea palettesr(pc),a2
        int d2 = Mem.l(Vars.map_rgbsr);               // move.l map_rgbsr(pc),d2
        int d7 = 15;                                  // moveq #15,d7
        do {                                          // .loop
            int d3 = Mem.l(a0); a0 += 4;              // move.l (a0)+,d3
            d3 -= d0;                                 // sub.l d0,d3  ; offset
            int d4 = d3;                              // move.l d3,d4
            d3 += d1;                                 // add.l d1,d3
            Mem.wl(a1, d3); a1 += 4;                  // move.l d3,(a1)+
            d4 += d2;                                 // add.l d2,d4
            Mem.wl(a2, d4); a2 += 4;                  // move.l d4,(a2)+
        } while (d7-- != 0);                          // dbf d7,.loop  (16)

        // d7 = (map_rgbsend-map_rgbs)/2 - 1 (calculé par l'original mais inutilisé par makeapal)
        int d7b = Mem.l(Vars.map_rgbsend);            // move.l map_rgbsend(pc),d7
        d7b -= Mem.l(Vars.map_rgbs);                  // sub.l map_rgbs(pc),d7
        d7b = M68k.lsrw(d7b, 1);                      // lsr #1,d7
        d7b = M68k.subw(d7b, 1);                      // subq #1,d7

        makeapal(Mem.l(Vars.map_rgbsw), 0xffff, 0);   // map_rgbsw, #$ffff, #0
        makeapal(Mem.l(Vars.map_rgbsr), 0xff00, 16);  // map_rgbsr, #$ff00, #16
    }
}
