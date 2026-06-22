package gloom;

import gloom.data.Tables;

/**
 * Sous-système 07 — Joueur : contrôle, rotation, déplacement avec collision murs.
 *
 * Traduction littérale de gloom.s :
 *  - getcntrl (4888) : copie l'input du contrôleur (joyx0) vers joyx/joyy/joyb/joys ;
 *  - rotplayer (4899) : rotation avec inertie (ob_rotspeed) ;
 *  - moveplayer (4952) : déplacement avant/arrière/strafe via camrots, + head-bob ;
 *  - checknewslow (5716) / checkpolydist (5792) : mur le plus proche de (d6,d7) ;
 *  - adjustpos/adjustposq (5275/5260) : glissement le long du mur heurté ;
 *  - playerlogic (5134) : orchestration.
 *
 * STUBÉS (tours suivants) : playertimers (powerups), checksuck (deathhead), checkevent
 * (déclenchement de zones — portes), checkfire (tir → besoin de shoot/balles).
 *
 * Position candidate dans les "registres" d6/d7 (16.16), partagée par la chaîne (comme l'asm).
 */
public final class Player {

    private static final int grdshft = 8;
    private static final int maxrotsp = 0x40000, rotacc = 0x20000, rotrevacc = 0x40000, rotsetacc = 0x20000;

    /** d6/d7 : position candidate (ob_x/ob_z 16.16). closest/closewall/penet : résultat de checknewslow. */
    public static int d6, d7, closest, closewall, penet;

    private Player() {
    }

    /** getcntrl (4888) : joyx/joyy/joyb/joys ← joyx0[ob_cntrl]. */
    public static void getcntrl(int a5) {
        int cntrl = Mem.uw(a5 + Defs.ob_cntrl);
        int src = Vars.joyx0 + cntrl * 8;
        Mem.wl(Vars.joyx, Mem.l(src));        // joyx, joyy
        Mem.wl(Vars.joyb, Mem.l(src + 4));    // joyb, joys
    }

    /** rotplayer (4899) : accélère ob_rotspeed selon joyx, l'ajoute à ob_rot. */
    public static void rotplayer(int a5) {
        if ((short) Mem.w(Vars.joys) == 0 && (short) Mem.w(Vars.joyx) != 0) {   // pas de strafe + joyx
            int d0 = Mem.w(Vars.joyx);
            int d1 = rotacc;
            int rs = Mem.l(a5 + Defs.ob_rotspeed);
            if (rs != 0 && ((d0 ^ rs) < 0)) d1 = rotrevacc;       // demi-tour rapide
            if ((short) Mem.w(Vars.joyx) < 0) d1 = -d1;            // .plus / neg
            rs += d1;
            if (rs > maxrotsp) rs = maxrotsp;
            else if (rs < -maxrotsp) rs = -maxrotsp;
            Mem.wl(a5 + Defs.ob_rotspeed, rs);
        } else {
            // .norot : décélération
            int rs = Mem.l(a5 + Defs.ob_rotspeed);
            if (rs != 0) {
                if (rs < 0) { rs += rotsetacc; if (rs > 0) rs = 0; }
                else { rs -= rotsetacc; if (rs < 0) rs = 0; }
                Mem.wl(a5 + Defs.ob_rotspeed, rs);
            }
        }
        // .addrot
        int rs = Mem.l(a5 + Defs.ob_rotspeed);
        Mem.wl(a5 + Defs.ob_rot, Mem.l(a5 + Defs.ob_rot) + rs);
    }

    /** moveplayer (4952) : déplacement + collision + head-bob. d6/d7 doivent valoir ob_x/ob_z. */
    public static void moveplayer(int a5) {
        int joyy = Mem.w(Vars.joyy), joysv = Mem.w(Vars.joys), joyxv = Mem.w(Vars.joyx);
        boolean moved = true;
        if ((short) joyy == 0) {
            if ((short) joysv == 0 || (short) joyxv == 0) {
                // .still : juste le rebond
                unbounce(a5);
                if (Mem.uw(a5 + Defs.ob_bounce) == 0) return;
                frameAnim(a5);
                return;
            }
            // strafe seul
            strafe(a5, joyxv);
        } else {
            // .move : avant/arrière
            int d0 = M68k.negw(joyy);                              // neg d0
            d0 = M68k.muls(d0, Mem.w(a5 + Defs.ob_movspeed));      // muls movspeed
            int rot = Mem.uw(a5 + Defs.ob_rot) & 255;
            int a1 = Mem.l(Tables.camrots) + rot * 8;
            int d1 = d0;
            int vx = M68k.muls(d0, Mem.w(a1 + 2)); vx += vx;       // muls 2(a1) ; add.l
            int vz = M68k.muls(d1, Mem.w(a1 + 6)); vz += vz;       // muls 6(a1) ; add.l
            d6 += -vx;                                             // neg.l d0 ; add.l d0,d6
            d7 += vz;
            if ((short) joysv != 0 && (short) joyxv != 0) strafe(a5, joyxv);
        }
        // .check : collision
        if (!checknewslow(a5)) commit(a5);
        else if (!adjustpos(a5)) commit(a5);
        else if (!adjustpos(a5)) commit(a5);
        else { d6 = Mem.l(a5 + Defs.ob_x); d7 = Mem.l(a5 + Defs.ob_z); }   // revert
        bounce(a5);
    }

    private static void strafe(int a5, int joyxv) {
        int rot = (((joyxv << 6) & 0xffff) + Mem.uw(a5 + Defs.ob_rot)) & 255;  // lsl #6 ; +rot
        int a1 = Mem.l(Tables.camrots) + rot * 8;
        int d0 = Mem.w(a5 + Defs.ob_movspeed), d1 = d0;
        int vx = M68k.muls(d0, Mem.w(a1 + 2)); vx += vx;
        int vz = M68k.muls(d1, Mem.w(a1 + 6)); vz += vz;
        d6 += -vx;
        d7 += vz;
    }

    private static void commit(int a5) {
        Mem.wl(a5 + Defs.ob_x, d6);
        Mem.wl(a5 + Defs.ob_z, d7);
    }

    private static void bounce(int a5) {
        int d2 = Mem.uw(a5 + Defs.ob_bounce);
        Mem.ww(a5 + Defs.ob_bounce, d2 + 20);
        int d1 = Mem.uw(a5 + Defs.ob_bounce) & 255;
        if ((d2 & 255) < 64 && d1 < 64) footstep(a5);
        frameAnim(a5);
    }

    private static void frameAnim(int a5) {
        Mem.wl(a5 + Defs.ob_frame, (Mem.l(a5 + Defs.ob_frame) + Mem.l(a5 + Defs.ob_framespeed)) & 3);
    }

    /** unbounce (4940) : amortit le head-bob à l'arrêt. */
    public static void unbounce(int a5) {
        if (Mem.uw(a5 + Defs.ob_bounce) == 0) return;
        Mem.ww(a5 + Defs.ob_bounce, Mem.uw(a5 + Defs.ob_bounce) + 30);
        int d1 = Mem.uw(a5 + Defs.ob_bounce) & 127;
        if (d1 >= 30) return;
        Mem.ww(a5 + Defs.ob_bounce, 0);
        Mem.wl(a5 + Defs.ob_frame, 0);
        footstep(a5);
    }

    private static void footstep(int a5) {
        Sfx.playsfx(Vars.footstepsfx, 16, 0);                   // bruit de pas (gloom.s:4876)
    }

    /** playerlogic (5134) : contrôle + collision + rotation + déplacement. */
    public static void playerlogic(int a5) {
        playertimers(a5);
        getcntrl(a5);
        d6 = Mem.l(a5 + Defs.ob_x);
        d7 = Mem.l(a5 + Defs.ob_z);
        if (!checknewslow(a5)) {
            // .newok : ok
        } else if (!adjustpos(a5)) {
            commit(a5);
        } else if (!adjustpos(a5)) {
            commit(a5);
        } else {
            // coincé/écrasé : −1 PV ; si mort → playerdie, sinon flash rouge (gloom.s:5152)
            int hp = M68k.w(Mem.w(a5 + Defs.ob_hitpoints) - 1);
            Mem.ww(a5 + Defs.ob_hitpoints, hp);
            if (hp <= 0) { playerdie(a5); return; }                 // ble playerdie
            Mem.ww(a5 + Defs.ob_update, -1);
            redpal(a5);                                             // bra redpal
        }
        // checksuck — reporté (deathhead)
        checkevent(a5);                                         // dans une zone-trigger ?
        rotplayer(a5);
        moveplayer(a5);
        Objects.checkfire(a5);                                   // tir (fall-through checkfire dans l'original)
    }

    // ==================================================================
    // Mort du joueur (gloom.s : playerdie 4628 / playerdeath 4544 / playerdead 4469 /
    //   waitrestart 4504 / playerlogic0 5120 / redpal 4618 / playerhit 4622)
    // ==================================================================

    /** redpal (gloom.s:4618) : bascule la palette du joueur sur la version rouge pour 2 frames. */
    public static void redpal(int a5) {
        Mem.wl(a5 + Defs.ob_palette, Vars.palettesr);
        Mem.ww(a5 + Defs.ob_paltimer, 2);
    }

    /** playerhit (gloom.s:4622) : le joueur encaisse des dégâts (flash rouge). a0 = attaquant. */
    public static void playerhit(int a5, int a0) {
        if (Mem.w(a0 + Defs.ob_damage) == 0) return;
        Mem.ww(a5 + Defs.ob_update, -1);
        redpal(a5);
    }

    /** playerdie (gloom.s:4628) : déclenche la mort (PV=0, bascule en animation de mort). */
    public static void playerdie(int a5) {
        redpal(a5);
        Mem.ww(a5 + Defs.ob_hitpoints, 0);
        Mem.ww(a5 + Defs.ob_update, -1);
        Mem.wl(a5 + Defs.ob_logic, Objects.L_PLAYERDEATH);
        Mem.ww(a5 + Defs.ob_colltype, 0);
        Mem.ww(a5 + Defs.ob_collwith, 0);
    }

    /** playerdeath (gloom.s:4544) : la caméra bascule au sol, puis décompte une vie. */
    public static void playerdeath(int a5) {
        getcntrl(a5);
        Mem.ww(a5 + Defs.ob_rot, Mem.uw(a5 + Defs.ob_rot) + 4);   // addq #4,ob_rot (vrille)
        int eyey = M68k.w(Mem.w(a5 + Defs.ob_eyey) + 4);          // addq #4,ob_eyey (chute)
        Mem.ww(a5 + Defs.ob_eyey, eyey);
        if (eyey < -32) return;                                   // cmp #-32 ; blt (chute en cours)
        Mem.ww(a5 + Defs.ob_eyey, -32);
        Mem.wl(a5 + Defs.ob_logic, Objects.L_PLAYERDEAD);
        Mem.ww(a5 + Defs.ob_delay, 63);
        // 1 joueur (.notcom/.one) : décompte la vie
        Mem.ww(a5 + Defs.ob_lives, M68k.w(Mem.w(a5 + Defs.ob_lives) - 1));
        Mem.ww(a5 + Defs.ob_update, -1);
    }

    /** playerdead (gloom.s:4469) : attend, puis respawn (s'il reste des vies) ou game over. */
    public static void playerdead(int a5) {
        getcntrl(a5);
        int d = M68k.w(Mem.w(a5 + Defs.ob_delay) - 1);
        Mem.ww(a5 + Defs.ob_delay, d);
        if (d > 0) return;                                        // bgt (attend)
        if (Mem.w(a5 + Defs.ob_lives) != 0) {                     // tst ob_lives ; beq .dead
            Mem.wl(a5 + Defs.ob_logic, Objects.L_WAITRESTART);    // vies restantes → attend le respawn
            return;
        }
        Mem.wl(a5 + Defs.ob_logic, Objects.L_RTS);                // .dead : plus de vies
        Mem.ww(Vars.finished, 2);                                 // → GAME OVER (mainloop)
    }

    /** waitrestart (gloom.s:4504) : sur appui-feu, ré-initialise le joueur à son point de spawn. */
    public static void waitrestart(int a5) {
        getcntrl(a5);
        if (!Objects.checkfireb(a5)) return;                      // attend l'appui-feu
        int weapon = Mem.w(a5 + Defs.ob_weapon);                  // conserve l'arme
        // restaure le bloc objinfo (stats par défaut) du joueur (player1 = entrée 0)
        int tmpl = gloom.data.ObjInfo.objinfo + 4;                // saute le pointeur de slot
        int dst = a5 + Defs.ob_info;
        for (int i = 0; i < gloom.data.ObjInfo.oilen - 4; i++) Mem.wb(dst + i, Mem.ub(tmpl + i));
        Mem.ww(a5 + Defs.ob_colltype, 0);                         // clr.l ob_colltype (colltype+collwith)
        Mem.ww(a5 + Defs.ob_collwith, 0);
        Mem.ww(a5 + Defs.ob_delay, 75);
        Mem.wl(a5 + Defs.ob_logic, Objects.L_PLAYERLOGIC0);
        Mem.ww(a5 + Defs.ob_x, Mem.w(Vars.p1x));                  // position de spawn (mot fort)
        Mem.ww(a5 + Defs.ob_z, Mem.w(Vars.p1z));
        Mem.ww(a5 + Defs.ob_rot, Mem.w(Vars.p1r));
        Mem.ww(a5 + Defs.ob_weapon, weapon);
        Mem.ww(a5 + Defs.ob_bounce, 0);
        int desc = Mem.l(a5 + Defs.ob_shape);                     // la copie a remis ob_shape = descripteur
        Mem.wl(a5 + Defs.ob_chunks, Mem.l(desc + 4));
        Mem.wl(a5 + Defs.ob_shape, Mem.l(desc));
        resetplayer(a5);
        Mem.ww(a5 + Defs.ob_update, -1);
        Mem.ww(a5 + Defs.ob_lastbut, -1);                         // st ob_lastbut (évite un tir au respawn)
    }

    /** playerlogic0 (gloom.s:5120) : invincibilité de respawn (75 frames) puis playerlogic. */
    public static void playerlogic0(int a5) {
        int d = M68k.w(Mem.w(a5 + Defs.ob_delay) - 1);
        Mem.ww(a5 + Defs.ob_delay, d);
        if (d > 0) { playerlogic(a5); return; }                   // bgt playerlogic (invincible : colltype=0)
        // restaure colltype/collwith par défaut (entrée objinfo du joueur)
        int tmpl = gloom.data.ObjInfo.objinfo + 4 + (Defs.ob_colltype - Defs.ob_info);
        Mem.wl(a5 + Defs.ob_colltype, Mem.l(tmpl));
        Mem.wl(a5 + Defs.ob_logic, Objects.L_PLAYER);
        playerlogic(a5);                                          // tombe dans playerlogic
    }

    // ==================================================================
    // Collision murs
    // ==================================================================

    /** checknewslow (5716) : trouve le mur le plus proche de (d6,d7). Renvoie true si collision. */
    public static boolean checknewslow(int a5) {
        int a0 = Mem.l(Vars.map_grid);
        int px = (short) (d6 >> 16);                   // swap → partie entière (position monde)
        int pz = (short) (d7 >> 16);
        int a2 = Mem.l(Vars.map_poly);
        Render.incframe();
        int frame = Mem.uw(Vars.frame);
        closest = 0x3fff;
        for (int cell = 0; cell < 9; cell++) {         // checkoffs : 9 cellules
            int ox = (short) Mem.w(Vars.checkoffs + cell * 4);
            int oz = (short) Mem.w(Vars.checkoffs + cell * 4 + 2);
            int gx = px + ox, gz = pz + oz;
            if (Integer.compareUnsigned(gx, 32 << grdshft) >= 0) continue;
            if (Integer.compareUnsigned(gz, 32 << grdshft) >= 0) continue;
            int idx = ((gz >>> grdshft) << 5) + (gx >>> grdshft);
            int a3 = a0 + idx * 8;
            int n = Mem.w(a3);                         // nb polys
            if ((short) n < 0) continue;
            int off = Mem.uw(a3 + 2);
            int pp = Mem.l(Vars.map_ppnt) + off * 2;
            do {
                int poly = Mem.uw(pp); pp += 2;
                checkpolydist(a2 + ((poly << 5) & 0x1fffff), frame, px, pz);
            } while (n-- != 0);
        }
        // rotpolys
        int rp = Vars.rotpolys;
        while (true) {
            rp = Mem.l(rp);
            if (Mem.l(rp) == 0) break;
            int pa = Mem.l(rp + Defs.rp_first);
            int d4 = Mem.uw(rp + Defs.rp_num) - 1;
            do { checkpolydist(pa, frame, px, pz); pa += 32; } while (d4-- != 0);
        }
        penet = M68k.w(closest - Mem.w(a5 + Defs.ob_rad));
        return penet < 0;                              // collision si penet négatif
    }

    /** checkpolydist (5792) : met à jour closest/closewall avec la distance perpendiculaire au mur a4. */
    private static void checkpolydist(int a4, int frame, int px, int pz) {
        if (Mem.uw(a4 + Defs.zo_done) == frame) return;
        Mem.ww(a4 + Defs.zo_done, frame);
        // distance depuis l'extrémité (rejette si hors segment)
        int d0 = M68k.muls(M68k.w(Mem.w(a4 + Defs.zo_rx) - px), Mem.w(a4 + Defs.zo_na))
               + M68k.muls(M68k.w(Mem.w(a4 + Defs.zo_rz) - pz), Mem.w(a4 + Defs.zo_nb));
        d0 = M68k.swap(d0 << 1) & 0xffff;
        if (Integer.compareUnsigned(d0, Mem.uw(a4 + Defs.zo_ln)) >= 0) return;
        // distance perpendiculaire
        int p = M68k.muls(M68k.w(Mem.w(a4 + Defs.zo_rx) - px), Mem.w(a4 + Defs.zo_a))
              + M68k.muls(M68k.w(Mem.w(a4 + Defs.zo_rz) - pz), Mem.w(a4 + Defs.zo_b));
        p <<= 1;
        if (p < 0) p = -p;
        p = M68k.swap(p) & 0xffff;
        if (Integer.compareUnsigned(p, closest & 0xffff) < 0) {
            closest = p;
            closewall = a4;
        }
    }

    /** adjustposq (5260) : glisse (d6,d7) le long du mur closewall, du montant de pénétration. */
    private static void adjustposq() {
        int d0 = M68k.negw(penet);                     // neg d0
        int d1 = d0;
        int a4 = closewall;
        d0 = M68k.muls(d0, Mem.w(a4 + Defs.zo_a)); d0 += d0;
        d1 = M68k.muls(d1, Mem.w(a4 + Defs.zo_b)); d1 += d1;
        d6 -= d0;
        d7 -= d1;
    }

    /** adjustpos (5275) : glisse puis re-teste la collision. Renvoie true si toujours en collision. */
    private static boolean adjustpos(int a5) {
        adjustposq();
        return checknewslow(a5);
    }

    /** checkvecs (4431) : déplace a5 de ob_xvec/ob_zvec si possible (collision murs). true=heurté. */
    public static boolean checkvecs(int a5) {
        d6 = Mem.l(a5 + Defs.ob_xvec) + Mem.l(a5 + Defs.ob_x);   // candidate = pos + vec
        d7 = Mem.l(a5 + Defs.ob_zvec) + Mem.l(a5 + Defs.ob_z);
        boolean hit = false;
        if (checknewslow(a5)) {                                 // collision à la nouvelle pos ?
            d6 = Mem.l(a5 + Defs.ob_x);
            d7 = Mem.l(a5 + Defs.ob_z);
            if (!checknewslow(a5)) return true;                 // ancienne pos OK → reste, signale hit
            adjustposq();                                       // .fix : glisse
            hit = true;
        }
        commit(a5);                                             // .ok : commit d6/d7
        return hit;
    }

    // ==================================================================
    // 08e — Événements & portes : playertimers + checkevent (gloom.s:4772/5039)
    // ==================================================================

    /**
     * playertimers (gloom.s:4772) : décompte les timers de powerups (mega/thermo/invisible/
     * paltimer) et anime la téléportation/sortie (ob_pixsizeadd → ob_pixsize). Les messages HUD
     * sont stubés (09). L'effet « hyper » (échelle deathhead/mort) est reporté (normalement 0).
     */
    public static void playertimers(int a5) {
        decTimer(a5, Defs.ob_mega);                             // mega weapon
        decTimer(a5, Defs.ob_thermo);                           // thermo glasses
        int mt = Mem.w(a5 + Defs.ob_messtimer);                 // ob_messtimer (ble .notm ; subq #2)
        if (mt > 0) Mem.ww(a5 + Defs.ob_messtimer, mt - 2);
        decTimer(a5, Defs.ob_invisible);                        // invisibility

        int pt = Mem.w(a5 + Defs.ob_paltimer);                  // ob_paltimer
        if (pt != 0) {
            pt = M68k.w(pt - 1);
            Mem.ww(a5 + Defs.ob_paltimer, pt);
            if (pt == 0) Mem.wl(a5 + Defs.ob_palette, Vars.palettes);
        }

        // animation téléport / sortie : ob_pixsizeadd fait croître ob_pixsize jusqu'à 24
        int d0 = Mem.w(a5 + Defs.ob_pixsizeadd);
        if (d0 != 0) {
            int ps = M68k.w(Mem.w(a5 + Defs.ob_pixsize) + d0);
            Mem.ww(a5 + Defs.ob_pixsize, ps);
            if (ps == 0) {
                Mem.ww(a5 + Defs.ob_pixsizeadd, 0);             // téléport-in terminé
            } else if (ps == 24) {                              // pleinement pixelisé
                Mem.ww(Vars.finished, Mem.w(Vars.finished2));   // move finished2,finished
                if (Mem.w(Vars.finished2) == 0) {               // pas une sortie → on téléporte
                    Mem.ww(a5 + Defs.ob_x, Mem.w(a5 + Defs.ob_telex));
                    Mem.ww(a5 + Defs.ob_z, Mem.w(a5 + Defs.ob_telez));
                    Mem.ww(a5 + Defs.ob_rot, Mem.w(a5 + Defs.ob_telerot));
                    Mem.ww(a5 + Defs.ob_pixsizeadd, M68k.negw(d0)); // inverse → téléport-in
                }
            }
        }
        // hyper (gloom.s:4825) : powerup invincibilité (invincgot pose ob_hyper=-$200). Le joueur
        // grossit (échelle/eyey/firey/gutsy) jusqu'à maxsize puis rétrécit à $200 (fin).
        int hy = Mem.w(a5 + Defs.ob_hyper);
        if (hy != 0) {
            if (hy < 0) {                                          // minus : grossit
                int hd0 = M68k.w(hy - 4);
                int hd1 = M68k.negw(hd0);
                if ((hd1 & 0xffff) == MAXSIZE) hd0 = M68k.w((750 << 2) + MAXSIZE); // → bascule en rétrécissement
                hyperScale(a5, hd1);
                Mem.ww(a5 + Defs.ob_hyper, hd0);
            } else {                                               // .hplus : rétrécit
                int hd0 = M68k.w(hy - 4);
                if (Integer.compareUnsigned(hd0 & 0xffff, MAXSIZE) > 0) {
                    Mem.ww(a5 + Defs.ob_hyper, hd0);               // encore trop grand : pas de mise à l'échelle
                } else {
                    if ((hd0 & 0xffff) == MAXSIZE) hd0 = MAXSIZE;  // == maxsize : « hyper out »
                    int hd1 = hd0;
                    if (hd0 == 0x200) hd0 = 0;                     // retour à la taille normale → fin
                    hyperScale(a5, hd1);
                    Mem.ww(a5 + Defs.ob_hyper, hd0);
                }
            }
        }
    }

    private static final int MAXSIZE = 0x280;

    /** Met le joueur à l'échelle d1 (ob_scale + eyey/firey/gutsy proportionnels), cf. hyper. */
    private static void hyperScale(int a5, int d1) {
        d1 &= 0xffff;
        Mem.ww(a5 + Defs.ob_scale, d1);
        Mem.ww(a5 + Defs.ob_eyey,  (short) (-((d1 * ((110 << 16) / 0x200)) >>> 16))); // pl_eyey=110
        Mem.ww(a5 + Defs.ob_firey, (short) (-((d1 * ((60 << 16) / 0x200)) >>> 16)));  // pl_firey=60
        Mem.ww(a5 + Defs.ob_gutsy, (short) (-((d1 * ((64 << 16) / 0x200)) >>> 16)));  // pl_gutsy=64
    }

    /** Décrémente un timer de powerup (effet retiré quand il atteint 0 ; message HUD stubé). */
    private static void decTimer(int a5, int field) {
        int v = Mem.w(a5 + field);
        if (v == 0) return;
        Mem.ww(a5 + field, M68k.w(v - 1));
    }

    /**
     * checkevent (gloom.s:5039) : si le joueur est dans une zone-trigger, exécute son événement
     * (porte, téléport, changement de texture, rotation, sortie de niveau) et le neutralise.
     */
    public static void checkevent(int a5) {
        int a4 = checknew2(a5);                                 // zone-trigger contenant le joueur ?
        if (a4 == 0) return;                                    // beq .rts
        if ((Mem.w(a5 + Defs.ob_pixsizeadd) | Mem.w(Vars.finished2)) != 0) return; // déjà en transition
        int d0 = Mem.w(a4 + Defs.zo_ev);                        // numéro d'événement de la zone
        if (d0 < 0) return;                                     // bmi .rts (déjà déclenché / aucun)

        if (d0 == 24) {                                         // SORTIE de niveau
            Mem.ww(Vars.finished2, 3);
            Mem.ww(a5 + Defs.ob_pixsizeadd, 1);                 // pixel out
            Sfx.playsfx(Mem.l(Vars.telesfx), 64, 10);           // dotelesfx (solo → .onewin)
        }
        if (d0 < 19) {                                          // .notexit ; <19 → déclenchement unique
            // neutralise toutes les zones du même type d'événement (négation de zo_ev)
            int a0 = Mem.l(Vars.map_poly);
            int a1 = Mem.l(Vars.map_ppnt);
            while (Integer.compareUnsigned(a0, a1) < 0) {
                if (Mem.w(a0 + Defs.zo_ev) == d0) Mem.ww(a0 + Defs.zo_ev, M68k.negw(Mem.w(a0 + Defs.zo_ev)));
                a0 += 32;
            }
        }
        Mem.wl(Vars.eventobj, a5);                              // move.l a5,eventobj
        Events.execevent(d0);
    }

    /**
     * checknew2 (gloom.s:5618) : balaie les 9 cellules de grille autour du joueur, liste TRIGGER
     * (cellule+4), et renvoie le 1er polygone-zone dont la distance < ob_rad (0 sinon).
     */
    public static int checknew2(int a5) {
        int a0 = Mem.l(Vars.map_grid);
        int px = (short) (d6 >> 16);                            // position monde (mot fort)
        int pz = (short) (d7 >> 16);
        int a2 = Mem.l(Vars.map_poly);
        Render.incframe();
        int frame = Mem.uw(Vars.frame);
        int rad = Mem.w(a5 + Defs.ob_rad);
        for (int cell = 0; cell < 9; cell++) {                  // checkoffs : 9 cellules
            int ox = (short) Mem.w(Vars.checkoffs + cell * 4);
            int oz = (short) Mem.w(Vars.checkoffs + cell * 4 + 2);
            int gx = px + ox, gz = pz + oz;
            if (Integer.compareUnsigned(gx, 32 << grdshft) >= 0) continue;
            if (Integer.compareUnsigned(gz, 32 << grdshft) >= 0) continue;
            int idx = ((gz >>> grdshft) << 5) + (gx >>> grdshft);
            int a3 = a0 + idx * 8 + 4;                          // +4 : liste TRIGGER de la cellule
            int n = Mem.w(a3);                                  // nb polys-trigger
            if ((short) n < 0) continue;
            int off = Mem.uw(a3 + 2);
            int pp = Mem.l(Vars.map_ppnt) + off * 2;
            do {
                int poly = Mem.uw(pp); pp += 2;
                int a4 = a2 + ((poly << 5) & 0x1fffff);
                if (Mem.uw(a4 + Defs.zo_done) != frame) {       // pas encore visité cette frame
                    Mem.ww(a4 + Defs.zo_done, frame);
                    if (M68k.w(findsegdist(a4, px, pz) - rad) < 0) return a4; // dans la zone !
                }
            } while (n-- != 0);
        }
        return 0;
    }

    /** resetplayer (gloom.s) : remet l'état du joueur à neuf au chargement d'un niveau. */
    public static void resetplayer(int a5) {
        Mem.ww(a5 + Defs.ob_update, -1);                        // st ob_update
        Mem.ww(a5 + Defs.ob_mega, 0);
        Mem.ww(a5 + Defs.ob_thermo, 0);
        Mem.ww(a5 + Defs.ob_infra, 0);
        Mem.ww(a5 + Defs.ob_invisible, 0);
        Mem.ww(a5 + Defs.ob_pixsize, 0);
        Mem.ww(a5 + Defs.ob_pixsizeadd, 0);
        Mem.ww(a5 + Defs.ob_bouncecnt, 0);
        Mem.ww(a5 + Defs.ob_messtimer, -1);
        Mem.wl(a5 + Defs.ob_palette, Vars.palettes);
    }

    /** findsegdist (gloom.s:5831) : distance perpendiculaire de (px,pz) au segment a4, ou $3fff hors segment. */
    private static int findsegdist(int a4, int px, int pz) {
        int d0 = M68k.muls(M68k.w(Mem.w(a4 + Defs.zo_rx) - px), Mem.w(a4 + Defs.zo_na))
               + M68k.muls(M68k.w(Mem.w(a4 + Defs.zo_rz) - pz), Mem.w(a4 + Defs.zo_nb));
        d0 = M68k.swap(d0 << 1) & 0xffff;                       // distance depuis l'extrémité
        if (Integer.compareUnsigned(d0, Mem.uw(a4 + Defs.zo_ln)) >= 0) return 0x3fff; // hors segment
        int p = M68k.muls(M68k.w(Mem.w(a4 + Defs.zo_rx) - px), Mem.w(a4 + Defs.zo_a))
              + M68k.muls(M68k.w(Mem.w(a4 + Defs.zo_rz) - pz), Mem.w(a4 + Defs.zo_b));
        p <<= 1;
        if (p < 0) p = -p;
        return M68k.swap(p) & 0xffff;                           // distance perpendiculaire
    }
}
