package gloom;

import gloom.data.ObjInfo;
import gloom.data.Tables;

/**
 * Sous-système 08a — Objets : chargement, spawn, et rendu en sprites.
 *
 * Traduction littérale de gloom.s :
 *  - loadanobj (2299) / remapanim (12963) / exec_loadobjs (2291) : chargement des sprites
 *    d'objets (objs/*) + remap couleurs ;
 *  - exec_addobj (2407) + calcvecs (4431) + rnddelay (2461) : spawn d'un objet (copie le
 *    bloc objinfo, calcule vecteurs/rayon) ;
 *  - calcscene (2626) : projette tous les objets en entrées `sh` (shapelist) ;
 *  - drawshape_1/_8/_q + drawshape (5532-5616) : construit l'entrée sprite ;
 *  - calcangle2 (2570) : angle caméra→objet (pour les sprites à 8 directions).
 *
 * L'IA (ob_logic) et le combat (ob_hit/ob_die) sont **stubés** ici (sous-système 08b) :
 * les objets sont spawnés et rendus mais immobiles. Les identifiants logic/hit/die sont
 * stockés dans objinfo pour 08b ; seul `ob_render` est dispatché.
 */
public final class Objects {

    private Objects() {
    }

    // --- identifiants de routine (remplacent les adresses de gloom.s) ---
    // render
    public static final int R_DRAWSHAPE_1 = 1;
    public static final int R_DRAWSHAPE_1SC = 2;      // drawshape_1sc : 1 frame, échelle ob_scale (gibs)
    public static final int R_DRAWSHAPE_8 = 8;
    // logic (08b ; 0 = rts/no-op)
    public static final int L_RTS = 0, L_PLAYER = 1, L_WEAPON = 2, L_BOUNCY = 3, L_MONSTER = 4,
            L_DRAGON = 5, L_BALDY = 6, L_TERRA = 7, L_GHOUL = 8, L_PHANTOM = 9, L_DEMON = 10,
            L_LIZARD = 11, L_DEATHHEAD = 12, L_TROLL = 13, L_FIRE = 14, L_PAUSE = 15,
            L_SPARKS = 16, L_CHUNK = 17,              // 08c : étincelles d'impact, gibs (chunklogic)
            L_PLAYERDEATH = 18, L_PLAYERDEAD = 19, L_WAITRESTART = 20, L_PLAYERLOGIC0 = 21, // mort joueur
            L_BALDYCHARGE = 22, L_BALDYPUNCH = 23, L_TROLLLOGIC2 = 24, L_TERRALOGIC2 = 25,   // IA monstres
            L_DEMONPAUSE = 26,
            L_DEATHCHARGE = 27, L_DEATHSUCK = 28, L_HOMEIN = 29, L_DRAGONDEAD = 30;          // boss
    public static final int D_KILL = 12;              // ob_die = killobject (balle : retrait simple)
    // hit/die (08b) — identifiants opaques, non dispatchés en 08a
    public static final int H_RTS = 0, H_PLAYERHIT = 1, H_HEALTHGOT = 2, H_WEAPONGOT = 3,
            H_THERMOGOT = 4, H_INVISIGOT = 5, H_INVINCGOT = 6, H_BOUNCYGOT = 7, H_HURTNGRUNT = 8,
            H_HURTTERRA = 9, H_HURTGHOUL = 10, H_LIZHURT = 11, H_HURTDEATH = 12, H_TROLLHURT = 13,
            H_MAKESPARKSQ = 14;                           // missile dragon : étincelles quand touché
    public static final int D_PLAYERDIE = 1, D_HEALTHGOT = 2, D_WEAPONGOT = 3, D_THERMOGOT = 4,
            D_INVISIGOT = 5, D_INVINCGOT = 6, D_BOUNCYGOT = 7, D_BLOWDRAGON = 8, D_BLOWOBJECT = 9,
            D_BLOWTERRA = 10, D_BLOWDEATH = 11, D_BLOWDB = 13;   // missile dragon : étincelles + retrait

    // ==================================================================
    // Chargement
    // ==================================================================

    /** loadanobj (gloom.s:2299) : charge le fichier sprite du type `objType` + remap. */
    public static void loadanobj(int objType) {
        int a2 = ObjInfo.objinfo + objType * ObjInfo.oilen;       // lea objinfo ; mulu oilen
        int desc = Mem.l(a2 + ObjInfo.OB_SHAPE);                  // _ob_shape(objinfo) = descripteur
        int a3 = desc + 8;                                        // lea 8(a2),a3 ; nom de fichier
        if (Mem.l(desc) == 0) {                                   // tst.l (a2) ; bne .skip
            int d0 = Files.loadfile(a3, 1);
            Mem.wl(desc, d0);
            if (d0 != 0) remapanim(d0);
        }
        if (Mem.l(desc + 4) != 0) return;                         // tst.l 4(a2) ; bne .rts
        // variante "2" (chunks) : nom + '2'
        int end = a3; while (Mem.ub(end) != 0) end++;             // .loop tst.b (a3)+
        Mem.wb(end, '2');                                         // move.b #'2',-(a3)
        int d0 = Files.loadfile(a3, 1);
        Mem.wb(end, 0);                                           // clr.b (a3)
        Mem.wl(desc + 4, d0);
        if (d0 != 0) remapanim(d0);
    }

    /** remapanim (gloom.s:12963) : ajoute la palette du sprite à map_rgbs et remappe ses frames. */
    public static void remapanim(int a6) {
        int d6 = Mem.uw(a6 + Defs.an_rotshft);                    // movem (a6),d6-d7
        int d7 = Mem.uw(a6 + Defs.an_frames);
        d7 = (d7 << d6) - 1;                                      // lsl d6,d7 ; subq #1,d7 (nb frames-1)
        Map.addpal(a6 + Mem.l(a6 + Defs.an_pal));                 // add.l an_pal(a0),a0 ; addpal
        int a5 = a6 + Defs.an_size;                               // lea an_size(a6),a5
        for (int f = 0; f <= d7; f++) {                           // .loop
            int a0 = a6 + Mem.l(a5); a5 += 4;                     // add.l (a5)+,a0 ; début de la frame
            a0 += 4;                                              // addq #4,a0 ; saute les handles
            int w = Mem.uw(a0), h = Mem.uw(a0 + 2);               // movem (a0)+,d0-d1 (w/h)
            a0 += 4;
            int len = w * h;                                      // mulu d1,d0
            Map.remap(a0, a0 + len);                              // lea 0(a0,d0),a1 ; remap
        }
    }

    /** exec_loadobjs (gloom.s:2291) : charge tous les types listés (jusqu'à un mot négatif). Renvoie a6. */
    public static int exec_loadobjs(int a6) {
        while (true) {                                            // .loop
            int d0 = Mem.w(a6); a6 += 2;                          // move (a6)+,d0
            if ((short) d0 < 0) break;                            // bmi .done
            loadanobj(d0);
        }
        return a6;
    }

    // ==================================================================
    // Spawn (exec_addobj)
    // ==================================================================

    /** exec_addobj (gloom.s:2407) : spawn d'un objet depuis l'événement. Renvoie a6. */
    public static int exec_addobj(int a6) {
        Mem.wl(ObjInfo.dummy, 0);                                 // clr.l dummy : libère le slot partagé
        int type = Mem.uw(a6); a6 += 2;                           // move (a6)+,d0
        int a2 = ObjInfo.objinfo + type * ObjInfo.oilen;
        int slot = Mem.l(a2); a2 += 4;                            // move.l (a2)+,a3 (slot)
        if (Mem.l(slot) != 0) { return a6 + 8; }                  // tst.l (a3) ; bne .no (addq #8,a6)

        // player ? (cmp #2,-2(a6) ; bcc .notp) : type < 2 → addfirst, sinon addlast
        int a5 = (Integer.compareUnsigned(type, 2) < 0)
                ? Lists.addfirst(Vars.objects)
                : Lists.addlast(Vars.objects);
        if (a5 == 0) { return a6 + 8; }                           // .bum beq .no
        Mem.wl(slot, a5);                                         // move.l a5,(a3)

        Mem.ww(a5 + Defs.ob_x, Mem.w(a6)); a6 += 2;               // move (a6)+,ob_x  (mot fort)
        Mem.ww(a5 + Defs.ob_y, Mem.w(a6)); a6 += 2;
        Mem.ww(a5 + Defs.ob_z, Mem.w(a6)); a6 += 2;
        Mem.ww(a5 + Defs.ob_rot, Mem.w(a6)); a6 += 2;

        // copie du bloc objinfo (80 octets) dans ob_info
        int a3 = a5 + Defs.ob_info;
        for (int i = 0; i < (ObjInfo.oilen - 4); i++) Mem.wb(a3 + i, Mem.ub(a2 + i));

        // invisible si bit haut de blood
        Mem.ww(a5 + Defs.ob_invisible, (short) Mem.w(a5 + Defs.ob_blood) < 0 ? -1 : 0);

        calcvecs(a5);                                             // ob_xvec/ob_zvec

        int sh = Mem.l(a5 + Defs.ob_shape);                       // descripteur
        Mem.wl(a5 + Defs.ob_chunks, Mem.l(sh + 4));               // chunks
        int anset = Mem.l(sh);                                    // (descripteur) = anim set chargé
        Mem.wl(a5 + Defs.ob_shape, anset);
        int maxw = Mem.uw(anset + Defs.an_maxw);                  // an_maxw → rayon
        Mem.ww(a5 + Defs.ob_rad, maxw);
        Mem.wl(a5 + Defs.ob_radsq, maxw * maxw);
        Mem.wl(a5 + Defs.ob_washit, 0);
        rnddelay(a5);
        return a6;
    }

    /** calcvecs (gloom.s:4431) : vecteurs de déplacement depuis ob_rot/ob_movspeed. */
    public static void calcvecs(int a5) {
        int d0 = Mem.uw(a5 + Defs.ob_rot) & 255;                  // ob_rot & 255 (mot fort du long)
        int a0 = Mem.l(Tables.camrots) + d0 * 8;                  // camrots[rot]
        int d4 = Mem.w(a5 + Defs.ob_movspeed);                    // ob_movspeed (mot fort)
        int d5 = d4;
        d4 = M68k.muls(d4, Mem.w(a0 + 2));                        // muls 2(a0),d4
        d4 = -(d4 + d4);                                          // add.l d4,d4 ; neg.l d4
        Mem.wl(a5 + Defs.ob_xvec, d4);
        d5 = M68k.muls(d5, Mem.w(a0 + 6));                        // muls 6(a0),d5
        d5 = d5 + d5;                                             // add.l d5,d5
        Mem.wl(a5 + Defs.ob_zvec, d5);
    }

    /** rnddelay (gloom.s:2461) : ob_delay = base + rnd(range). */
    public static void rnddelay(int a5) {
        int d0 = Maths.rndn(Mem.uw(a5 + Defs.ob_range)) & 0xffff; // rndn(ob_range)
        d0 = M68k.w(d0 + Mem.w(a5 + Defs.ob_base));               // + ob_base
        Mem.ww(a5 + Defs.ob_delay, d0);
    }

    // ==================================================================
    // calcscene (gloom.s:2626) : projette les objets en sprites
    // ==================================================================

    /** a5 = joueur (caméra). Pose la palette/thermo, calccamera+makewalls, puis projette objets/gore. */
    public static void calcscene(int a5) {
        Mem.wl(Vars.palette, Mem.l(a5 + Defs.ob_palette) != 0 ? Mem.l(a5 + Defs.ob_palette) : Vars.palettes);
        Mem.ww(Vars.thermo, Mem.w(a5 + Defs.ob_thermo));
        Mem.ww(Vars.infra, Mem.w(a5 + Defs.ob_infra));
        Mem.ww(Vars.pixsize, Mem.w(a5 + Defs.ob_pixsize));
        Mem.wl(Vars.shapelist, 0);
        int currplayer = a5;
        Render.calccamera(a5);
        Render.makewalls();

        int o = Vars.objects;                                     // lea objects,a5
        while (true) {                                            // .loop
            o = Mem.l(o);
            if (Mem.l(o) == 0) break;                             // tst.l (a5) ; beq .done
            if (o == currplayer) continue;                       // skip self
            int render = Mem.l(o + Defs.ob_render);               // ob_render (identifiant)
            // shaperender (drawobj*) selon invisibilité
            int inv = (short) Mem.w(o + Defs.ob_invisible);
            int shr = Render.OBJ_NORM;
            if (inv != 0) shr = (inv < 0) ? Render.OBJ_TRANS : Render.OBJ_INVS;
            drawObject(render, o, shr);
        }
        // gore
        int g = Vars.gore;
        while (true) {                                            // .loop2
            g = Mem.l(g);
            if (Mem.l(g) == 0) break;
            int x = Mem.w(g + Defs.go_x), z = Mem.w(g + Defs.go_z);
            drawshape_q(Mem.l(g + Defs.go_shape), x, 0, z, 0x200, Render.OBJ_NORM);
        }
    }

    /** Dispatch ob_render (drawshape_1 / drawshape_8). */
    private static void drawObject(int render, int a5, int shaperender) {
        if (render == R_DRAWSHAPE_8) drawshape_8(a5, shaperender);
        else if (render == R_DRAWSHAPE_1SC) drawshape_1sc(a5, shaperender);
        else drawshape_1(a5, shaperender);
    }

    /** drawshape_1sc (gloom.s:5532) : 1 frame, échelle ob_scale (gibs). */
    public static void drawshape_1sc(int a5, int shaperender) {
        int a0 = Mem.l(a5 + Defs.ob_shape);
        int d0 = Mem.w(a5 + Defs.ob_frame);
        a0 += Mem.l(a0 + 12 + d0 * 4);
        drawshape(a0, Mem.w(a5 + Defs.ob_x), Mem.w(a5 + Defs.ob_y), Mem.w(a5 + Defs.ob_z),
                Mem.uw(a5 + Defs.ob_scale), shaperender);
    }

    // ==================================================================
    // drawshape_* (gloom.s:5532-5616)
    // ==================================================================

    /** drawshape_1 (gloom.s:5540) : 1 frame, échelle fixe $200. */
    public static void drawshape_1(int a5, int shaperender) {
        int a0 = Mem.l(a5 + Defs.ob_shape);
        int d0 = Mem.w(a5 + Defs.ob_frame);                       // ob_frame (mot fort = frame)
        a0 += Mem.l(a0 + 12 + d0 * 4);                            // add.l 12(a0,d0*4),a0
        drawshape(a0, Mem.w(a5 + Defs.ob_x), Mem.w(a5 + Defs.ob_y), Mem.w(a5 + Defs.ob_z), 0x200, shaperender);
    }

    /** drawshape_8 (gloom.s:5547) : sprite à 8 directions + échelle ob_scale. */
    public static void drawshape_8(int a5, int shaperender) {
        int ang = calcangle2(a5);                                 // bsr calcangle2
        int d0 = (ang + 16) & 0xffff;                             // add #16,d0
        d0 = (d0 - (Mem.uw(a5 + Defs.ob_rot) & 0xffff)) & 0xffff; // sub ob_rot(a5),d0 (mot)
        d0 = (d0 >>> 5) & 7;                                      // lsr #5,d0 ; and #7,d0
        int d1 = Mem.uw(a5 + Defs.ob_frame) & 0xffff;             // ob_frame
        d1 = (d1 << 3) & 0xffff;                                  // lsl #3,d1
        d0 = (d0 | d1) & 0xffff;                                  // or d1,d0
        int a0 = Mem.l(a5 + Defs.ob_shape);
        a0 += Mem.l(a0 + 12 + d0 * 4);                            // add.l 12(a0,d0*4),a0
        drawshape(a0, Mem.w(a5 + Defs.ob_x), Mem.w(a5 + Defs.ob_y), Mem.w(a5 + Defs.ob_z),
                Mem.uw(a5 + Defs.ob_scale), shaperender);
    }

    /** drawshape / drawshape_q (gloom.s:5562/5565) : projette (x,y,z) et insère un `sh` trié Z. */
    public static void drawshape_q(int a0, int d0, int d1, int d2, int d7, int shaperender) {
        d0 = M68k.w(d0 - Mem.w(Vars.camx));                       // sub camx
        d1 = M68k.w(d1 - Mem.w(Vars.camy));
        d2 = M68k.w(d2 - Mem.w(Vars.camz));
        int cm1 = Mem.w(Vars.cm1), cm2 = Mem.w(Vars.cm2), cm3 = Mem.w(Vars.cm3), cm4 = Mem.w(Vars.cm4);
        // Z = (x*cm3 + z*cm4)*2 >> 16
        int z = M68k.swap((M68k.muls(d0, cm3) + M68k.muls(d2, cm4)) << 1);
        if ((short) z <= 0) return;                               // tst d2 ; ble .rts
        if (Integer.compareUnsigned(z & 0xffff, 16 << 7) >= 0) return; // cmp #maxz ; bcc
        z = (short) z;
        // X = (x*cm1 + z0*cm2)*2 >> 16   (d5 = z0 original)
        int x = M68k.swap((M68k.muls(d0, cm1) + M68k.muls(d2, cm2)) << 1);
        x = (short) x;

        int a1 = Mem.l(Vars.memat);                               // sh alloué dans l'arène
        Mem.wl(Vars.memat, a1 + Defs.sh_size);
        Mem.ww(a1 + Defs.sh_x, x); Mem.ww(a1 + Defs.sh_y, d1); Mem.ww(a1 + Defs.sh_z, z);
        Mem.wl(a1 + Defs.sh_shape, a0);
        Mem.ww(a1 + Defs.sh_scale, d7);
        Mem.wl(a1 + Defs.sh_render, shaperender);
        // insertion triée par Z dans shapelist
        int a2 = Vars.shapelist;
        while (true) {
            int d = Mem.l(a2);
            if (d == 0) { Mem.wl(a1, d); Mem.wl(a2, a1); return; } // .end
            int a3 = a2; a2 = d;
            if ((short) z > Mem.w(a2 + Defs.sh_z)) {              // cmp sh_z(a2),d2 ; ble .loop
                Mem.wl(a1, a2); Mem.wl(a3, a1); return;
            }
        }
    }

    private static void drawshape(int a0, int x, int y, int z, int scale, int shaperender) {
        drawshape_q(a0, x, y, z, scale, shaperender);
    }

    /** calcangle2 (gloom.s:2570) : angle caméra→objet (0..255). */
    public static int calcangle2(int a5) {
        int d0 = M68k.w(Mem.w(Vars.camx) - Mem.w(a5 + Defs.ob_x));
        int d1 = M68k.w(Mem.w(Vars.camz) - Mem.w(a5 + Defs.ob_z));
        return Maths.calcangle_(d0, d1) & 0xff;
    }

    // ==================================================================
    // 08b / 07 — Boucle objets (obj_loop) + IA monstre + dispatch
    //
    // NB portage : la machinerie killjsr/hit_ret de l'original (suppression d'objet en
    // pleine boucle) est remplacée par un SNAPSHOT de la liste + suppression DIFFÉRÉE
    // (set `dead`), équivalent en 08b-lite (pas de spawn en cours de frame : pas de tir).
    // ==================================================================

    /**
     * Objets à retirer en fin de frame (suppression différée). Statique pour que les
     * logiques (firelogic/calcbounce, killobject) puissent s'auto-tuer en pleine boucle,
     * comme killjsr/killobject le font dans l'original.
     */
    static java.util.HashSet<Integer> deadThisFrame;

    /** obj_loop (gloom.s:2973) : exécute ob_logic de chaque objet, puis la collision (échange de dégâts). */
    public static void obj_loop() {
        java.util.ArrayList<Integer> objs = new java.util.ArrayList<>();
        int n = Mem.l(Vars.objects);
        while (Mem.l(n) != 0) { objs.add(n); n = Mem.l(n); }     // snapshot des objets vivants
        deadThisFrame = new java.util.HashSet<>();

        for (int a5 : objs) {
            if (deadThisFrame.contains(a5)) continue;
            dispatchLogic(Mem.l(a5 + Defs.ob_logic), a5);        // jsr (ob_logic) — peut spawner des balles

            int collwith = Mem.uw(a5 + Defs.ob_collwith);
            if (collwith == 0) { Mem.wl(a5 + Defs.ob_washit, 0); continue; }
            int rad = Mem.w(a5 + Defs.ob_rad);
            int cx = Mem.w(a5 + Defs.ob_x), cz = Mem.w(a5 + Defs.ob_z);   // positions entières (mot fort)
            for (int a0 : objs) {
                if (a0 == a5 || deadThisFrame.contains(a0)) continue;
                if ((Mem.uw(a0 + Defs.ob_colltype) & collwith) == 0) continue;
                int rsum = Mem.w(a0 + Defs.ob_rad) + rad;
                int dx = Math.abs(Mem.w(a0 + Defs.ob_x) - cx); if (dx >= rsum) continue;
                int dz = Math.abs(Mem.w(a0 + Defs.ob_z) - cz); if (dz >= rsum) continue;
                if (dx * dx + dz * dz >= rsum * rsum) continue;
                if (Mem.l(a5 + Defs.ob_washit) == a0) continue;   // déjà touché cette série
                Mem.wl(a5 + Defs.ob_washit, a0);
                if (Mem.w(Vars.finished2) != 0) continue;
                hitOne(a0, a5);                                   // l'autre encaisse les dégâts du courant
                hitOne(a5, a0);                                   // le courant encaisse ceux de l'autre
            }
        }
        for (int d : deadThisFrame) Lists.killitem(Vars.objects, d); // suppression différée
        deadThisFrame = null;
    }

    /** killobject (gloom.s) : retire l'objet a5 (différé dans obj_loop, immédiat sinon). */
    static void killobject(int a5) {
        if (deadThisFrame != null) deadThisFrame.add(a5);
        else Lists.killitem(Vars.objects, a5);
    }

    private static void hitOne(int hit, int attacker) {
        if (deadThisFrame.contains(hit)) return;
        int dmg = Mem.w(attacker + Defs.ob_damage);
        int hp = M68k.w(Mem.w(hit + Defs.ob_hitpoints) - dmg);
        Mem.ww(hit + Defs.ob_hitpoints, hp);
        if (hp > 0) dispatchHit(Mem.l(hit + Defs.ob_hit), hit, attacker);
        else dispatchDie(Mem.l(hit + Defs.ob_die), hit, attacker);
    }

    /** Dispatch ob_logic (08a/08b). */
    private static void dispatchLogic(int id, int a5) {
        switch (id) {
            case L_PLAYER -> Player.playerlogic(a5);
            case L_PLAYERDEATH -> Player.playerdeath(a5);         // animation de mort
            case L_PLAYERDEAD -> Player.playerdead(a5);           // attente puis respawn / game over
            case L_WAITRESTART -> Player.waitrestart(a5);         // attend l'appui-feu pour respawn
            case L_PLAYERLOGIC0 -> Player.playerlogic0(a5);       // invincibilité de respawn
            case L_FIRE -> firelogic(a5);                         // balle : déplacement + collision murs
            case L_PAUSE -> pauselogic(a5);                       // monstre en pause post-tir
            case L_SPARKS -> sparkslogic(a5);                     // étincelle d'impact
            case L_CHUNK -> chunklogic(a5);                       // gib en vol
            // IA spécifiques par type de monstre
            case L_BALDY -> baldylogic(a5);
            case L_BALDYCHARGE -> baldycharge(a5);
            case L_BALDYPUNCH -> baldypunch(a5);
            case L_LIZARD -> lizardlogic(a5);
            case L_TROLL -> trolllogic(a5);
            case L_TROLLLOGIC2 -> trolllogic2(a5);
            case L_TERRA -> terralogic(a5);
            case L_TERRALOGIC2 -> terralogic2(a5);
            case L_GHOUL -> ghoullogic(a5);
            case L_DEMON -> demonlogic(a5);
            case L_DEMONPAUSE -> demonpause(a5);
            case L_PHANTOM -> phantomlogic(a5);
            // boss
            case L_DRAGON -> dragonlogic(a5);
            case L_DEATHHEAD -> deathheadlogic(a5);
            case L_DEATHCHARGE -> deathcharge(a5);
            case L_DEATHSUCK -> deathsuck(a5);
            case L_HOMEIN -> homeinlogic(a5);                     // missile à tête chercheuse du dragon
            case L_DRAGONDEAD -> dragondead(a5);                  // compte à rebours → fin de partie
            case L_RTS, L_WEAPON, L_BOUNCY -> { /* item immobile (logique reportée) */ }
            // L_MONSTER (marine) : IA générique chasse+tir
            default -> monsterlogic(a5);
        }
    }

    /** Dispatch ob_hit : dégâts déjà appliqués ; joueur → flash rouge, monstre touché → grognement. */
    private static void dispatchHit(int id, int hit, int attacker) {
        if (id == H_PLAYERHIT) Player.playerhit(hit, attacker);   // flash rouge sur dégâts
        else if (id == H_HURTDEATH) hurtdeath(hit);               // deathhead touché → aspire l'âme du joueur
        else if (id == H_MAKESPARKSQ) makesparksq(hit);           // missile dragon touché → étincelles
        else if (id >= H_HURTNGRUNT) Sfx.playsfx(Vars.gruntsfx, 48, 1); // monstre blessé (grunt)
    }

    /** Dispatch ob_die : mort/ramassage. attacker = joueur qui ramasse, hit = pickup. */
    private static void dispatchDie(int id, int hit, int attacker) {
        switch (id) {
            case D_KILL -> killobject(hit);                       // balle morte (touchée / fin de course)
            case D_HEALTHGOT -> { if (isPlayer(attacker)) { token(); inchealth(attacker); } killobject(hit); }
            case D_WEAPONGOT -> { if (isPlayer(attacker)) { token(); weapond0(attacker, Mem.w(hit + Defs.ob_weapon)); } killobject(hit); }
            case D_THERMOGOT -> { if (isPlayer(attacker)) { token(); addTimer(attacker, Defs.ob_thermo, 1500); } killobject(hit); } // thermo (et infra)
            case D_INVISIGOT -> { if (isPlayer(attacker)) { token(); addTimer(attacker, Defs.ob_invisible, 1500); } killobject(hit); }
            case D_INVINCGOT -> { if (isPlayer(attacker)) { token(); if (Mem.w(attacker + Defs.ob_hyper) == 0) Mem.ww(attacker + Defs.ob_hyper, -0x200); } killobject(hit); }
            case D_BOUNCYGOT -> { if (isPlayer(attacker)) { token(); if (Mem.w(attacker + Defs.ob_bouncecnt) < 3) Mem.ww(attacker + Defs.ob_bouncecnt, Mem.w(attacker + Defs.ob_bouncecnt) + 1); } killobject(hit); }
            case D_BLOWOBJECT -> blowobject(hit);                 // sang + gibs + retrait
            case D_BLOWTERRA -> blowterra(hit);                   // robot : sfx différent, même gore
            case D_BLOWDRAGON -> blowdragon(hit);                 // dragon : explosion + fin de partie (gagné)
            case D_BLOWDEATH -> blowdeath(hit);                   // deathhead : libère l'âme aspirée + gore
            case D_BLOWDB -> { makesparksq(hit); killobject(hit); } // blowdb : missile dragon détruit
            case D_PLAYERDIE -> Player.playerdie(hit);            // mort du joueur (anim → vies → respawn/game over)
            default -> { }
        }
    }

    private static final int IRELOAD = 5;          // ireload : valeur de rechargement initiale

    private static void token() { Sfx.playsfx(Vars.tokensfx, 64, 0); }  // playtsfx (son de ramassage)

    /** Vrai si l'objet est un joueur (les powerups ne profitent qu'au joueur, pas aux monstres). */
    private static boolean isPlayer(int obj) {
        return obj == Mem.l(gloom.data.ObjInfo.player1) || obj == Mem.l(gloom.data.ObjInfo.player2);
    }

    private static void addTimer(int a5, int field, int add) {
        Mem.ww(a5 + field, M68k.w(Mem.w(a5 + field) + add));
    }

    /** weapond0 (gloom.s:4671) : d0 = nouvelle arme. Différente → change ; identique → boost (reload/mega). */
    static void weapond0(int a5, int d0) {
        if (d0 != Mem.w(a5 + Defs.ob_weapon)) {                   // .new : nouvelle arme
            Mem.ww(a5 + Defs.ob_weapon, d0);
            Mem.wb(a5 + Defs.ob_reload, IRELOAD);
            Mem.ww(a5 + Defs.ob_update, -1);
            return;
        }
        int r = (Mem.ub(a5 + Defs.ob_reload) - 1) & 0xff;         // subq.b #1,ob_reload
        if (r != 0) { Mem.wb(a5 + Defs.ob_reload, r); Mem.ww(a5 + Defs.ob_update, -1); return; } // tir plus rapide
        Mem.wb(a5 + Defs.ob_reload, 1);                           // rechargement maxé → mega
        Mem.ww(a5 + Defs.ob_mega, M68k.w(Mem.w(a5 + Defs.ob_mega) + 250));
    }

    /** inchealth (gloom.s:4636) : +5 PV, plafonné à 25. */
    private static void inchealth(int a5) {
        int hp = Mem.w(a5 + Defs.ob_hitpoints) + 5;
        if (hp > 25) hp = 25;
        Mem.ww(a5 + Defs.ob_hitpoints, hp);
        Mem.ww(a5 + Defs.ob_update, -1);
    }

    /** monsterlogic (gloom.s:4170) : à expiration du délai, tire sur le joueur (fire1) ; sinon se déplace. */
    public static void monsterlogic(int a5) {
        Mem.ww(a5 + Defs.ob_oldrot, Mem.w(a5 + Defs.ob_rot));
        int delay = M68k.w(Mem.w(a5 + Defs.ob_delay) - 1);
        Mem.ww(a5 + Defs.ob_delay, delay);
        if (delay <= 0) {
            fire1(a5);                                            // vise + tire + bascule en pauselogic
            return;
        }
        monstermove(a5);
    }

    /** monstermove (gloom.s:4177) : avance ; si mur → évitement (monsterfix). */
    private static void monstermove(int a5) {
        if (!Player.checkvecs(a5)) { monsternew(a5); return; }     // beq monsternew (a bougé)
        monsterfix(a5);
    }

    /** monsterfix (gloom.s:4182) : essaie ±64°, +128°, oldrot+128° pour contourner un mur. */
    private static void monsterfix(int a5) {
        int d1 = ((short) Maths.rndw() >= 0) ? 64 : -64;
        Mem.ww(a5 + Defs.ob_rot, Mem.uw(a5 + Defs.ob_rot) + d1); calcvecs(a5);
        if (!Player.checkvecs(a5)) { monsternew(a5); return; }
        Mem.ww(a5 + Defs.ob_rot, Mem.uw(a5 + Defs.ob_rot) + 128); calcvecs(a5);
        if (!Player.checkvecs(a5)) { monsternew(a5); return; }
        Mem.ww(a5 + Defs.ob_rot, Mem.uw(a5 + Defs.ob_oldrot) + 128); calcvecs(a5);
        Player.checkvecs(a5);
        monsternew(a5);
    }

    /** monsternew (gloom.s:4203) : animation de l'image. */
    private static void monsternew(int a5) {
        Mem.wl(a5 + Defs.ob_frame, (Mem.l(a5 + Defs.ob_frame) + Mem.l(a5 + Defs.ob_framespeed)) & 3);
    }

    // ------------------------------------------------------------------
    // IA spécifiques par type de monstre (gloom.s:3733-4137)
    // ------------------------------------------------------------------

    /** checkcoll (gloom.s) : collision (somme des rayons) entre a5 et a0. */
    private static boolean checkcoll(int a5, int a0) {
        if (a0 == 0) return false;
        int rsum = Mem.w(a5 + Defs.ob_rad) + Mem.w(a0 + Defs.ob_rad);
        int dx = Math.abs(M68k.w(Mem.w(a0 + Defs.ob_x) - Mem.w(a5 + Defs.ob_x)));
        if (dx >= rsum) return false;
        int dz = Math.abs(M68k.w(Mem.w(a0 + Defs.ob_z) - Mem.w(a5 + Defs.ob_z)));
        if (dz >= rsum) return false;
        return dx * dx + dz * dz < rsum * rsum;
    }

    /** bl2 (gloom.s:3874) : charge baldy/lizard/troll — vise, ×4 vitesse/anim, → baldycharge. */
    private static void bl2(int a5) {
        Mem.ww(a5 + Defs.ob_rot, pickcalc(a5));
        Mem.wl(a5 + Defs.ob_movspeed, Mem.l(a5 + Defs.ob_movspeed) << 2);
        Mem.wl(a5 + Defs.ob_framespeed, Mem.l(a5 + Defs.ob_framespeed) << 2);
        calcvecs(a5);
        Mem.wl(a5 + Defs.ob_oldlogic, Mem.l(a5 + Defs.ob_logic));
        Mem.wl(a5 + Defs.ob_logic, L_BALDYCHARGE);
    }

    public static void baldylogic(int a5) {                       // 3863
        int d = M68k.w(Mem.w(a5 + Defs.ob_delay) - 1);
        Mem.ww(a5 + Defs.ob_delay, d);
        if (d > 0) { monstermove(a5); return; }                   // charge ?
        bl2(a5);
    }

    public static void lizardlogic(int a5) {                      // 3841 (sfx d'ambiance omis)
        int d = M68k.w(Mem.w(a5 + Defs.ob_delay) - 1);
        Mem.ww(a5 + Defs.ob_delay, d);
        if (d > 0) { monstermove(a5); return; }
        bl2(a5);
    }

    public static void trolllogic(int a5) {                       // 3812 : grossit le rayon puis charge
        int d0 = M68k.swap(M68k.mulu(Mem.w(a5 + Defs.ob_rad), 0xa000)) & 0xffff;
        Mem.ww(a5 + Defs.ob_rad, d0);
        Mem.wl(a5 + Defs.ob_radsq, M68k.mulu(d0, d0));
        Mem.wl(a5 + Defs.ob_logic, L_TROLLLOGIC2);
        trolllogic2(a5);
    }

    public static void trolllogic2(int a5) {                      // 3820
        int d = M68k.w(Mem.w(a5 + Defs.ob_delay) - 1);
        Mem.ww(a5 + Defs.ob_delay, d);
        if (d > 0) { monstermove(a5); return; }
        bl2(a5);
    }

    public static void baldycharge(int a5) {                      // 3733
        if (!Player.checkvecs(a5)) { baldySkip(a5); return; }     // beq baldy_skip (pas de mur)
        baldyToNorm(a5);                                          // mur → repasse en normal
    }

    private static void baldyToNorm(int a5) {                     // baldy_tonorm
        Mem.wl(a5 + Defs.ob_movspeed, Mem.l(a5 + Defs.ob_movspeed) >> 2);
        Mem.wl(a5 + Defs.ob_framespeed, Mem.l(a5 + Defs.ob_framespeed) >> 2);
        Mem.wl(a5 + Defs.ob_logic, Mem.l(a5 + Defs.ob_oldlogic));
        rnddelay(a5);
        monsterfix(a5);
    }

    private static void baldySkip(int a5) {                       // baldy_skip
        int d0 = M68k.w(pickcalc(a5) - Mem.w(a5 + Defs.ob_rot));
        if (d0 > 32 || d0 < -32) { baldyToNorm(a5); return; }     // pas aligné → normal
        int player = Mem.l(gloom.data.ObjInfo.player1);
        Mem.wl(a5 + Defs.ob_washit, player);
        if (!checkcoll(a5, player)) { monsternew(a5); return; }   // pas de contact
        Mem.wl(a5 + Defs.ob_logic, L_BALDYPUNCH);                 // mode coup de poing
        Mem.ww(a5 + Defs.ob_delay, Mem.w(a5 + Defs.ob_punchrate));
        Mem.wl(a5 + Defs.ob_frame, 0);
    }

    public static void baldypunch(int a5) {                       // baldypunch
        int player = Mem.l(gloom.data.ObjInfo.player1);
        if (!checkcoll(a5, player)) { Mem.wl(a5 + Defs.ob_frame, 0); baldyToNorm(a5); return; }
        int d = M68k.w(Mem.w(a5 + Defs.ob_delay) - 1);
        Mem.ww(a5 + Defs.ob_delay, d);
        if (d > 0) return;
        Mem.ww(a5 + Defs.ob_delay, Mem.w(a5 + Defs.ob_punchrate));
        if (Mem.w(a5 + Defs.ob_frame) == 0) {                     // image debout → frappe
            Mem.wl(a5 + Defs.ob_washit, 0);
            Mem.ww(a5 + Defs.ob_rot, pickcalc(a5));
            Mem.ww(a5 + Defs.ob_frame, 5);                        // image de coup
        } else {
            Mem.ww(a5 + Defs.ob_frame, 0);                        // retour debout
        }
    }

    public static void terralogic(int a5) {                       // 3890 : robot, marche puis fusille
        Mem.ww(a5 + Defs.ob_oldrot, Mem.w(a5 + Defs.ob_rot));
        int d = M68k.w(Mem.w(a5 + Defs.ob_delay) - 1);
        Mem.ww(a5 + Defs.ob_delay, d);
        if (d <= 0) {                                             // .fire : passe en rafale
            Mem.ww(a5 + Defs.ob_frame, 0);
            Mem.ww(a5 + Defs.ob_delay, 1);
            Mem.ww(a5 + Defs.ob_delay2, Mem.w(a5 + Defs.ob_firecnt));
            Mem.wl(a5 + Defs.ob_logic, L_TERRALOGIC2);
            return;
        }
        monstermove(a5);                                          // (sfx robot d'ambiance omis)
    }

    public static void terralogic2(int a5) {                      // 3914
        int d = M68k.w(Mem.w(a5 + Defs.ob_delay) - 1);
        Mem.ww(a5 + Defs.ob_delay, d);
        if (d > 0) return;
        Mem.ww(a5 + Defs.ob_delay, Mem.w(a5 + Defs.ob_firerate));
        Mem.ww(a5 + Defs.ob_rot, pickcalc(a5));
        calcvecs(a5);
        shoot(a5, 4, 0, 1, 3, 16, Tables.bullet4, Tables.sparks4);
        Sfx.playsfx(Vars.shootsfx3, 32, 5);
        int d2 = M68k.w(Mem.w(a5 + Defs.ob_delay2) - 1);
        Mem.ww(a5 + Defs.ob_delay2, d2);
        if (d2 > 0) return;
        rnddelay(a5);
        Mem.wl(a5 + Defs.ob_logic, L_TERRA);
    }

    public static void ghoullogic(int a5) {                       // 3950 : flotte, ignore les murs, tire
        // flottement vertical via camrots
        Mem.ww(a5 + Defs.ob_bounce, Mem.uw(a5 + Defs.ob_bounce) + 8);
        int s = (short) Mem.w(Mem.l(Tables.camrots) + (Mem.uw(a5 + Defs.ob_bounce) & 255) * 8);
        Mem.ww(a5 + Defs.ob_y, ((s << 5) >> 16) - 32);
        Mem.ww(a5 + Defs.ob_rot, pickcalc(a5));
        int d = M68k.w(Mem.w(a5 + Defs.ob_delay) - 1);
        Mem.ww(a5 + Defs.ob_delay, d);
        if (d <= 0) {                                             // tir
            Mem.ww(a5 + Defs.ob_frame, 1);
            Mem.wl(a5 + Defs.ob_framespeed, 0x2000);
            shoot(a5, 4, 0, 1, 3, 20, Tables.bullet2, Tables.sparks2);
            rnddelay(a5);
        }
        // déplacement libre (ignore les murs) ; recale le vecteur au hasard
        if (Integer.compareUnsigned(Maths.rndw() & 0xffff, (Mem.w(a5 + Defs.ob_movspeed) << 8) & 0xffff) < 0) {
            calcvecs(a5);
        }
        Mem.wl(a5 + Defs.ob_x, Mem.l(a5 + Defs.ob_x) + Mem.l(a5 + Defs.ob_xvec));
        Mem.wl(a5 + Defs.ob_z, Mem.l(a5 + Defs.ob_z) + Mem.l(a5 + Defs.ob_zvec));
        int fs = Mem.l(a5 + Defs.ob_framespeed);
        if (fs == 0) return;
        Mem.wl(a5 + Defs.ob_frame, Mem.l(a5 + Defs.ob_frame) + fs);
        if (Integer.compareUnsigned(Mem.uw(a5 + Defs.ob_frame), 3) < 0) return;
        Mem.wl(a5 + Defs.ob_frame, 0);
        Mem.wl(a5 + Defs.ob_framespeed, 0);
    }

    public static void demonlogic(int a5) {                       // 4056
        Mem.ww(a5 + Defs.ob_oldrot, Mem.w(a5 + Defs.ob_rot));
        int d = M68k.w(Mem.w(a5 + Defs.ob_delay) - 1);
        Mem.ww(a5 + Defs.ob_delay, d);
        if (d > 0) { monstermove(a5); return; }
        Mem.ww(a5 + Defs.ob_rot, pickcalc(a5));
        calcvecs(a5);
        Mem.ww(a5 + Defs.ob_delay, 5 * 8 - 1);                    // 5<<3-1
        Mem.wl(a5 + Defs.ob_oldlogic, Mem.l(a5 + Defs.ob_logic));
        Mem.wl(a5 + Defs.ob_logic, L_DEMONPAUSE);
    }

    public static void demonpause(int a5) {                       // 4015 : rafale (3/4 dégâts)
        int d0 = Mem.w(a5 + Defs.ob_delay);
        Mem.ww(a5 + Defs.ob_frame, ((d0 & 4) != 0) ? 5 : 0);
        if ((d0 & 7) == 7) {                                      // tir
            int w = (d0 >> 3) & 7; if (w > 4) w = 4;
            initWtable();
            int dmg = M68k.swap(M68k.mulu(WT_DMG[w], 0xc000)) & 0xffff;   // ×3/4
            shoot(a5, 4, 0, WT_HP[w], dmg, WT_SPEED[w], wtBullet[w], wtSparks[w]);
            Sfx.playsfx(shootSfx(w), 32, 0);
        }
        int dd = M68k.w(d0 - 1);
        Mem.ww(a5 + Defs.ob_delay, dd);
        if (dd > 0) return;
        rnddelay(a5);
        Mem.wl(a5 + Defs.ob_logic, Mem.l(a5 + Defs.ob_oldlogic));
    }

    public static void phantomlogic(int a5) {                     // 4071 (fire1 avec bullet3)
        Mem.ww(a5 + Defs.ob_oldrot, Mem.w(a5 + Defs.ob_rot));
        int d = M68k.w(Mem.w(a5 + Defs.ob_delay) - 1);
        Mem.ww(a5 + Defs.ob_delay, d);
        if (d > 0) { monstermove(a5); return; }
        Mem.ww(a5 + Defs.ob_rot, pickcalc(a5));
        calcvecs(a5);
        Mem.ww(a5 + Defs.ob_delay, 7);
        Mem.wl(a5 + Defs.ob_oldlogic, Mem.l(a5 + Defs.ob_logic));
        Mem.wl(a5 + Defs.ob_logic, L_PAUSE);
        Mem.ww(a5 + Defs.ob_frame, 5);
        shoot(a5, 4, 0, 1, 3, 20, Tables.bullet3, Tables.sparks3);
    }

    // ==================================================================
    // BOSS — dragon (gloom.s:4336) & deathhead (4108)
    // ==================================================================

    /** dragonanim (4316) : cycle l'image (0..3). */
    private static void dragonanim(int a5) {
        int f = Mem.l(a5 + Defs.ob_frame) + Mem.l(a5 + Defs.ob_framespeed);
        Mem.wl(a5 + Defs.ob_frame, f & 3);
    }

    /** getobrot (4321) : renvoie ob_rotspeed ; si nul, le randomise à ±4. */
    private static int getobrot(int a5) {
        int d0 = Mem.w(a5 + Defs.ob_rotspeed);
        if (d0 != 0) return d0;
        d0 = ((Maths.rndw() & 1) != 0) ? 1 : -1;
        d0 <<= 2;                                                  // ±4
        Mem.ww(a5 + Defs.ob_rotspeed, d0);
        return d0;
    }

    /** dragonlogic (4336) : croise en cercle, vise le joueur, crache des missiles à tête chercheuse. */
    public static void dragonlogic(int a5) {
        dragonanim(a5);
        dragonfire(a5);
        if (Player.checkvecs(a5)) {                                // mur → tourne (×4) et continue
            int d0 = getobrot(a5) << 2;
            Mem.ww(a5 + Defs.ob_rot, M68k.w(Mem.w(a5 + Defs.ob_rot) + d0));
            calcvecs(a5);
            return;
        }
        int target = pickcalc(a5);                                 // .nohit : suis-je pointé vers le joueur ?
        int d1 = M68k.w((Mem.uw(a5 + Defs.ob_rot) & 0xff) - target);
        if (d1 < 0) d1 = -d1;
        int near = (Mem.w(a5 + Defs.ob_rotspeed) != 0) ? 6 : 24;
        if (Integer.compareUnsigned(d1, near) >= 0) {              // pas pointé → tourne
            Mem.ww(a5 + Defs.ob_rot, M68k.w(Mem.w(a5 + Defs.ob_rot) + getobrot(a5)));
            calcvecs(a5);
            return;
        }
        if (Mem.w(a5 + Defs.ob_rotspeed) != 0) Mem.ww(a5 + Defs.ob_rotspeed, 0); // pointé → fonce tout droit
    }

    /** dragonfire (4209) : par salves, crée un missile à tête chercheuse (bullet5/sparks5). */
    private static void dragonfire(int a5) {
        int d0 = M68k.w(Mem.w(a5 + Defs.ob_delay) - 1);
        Mem.ww(a5 + Defs.ob_delay, d0);
        if (d0 >= 0) return;                                       // bpl .rts (délai > 0)
        if (d0 <= -16 * 8) { Mem.ww(a5 + Defs.ob_delay, 47); return; } // fin de salve → pause
        if ((d0 & 7) != 0) return;                                // tire tous les 8 ticks pendant la salve

        int a0 = Lists.addlast(Vars.objects);
        if (a0 == 0) return;
        Mem.ww(a0 + Defs.ob_bouncecnt, Mem.w(a5 + Defs.ob_bouncecnt));
        Mem.ww(a0 + Defs.ob_x, Mem.w(a5 + Defs.ob_x));
        Mem.ww(a0 + Defs.ob_y, M68k.w(Mem.w(a5 + Defs.ob_y) + Mem.w(a5 + Defs.ob_firey)));
        Mem.ww(a0 + Defs.ob_z, Mem.w(a5 + Defs.ob_z));
        Mem.wl(a0 + Defs.ob_logic, L_HOMEIN);
        Mem.wl(a0 + Defs.ob_render, R_DRAWSHAPE_1);
        Mem.wl(a0 + Defs.ob_hit, H_MAKESPARKSQ);
        Mem.wl(a0 + Defs.ob_die, D_BLOWDB);
        Mem.ww(a0 + Defs.ob_colltype, 0);
        Mem.ww(a0 + Defs.ob_collwith, 24 + 3);                     // p1/p2/balles
        Mem.ww(a0 + Defs.ob_hitpoints, 1);
        Mem.ww(a0 + Defs.ob_damage, 3);
        int speed = 16;
        Mem.ww(a0 + Defs.ob_movspeed, speed);
        Mem.wl(a0 + Defs.ob_shape, Tables.bullet5);
        Mem.ww(a0 + Defs.ob_invisible, 0);
        Mem.ww(a0 + Defs.ob_frame, 0);
        Mem.wl(a0 + Defs.ob_chunks, Tables.sparks5);

        int rot = Mem.uw(a5 + Defs.ob_rot) & 255;
        int a1 = Mem.l(Tables.camrots) + rot * 8;
        int cx = Mem.w(a1 + 2);
        Mem.ww(a0 + Defs.ob_nxvec, cx);
        int xv = M68k.muls(M68k.negw(cx), speed); xv += xv;
        int cz = Mem.w(a1 + 6);
        Mem.ww(a0 + Defs.ob_nzvec, cz);
        int zv = M68k.muls(cz, speed); zv += zv;
        Mem.wl(a0 + Defs.ob_xvec, xv);
        Mem.wl(a0 + Defs.ob_zvec, zv);
        Mem.ww(a0 + Defs.ob_rad, 32);
        Mem.wl(a0 + Defs.ob_radsq, 32 * 32);
    }

    /** homeinlogic (4285) : missile à tête chercheuse — accélère vers le joueur (vitesse plafonnée). */
    public static void homeinlogic(int a5) {
        if (Player.checkvecs(a5)) { makesparksq(a5); killobject(a5); return; } // mur → blowdb
        int d0 = pickcalc(a5);
        int a0 = Mem.l(Tables.camrots) + d0 * 8;
        int d4 = (M68k.negw(Mem.w(a0 + 2))) << 2;                  // accélération X
        int d5 = (Mem.w(a0 + 6)) << 2;                             // accélération Z
        d4 += Mem.l(a5 + Defs.ob_xvec);
        if (Integer.compareUnsigned(Math.abs(d4), 0x200000) < 0) Mem.wl(a5 + Defs.ob_xvec, d4);
        d5 += Mem.l(a5 + Defs.ob_zvec);
        if (Integer.compareUnsigned(Math.abs(d5), 0x200000) < 0) Mem.wl(a5 + Defs.ob_zvec, d5);
        putfire(a5);
    }

    /** blowdragon (3253) : explosion bruyante + gibs, puis dragondead → fin de partie (victoire). */
    private static void blowdragon(int a5) {
        Sfx.playsfx(Vars.diesfx, 64, 50);
        Sfx.playsfx(Vars.robodiesfx, 64, 50);
        bloodymess2(a5, 63);
        int chunks = Mem.l(a5 + Defs.ob_chunks);
        if (chunks != 0) for (int i = 0; i < 4; i++) blowchunx(a5, chunks);
        Mem.wl(a5 + Defs.ob_logic, L_DRAGONDEAD);
        Mem.wl(a5 + Defs.ob_render, 0);                            // rts (plus dessiné)
        Mem.ww(a5 + Defs.ob_colltype, 0);
        Mem.ww(a5 + Defs.ob_collwith, 0);
        Mem.ww(a5 + Defs.ob_delay, 127);
    }

    /** dragondead (3290) : compte à rebours puis finished=3 (fin de partie : le dragon est mort). */
    public static void dragondead(int a5) {
        int d = M68k.w(Mem.w(a5 + Defs.ob_delay) - 1);
        Mem.ww(a5 + Defs.ob_delay, d);
        if (d > 0) return;
        Mem.ww(Vars.finished, 3);
    }

    /** blowdeath (3297) : si c'est la deathhead qui aspire → coupe l'aspiration, puis gore normal. */
    private static void blowdeath(int a5) {
        if (a5 == Mem.l(Vars.sucker)) { Mem.wl(Vars.sucker, 0); Mem.wl(Vars.sucking, 0); }
        blowobject(a5);
    }

    // --- deathhead ---

    /** deathbounce (4096) : flottement vertical sinusoïdal (ob_bounce → ob_y). */
    private static void deathbounce(int a5) {
        int b = (Mem.uw(a5 + Defs.ob_bounce) + 4) & 0xffff;
        Mem.ww(a5 + Defs.ob_bounce, b);
        int d0 = (short) Mem.w(Mem.l(Tables.camrots) + (b & 255) * 8); // camrots[bounce][0]
        d0 = (d0 << 5) >> 16;                                      // <<5 ; swap (mot fort)
        d0 = M68k.w(d0 - 48);
        Mem.ww(a5 + Defs.ob_y, d0);
    }

    /** deathanim (4139) : l'image oscille entre 0x8000 et 0x28000 (bouche qui s'ouvre/ferme). */
    private static void deathanim(int a5) {
        int fs = Mem.l(a5 + Defs.ob_framespeed);
        int f = Mem.l(a5 + Defs.ob_frame) + fs;                    // frame += framespeed
        if (f < 0x8000 || f >= 0x28000) {                          // hors bornes → inverse + revient
            fs = -fs;
            f += fs;                                               // annule le pas et repart
            Mem.wl(a5 + Defs.ob_framespeed, fs);
        }
        Mem.wl(a5 + Defs.ob_frame, f);
    }

    /** deathheadlogic (4108) : flotte en cercle ; quand alignée sur le joueur → charge. */
    public static void deathheadlogic(int a5) {
        deathbounce(a5);
        if (Player.checkvecs(a5)) {                                // mur → demi-tour + nouveau délai
            Mem.ww(a5 + Defs.ob_rot, M68k.w(Mem.w(a5 + Defs.ob_rot) + 128));
            rnddelay(a5);
        } else {
            int d0 = pickcalc(a5);
            int d1 = M68k.w((Mem.uw(a5 + Defs.ob_rot) & 0xff) - d0);
            if (d1 < 0) d1 = -d1;
            if (Integer.compareUnsigned(d1, 16) < 0) {             // aligné → CHARGE
                Mem.ww(a5 + Defs.ob_rot, d0);
                Mem.wl(a5 + Defs.ob_logic, L_DEATHCHARGE);
                calcvecs(a5);
                return;
            }
        }
        Mem.ww(a5 + Defs.ob_rot, M68k.w(Mem.w(a5 + Defs.ob_rot) + Mem.w(a5 + Defs.ob_delay))); // cercle
        calcvecs(a5);
    }

    /** deathcharge (4151) : fonce sur le joueur jusqu'à le perdre (>128°) ou heurter un mur. */
    public static void deathcharge(int a5) {
        deathbounce(a5);
        deathanim(a5);
        int d0 = M68k.w(pickcalc(a5) - (Mem.uw(a5 + Defs.ob_rot) & 0xff));
        if (d0 < 0) d0 = -d0;
        boolean revert = Integer.compareUnsigned(d0, 128) >= 0;    // joueur derrière → abandonne
        if (!revert) {
            if (!Player.checkvecs(a5)) return;                     // pas de mur → continue à charger
            Mem.ww(a5 + Defs.ob_rot, M68k.w(Mem.w(a5 + Defs.ob_rot) + 128)); // mur → demi-tour
        }
        Mem.wl(a5 + Defs.ob_logic, L_DEATHHEAD);
        Mem.wl(a5 + Defs.ob_frame, 0x8000);
        rnddelay(a5);
    }

    /** hurtdeath (3355) : la deathhead, touchée, commence à aspirer l'âme du joueur. */
    private static void hurtdeath(int a5) {
        if (Mem.l(Vars.sucking) != 0) return;                      // déjà en train d'aspirer
        int player = Mem.l(gloom.data.ObjInfo.player1);            // pickplayer (mono-joueur)
        if (player == 0 || Mem.l(player + Defs.ob_logic) != L_PLAYER) return;
        Mem.wl(Vars.sucking, player);
        Mem.wl(Vars.sucker, a5);
        Mem.ww(a5 + Defs.ob_oldrot, Mem.w(a5 + Defs.ob_rot));
        Mem.wl(a5 + Defs.ob_oldlogic, Mem.l(a5 + Defs.ob_logic));
        Mem.wl(a5 + Defs.ob_logic, L_DEATHSUCK);
        Mem.wl(a5 + Defs.ob_hit, H_RTS);
        Mem.ww(a5 + Defs.ob_delay, 64);
        deathsuck(a5);
    }

    /** deathsuck (3378) : pointée vers le joueur, aspire son âme (particules) 64 ticks. */
    public static void deathsuck(int a5) {
        deathbounce(a5);
        deathanim(a5);
        int d = M68k.w(Mem.w(a5 + Defs.ob_delay) - 1);
        Mem.ww(a5 + Defs.ob_delay, d);
        if (d <= 0) {                                              // fin : restaure l'état normal
            Mem.ww(a5 + Defs.ob_rot, Mem.w(a5 + Defs.ob_oldrot));
            Mem.wl(a5 + Defs.ob_logic, Mem.l(a5 + Defs.ob_oldlogic));
            Mem.wl(a5 + Defs.ob_hit, H_HURTDEATH);
            Mem.wl(Vars.sucker, 0);
            Mem.wl(Vars.sucking, 0);
            rnddelay(a5);
            return;
        }
        int player = Mem.l(Vars.sucking);
        int dx = M68k.w(Mem.w(player + Defs.ob_x) - Mem.w(a5 + Defs.ob_x));
        int dz = M68k.w(Mem.w(player + Defs.ob_z) - Mem.w(a5 + Defs.ob_z));
        int rot = Maths.calcangle_(dx, dz) & 0xff;                 // calcangle : pointe vers le joueur
        Mem.ww(a5 + Defs.ob_rot, rot);
        int suckangle = Mem.l(Tables.camrots) + ((rot + 128) & 255) * 8;
        for (int i = 0; i < 4; i++) addsoul(a5, player, suckangle);
    }

    /** addsoul (3414) : crée une particule d'âme près du joueur, filant vers la deathhead (a5). */
    private static void addsoul(int a5, int player, int suckangle) {
        int a0 = Lists.addlast(Vars.blood);
        if (a0 == 0) return;
        int d2 = -((((short) Mem.w(suckangle + 2)) << 5));         // vitesse X (vers la deathhead)
        int d3 = (((short) Mem.w(suckangle + 6)) << 5);            // vitesse Z
        Mem.wl(a0 + Defs.bl_xvec, d2);
        Mem.wl(a0 + Defs.bl_dest, a5);                             // bl_yvec/bl_dest = cible (deathhead)
        Mem.wl(a0 + Defs.bl_zvec, d3);
        int px = M68k.w((d2 >> 16) * 2 + Mem.w(player + Defs.ob_x)); // position de départ (offset joueur)
        int pz = M68k.w((d3 >> 16) * 2 + Mem.w(player + Defs.ob_z));
        Mem.ww(a0 + Defs.bl_x, M68k.w((Maths.rndw() & 63) - 32 + px));
        Mem.ww(a0 + Defs.bl_y, M68k.w((Maths.rndw() & 63) - 32 + 110)); // >0 → âme (homing dans moveblood)
        Mem.ww(a0 + Defs.bl_z, M68k.w((Maths.rndw() & 63) - 32 + pz));
        Mem.ww(a0 + Defs.bl_color, (Maths.rndw() & 1) != 0 ? 0x0f0 : 0x0ff);
    }

    /** pickcalc (gloom.s:3617) — version mono-joueur : angle vers player1. */
    private static int pickcalc(int a5) {
        int a0 = Mem.l(gloom.data.ObjInfo.player1);
        if (a0 == 0) return Mem.uw(a5 + Defs.ob_rot) & 0xff;
        int dx = M68k.w(Mem.w(a0 + Defs.ob_x) - Mem.w(a5 + Defs.ob_x));
        int dz = M68k.w(Mem.w(a0 + Defs.ob_z) - Mem.w(a5 + Defs.ob_z));
        int d0 = Maths.calcangle_(dx, dz) & 0xff;
        if (Mem.w(a0 + Defs.ob_invisible) != 0)                  // joueur invisible → visée imprécise (±32)
            d0 = (d0 + (Maths.rndw() & 63) - 32) & 0xff;
        return d0;
    }

    // ==================================================================
    // Tir / balles (gloom.s : shoot 3559, firelogic 5352, calcbounce 5298,
    //   fire1 3631, pauselogic 3534, checkfire 5164, checkfireb 5285, wtable 5249)
    // ==================================================================

    /**
     * wtable (gloom.s:5249) : 5 armes, chacune {hitpoints, damage, speed, balle, sparks, sfx}.
     * Les pointeurs balle/sparks viennent de {@link Tables}; le sfx est ignoré (audio = 09).
     */
    private static final int[] WT_HP    = { 1, 5, 10, 15, 20 };
    private static final int[] WT_DMG   = { 1, 2, 2,  3,  5 };
    private static final int[] WT_SPEED = { 32, 36, 40, 40, 24 };
    private static int[] wtBullet, wtSparks;

    private static void initWtable() {
        // reconstruit à chaque appel : les pointeurs d'armes (Tables) changent à chaque niveau
        // (rechargés + remappés par LevelScene.remapWeapons).
        wtBullet = new int[]{ Tables.bullet1, Tables.bullet2, Tables.bullet3, Tables.bullet4, Tables.bullet5 };
        wtSparks = new int[]{ Tables.sparks1, Tables.sparks2, Tables.sparks3, Tables.sparks4, Tables.sparks5 };
    }

    /** sfx de tir par arme (wtable +14 : shootsfx3/5/1/4/5). Lu à l'appel (chargé par Sfx.loadSamples). */
    private static int shootSfx(int w) {
        return switch (w) {
            case 0 -> Vars.shootsfx3; case 1 -> Vars.shootsfx5; case 2 -> Vars.shootsfx;
            case 3 -> Vars.shootsfx4; default -> Vars.shootsfx5;
        };
    }

    /**
     * shoot (gloom.s:3559) : crée une balle en tête de la liste objects.
     * d2=colltype, d3=collwith, d4=hitpoints, d5=damage, d6=speed, a2=forme balle, a3=forme sparks.
     */
    static void shoot(int a5, int d2, int d3, int d4, int d5, int d6, int a2, int a3) {
        int a0 = Lists.addfirst(Vars.objects);
        if (a0 == 0) return;                                      // beq .rts (liste pleine)

        Mem.ww(a0 + Defs.ob_bouncecnt, Mem.w(a5 + Defs.ob_bouncecnt));
        Mem.ww(a0 + Defs.ob_x, Mem.w(a5 + Defs.ob_x));
        Mem.ww(a0 + Defs.ob_y, M68k.w(Mem.w(a5 + Defs.ob_y) + Mem.w(a5 + Defs.ob_firey)));
        Mem.ww(a0 + Defs.ob_z, Mem.w(a5 + Defs.ob_z));
        Mem.wl(a0 + Defs.ob_logic, L_FIRE);                       // firelogic
        Mem.wl(a0 + Defs.ob_render, R_DRAWSHAPE_1);               // drawshape_1
        Mem.wl(a0 + Defs.ob_hit, H_RTS);                          // rts
        Mem.wl(a0 + Defs.ob_die, D_KILL);                         // killobject
        Mem.ww(a0 + Defs.ob_colltype, d2);
        Mem.ww(a0 + Defs.ob_collwith, d3);
        Mem.ww(a0 + Defs.ob_hitpoints, d4);
        Mem.ww(a0 + Defs.ob_damage, d5);
        Mem.ww(a0 + Defs.ob_movspeed, d6);                        // mot fort du long ob_movspeed
        Mem.wl(a0 + Defs.ob_shape, a2);
        Mem.ww(a0 + Defs.ob_invisible, 0);
        Mem.ww(a0 + Defs.ob_frame, 0);
        Mem.wl(a0 + Defs.ob_chunks, a3);

        int rot = Mem.uw(a5 + Defs.ob_rot) & 255;
        int a1 = Mem.l(Tables.camrots) + rot * 8;
        int cx = Mem.w(a1 + 2);                                   // sin
        Mem.ww(a0 + Defs.ob_nxvec, cx);
        int xv = M68k.muls(M68k.negw(cx), d6); xv += xv;          // (-sin*speed)*2
        Mem.wl(a0 + Defs.ob_xvec, xv);
        int cz = Mem.w(a1 + 6);                                   // cos
        Mem.ww(a0 + Defs.ob_nzvec, cz);
        int zv = M68k.muls(cz, d6); zv += zv;                     // (cos*speed)*2
        Mem.wl(a0 + Defs.ob_zvec, zv);

        Mem.ww(a0 + Defs.ob_rad, 32);
        Mem.wl(a0 + Defs.ob_radsq, 32 * 32);
    }

    /** firelogic (gloom.s:5352) : avance la balle ; si elle heurte un mur → calcbounce, sinon anime. */
    public static void firelogic(int a5) {
        if (Player.checkvecs(a5)) calcbounce(a5);                 // bne calcbounce
        else putfire(a5);                                         // beq putfire
    }

    /** putfire (gloom.s:5355) : cycle l'image de la balle. */
    private static void putfire(int a5) {
        int f = M68k.w(Mem.w(a5 + Defs.ob_frame) + 1);            // addq #1,ob_frame (mot)
        int a0 = Mem.l(a5 + Defs.ob_shape);
        if (Integer.compareUnsigned(f & 0xffff, Mem.uw(a0 + 2)) >= 0) f = 0; // cmp 2(a0) ; bcs .skip
        Mem.ww(a5 + Defs.ob_frame, f);
    }

    /**
     * calcbounce (gloom.s:5298) : R = 2 N (N·V) − V. Décrémente ob_bouncecnt : à 0 → sparks + mort ;
     * sinon réfléchit le vecteur sur la normale de closewall puis re-teste. Les balles des armes de
     * base ont bouncecnt=0 → meurent au 1er impact (branche rebond conservée par fidélité).
     */
    private static void calcbounce(int a5) {
        int bc = M68k.w(Mem.w(a5 + Defs.ob_bouncecnt) - 1);
        Mem.ww(a5 + Defs.ob_bouncecnt, bc);
        if (bc < 0) {                                             // bge .nok
            makesparksq(a5);
            killobject(a5);
            return;
        }
        int a4 = Player.closewall;
        int d0 = Mem.w(a5 + Defs.ob_nxvec);                       // movem.w (extension de signe)
        int d1 = Mem.w(a5 + Defs.ob_nzvec);
        int d2 = M68k.negw(Mem.w(a4 + Defs.zo_na));               // neg d2
        int d3 = Mem.w(a4 + Defs.zo_nb);
        int d4 = M68k.muls(d0, d2) + M68k.muls(d1, d3);           // produit scalaire ...
        d4 = (short) M68k.swap(d4 + d4);                          // (...*2) puis swap → mot fort
        d2 = (M68k.muls(d4, d2) << 2) - (d0 << 16);               // muls;lsl#2;swap d0;clr d0;sub.l
        d2 = (short) M68k.swap(d2);
        d3 = (M68k.muls(d4, d3) << 2) - (d1 << 16);
        d3 = (short) M68k.swap(d3);
        Mem.ww(a5 + Defs.ob_nxvec, d2);
        Mem.ww(a5 + Defs.ob_nzvec, d3);
        int sp = Mem.w(a5 + Defs.ob_movspeed);
        int xv = M68k.muls(M68k.negw(d2), sp); xv += xv;
        Mem.wl(a5 + Defs.ob_xvec, xv);
        int zv = M68k.muls(d3, sp); zv += zv;
        Mem.wl(a5 + Defs.ob_zvec, zv);
        if (Player.checkvecs(a5)) calcbounce(a5);                 // bne calcbounce
        else putfire(a5);                                         // beq putfire
    }

    // ==================================================================
    // Gore & sparks (gloom.s : makesparks 3100, sparkslogic 3131, bloodymess 3140,
    //   blowobject 3302, blowchunx 3321, chunklogic 3196, moveblood 5365)
    // ==================================================================

    // bloodspeed* (3085/3090/3095) : vitesse aléatoire = rndw étendu, décalé.
    private static int bloodspeed()  { return ((short) Maths.rndw()) << 2; }
    private static int bloodspeed2() { return ((short) Maths.rndw()) << 5; }
    private static int bloodspeed3() { return ((short) Maths.rndw()) << 4; }

    /** makesparksq (gloom.s:3100) : étincelles depuis ob_chunks de l'objet. */
    private static void makesparksq(int a5) {
        makesparks(a5, Mem.l(a5 + Defs.ob_chunks));
    }

    /** makesparks (gloom.s:3102) : crée une étincelle par frame de la forme a2 (objets, sparkslogic). */
    private static void makesparks(int a5, int a2) {
        if (a2 == 0) return;                                      // pas de forme d'étincelles
        int d2 = Mem.l(a5 + Defs.ob_x), d3 = Mem.l(a5 + Defs.ob_y), d4 = Mem.l(a5 + Defs.ob_z);
        for (int d5 = M68k.w(Mem.w(a2 + 2) - 1); d5 >= 0; d5--) { // 2(a2) = nb frames ; dbf
            int a0 = Lists.addlast(Vars.objects);
            if (a0 == 0) return;                                  // beq .rts
            Mem.wl(a0 + Defs.ob_x, d2); Mem.wl(a0 + Defs.ob_y, d3); Mem.wl(a0 + Defs.ob_z, d4);
            Mem.wl(a0 + Defs.ob_xvec, bloodspeed2());
            Mem.wl(a0 + Defs.ob_yvec, bloodspeed2());
            Mem.wl(a0 + Defs.ob_zvec, bloodspeed2());
            Mem.wl(a0 + Defs.ob_shape, a2);
            Mem.ww(a0 + Defs.ob_frame, d5);
            Mem.wl(a0 + Defs.ob_logic, L_SPARKS);
            Mem.wl(a0 + Defs.ob_render, R_DRAWSHAPE_1);
            Mem.ww(a0 + Defs.ob_invisible, 0);
            Mem.ww(a0 + Defs.ob_colltype, 0);
            Mem.ww(a0 + Defs.ob_collwith, 0);
            Mem.ww(a0 + Defs.ob_delay, (Maths.rndw() & 15) + 15);
        }
    }

    /** sparkslogic (gloom.s:3131) : avance l'étincelle, meurt quand ob_delay expire. */
    public static void sparkslogic(int a5) {
        int d = M68k.w(Mem.w(a5 + Defs.ob_delay) - 1);
        Mem.ww(a5 + Defs.ob_delay, d);
        if (d <= 0) { killobject(a5); return; }                  // ble killobject
        Mem.wl(a5 + Defs.ob_x, Mem.l(a5 + Defs.ob_x) + Mem.l(a5 + Defs.ob_xvec));
        Mem.wl(a5 + Defs.ob_y, Mem.l(a5 + Defs.ob_y) + Mem.l(a5 + Defs.ob_yvec));
        Mem.wl(a5 + Defs.ob_z, Mem.l(a5 + Defs.ob_z) + Mem.l(a5 + Defs.ob_zvec));
    }

    /** bloodymess (3140) / bloodymess2 (3168) : projette d7+1 gouttes de sang (liste blood). */
    private static void bloodymess(int a5, int d7)  { bloodySpray(a5, d7, false); }
    private static void bloodymess2(int a5, int d7) { bloodySpray(a5, d7, true); }

    private static void bloodySpray(int a5, int d7, boolean fast) {
        int d2 = bloodspeed2() + Mem.l(a5 + Defs.ob_x);          // origine + dispersion
        int d3 = bloodspeed2() + Mem.l(a5 + Defs.ob_gutsy);      // ob_gutsy lu en long (Y 16.16) — fidèle à l'asm
        int d4 = bloodspeed2() + Mem.l(a5 + Defs.ob_z);
        for (; d7 >= 0; d7--) {                                  // dbf d7
            int a0 = Lists.addlast(Vars.blood);
            if (a0 == 0) return;                                 // beq .done
            Mem.wl(a0 + Defs.bl_x, d2); Mem.wl(a0 + Defs.bl_y, d3); Mem.wl(a0 + Defs.bl_z, d4);
            Mem.wl(a0 + Defs.bl_xvec, fast ? bloodspeed3() : bloodspeed());
            Mem.wl(a0 + Defs.bl_yvec, fast ? bloodspeed3() : bloodspeed());
            Mem.wl(a0 + Defs.bl_zvec, fast ? bloodspeed3() : bloodspeed());
            Mem.ww(a0 + Defs.bl_color, Mem.w(a5 + Defs.ob_blood));
        }
    }

    /** splat (gloom.s:3242) : sfx d'impact au sol — stub (audio 09). */
    private static void splat() { Sfx.playsfx(Vars.splatsfx, 32, -1); }

    /** blowobject (gloom.s:3302) : mort d'un monstre — sang, gibs, retrait. */
    static void blowobject(int a5) {
        Sfx.playsfx(Vars.diesfx, 64, 2);                         // diesfx
        blowquick(a5);
    }

    /** blowterra (gloom.s:3247) : mort d'un robot — sfx différent, sinon identique. */
    static void blowterra(int a5) {
        Sfx.playsfx(Vars.robodiesfx, 64, 20);                    // robodiesfx
        blowquick(a5);
    }

    /** blowquick (gloom.s:3306) : gerbe de sang (bloodymess2 ×31) + gibs (blowchunx) puis retrait. */
    private static void blowquick(int a5) {
        bloodymess2(a5, 31);
        int chunks = Mem.l(a5 + Defs.ob_chunks);
        if (chunks == 0) {                                       // pas de gibs → encore du sang
            bloodymess2(a5, 15);
            killobject(a5);
            return;
        }
        blowchunx(a5, chunks);
        killobject(a5);
    }

    /** blowchunx (gloom.s:3321) : un gib volant par frame de la forme a4 (objets, chunklogic). */
    private static void blowchunx(int a5, int a4) {
        for (int d7 = M68k.w(Mem.w(a4 + 2) - 1); d7 >= 0; d7--) { // 2(a4) = nb frames ; dbf
            int a0 = Lists.addlast(Vars.objects);
            if (a0 == 0) { killobject(a5); return; }             // beq killobject
            Mem.wl(a0 + Defs.ob_x, Mem.l(a5 + Defs.ob_x));
            Mem.wl(a0 + Defs.ob_y, -64 << 16);                   // #-64<<16 (au-dessus du sol)
            Mem.wl(a0 + Defs.ob_z, Mem.l(a5 + Defs.ob_z));
            Mem.wl(a0 + Defs.ob_xvec, bloodspeed3());
            Mem.wl(a0 + Defs.ob_yvec, bloodspeed3() - 0x40000);  // poussée vers le haut
            Mem.wl(a0 + Defs.ob_zvec, bloodspeed3());
            Mem.ww(a0 + Defs.ob_invisible, 0);
            Mem.ww(a0 + Defs.ob_colltype, 0);
            Mem.ww(a0 + Defs.ob_collwith, 0);
            Mem.wl(a0 + Defs.ob_logic, L_CHUNK);
            Mem.wl(a0 + Defs.ob_shape, a4);
            Mem.wl(a0 + Defs.ob_render, R_DRAWSHAPE_1SC);
            Mem.ww(a0 + Defs.ob_frame, d7);
            Mem.ww(a0 + Defs.ob_scale, Mem.w(a5 + Defs.ob_scale));
            int mw = Mem.w(a4 + Defs.an_maxw);
            Mem.ww(a0 + Defs.ob_rad, mw);
            Mem.wl(a0 + Defs.ob_radsq, mw * mw);
        }
    }

    /** chunklogic (gloom.s:3196) : gib avec gravité ; au sol → splat + décal de gore (si mode≠0). */
    public static void chunklogic(int a5) {
        if (Mem.w(Vars.mode) == 0) { chunklogic2(a5); return; }  // beq chunklogic2
        Mem.wl(a5 + Defs.ob_yvec, Mem.l(a5 + Defs.ob_yvec) + 0x8000); // gravité
        int d0 = Mem.l(a5 + Defs.ob_yvec) + Mem.l(a5 + Defs.ob_y);
        if (d0 < 0) {                                            // blt .skip : encore en l'air
            Mem.wl(a5 + Defs.ob_y, d0);
            if (Player.checkvecs(a5)) {                          // heurte un mur → stoppe l'horizontale
                Mem.wl(a5 + Defs.ob_xvec, 0);
                Mem.wl(a5 + Defs.ob_zvec, 0);
            }
            return;
        }
        // au sol : décal de gore + retrait
        splat();
        int a0 = Lists.addlast(Vars.gore);
        if (a0 == 0) {                                           // liste pleine : recycle le plus ancien
            Lists.killitem(Vars.gore, Mem.l(Vars.gore));
            a0 = Lists.addlast(Vars.gore);
            if (a0 == 0) { killobject(a5); return; }
        }
        Mem.ww(a0 + Defs.go_x, Mem.w(a5 + Defs.ob_x));
        Mem.ww(a0 + Defs.go_z, Mem.w(a5 + Defs.ob_z));
        int a1 = Mem.l(a5 + Defs.ob_shape);
        a1 += Mem.l(a1 + 12 + Mem.w(a5 + Defs.ob_frame) * 4);
        Mem.wl(a0 + Defs.go_shape, a1);
        killobject(a5);
    }

    /** chunklogic2 (gloom.s:3231) : gib qui tombe sans laisser de décal (mode=0). */
    public static void chunklogic2(int a5) {
        Mem.wl(a5 + Defs.ob_yvec, Mem.l(a5 + Defs.ob_yvec) + 0x8000);
        int d0 = Mem.l(a5 + Defs.ob_yvec);
        Mem.wl(a5 + Defs.ob_y, Mem.l(a5 + Defs.ob_y) + d0);
        if (Mem.l(a5 + Defs.ob_y) >= 0) { killobject(a5); return; } // bge → au sol : retrait
        Mem.wl(a5 + Defs.ob_x, Mem.l(a5 + Defs.ob_x) + Mem.l(a5 + Defs.ob_xvec));
        Mem.wl(a5 + Defs.ob_z, Mem.l(a5 + Defs.ob_z) + Mem.l(a5 + Defs.ob_zvec));
    }

    /** moveblood (gloom.s:5365) : physique des gouttes de sang (gravité, mort au sol). */
    public static void moveblood() {
        java.util.ArrayList<Integer> list = new java.util.ArrayList<>();
        int n = Mem.l(Vars.blood);
        while (Mem.l(n) != 0) { list.add(n); n = Mem.l(n); }     // snapshot
        for (int a5 : list) {
            if (Mem.w(a5 + Defs.bl_y) > 0) {                     // ble .do ; ici bl_y > 0
                if (Mem.l(Vars.sucking) == 0) { Lists.killitem(Vars.blood, a5); continue; }
                // aspiration (deathhead) : se dirige vers bl_dest
                Mem.wl(a5 + Defs.bl_x, Mem.l(a5 + Defs.bl_x) + Mem.l(a5 + Defs.bl_xvec));
                Mem.wl(a5 + Defs.bl_z, Mem.l(a5 + Defs.bl_z) + Mem.l(a5 + Defs.bl_zvec));
                int a0 = Mem.l(a5 + Defs.bl_dest);
                int dx = M68k.w(Mem.w(a5 + Defs.bl_x) - Mem.w(a0 + Defs.ob_x));
                int dz = M68k.w(Mem.w(a5 + Defs.bl_z) - Mem.w(a0 + Defs.ob_z));
                int dist = M68k.muls(dx, dx) + M68k.muls(dz, dz);
                if (Integer.compareUnsigned(dist, 64 * 64) >= 0) continue; // bcc .loop
                Lists.killitem(Vars.blood, a5);
                continue;
            }
            // .do : gravité
            Mem.wl(a5 + Defs.bl_yvec, Mem.l(a5 + Defs.bl_yvec) + 0x8000);
            int d0 = Mem.l(a5 + Defs.bl_xvec), d1 = Mem.l(a5 + Defs.bl_yvec), d2 = Mem.l(a5 + Defs.bl_zvec);
            int ny = Mem.l(a5 + Defs.bl_y) + d1;
            Mem.wl(a5 + Defs.bl_y, ny);
            if (ny >= 0) { Lists.killitem(Vars.blood, a5); continue; } // bge → au sol : meurt
            Mem.wl(a5 + Defs.bl_x, Mem.l(a5 + Defs.bl_x) + d0);
            Mem.wl(a5 + Defs.bl_z, Mem.l(a5 + Defs.bl_z) + d2);
        }
    }

    /** fire1 (gloom.s:3631) : un monstre vise le joueur (avec imprécision), tire, puis passe en pause. */
    static void fire1(int a5) {
        int d0 = pickcalc(a5);                                    // angle vers le joueur
        d0 = ((Maths.rndw() & 31) - 16 + d0) & 255;               // bruit d'imprécision
        Mem.ww(a5 + Defs.ob_rot, d0);
        calcvecs(a5);
        Mem.ww(a5 + Defs.ob_delay, 7);
        Mem.wl(a5 + Defs.ob_oldlogic, Mem.l(a5 + Defs.ob_logic)); // sauve la logique courante
        Mem.wl(a5 + Defs.ob_logic, L_PAUSE);                      // pauselogic
        Mem.wl(a5 + Defs.ob_frame, 0);
        shoot(a5, 4, 0, 1, 1, 20, Tables.bullet1, Tables.sparks1);// colltype 4 = balle de monstre
    }

    /** pauselogic (gloom.s:3534) : décompte la pause post-tir, restaure la logique, recale le cap. */
    public static void pauselogic(int a5) {
        int d = M68k.w(Mem.w(a5 + Defs.ob_delay) - 1);
        Mem.ww(a5 + Defs.ob_delay, d);
        if (d > 0) return;                                        // bgt .skip
        rnddelay(a5);
        Mem.wl(a5 + Defs.ob_logic, Mem.l(a5 + Defs.ob_oldlogic)); // restaure la logique du monstre
        int a0 = Mem.l(gloom.data.ObjInfo.player1);
        if (a0 == 0) return;
        int d0 = pickcalc(a5);
        int d1 = M68k.w((Mem.uw(a0 + Defs.ob_rot) & 255) - d0);
        if (d1 < 0) d1 = -d1;                                     // bpl .pl ; neg d1
        if (d1 < 64 || d1 >= 192) return;                         // joueur devant → garde le cap (.skip)
        Mem.ww(a5 + Defs.ob_rot, Mem.w(a5 + Defs.ob_oldrot));     // .useold
        calcvecs(a5);
    }

    /**
     * checkfire (gloom.s:5164) : tir du joueur. Sur front montant du bouton (checkfireb) et hors
     * rechargement, tire l'arme courante (wtable[ob_weapon]) puis arme le cooldown ob_reloadcnt.
     * Bloc triche (cheat) et tir multiple « mega » non portés (cheat=0, powerup mega reporté).
     */
    public static void checkfire(int a5) {
        if (!checkfireb(a5)) {                                    // beq .nofire
            int rc = Mem.ub(a5 + Defs.ob_reloadcnt);
            if (rc != 0) Mem.wb(a5 + Defs.ob_reloadcnt, rc - 1);
            return;
        }
        int rc = Mem.ub(a5 + Defs.ob_reloadcnt);
        if (rc != 0) { Mem.wb(a5 + Defs.ob_reloadcnt, rc - 1); return; } // .nofire2 (en rechargement)

        int d2 = (Mem.uw(a5 + Defs.ob_collwith) & 3) ^ 3;         // colltype = (collwith&3) eor 3
        int d3 = 0;                                               // collwith balle = 0
        initWtable();
        int w = Mem.uw(a5 + Defs.ob_weapon);
        int hp = WT_HP[w], dmg = WT_DMG[w], sp = WT_SPEED[w], bu = wtBullet[w], spk = wtSparks[w];

        // tir multiple « mega » (gloom.s:5204) : 0=simple, <ok=double (±4°), ≥ok=triple (±8° + base)
        int mega = Mem.w(a5 + Defs.ob_mega);
        int rot0 = Mem.uw(a5 + Defs.ob_rot);
        if (mega == 0) {
            shoot(a5, d2, d3, hp, dmg, sp, bu, spk);
        } else if (Integer.compareUnsigned(mega, OK) >= 0) {      // .threeway
            Mem.ww(a5 + Defs.ob_rot, rot0 + 8); shoot(a5, d2, d3, hp, dmg, sp, bu, spk);
            Mem.ww(a5 + Defs.ob_rot, rot0 - 8); shoot(a5, d2, d3, hp, dmg, sp, bu, spk);
            Mem.ww(a5 + Defs.ob_rot, rot0);     shoot(a5, d2, d3, hp, dmg, sp, bu, spk);
        } else {                                                  // double (pas de tir central)
            Mem.ww(a5 + Defs.ob_rot, rot0 + 4); shoot(a5, d2, d3, hp, dmg, sp, bu, spk);
            Mem.ww(a5 + Defs.ob_rot, rot0 - 4); shoot(a5, d2, d3, hp, dmg, sp, bu, spk);
            Mem.ww(a5 + Defs.ob_rot, rot0);
        }
        Sfx.playsfx(shootSfx(w), 32, 0);                          // sfx de tir de l'arme
        Mem.wb(a5 + Defs.ob_reloadcnt, Mem.b(a5 + Defs.ob_reload));
    }

    private static final int OK = 750 + 125;                      // seuil overkill (gloom.s:17 `ok equ`)

    /** checkfireb (gloom.s:5285) : true uniquement au front montant de joyb (un tir par appui). */
    static boolean checkfireb(int a5) {
        int d0 = Mem.w(Vars.joyb);
        if (d0 == 0) { Mem.ww(a5 + Defs.ob_lastbut, 0); return false; }  // .nofire
        if (Mem.w(a5 + Defs.ob_lastbut) != 0) return false;             // .skip (déjà enfoncé)
        Mem.ww(a5 + Defs.ob_lastbut, d0);
        return true;
    }
}
