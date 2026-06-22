package gloom;

import gloom.data.Tables;

/**
 * Sous-système 05 — Rendu (cœur). Étape 05a : caméra + collecte des murs visibles.
 *
 * Traduction littérale de gloom.s :
 *  - incframe (gloom.s:5625) : compteur de frame (+ reset des zo_done au wrap) ;
 *  - calccamera (gloom.s:5904) : pose camx/y/z/r et les matrices cm/icm depuis camrots ;
 *  - makewalls (gloom.s:6136) : parcourt la grille (gridoffs) + les rotpolys et collecte
 *    les murs visibles dans `inlist` ;
 *  - dothezone/dothezone2/dothezone3 (gloom.s:6378/6333/6403) : transforme une zone en
 *    espace caméra, rejette (Z / back-face), projette les X écran, range un `wl` ;
 *  - makeoutlist (gloom.s:6208) : tri par occultation → `outlist` (avant→arrière).
 *
 * Pure géométrie en virgule fixe : aucune dépendance à l'affichage. Les `wl` sont alloués
 * dans l'arène de frame `memat`. Voir docs/ARCHITECTURE.md §4.
 *
 * Constantes : exshft=3, grdshft=8, maxz=16<<7. focshft dépend de la VARIANTE de moteur :
 * 6 pour gloom.s (chunky), 7 pour gloom2.s (planaire/AGA) — cf. {@link #configure(boolean)}.
 */
public final class Render {

    /** focshft : décalage de focale. gloom.s=6, gloom2.s=7. Modifié par {@link #configure(boolean)}. */
    static int focshft = 6;
    /** Table castrots active (rayons par colonne). Dépend de la variante (castrots64/128). */
    static int castrotsTable = 0;          // 0 → utilise Tables.castrots (résolu paresseusement)
    private static final int exshft = 3;
    private static final int grdshft = 8;
    private static final int maxz = 16 << 7;   // 2048

    /**
     * Sélectionne la variante de moteur de rendu : false=gloom.s (focshft 6, castrots64),
     * true=gloom2.s (focshft 7, castrots128). Les deux partagent le MÊME rasteriseur ; seuls
     * la focale et la table de rayons diffèrent (le reste de gloom2 = affichage planaire/c2p,
     * remplacé côté hôte par le framebuffer RGB, donc sans objet ici).
     */
    public static void configure(boolean gloom2) {
        focshft = gloom2 ? 7 : 6;
        castrotsTable = gloom2 ? Tables.castrots128 : Tables.castrots;
    }

    private Render() {
    }

    // ==================================================================
    // incframe (gloom.s:5625)
    // ==================================================================

    public static void incframe() {
        int f = M68k.w(Mem.uw(Vars.frame) + 1);       // addq #1,frame
        Mem.ww(Vars.frame, f);
        if (f != 0) return;                            // bne .skip
        // frame a bouclé : remet tous les zo_done à 0
        int a0 = Mem.l(Vars.map_poly);                 // move.l map_poly,a0
        int a1 = Mem.l(Vars.map_ppnt);                 // move.l map_ppnt,a1  (fin des polys)
        while (Integer.compareUnsigned(a0, a1) < 0) {  // cmp.l a1,a0 ; bcs .loop
            Mem.ww(a0, 0);                             // move d0,(a0)  ; zo_done = 0
            a0 += 32;                                  // add.l d1,a0   (d1=32)
        }
        Mem.ww(Vars.frame, M68k.w(Mem.uw(Vars.frame) + 1)); // addq #1,frame (→ 1)
    }

    // ==================================================================
    // calccamera (gloom.s:5904) : a0 = objet joueur
    // ==================================================================

    public static void calccamera(int a0) {
        int a2 = Mem.l(Tables.camrots);                // move.l camrots,a2
        Mem.ww(Vars.camx, Mem.w(a0 + Defs.ob_x));      // move ob_x(a0),camx  (mot fort = entier)
        int d0 = Mem.w(a0 + Defs.ob_y);                // move ob_y(a0),d0
        d0 = M68k.addw(d0, Mem.w(a0 + Defs.ob_eyey));  // add ob_eyey(a0),d0
        // bounce
        int d1 = Mem.uw(a0 + Defs.ob_bounce) & 255;    // move ob_bounce ; and #255,d1
        d1 = Mem.w(a2 + d1 * 8 + 2);                    // move 2(a2,d1*8),d1  (composante sin)
        d1 = M68k.muls(d1, 20);                        // muls #20,d1
        d1 = M68k.swap(d1);                            // swap d1
        d0 = M68k.addw(d0, d1);                        // add d1,d0
        Mem.ww(Vars.camy, d0);                         // move d0,camy
        Mem.ww(Vars.camz, Mem.w(a0 + Defs.ob_z));      // move ob_z(a0),camz
        d0 = Mem.uw(a0 + Defs.ob_rot) & 255;           // move ob_rot(a0),d0 ; and #255,d0
        Mem.ww(Vars.camr, d0);                         // move d0,camr
        int a1 = Mem.l(Tables.camrots) + d0 * 8;       // lea 0(a1,d0*8),a1
        Mem.wl(Vars.cm1, Mem.l(a1));                   // move.l (a1)+,cm1
        Mem.wl(Vars.cm3, Mem.l(a1 + 4));               // move.l (a1),cm3
        // matrice inverse : camrots[(-rot)&255]
        d0 = (-d0) & 255;                              // neg d0 ; and #255,d0
        a1 = Mem.l(Tables.camrots) + d0 * 8;
        Mem.wl(Vars.icm1, Mem.l(a1));                  // move.l (a1)+,icm1
        Mem.wl(Vars.icm3, Mem.l(a1 + 4));              // move.l (a1),icm3
    }

    // ==================================================================
    // makewalls (gloom.s:6136)
    // ==================================================================

    public static void makewalls() {
        incframe();                                    // bsr incframe
        Mem.wl(Vars.inlist, 0);                        // clr.l inlist
        Mem.wl(Vars.inlistf, Vars.inlist);             // move.l #inlist,inlistf

        int a4 = Mem.l(Vars.map_poly);                 // map_poly
        int a3 = Mem.l(Vars.map_ppnt);                 // map_ppnt
        int a2 = Mem.l(Vars.map_grid);                 // map_grid
        int d6 = Mem.uw(Vars.camx) >>> grdshft;        // camx >> grdshft  (lsr.w, non signé)
        int d7 = Mem.uw(Vars.camz) >>> grdshft;        // camz >> grdshft
        int a6 = Tables.gridoffs;                      // lea gridoffs,a6
        int d5 = (Tables.gridoffsLen >> 2) - 1;        // (gridoffsf-gridoffs)/4 - 1

        int gi = a6;
        do {                                           // .loop  (parcours gridoffs)
            int d0 = Mem.w(gi);                        // movem (a6)+,d0-d1
            int d1 = Mem.w(gi + 2);
            gi += 4;
            d0 += d6;                                  // add d6,d0
            if (Integer.compareUnsigned(d0, 32) >= 0) continue; // cmp #32,d0 ; bcc .skip
            d1 += d7;                                  // add d7,d1
            if (Integer.compareUnsigned(d1, 32) >= 0) continue; // cmp #32,d1 ; bcc .skip

            d1 = (d1 << 5) & 0xffff;                   // lsl #5,d1  (Y*32)
            d0 = (d0 + d1) & 0xffff;                   // add d1,d0
            int a0 = a2 + d0 * 8;                      // lea 0(a2,d0*8),a0  (cellule grille)
            int d4 = Mem.w(a0);                        // move (a0)+,d4  ; nb polys
            if ((short) d4 < 0) continue;              // bmi .skip
            int off = Mem.uw(a0 + 2);                  // move (a0),d0  ; offset des polys
            int pp = a3 + off * 2;                     // lea 0(a3,d0*2),a0
            do {                                       // .loop2
                int poly = Mem.uw(pp); pp += 2;        // move (a0)+,d0  ; poly#
                int a1 = a4 + ((poly << 5) & 0x1fffff);// lsl #5,d0 ; lea 0(a4,d0),a1
                dothezone(a1);                         // bsr dothezone
            } while (d4-- != 0);                       // dbf d4,.loop2
        } while (d5-- != 0);                           // dbf d5,.loop

        // rotpolys / morphs
        int rp = Vars.rotpolys;                        // lea rotpolys,a3
        while (true) {                                 // .loop3
            rp = Mem.l(rp);                            // move.l (a3),a3
            if (Mem.l(rp) == 0) break;                 // tst.l (a3) ; beq .rpdone
            int pa = Mem.l(rp + Defs.rp_first);        // rp_first
            int d4 = Mem.uw(rp + Defs.rp_num) - 1;     // rp_num-1
            do {                                       // .loop4
                dothezone2(pa);                        // bsr dothezone2
                pa += 32;                              // lea 32(a4),a4
            } while (d4-- != 0);                       // dbf d4,.loop4
        }
        makeoutlist();
    }

    // ==================================================================
    // dothezone / dothezone2 / dothezone3 (gloom.s:6378/6333/6403)
    // ==================================================================

    /** dothezone : cam-relative puis transform. */
    public static void dothezone(int a1) {
        if (Mem.uw(a1 + Defs.zo_done) == Mem.uw(Vars.frame)) return; // frame dedup
        Mem.ww(a1 + Defs.zo_done, Mem.uw(Vars.frame));
        if ((short) Mem.w(a1 + Defs.zo_open) < 0) return;            // tst zo_open ; bmi
        int camxv = Mem.w(Vars.camx), camzv = Mem.w(Vars.camz);
        int d0 = M68k.w(Mem.w(a1 + Defs.zo_lx) - camxv);             // sub d6,d0
        int d1 = M68k.w(Mem.w(a1 + Defs.zo_lz) - camzv);             // sub d7,d1
        int d2 = M68k.w(Mem.w(a1 + Defs.zo_rx) - camxv);             // sub d6,d2
        int d3 = M68k.w(Mem.w(a1 + Defs.zo_rz) - camzv);             // sub d7,d3
        dothezone3(a1, d0, d1, d2, d3);
    }

    /** dothezone2 : cam-relative + bornes maxz, puis transform (rotpolys). */
    public static void dothezone2(int a1) {
        if (Mem.uw(a1 + Defs.zo_done) == Mem.uw(Vars.frame)) return;
        Mem.ww(a1 + Defs.zo_done, Mem.uw(Vars.frame));
        if ((short) Mem.w(a1 + Defs.zo_open) < 0) return;
        int camxv = Mem.w(Vars.camx), camzv = Mem.w(Vars.camz);
        int d0 = M68k.w(Mem.w(a1 + Defs.zo_lx) - camxv);
        int d1 = M68k.w(Mem.w(a1 + Defs.zo_lz) - camzv);
        int d2 = M68k.w(Mem.w(a1 + Defs.zo_rx) - camxv);
        int d3 = M68k.w(Mem.w(a1 + Defs.zo_rz) - camzv);
        int d4 = maxz, d5 = -maxz;
        if (d0 >= d4 || d0 <= d5) return;              // bornes ±maxz sur les 4 coords
        if (d1 >= d4 || d1 <= d5) return;
        if (d2 >= d4 || d2 <= d5) return;
        if (d3 >= d4) return;
        if (d3 <= d5) return;                          // cmp d5,d3 ; bgt dothezone3 (sinon .rts2)
        dothezone3(a1, d0, d1, d2, d3);
    }

    /** dothezone3 : transform caméra, back-face, projection, rangement du wl. */
    public static void dothezone3(int a1, int d0, int d1, int d2, int d3) {
        int cm1 = Mem.w(Vars.cm1), cm2 = Mem.w(Vars.cm2), cm3 = Mem.w(Vars.cm3), cm4 = Mem.w(Vars.cm4);
        // LX
        int d4 = d0, d5 = d1;
        d0 = M68k.swap((M68k.muls(d0, cm1) + M68k.muls(d5, cm2)) << 1);   // (lx*cm1+lz*cm2)*2>>16
        d1 = M68k.swap((M68k.muls(d4, cm3) + M68k.muls(d1, cm4)) << 1);   // LZ
        d4 = d2; d5 = d3;
        d2 = M68k.swap((M68k.muls(d2, cm1) + M68k.muls(d5, cm2)) << 1);   // RX
        d3 = M68k.swap((M68k.muls(d4, cm3) + M68k.muls(d3, cm4)) << 1);   // RZ

        // Z checks
        if (!((short) d1 > 0)) {                       // tst d1 ; bgt .zok
            if ((short) d3 <= 0) return;               // tst d3 ; ble .skip2
        }
        if (!((short) d1 < maxz)) {                    // cmp #maxz,d1 ; blt .zok2
            if ((short) d3 >= maxz) return;            // cmp #maxz,d3 ; bge .skip2
        }

        // précision étendue (rol.l #exshft)
        d0 = Integer.rotateLeft(d0, exshft);
        d1 = Integer.rotateLeft(d1, exshft);
        d2 = Integer.rotateLeft(d2, exshft);
        d3 = Integer.rotateLeft(d3, exshft);

        // back-face : a=LZ-RZ, b=RX-LX, c=LX*a+LZ*b
        int a = M68k.w(d1 - d3);                       // move d1,d4 ; sub d3,d4
        int b = M68k.w(d2 - d0);                       // move d2,d5 ; sub d0,d5
        int c = M68k.muls(d0, a) + M68k.muls(d1, b);   // LX*a + LZ*b
        if (c < 0) return;                             // bpl .front ; sinon back-face → skip

        int a5 = Mem.l(Vars.memat);                    // move.l memat,a5
        Mem.ww(a5 + Defs.wl_lx, d0); Mem.ww(a5 + Defs.wl_lz, d1);   // movem d0-d5,wl_lx
        Mem.ww(a5 + Defs.wl_rx, d2); Mem.ww(a5 + Defs.wl_rz, d3);
        Mem.ww(a5 + Defs.wl_a, a);   Mem.ww(a5 + Defs.wl_b, b);
        Mem.wl(a5 + Defs.wl_c, c);                     // move.l d6,wl_c

        // projection X gauche (lsx)
        int minxv = Mem.w(Vars.minx), maxxv = Mem.w(Vars.maxx);
        if ((short) d1 > 0) {                          // .z1ok
            int q = (((int) (short) d0) << focshft);
            int div = (short) d1;
            q = q / div;                               // divs d1,d0
            if (q < -32768 || q > 32767) {             // bvs .ov1
                Mem.ww(a5 + Defs.wl_lsx, minxv);
            } else {
                q = M68k.w(q - 1);                     // subq #1,d0
                if (q >= maxxv) return;                // cmp maxx,d0 ; bge .skip2
                Mem.ww(a5 + Defs.wl_lsx, q);
            }
        } else {
            Mem.ww(a5 + Defs.wl_lsx, minxv);           // .ov1
        }
        // projection X droite (rsx)
        if ((short) d3 > 0) {                          // .z2ok
            int q = (((int) (short) d2) << focshft);
            int div = (short) d3;
            q = q / div;                               // divs d3,d2
            if (q < -32768 || q > 32767) {             // bvs .ov2
                Mem.ww(a5 + Defs.wl_rsx, maxxv);
            } else {
                q = M68k.w(q + 1);                     // addq #1,d2
                if (q < minxv) return;                 // cmp minx,d2 ; blt .skip2
                Mem.ww(a5 + Defs.wl_rsx, q);
            }
        } else {
            Mem.ww(a5 + Defs.wl_rsx, maxxv);           // .ov2
        }

        // near/far Z = min/max(LZ,RZ)
        int nz = d1, fz = d3;
        if ((short) d3 < (short) d1) { nz = d3; fz = d1; }  // cmp d1,d3 ; bge .zskp ; exg
        Mem.ww(a5 + Defs.wl_nz, nz); Mem.ww(a5 + Defs.wl_fz, fz);

        // textures / scale / open
        Mem.wl(a5 + Defs.wl_t, Mem.l(a1 + Defs.zo_t));          // move.l zo_t(a1),wl_t
        Mem.wl(a5 + Defs.wl_t + 4, Mem.l(a1 + Defs.zo_t + 4));  // move.l zo_t+4(a1),wl_t+4
        Mem.ww(a5 + Defs.wl_sc, Mem.uw(a1 + Defs.zo_sc));       // move zo_sc(a1),wl_sc
        Mem.ww(a5 + Defs.wl_open, Mem.uw(a1 + Defs.zo_open));   // move zo_open(a1),wl_open

        // lien en fin d'inlist
        Mem.wl(a5, 0);                                 // clr.l (a5)
        int tail = Mem.l(Vars.inlistf);                // move.l inlistf,a1
        Mem.wl(tail, a5);                              // move.l a5,(a1)
        Mem.wl(Vars.inlistf, a5);                      // move.l a5,inlistf
        Mem.wl(Vars.memat, a5 + Defs.wl_size);         // add.l #wl_size,memat
    }

    // ==================================================================
    // makeoutlist (gloom.s:6208) : tri par occultation
    // ==================================================================

    public static void makeoutlist() {
        Mem.wl(Vars.outlist, 0);                       // clr.l outlist
        Mem.wl(Vars.outlistf, Vars.outlist);           // move.l #outlist,outlistf

        while (true) {                                 // .loop
            int a0 = Mem.l(Vars.inlist);               // move.l (inlist),a0  (1er candidat)
            if (a0 == 0) return;                       // beq .done
            int a2 = Vars.inlist;                      // a2 = slot prédécesseur de a0 (&inlist)
            // Passe avant UNIQUE : a0 « monte » vers l'avant à chaque mur trouvé devant lui.
            int a1prev = Vars.inlist;                  // lea inlist,a1
            while (true) {                             // .loop2
                int a1 = Mem.l(a1prev);                // move.l (a1),d0
                if (a1 == 0) break;                    // beq .none
                if (a1 != a0 && inFront(a0, a1)) {     // (pas soi-même) ; a1 devant a0 ?
                    a0 = a1;                           // .swap move.l a1,a0
                    a2 = a1prev;                       // move.l a3,a2  (prédécesseur de a1)
                }
                a1prev = a1;                           // le scan continue depuis a1 (PAS de restart)
            }
            // .none : rien devant a0 → on le sort vers outlist
            Mem.wl(a2, Mem.l(a0));                     // move.l (a0),(a2)  ; délie d'inlist
            Mem.wl(a0, 0);                             // clr.l (a0)
            int tail = Mem.l(Vars.outlistf);           // move.l outlistf,a2
            Mem.wl(tail, a0);                          // move.l a0,(a2)
            Mem.wl(Vars.outlistf, a0);                 // move.l a0,outlistf
        }
    }

    /** Renvoie vrai si a1 occulte (est devant) a0 (cœur de .loop2 de makeoutlist). */
    private static boolean inFront(int a0, int a1) {
        // chevauchement X écran
        if (Mem.w(a0 + Defs.wl_rsx) < Mem.w(a1 + Defs.wl_lsx)) return false; // blt .loop2
        if (Mem.w(a0 + Defs.wl_lsx) > Mem.w(a1 + Defs.wl_rsx)) return false; // bgt .loop2
        // chevauchement Z near/far
        if (Mem.w(a1 + Defs.wl_nz) >= Mem.w(a0 + Defs.wl_fz)) return false;  // bge .loop2 (derrière)
        if (Mem.w(a1 + Defs.wl_fz) <= Mem.w(a0 + Defs.wl_nz)) return true;   // ble .swap (devant)
        if (Mem.uw(a1 + Defs.wl_open) != 0) return true;                     // bne .swap

        // teste les points de a0 contre la ligne de a1
        int d5 = Mem.w(a1 + Defs.wl_a), d6 = Mem.w(a1 + Defs.wl_b);
        int d7 = Mem.l(a1 + Defs.wl_c);
        int e0 = (M68k.muls(M68k.w(Mem.w(a1 + Defs.wl_lx) - Mem.w(a0 + Defs.wl_lx)), d5)
                + M68k.muls(M68k.w(Mem.w(a1 + Defs.wl_lz) - Mem.w(a0 + Defs.wl_lz)), d6)) ^ d7;
        int e1 = (M68k.muls(M68k.w(Mem.w(a1 + Defs.wl_lx) - Mem.w(a0 + Defs.wl_rx)), d5)
                + M68k.muls(M68k.w(Mem.w(a1 + Defs.wl_lz) - Mem.w(a0 + Defs.wl_rz)), d6)) ^ d7;
        if ((e0 | e1) >= 0) return false;              // both a0 devant a1 → no swap (.loop2)
        if ((e0 & e1) < 0) return true;                // both a0 derrière a1 → .swap

        // teste les points de a1 contre la ligne de a0
        d5 = Mem.w(a0 + Defs.wl_a); d6 = Mem.w(a0 + Defs.wl_b);
        d7 = Mem.l(a0 + Defs.wl_c);
        int f0 = (M68k.muls(M68k.w(Mem.w(a0 + Defs.wl_lx) - Mem.w(a1 + Defs.wl_lx)), d5)
                + M68k.muls(M68k.w(Mem.w(a0 + Defs.wl_lz) - Mem.w(a1 + Defs.wl_lz)), d6)) ^ d7;
        int f1 = (M68k.muls(M68k.w(Mem.w(a0 + Defs.wl_lx) - Mem.w(a1 + Defs.wl_rx)), d5)
                + M68k.muls(M68k.w(Mem.w(a0 + Defs.wl_lz) - Mem.w(a1 + Defs.wl_rz)), d6)) ^ d7;
        if ((f0 & f1) < 0) return false;               // both a1 derrière a0 → .loop2
        if ((f0 | f1) < 0) return false;               // .loop2
        return true;                                   // chute dans .swap → a1 occulte a0
    }

    // ==================================================================
    // 05b — Rasterisation des murs
    //
    // DÉCISION DE PORTAGE : l'affichage d'origine est un « copper-chunky » (chaque pixel
    // = un poke de registre couleur dans la copperlist). On le REMPLACE par un framebuffer
    // plat alloué dans Mem (mots $0RGB) : cop = base, copmod = octets/ligne (= width*2),
    // coloffs[col] = col*2. drawstrip reste donc quasi-littéral (écrit un mot, avance de
    // copmod) ; seuls le CLS par blitter et le banking copper disparaissent (cf. ARCHITECTURE
    // §0.3/§4). castwalls (calcul des `vd`) est porté fidèlement.
    // ==================================================================

    /** Prépare un framebuffer plat de w×h dans Mem et pose les globaux d'affichage. */
    public static void setupFramebuffer(int w, int h) {
        int fb = Mem.alloc(w * h * 2);                 // mots $0RGB
        Mem.wl(Vars.cop, fb);
        Mem.ww(Vars.copmod, w * 2);                    // stride vertical en octets
        Mem.ww(Vars.width, w);
        Mem.ww(Vars.hite, h);
        Mem.ww(Vars.maxx, w / 2);                      // midx = maxx
        Mem.ww(Vars.minx, -(w / 2));
        Mem.ww(Vars.maxy, h / 2);                      // midy = maxy
        Mem.ww(Vars.miny, -(h / 2));
        Mem.wl(Vars.vertdraws, Mem.alloc(w * Defs.vd_size));
        for (int col = 0; col < 320; col++) {
            Mem.wl(Vars.coloffs + col * 4, col * 2);   // offset framebuffer de la colonne
        }
    }

    /** Efface le framebuffer (couleur de fond). Remplace le CLS par blitter de drawstrip. */
    public static void clearFramebuffer(int rgb) {
        int fb = Mem.l(Vars.cop);
        int n = Mem.uw(Vars.width) * Mem.uw(Vars.hite);
        for (int i = 0; i < n; i++) Mem.ww(fb + i * 2, rgb);
    }

    /** divu 32/16 → renvoie (reste<<16)|quotient, ou -1 si débordement (bvs → .dfix). */
    private static int divuRaw(int dividend, int divisorWord) {
        int div = divisorWord & 0xffff;
        if (div == 0) return -1;
        long dd = dividend & 0xffffffffL;
        long q = dd / div;
        if (q > 0xffff) return -1;
        long r = dd % div;
        return (int) ((r << 16) | q);
    }

    /** divs 32/16 → quotient (overflow non détecté ici ; appels gardés). */
    private static int divs16(int dividend, int divisorWord) {
        return dividend / (short) divisorWord;
    }

    // ------------------------------------------------------------------
    // castwalls (gloom.s:6679) — ray-cast par colonne, remplit les `vd`
    // ------------------------------------------------------------------

    public static void castwalls() {
        int ct = castrotsTable != 0 ? castrotsTable : Tables.castrots;  // table selon la variante
        int a6 = Mem.l(ct) + Mem.w(Vars.minx) * 8;     // castrots + minx*8
        int d7 = Mem.w(Vars.minx);                     // colonne courante (centrée)
        int a4 = Mem.l(Vars.vertdraws);                // tableau vd

        while (true) {                                 // .loop (par colonne)
            int a5 = Vars.outlist;                     // lea outlist,a5
            boolean filled = false;
            inner:
            while (true) {                             // .loop2 (cherche le mur couvrant)
                a5 = Mem.l(a5);                        // move.l (a5),a5
                if (a5 == 0) break;                    // beq .empty
                if (d7 < Mem.w(a5 + Defs.wl_lsx)) continue; // cmp wl_lsx,d7 ; blt
                if (d7 > Mem.w(a5 + Defs.wl_rsx)) continue; // cmp wl_rsx,d7 ; bgt

                // LX = wl_lx*cr0 + wl_lz*cr1
                int d0 = M68k.muls(Mem.w(a5 + Defs.wl_lx), Mem.w(a6))
                       + M68k.muls(Mem.w(a5 + Defs.wl_lz), Mem.w(a6 + 2));
                if (d0 > 0) continue;                  // bgt .loop2
                // RX = wl_rx*cr0 + wl_rz*cr1
                int d1 = M68k.muls(Mem.w(a5 + Defs.wl_rx), Mem.w(a6))
                       + M68k.muls(Mem.w(a5 + Defs.wl_rz), Mem.w(a6 + 2));
                if (d1 < 0) continue;                  // blt .loop2

                d1 = d1 - d0;                          // sub.l d0,d1
                d1 = M68k.swap(d1);                    // swap d1
                int frac;
                if ((short) d1 <= 0) {                 // tst d1 ; ble .dfix
                    frac = M68k.lsrw(-1, 1);           // .dfix moveq #-1,d0 ; lsr #1
                } else {
                    int q = divuRaw(-d0, d1);          // neg.l d0 ; divu d1,d0 ; bvs .dfix
                    frac = M68k.lsrw(q, 1);            // lsr #1,d0  (frac non signé)
                }
                frac &= 0xffff;
                if (Integer.compareUnsigned(frac, Mem.uw(a5 + Defs.wl_open)) < 0) continue; // cmp wl_open ; bcs

                // Z = lz + frac*(rz-lz)
                int lz = (M68k.muls(Mem.w(a5 + Defs.wl_lx), Mem.w(a6 + 4))
                        + M68k.muls(Mem.w(a5 + Defs.wl_lz), Mem.w(a6 + 6))) << 1;
                int rz = (M68k.muls(Mem.w(a5 + Defs.wl_rx), Mem.w(a6 + 4))
                        + M68k.muls(Mem.w(a5 + Defs.wl_rz), Mem.w(a6 + 6))) << 1;
                int d2 = rz - lz;                      // sub.l d1,d2
                d2 = M68k.swap(d2);                    // swap d2
                d2 = M68k.muls(d2, frac);              // muls d0,d2
                d2 = (d2 << 1) + lz;                   // add.l d2,d2 ; add.l d1,d2
                d2 = M68k.swap(d2);                    // swap d2  → Z (exfixed)
                if ((short) d2 < 8) continue;          // cmp #exone,d2 ; blt
                if (Integer.compareUnsigned(d2 & 0xffff, (maxz << exshft) & 0xffff) >= 0) break; // cmp #maxz<<exshft ; bcs .zisok sinon .empty

                boolean seethru = fillColumn(a4, a5, frac, d2, d7);   // .zisok
                if (seethru) continue;                 // strip ajouté : continue à chercher le mur SOLIDE derrière
                filled = true;                         // mur solide → vd principal rempli
                break;
            }
            if (!filled && a5 == 0) {                  // .empty
                Mem.ww(a4 + Defs.vd_z, 32767);
                Mem.wl(a4 + Defs.vd_data, 0);
            }
            // .next
            a4 += Defs.vd_size;
            a6 += 8;
            d7 += 1;
            if (d7 >= Mem.w(Vars.maxx)) return;        // cmp maxx,d7 ; blt .loop
        }
    }

    /**
     * .zisok (gloom.s:6753) : sélection de colonne de texture + remplissage du vd.
     * Renvoie true si la colonne est SEE-THROUGH (un strip a été ajouté à la shapelist via
     * makestrip et le vd principal a4 n'a PAS été rempli) → l'appelant doit continuer à scanner
     * les murs pour trouver le mur SOLIDE derrière (cf. gloom.s:6848 `cmp.l a0,a4 ; bne .loop2`).
     */
    private static boolean fillColumn(int a4, int a5, int frac, int d2z, int d7) {
        int a0 = a4;                                   // move.l a4,a0
        int d0 = frac;
        int d1 = Mem.w(a5 + Defs.wl_sc);               // wl_sc
        if ((short) d1 > 0) {                          // bgt .mul
            d0 = M68k.mulu(d1, d0);                     // mulu d1,d0
        } else {
            d1 = -d1;                                  // neg d1
            d0 = (d0 << 1) >>> d1;                      // ext.l d0 ; add.l d0,d0 ; lsr.l d1,d0
        }
        d1 = M68k.swap(d0) & 7;                        // move.l d0,d1 ; swap d1 ; and #7,d1
        d1 = Mem.ub(a5 + Defs.wl_t + d1);              // move.b wl_t(a5,d1),d1  (numéro de texture)
        int a3 = Mem.l(Vars.textures + d1 * 4);        // textures[d1]
        d0 = (d0 << 6);                                // lsl.l #6,d0
        d0 = M68k.swap(d0) & 63;                        // swap d0 ; and #63  (colonne 0..63)
        a3 += d0 * 64 + d0;                            // move d0,d1 ; lsl #6,d0 ; add d1,d0 ; add d0,a3
        int z = M68k.asrw(d2z, exshft) & 0xffff;       // lsr #exshft,d2  → Z écran
        // octet d'en-tête de colonne : !=0 → strip (see-through, shapelist) ; 0 → solide
        if (Mem.ub(a3) != 0) {                         // tst.b (a3)+ ; bne makestrip
            a3 += 1;
            a0 = makestrip(d7, z);                     // bsr makestrip (a0 = vd alloué dans l'arène)
        } else {
            a3 += 1;                                   // tst.b (a3)+  (saute l'en-tête)
        }
        Mem.wl(a0 + Defs.vd_data, a3);                 // .solid move.l a3,vd_data(a0)

        // vd_z / vd_pal (ombrage par distance)
        int d3 = Mem.uw(Mem.l(Vars.darktable) + z * 2);// darktable[Z]
        Mem.ww(a0 + Defs.vd_z, z);                     // movem d2-d3,vd_z(a0)
        Mem.ww(a0 + Defs.vd_pal, d3);

        // vd_y (= y1, top centré) et vd_h (hauteur)
        int camy = Mem.w(Vars.camy);
        int y1 = divs16(((short) (-256 - camy)) << focshft, z);  // (-256-camy)*64 / Z
        int y2 = divs16(((short) (-camy)) << focshft, z);        // (-camy)*64 / Z
        int hh = M68k.w(y2 - y1);                       // sub d3,d4 ; hauteur
        Mem.ww(a0 + Defs.vd_y, y1);                    // movem d3-d4,vd_y(a0)
        Mem.ww(a0 + Defs.vd_h, hh);

        // vd_ystep (gloom.s:6829-6842 = block2). IMPORTANT : le bloc 6818-6827 (block1, le
        // numérateur 64<<17/(2h+1)) est SAUTÉ — les `elseif` non appariés de GenAm basculent
        // l'assemblage (block0 ON, block1 OFF, block2 ON). block2 consomme d5 = -256-camy
        // sauvegardé par block0 (`move d3,d5`), PAS le résultat de block1.
        int d5 = M68k.negw((short) (-256 - camy));      // d5 = -256-camy ; neg d5 (.w)
        int adj = ((short) camy <= -128) ? -1 : 0;      // cmp #-128,camy ; sle d4 ; ext d4
        d5 = M68k.addw(d5, adj);                         // add d4,d5
        d5 = M68k.swap(d5);                             // swap d5
        d5 = M68k.setw(d5, 0);                           // clr d5 (efface le mot bas)
        int d3b = (short) M68k.negw(y1);                 // neg d3 ; ext.l d3  → -y1
        if (d3b != 0) {
            d5 = (int) ((d5 & 0xffffffffL) / (d3b & 0xffffffffL)); // divu.l d3,d5
        }
        d5 = d5 >> 2;                                    // asr.l #2,d5
        Mem.wl(a0 + Defs.vd_ystep, d5);                  // move.l d5,vd_ystep(a0)
        return a0 != a4;                                 // cmp.l a0,a4 : see-through si a0 (strip) != a4
    }

    /** makestrip (gloom.s:6863) : strip see-through → vd dans l'arène + sh dans shapelist (trié Z). */
    public static int makestrip(int d7, int d2) {
        int a0 = Mem.l(Vars.memat);                    // move.l memat,a0
        Mem.wl(Vars.memat, a0 + Defs.vd_size);         // add #vd_size,memat
        int a1 = Mem.l(Vars.memat);                    // move.l memat,a1
        Mem.wl(Vars.memat, a1 + Defs.sh_size);         // add #sh_size,memat
        Mem.wl(a1, 0);                                 // clr.l (a1)
        Mem.ww(a1 + Defs.sh_x, d7);                    // move d7,sh_x(a1)
        Mem.ww(a1 + Defs.sh_z, d2);                    // move d2,sh_z(a1)
        Mem.wl(a1 + Defs.sh_shape, 0);                 // clr.l sh_shape(a1)
        Mem.wl(a1 + Defs.sh_strip, a0);                // move.l a0,sh_strip(a1)
        // insertion triée par Z dans shapelist
        int a2 = Vars.shapelist;                       // lea shapelist,a2
        while (true) {                                 // .loop
            int d0 = Mem.l(a2);                        // move.l (a2),d0
            if (d0 == 0) {                             // beq .end
                Mem.wl(a1, d0);                        // move.l d0,(a1)
                Mem.wl(a2, a1);                        // move.l a1,(a2)
                break;
            }
            int a3 = a2;                               // move.l a2,a3
            a2 = d0;                                    // move.l d0,a2
            if ((short) d2 < Mem.w(a2 + Defs.sh_z)) continue; // cmp sh_z(a2),d2 ; blt .loop
            Mem.wl(a1, a2);                            // move.l a2,(a1)
            Mem.wl(a3, a1);                            // move.l a1,(a3)
            break;
        }
        return a0;
    }

    // ------------------------------------------------------------------
    // renderwalls + drawstrip (framebuffer) — gloom.s:7405 / 6936
    // ------------------------------------------------------------------

    public static void renderwalls() {
        int a2 = Mem.l(Vars.palette);                  // base palettes
        int a4 = Mem.l(Vars.vertdraws);                // vd array
        int a5 = Vars.coloffs;                         // coloffs
        int w = Mem.uw(Vars.width);
        for (int col = 0; col < w; col++) {            // dbf width-1
            int a1 = Mem.l(Vars.cop) + Mem.l(a5); a5 += 4; // cop + coloffs[col]
            drawstripSolid(a1, a2, a4);
            a4 += Defs.vd_size;
        }
    }

    /** drawstrip_ variante solide (gloom.s:6936, solidstrip=-1) → framebuffer. */
    static void drawstripSolid(int a1, int a2, int a4) {
        int d6 = Mem.uw(Vars.hite);                    // move hite,d6
        int a0 = Mem.l(a4 + Defs.vd_data);             // vd_data
        if (a0 == 0) return;                           // beq .vertskip
        int d5 = Mem.w(a4 + Defs.vd_h);                // vd_h
        int d1 = Mem.l(a4 + Defs.vd_ystep);            // ystep
        int d0 = M68k.w(Mem.w(a4 + Defs.vd_y) + Mem.w(Vars.midy)); // vd_y + midy
        int copmod = Mem.uw(Vars.copmod);

        if (d0 >= 0) {                                 // bpl .noclip
            // (CLS par blitter ignoré : fond pré-effacé)
            a1 += d0 * copmod;                         // .skcl a1 += y*copmod
            int d2 = d0 + d5 - d6;                     // clip bas
            if (d2 > 0) { d5 -= d2; if (d5 <= 0) return; }
            d0 = 0;                                    // V part de 0
        } else {                                       // clip haut
            d5 += d0; if (d5 <= 0) return;             // add d0,d5
            d0 = -d0;                                  // neg d0
            d0 = (int) (((long) d0 & 0xffffffffL) * (d1 & 0xffffffffL)); // mulu.l d1,d0  (avance V)
            if (d6 < d5) d5 = d6;                       // clamp à hite
        }
        // .skipclip
        d5 -= 1;                                        // count-1
        int Vs = M68k.swap(d0);                         // swap d0
        int stepS = M68k.swap(d1);                      // swap d1
        int a3 = Mem.l(a2 + Mem.uw(a4 + Defs.vd_pal) * 4); // palettes[vd_pal]
        // « Thanx Hendrix » : sub d1,d0 ; add.l d1,d0 (amorce la retenue X)
        Vs = Vs - stepS;
        long prime = (Vs & 0xffffffffL) + (stepS & 0xffffffffL);
        Vs = (int) prime;
        int x = (int) (prime >>> 32) & 1;
        for (int i = 0; i <= d5; i++) {                 // dbf d5 → count itérations
            int texel = Mem.ub(a0 + (short) Vs);        // move.b 0(a0,d0),d3
            Mem.ww(a1, Mem.uw(a3 + texel * 2));         // move 0(a3,d3*2),(a1)
            a1 += copmod;                               // add.l d4,a1
            long s = (Vs & 0xffffffffL) + (stepS & 0xffffffffL) + x; // addx.l d1,d0
            Vs = (int) s;
            x = (int) (s >>> 32) & 1;
        }
    }

    // ==================================================================
    // 05c-1 — Sols / plafonds (flat) + pixelate
    // ==================================================================

    /**
     * flat (gloom.s:1777) : sol/plafond texturé en perspective.
     *  d0=ypos (Y monde du plan), d1=yadd (sens balayage : -1 sol / +1 plafond),
     *  d7=1ère ligne écran (maxy-1 sol / miny plafond), a0=texture 128×128.
     *
     * Ne remplit QUE les pixels de fond (==0) — les murs (non nuls) restent. Utilise la
     * matrice inverse icm pour passer écran→monde par scanline (Z constant par ligne).
     *
     * Le micro-accumulateur « swappé + addx » de l'original (1900-1925) est porté sous forme
     * 16.16 normale équivalente (entier en mot fort), cohérent avec le rendu framebuffer.
     */
    public static void flat(int ypos, int yadd, int firsty, int a0) {
        int flatcam = ((short) ypos) << focshft;       // ext.l ; lsl.l #focshft ; move.l ,flatcam
        int copmod = Mem.uw(Vars.copmod);
        int flatyadd2 = (short) yadd * copmod;
        int camx = Mem.w(Vars.camx), camz = Mem.w(Vars.camz);
        int minxv = Mem.w(Vars.minx), maxxv = Mem.w(Vars.maxx), midyv = Mem.w(Vars.midy);
        int widthv = Mem.uw(Vars.width);
        int icm1 = Mem.w(Vars.icm1), icm2 = Mem.w(Vars.icm2), icm3 = Mem.w(Vars.icm3), icm4 = Mem.w(Vars.icm4);
        int dtab = Mem.l(Vars.darktable);
        int pals = Mem.l(Vars.palette);

        int d7 = firsty;
        int a2 = Mem.l(Vars.cop) + Mem.l(Vars.coloffs) + (d7 + midyv) * copmod; // 1ère scanline
        while (true) {                                 // .vloop
            if ((short) d7 == 0) return;               // tst d7 ; beq .rts
            int z = flatcam / (short) d7;              // divs d7,d6 → Z
            if (Integer.compareUnsigned(z & 0xffff, maxz) >= 0) return; // cmp #maxz ; bcc .rts
            z = (short) z;
            int dark = Mem.uw(dtab + z * 2);           // darktable[Z]
            int a5pal = Mem.l(pals + dark * 4);        // palettes[dark]

            int leftX = M68k.muls(z, minxv) >> focshft;  // minx*Z >> focshft  (asr.l)
            int rightX = M68k.muls(z, maxxv) >> focshft; // maxx*Z >> focshft
            // rotation écran→monde par la matrice inverse (×2 ⇒ ~16.16)
            int wX1 = (M68k.muls(leftX, icm1) + M68k.muls(z, icm2)) << 1;
            int wZ1 = (M68k.muls(leftX, icm3) + M68k.muls(z, icm4)) << 1;
            int wX2 = (M68k.muls(rightX, icm1) + M68k.muls(z, icm2)) << 1;
            int wZ2 = (M68k.muls(rightX, icm3) + M68k.muls(z, icm4)) << 1;
            int xadd = (wX2 - wX1) / widthv;           // divs.l width
            int zadd = (wZ2 - wZ1) / widthv;
            int X = wX1 + (camx << 16);                // swap + add camx (entier en mot fort)
            int Zc = wZ1 + (camz << 16);

            int a3 = a2;
            for (int px = 0; px < widthv; px++) {      // .hloop (banques copper aplaties)
                if (Mem.uw(a3) == 0) {                 // tst (a3) ; bne .skip  (fond seulement)
                    int xi = (X >> 16) & 127;          // and #127
                    int zi = (Zc >> 16) & 127;
                    int texel = Mem.ub(a0 + ((xi << 7) + zi)); // move.b 0(a0,d1),d3
                    Mem.ww(a3, Mem.uw(a5pal + texel * 2));     // move 0(a5,d3*2),(a3)
                }
                X += xadd;                             // add.l d4,d0 (+ addx)
                Zc += zadd;                            // add.l d6,d1 (+ addx)
                a3 += 2;                               // colonne suivante (mot framebuffer)
            }
            d7 = M68k.w(d7 + yadd);                     // d7 += flatyadd
            a2 += flatyadd2;                            // a2 += flatyadd2
        }
    }

    /** pixelate (gloom.s:1639) : agrandit les « chixels » du framebuffer (effet mort/téléport). */
    public static void pixelate(int d0) {
        Mem.ww(Vars.pixsize, d0);                      // move d0,pixsize
        int d1 = d0;
        int a0 = Mem.l(Vars.cop);
        int copmod = Mem.uw(Vars.copmod);
        int widthv = Mem.uw(Vars.width);
        int hitev = Mem.uw(Vars.hite);
        int d2 = 0;                                    // x
        int d6 = d1 >> 1;                              // first X add
        while (true) {                                 // .forx
            int d5 = d2 + d6;                          // clamp largeur du bloc
            if (d5 > widthv) d5 = widthv;
            d5 -= d2;
            if (d5 <= 0) return;                       // .done
            d5 -= 1;
            for (int bx = 0; bx <= d5; bx++) {         // .loop2 (colonnes du bloc)
                int col = d2 + bx;
                int a5col = Mem.l(Vars.cop) + Mem.l(Vars.coloffs + col * 4);
                int a4src = Mem.l(Vars.cop) + Mem.l(Vars.coloffs + col * 4); // src = 1ère col du bloc
                a4src = Mem.l(Vars.cop) + Mem.l(Vars.coloffs + d2 * 4);
                int d3 = 0;                            // y
                int d7 = d1 >> 1;                      // first Y add
                while (true) {                         // .fory
                    int srcVal = Mem.uw(a4src + d3 * copmod); // move (a4),d0  (couleur de tête de bloc)
                    int d4 = d3 + d7;
                    if (d4 > hitev) d4 = hitev;
                    d4 -= d3;
                    if (d4 <= 0) break;                // .nextx
                    d4 -= 1;
                    for (int by = 0; by <= d4; by++) { // .loop (réplique verticale)
                        Mem.ww(a5col + (d3 + by) * copmod, srcVal);
                    }
                    d3 += d7;                          // .nexty
                    d7 = d1;
                }
            }
            d2 += d6;                                  // .nextx bloc suivant
            d6 = d1;
        }
    }

    /** makeqstrip (gloom.s:1700) : sol/plafond « split » (no-op en mode texturé, con0poke=$100). */
    public static void makeqstrip() {
        if (Mem.uw(Vars.con0poke) == 0x100) return;    // cmp #$100,con0poke ; bne .doit ; rts
        // Mode « split » (sol/plafond plat ombré) : non utilisé en mode texturé.
        // Reporté avec le CLS framebuffer si besoin (cf. PORT_SUBSYS_05).
    }

    // ==================================================================
    // 05c-2 — Sprites / strips see-through / sang
    // ==================================================================

    // Identifiants de routine de rendu d'objet (sh_render). Posés par calcscene (08).
    public static final int OBJ_NORM = 0, OBJ_INVS = 1, OBJ_TRANS = 2;

    /** drawshapes (gloom.s:7076) : parcourt shapelist (strips see-through + sprites, triés Z). */
    public static void drawshapes() {
        int a6 = Vars.shapelist;                       // lea shapelist,a6
        while (true) {                                 // .drawloop
            a6 = Mem.l(a6);                            // move.l (a6),a6
            if (a6 == 0) return;                       // beq .rts
            int shape = Mem.l(a6 + Defs.sh_shape);     // sh_shape
            if (shape == 0) {                          // strip de mur see-through
                int col = Mem.w(a6 + Defs.sh_x) + Mem.w(Vars.midx); // sh_x + midx
                int a1 = Mem.l(Vars.cop) + Mem.l(Vars.coloffs + col * 4);
                drawstripHoled(a1, Mem.l(Vars.palette), Mem.l(a6 + Defs.sh_strip)); // drawstrip2
            } else {
                drawSprite(a6, shape);
            }
        }
    }

    /** drawshapes (.shape, gloom.s:7097) : projection + clipping d'un sprite, puis sh_render. */
    private static void drawSprite(int a6, int a0) {
        int d2 = Mem.w(a6 + Defs.sh_z);                // sh_z (Z)
        int d7 = Mem.w(a6 + Defs.sh_scale);            // scale
        int xhandle = Mem.w(a0);                       // movem (a0)+,d3-d4
        int yhandle = Mem.w(a0 + 2);
        a0 += 4;
        int d0 = Mem.w(a6 + Defs.sh_x) - (M68k.muls(xhandle, d7) >> 8); // sub.l (xhandle*scale>>8)
        int d1 = Mem.w(a6 + Defs.sh_y) - (M68k.muls(yhandle, d7) >> 8);
        if (d2 == 0) return;
        int sx = (d0 << focshft) / d2;                 // screen X
        if (sx >= Mem.w(Vars.maxx)) return;
        int sy = (d1 << focshft) / d2;                 // screen Y
        if (sy >= Mem.w(Vars.maxy)) return;
        int spriteW = Mem.w(a0);                       // movem (a0),d3-d4
        int spriteH = Mem.w(a0 + 2);
        int sw = (M68k.muls(spriteW, d7) >> (8 - focshft)) / d2;  // screen width
        if (sw <= 0) return;
        int sh = (M68k.muls(spriteH, d7) >> (8 - focshft)) / d2;  // screen height
        if (sh <= 0) return;
        int xstep = (spriteW << 16) / sw;              // (spriteW<<16)/screenW
        int widthv = Mem.uw(Vars.width), hitev = Mem.uw(Vars.hite);

        // --- clip X ---
        int startcol, srcX;
        int scw = sw;
        sx += Mem.w(Vars.midx);                        // add midx,d0
        if (sx < 0) {                                  // bpl .xcskip
            scw += sx; if (scw <= 0) return;           // add d0,d3 ; ble
            srcX = (-sx) * xstep;                      // neg ; mulu.l d5,d0
            startcol = 0;
            if (scw > widthv) scw = widthv;            // cmp width ; ble ; move width,d3
        } else {
            startcol = sx;                             // move d0,d7
            int over = sx + scw - widthv;              // add d3,d0 ; sub width,d0
            if (over > 0) { scw -= over; if (scw <= 0) return; }
            srcX = xstep >> 1;                         // move.l d5,d0 ; lsr.l #1,d0
        }

        // --- clip Y ---
        int ystep = (spriteH << 16) / sh;
        int a1 = Mem.l(Vars.cop);
        int copmod = Mem.uw(Vars.copmod);
        int sch = sh, srcY;
        sy += Mem.w(Vars.midy);                        // add midy,d1
        if (sy < 0) {                                  // bpl .ycskip
            sch += sy; if (sch <= 0) return;
            srcY = (-sy) * ystep;
            if (sch > hitev) sch = hitev;
        } else {
            a1 += sy * copmod;                         // mulu copmod,d1 ; add.l,a1
            int over = sy + sch - hitev;
            if (over > 0) { sch -= over; if (sch <= 0) return; }
            srcY = ystep >> 1;
        }

        // --- setup commun + dispatch sh_render ---
        int a5 = Vars.coloffs + startcol * 4;          // &coloffs[startcol]
        int render = Mem.l(a6 + Defs.sh_render);       // routine (identifiant)
        int a6z = Mem.l(Vars.vertdraws) + startcol * Defs.vd_size; // Z-buffer colonne
        int dark = Mem.uw(Mem.l(Vars.darktable) + d2 * 2);
        int a2 = Mem.l(Mem.l(Vars.palette) + dark * 4);  // palettes[darktable[Z]]
        a0 += 2;                                       // addq #2,a0 → stride = (a0) = spriteH

        int w1 = scw - 1, h1 = sch - 1;
        switch (render) {
            case OBJ_INVS -> drawobjinvs(a0, a1, srcX, srcY, d2, w1, h1, xstep, ystep, a5, a6z);
            case OBJ_TRANS -> drawobjtrans(a0, a1, a2, srcX, srcY, d2, w1, h1, xstep, ystep, a5, a6z);
            default -> drawobjnorm(a0, a1, a2, srcX, srcY, d2, w1, h1, xstep, ystep, a5, a6z);
        }
    }

    /** drawobjnorm (gloom.s:7331) : sprite normal, occulté par les murs (vd_z). */
    private static void drawobjnorm(int a0, int a1, int a2, int srcX, int srcY, int z,
                                    int w1, int h1, int xstep, int ystep, int a5, int a6) {
        int copmod = Mem.uw(Vars.copmod);
        int height = Mem.uw(a0);                       // (a0) = stride colonne
        for (int col = 0; col <= w1; col++) {          // dbf d3
            int a4 = a1 + Mem.l(a5); a5 += 4;          // dest col
            if (Integer.compareUnsigned(z & 0xffff, Mem.uw(a6 + Defs.vd_z)) < 0) { // z<vd_z → devant
                int a3 = a0 + 2 + (srcX >> 16) * height;
                int sy = srcY;
                for (int row = 0; row <= h1; row++) {  // dbf d4
                    int texel = Mem.ub(a3 + (sy >> 16));
                    if (texel != 0) Mem.ww(a4, Mem.uw(a2 + texel * 2));
                    sy += ystep; a4 += copmod;
                }
            } else if ((short) Mem.w(Vars.thermo) != 0) {
                thermostrip(a0, a4, a2, srcX, h1, srcY, ystep);
            }
            srcX += xstep; a6 += Defs.vd_size;
        }
    }

    /** drawobjinvs (gloom.s:7241) : objet invisible — assombrit le fond (half-brite & $eee >>1). */
    private static void drawobjinvs(int a0, int a1, int srcX, int srcY, int z,
                                    int w1, int h1, int xstep, int ystep, int a5, int a6) {
        int copmod = Mem.uw(Vars.copmod);
        int height = Mem.uw(a0);
        for (int col = 0; col <= w1; col++) {
            int a4 = a1 + Mem.l(a5); a5 += 4;
            if (Integer.compareUnsigned(z & 0xffff, Mem.uw(a6 + Defs.vd_z)) < 0) {
                int a3 = a0 + 2 + (srcX >> 16) * height;
                int sy = srcY;
                for (int row = 0; row <= h1; row++) {
                    if (Mem.ub(a3 + (sy >> 16)) != 0) {     // texel != 0 → masque le fond
                        Mem.ww(a4, (Mem.uw(a4) & 0xeee) >>> 1);
                    }
                    sy += ystep; a4 += copmod;
                }
            }
            srcX += xstep; a6 += Defs.vd_size;
        }
    }

    /** drawobjtrans (gloom.s:7284) : objet transparent — mélange 50 % (texel & $eee + fond & $eee)>>1. */
    private static void drawobjtrans(int a0, int a1, int a2, int srcX, int srcY, int z,
                                     int w1, int h1, int xstep, int ystep, int a5, int a6) {
        int copmod = Mem.uw(Vars.copmod);
        int height = Mem.uw(a0);
        for (int col = 0; col <= w1; col++) {
            int a4 = a1 + Mem.l(a5); a5 += 4;
            if (Integer.compareUnsigned(z & 0xffff, Mem.uw(a6 + Defs.vd_z)) < 0) {
                int a3 = a0 + 2 + (srcX >> 16) * height;
                int sy = srcY;
                for (int row = 0; row <= h1; row++) {
                    int texel = Mem.ub(a3 + (sy >> 16));
                    if (texel != 0) {
                        int src = Mem.uw(a2 + texel * 2) & 0xeee;
                        int bg = Mem.uw(a4) & 0xeee;
                        Mem.ww(a4, (src + bg) >>> 1);
                    }
                    sy += ystep; a4 += copmod;
                }
            }
            srcX += xstep; a6 += Defs.vd_size;
        }
    }

    /** thermostrip (gloom.s:7376) : colonne de sprite en teinte bleue (vision thermique, à travers mur). */
    private static void thermostrip(int a0, int a4, int a2, int srcX, int h1, int srcY, int ystep) {
        int copmod = Mem.uw(Vars.copmod);
        int height = Mem.uw(a0);
        int a3 = a0 + 2 + (srcX >> 16) * height;
        int sy = srcY;
        for (int row = 0; row <= h1; row++) {
            int texel = Mem.ub(a3 + (sy >> 16));
            if (texel != 0) Mem.ww(a4, Mem.uw(a2 + texel * 2) & 0x00f);  // & $00f (bleu)
            sy += ystep; a4 += copmod;
        }
    }

    /** drawstrip_ variante non-solide (gloom.s:6936, solidstrip=0) : strips see-through. */
    static void drawstripHoled(int a1, int a2, int a4) {
        int d6 = Mem.uw(Vars.hite);
        int a0 = Mem.l(a4 + Defs.vd_data);
        if (a0 == 0) return;
        int d5 = Mem.w(a4 + Defs.vd_h);
        int d1 = Mem.l(a4 + Defs.vd_ystep);
        int d0 = M68k.w(Mem.w(a4 + Defs.vd_y) + Mem.w(Vars.midy));
        int copmod = Mem.uw(Vars.copmod);
        if (d0 >= 0) {                                 // .noclip
            a1 += d0 * copmod;
            int d2 = d0 + d5 - d6;
            if (d2 > 0) { d5 -= d2; if (d5 <= 0) return; }
            d0 = 0;
        } else {                                       // clip haut
            d5 += d0; if (d5 <= 0) return;
            d0 = -d0;
            d0 = (int) (((long) d0 & 0xffffffffL) * (d1 & 0xffffffffL));
            if (d6 < d5) d5 = d6;
        }
        d5 -= 1;
        // masque de transparence depuis l'octet d'en-tête (-1(a0))
        int hdr = (byte) Mem.ub(a0 - 1);               // move.b -1(a0),d6 ; ext d6
        int mask = Mem.uw(Vars.stripands + hdr * 2);   // stripands(pc,d6*2)
        int Vs = M68k.swap(d0);
        int stepS = M68k.swap(d1);
        int a3 = Mem.l(a2 + Mem.uw(a4 + Defs.vd_pal) * 4);
        Vs = Vs - stepS;
        long prime = (Vs & 0xffffffffL) + (stepS & 0xffffffffL);
        Vs = (int) prime;
        int x = (int) (prime >>> 32) & 1;
        for (int i = 0; i <= d5; i++) {
            int texel = Mem.ub(a0 + (short) Vs);
            if (texel != 0) Mem.ww(a1, Mem.uw(a3 + texel * 2));  // opaque
            else Mem.ww(a1, Mem.uw(a1) & mask);                  // transparent : AND masque (tint)
            a1 += copmod;
            long s = (Vs & 0xffffffffL) + (stepS & 0xffffffffL) + x;
            Vs = (int) s;
            x = (int) (s >>> 32) & 1;
        }
    }

    /** drawblood (gloom.s:5413) : pixels de sang projetés (partie .loop ; splat-écran HUD reporté). */
    public static void drawblood() {
        Mem.ww(Vars.scrnblood, 0);                     // clr scrnblood
        int a5 = Vars.blood;                           // lea blood,a5
        int cm1 = Mem.w(Vars.cm1), cm2 = Mem.w(Vars.cm2), cm3 = Mem.w(Vars.cm3), cm4 = Mem.w(Vars.cm4);
        int camx = Mem.w(Vars.camx), camy = Mem.w(Vars.camy), camz = Mem.w(Vars.camz);
        int minxv = Mem.w(Vars.minx), maxxv = Mem.w(Vars.maxx);
        int minyv = Mem.w(Vars.miny), maxyv = Mem.w(Vars.maxy);
        int midxv = Mem.w(Vars.midx), midyv = Mem.w(Vars.midy);
        int copmod = Mem.uw(Vars.copmod);
        while (true) {                                 // .loop
            a5 = Mem.l(a5);
            if (Mem.l(a5) == 0) return;                // tst.l (a5) ; beq .done
            int d6 = Mem.w(a5 + Defs.bl_color);        // bl_color
            if (d6 == 0) continue;                     // beq .loop (déjà splatté)
            int d0 = M68k.w(Mem.w(a5 + Defs.bl_x) - camx); // bl_x - camx
            int d1 = Mem.w(a5 + Defs.bl_y);            // bl_y
            if ((short) d1 > 0) d1 = M68k.negw(d1);    // ble .blok ; neg d1
            d1 = M68k.w(d1 - camy);                     // sub camy,d1
            int d2 = M68k.w(Mem.w(a5 + Defs.bl_z) - camz);
            // rotation caméra
            int x = M68k.swap((M68k.muls(d0, cm1) + M68k.muls(d2, cm2)) << 1);  // X
            int zr = M68k.swap((M68k.muls(d0, cm3) + M68k.muls(d2, cm4)) << 1); // Z
            d0 = (short) x; d2 = (short) zr;
            if (d2 == 0) continue;
            if (Integer.compareUnsigned(d2 & 0xffff, maxz) >= 0) continue;
            int px = ((short) d0 << focshft) / d2;     // screen X
            if (px < minxv || px >= maxxv) continue;
            int py = ((short) d1 << focshft) / d2;     // screen Y
            if (py < minyv || py >= maxyv) continue;
            // .pix si (Z>=40) OU (bl_y>0) ; sinon « sang sur écran » (splat HUD reporté)
            if (Integer.compareUnsigned(d2 & 0xffff, 40) >= 0 || (short) Mem.w(a5 + Defs.bl_y) > 0) {
                px += midxv; py += midyv;
                int dark = Mem.uw(Mem.l(Vars.darktable) + d2 * 2);
                int a1 = Mem.l(Vars.cop) + Mem.l(Vars.coloffs + px * 4) + py * copmod;
                int col = Mem.uw(Vars.blcols + dark * 2) & d6;   // blcols[dark] & bl_color
                Mem.ww(a1, col);
            } else {
                Mem.ww(Vars.scrnblood, 0xff);          // st scrnblood
                Mem.ww(a5 + Defs.bl_color, 0);         // clr bl_color (splat — partie .done reportée)
            }
        }
    }
}
