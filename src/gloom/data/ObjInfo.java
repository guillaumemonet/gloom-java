package gloom.data;

import gloom.Mem;
import static gloom.Objects.*;

/**
 * Sous-système 08a — Table de définitions d'objets `objinfo` (gloom.s:13116-13924).
 *
 * 24 entrées de 84 octets (`oilen`), index 0..23 (= type utilisé par la map). Chaque entrée :
 *   slot(l), rotspeed(l), movspeed(l), shape(l), logic(l), render(l), hit(l), die(l),
 *   eyey/firey/gutsy/othery/colltype/collwith/cntrl/damage/hitpoints/think (10 mots),
 *   frame(l), framespeed(l), base/range/weapon (3 mots), reload+reloadcnt (2 octets),
 *   hurtpause/punchrate/bouncecnt/something/scale/apad/blood/ypad (8 mots).
 *
 * Les pointeurs de routine (logic/render/hit/die) sont remplacés par les IDENTIFIANTS de
 * gloom.Objects (préfixes L_ R_ H_ D_). « shape » pointe un descripteur [main][chunks][nom].
 *
 * Constantes résolues : pl_eyey=110, pl_firey=60, pl_gutsy=64, ireload=5, cd32=0.
 */
public final class ObjInfo {

    public static final int oilen = 84;
    public static final int OB_SHAPE = 12;     // offset du champ shape dans une entrée

    public static final int objinfo;
    // slots globaux (où sont stockés les objets spawnés)
    public static final int player1, player2, dummy;
    // descripteurs nommés (chargés par loadanobj)
    public static final int player, tokens, marine, baldy, terra, ghoul, demon, phantom,
            lizard, deathhead, dragon, troll;

    static {
        player1 = Mem.alloc(4);
        player2 = Mem.alloc(4);
        dummy = Mem.alloc(4);
        player = desc("objs/player");
        tokens = desc("objs/tokens");
        marine = desc("objs/marine");
        baldy = desc("objs/baldy");
        terra = desc("objs/terra");
        ghoul = desc("objs/ghoul");
        demon = desc("objs/demon");
        phantom = desc("objs/phantom");
        lizard = desc("objs/lizard");
        deathhead = desc("objs/deathhead");
        dragon = desc("objs/dragon");
        troll = desc("objs/troll");
        // weapon1..5 : descripteurs = paires (bulletN,sparksN) déjà incbin (Tables.weapons)
        int w1 = Tables.weapons, w2 = w1 + 8, w3 = w1 + 16, w4 = w1 + 24, w5 = w1 + 32;

        Mem.align(2);
        objinfo = Mem.allocTop();

        //  slot   rotsp  movsp     shape  logic      render        hit          die         eyey firey gutsy oth ct cw cn dmg hp th  frame    fspd   bs rg wp  rl rc  hp pn bc sm  scale apd blood ypad
        e(player1, 0,     0xd0000,  player, L_PLAYER,  R_DRAWSHAPE_8, H_PLAYERHIT, D_PLAYERDIE, -110,-60,-64,0,  8, 6, 0, 1, 25, 0, 0,       0x6000, 1, 1, 0,  5, 0, 5, 0, 0, 0,  0x200,0, 0xf00, 1); // 0
        e(player2, 0,     0xd0000,  player, L_PLAYER,  R_DRAWSHAPE_8, H_PLAYERHIT, D_PLAYERDIE, -110,-60,-64,0, 16, 5, 1, 1, 25, 0, 0,       0x6000, 1, 1, 0,  5, 0, 5, 0, 0, 0,  0x200,0, 0xf00, 1); // 1
        e(dummy,   0,     0,        tokens, L_RTS,     R_DRAWSHAPE_1, H_HEALTHGOT, D_HEALTHGOT,         0,  0,  0, 0,  0,24, 0, 0,  0, 0, 0x20000, 0,      0, 0, 0,  0, 0, 5, 0, 0, 0,  0x200,0, 0xf00, 1); // 2 health
        e(dummy,   0,     0,        w2,     L_WEAPON,  R_DRAWSHAPE_1, H_WEAPONGOT, D_WEAPONGOT,         0,  0,  0, 0,  0,24, 0, 0,  0, 0, 0,       0x8000, 4, 4, 1,  0, 0, 0, 0, 0, 0,  0x200,0, 0,     0); // 3 weapon
        e(dummy,   0,     0,        tokens, L_RTS,     R_DRAWSHAPE_1, H_THERMOGOT, D_THERMOGOT,         0,  0,  0, 0,  0,24, 0, 0,  0, 0, 0,       0,      0, 0, 0,  0, 0, 5, 0, 0, 0,  0x200,0, 0xf00, 1); // 4 thermo
        e(dummy,   0,     0,        tokens, L_RTS,     R_DRAWSHAPE_1, H_THERMOGOT, D_THERMOGOT,         0,  0,  0, 0,  0,24, 0, 0,  0, 0, 0,       0,      0, 0, 0,  0, 0, 5, 0, 0, 0,  0x200,0, 0xf00, 1); // 5 infra
        e(dummy,   0,     0,        tokens, L_RTS,     R_DRAWSHAPE_1, H_INVISIGOT, D_INVISIGOT,         0,  0,  0, 0,  0,24, 0, 0,  0, 0, 0x10000, 0,      0, 0, 0,  0, 0, 5, 0, 0, 0,  0x200,0, 0xf00, 1); // 6 invisi
        e(dummy,   0,     0,        tokens, L_RTS,     R_DRAWSHAPE_1, H_INVINCGOT, D_INVINCGOT,         0,  0,  0, 0,  0,24, 0, 0,  0, 0, 0x20000, 0,      0, 0, 0,  0, 0, 5, 0, 0, 0,  0x200,0, 0xf00, 1); // 7 invinc
        e(dummy, 0xffff0000,0xc0000,dragon, L_DRAGON,  R_DRAWSHAPE_8, H_RTS,       D_BLOWDRAGON, -64,-144,-64,0, 0,27, 0,10,250, 0, 0,       0x4000,16,32, 0,  0, 0, 5, 0, 0, 0,  0x300,0, 0xf00, 1); // 8 dragon
        e(dummy,   0,     0,        tokens, L_BOUNCY,  R_DRAWSHAPE_1, H_BOUNCYGOT, D_BOUNCYGOT,         0,  0,  0, 0,  0,24, 0, 0,  0, 0, 0x30000, 0,      0, 0, 0,  0, 0, 0, 0, 0, 0,  0x200,0, 0xf00, 1); // 9 bouncy
        e(dummy, 0x30000, 0x60000,  marine, L_MONSTER, R_DRAWSHAPE_8, H_HURTNGRUNT,D_BLOWOBJECT, -64,-60,-64,0,  0,27, 0, 1,  5, 0, 0,       0x6000,16,32, 0,  0, 0, 5, 0, 0, 0,  0x200,0, 0xf00, 1); // 10 marine
        e(dummy, 0x30000, 0x40000,  baldy,  L_BALDY,   R_DRAWSHAPE_8, H_HURTNGRUNT,D_BLOWOBJECT, -64,-60,-64,0,  0,27, 0, 2, 10, 0, 0,       0x4000, 8,16, 0,  0, 0, 3, 4, 0, 0,  0x220,0, 0xf00, 1); // 11 baldy
        e(dummy, 0x30000, 0x20000,  terra,  L_TERRA,   R_DRAWSHAPE_8, H_HURTTERRA, D_BLOWTERRA,  -64,-60,-64,0,  0,27, 0, 1, 35, 0, 0,       0x6000,32,48, 0,  0, 0, 0,12, 0, 5,  0x280,0, 0xfff, 1); // 12 terra
        e(dummy, 0,       0x80000,  ghoul,  L_GHOUL,   R_DRAWSHAPE_8, H_HURTGHOUL, D_BLOWOBJECT, -64,-64,-64,0,  0,27, 0, 0,  5, 0, 0,       0,     32,48, 0,  0, 0, 5,12, 0, 5,  0x200,0, 0x80f0,1); // 13 ghoul
        e(dummy, 0x30000, 0xa0000,  phantom,L_PHANTOM, R_DRAWSHAPE_8, H_HURTNGRUNT,D_BLOWOBJECT, -64,-60,-64,0,  0, 3, 0, 3, 10, 0, 0,       0xa000, 8,16, 0,  0, 0, 7, 0, 0, 0,  0x280,0, 0xff0, 1); // 14 phantom
        e(dummy, 0x30000, 0x70000,  demon,  L_DEMON,   R_DRAWSHAPE_8, H_HURTNGRUNT,D_BLOWOBJECT, -64,-90,-72,0,  0, 3, 0, 5, 25, 0, 0,       0x7000,32, 4, 0,  0, 0, 5, 0, 0, 0,  0x380,0, 0xf00, 1); // 15 demon
        e(dummy, 0,       0,        w1,     L_WEAPON,  R_DRAWSHAPE_1, H_WEAPONGOT, D_WEAPONGOT,         0,  0,  0, 0,  0,24, 0, 0,  0, 0, 0,       0x8000, 4, 4, 0,  0, 0, 0, 0, 0, 0,  0x200,0, 0,     0); // 16 weapon1
        e(dummy, 0,       0,        w2,     L_WEAPON,  R_DRAWSHAPE_1, H_WEAPONGOT, D_WEAPONGOT,         0,  0,  0, 0,  0,24, 0, 0,  0, 0, 0,       0x8000, 4, 4, 1,  0, 0, 0, 0, 0, 0,  0x200,0, 0,     0); // 17 weapon2
        e(dummy, 0,       0,        w3,     L_WEAPON,  R_DRAWSHAPE_1, H_WEAPONGOT, D_WEAPONGOT,         0,  0,  0, 0,  0,24, 0, 0,  0, 0, 0,       0x8000, 4, 4, 2,  0, 0, 0, 0, 0, 0,  0x200,0, 0,     0); // 18 weapon3
        e(dummy, 0,       0,        w4,     L_WEAPON,  R_DRAWSHAPE_1, H_WEAPONGOT, D_WEAPONGOT,         0,  0,  0, 0,  0,24, 0, 0,  0, 0, 0,       0x8000, 4, 4, 3,  0, 0, 0, 0, 0, 0,  0x200,0, 0,     0); // 19 weapon4
        e(dummy, 0,       0,        w5,     L_WEAPON,  R_DRAWSHAPE_1, H_WEAPONGOT, D_WEAPONGOT,         0,  0,  0, 0,  0,24, 0, 0,  0, 0, 0,       0x8000, 4, 4, 4,  0, 0, 0, 0, 0, 0,  0x200,0, 0,     0); // 20 weapon5
        e(dummy, 0x30000, 0x60000,  lizard, L_LIZARD,  R_DRAWSHAPE_8, H_LIZHURT,   D_BLOWOBJECT, -64,-60,-64,0,  0,27, 0, 2, 10, 0, 0,       0x4000, 8, 8, 0,  0, 0, 2, 3, 0, 0,  0x240,0, 0xf0f, 1); // 21 lizard
        e(dummy, 0,       0xc0000,  deathhead,L_DEATHHEAD,R_DRAWSHAPE_8,H_HURTDEATH,D_BLOWDEATH,  -64,-60,-96,0,  0,27, 0, 3, 35, 0, 0x8000, 0x6000,-8,16, 0,  0, 0,10, 0, 0, 0,  0x200,0, 0xf00, 1); // 22 deathhead
        e(dummy, 0x30000, 0x60000,  troll,  L_TROLL,   R_DRAWSHAPE_8, H_TROLLHURT, D_BLOWOBJECT, -64,-60,-64,0,  0,27, 0, 3, 18, 0, 0,       0x4000, 8, 8, 0,  0, 0, 2, 3, 0, 0,  0x240,0, 0xf00, 1); // 23 troll
    }

    private ObjInfo() {
    }

    /** Émet une entrée de 84 octets dans l'ordre exact du struct objinfo. */
    private static void e(int slot, int rotspeed, int movspeed, int shape, int logic, int render,
                          int hit, int die, int eyey, int firey, int gutsy, int othery, int colltype,
                          int collwith, int cntrl, int damage, int hitpoints, int think, int frame,
                          int framespeed, int base, int range, int weapon, int reload, int reloadcnt,
                          int hurtpause, int punchrate, int bouncecnt, int something, int scale,
                          int apad, int blood, int ypad) {
        Mem.dcL(slot, rotspeed, movspeed, shape, logic, render, hit, die);
        Mem.dcW(eyey, firey, gutsy, othery, colltype, collwith, cntrl, damage, hitpoints, think);
        Mem.dcL(frame, framespeed);
        Mem.dcW(base, range, weapon);
        Mem.dcB(reload, reloadcnt);
        Mem.dcW(hurtpause, punchrate, bouncecnt, something, scale, apad, blood, ypad);
    }

    /**
     * Libère (réinitialise) les descripteurs de sprites des MONSTRES, comme `freeobjlist`
     * (gloom.s:10285) que `scriptplay` appelle à chaque chargement de niveau : les monstres
     * sont rechargés et re-remappés à chaque niveau (leur palette s'ajoute APRÈS celle de la
     * tuile, dont la taille varie par niveau → leurs index de palette changent). Sans ce
     * reset, un monstre mis en cache au niveau 1 garde des index obsolètes → palette qui « saute ».
     * Les permanents (player/tokens/armes) NE sont PAS touchés (index fixes, remappés une fois).
     */
    public static void clearMonsterShapes() {
        int[] descs = {marine, baldy, terra, ghoul, demon, phantom, lizard, deathhead, dragon, troll};
        for (int d : descs) { Mem.wl(d, 0); Mem.wl(d + 4, 0); }   // clr.l (a3) ; clr.l 4(a3)
    }

    /** Descripteur d'objet : [main(l)=0][chunks(l)=0][nom de fichier 0-terminé]. */
    private static int desc(String name) {
        int base = Mem.alloc(8);
        Mem.dcStr(name);
        Mem.dcB(0);
        return base;
    }
}
