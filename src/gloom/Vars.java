package gloom;

/**
 * Variables globales de gloom.s (section data/bss).
 *
 * gloom.s est monolithique : ses globaux `dc.l`/`ds` (bloc ~7560-7660 et au-delà)
 * sont rassemblés ici, sous forme d'adresses Mem, au fur et à mesure que les
 * sous-systèmes en ont besoin. (Les tables précalculées et leurs pointeurs
 * sqr/castrots/camrots vivent dans gloom.data.Tables.)
 *
 * Pour l'instant : ce dont le sous-système 03 (palettes) a besoin.
 */
public final class Vars {

    // --- équates ---
    /** maxz = 16<<darkshft = 16<<7 (gloom.s:37) — profondeur d'ombrage max. */
    public static final int maxz = 16 << 7;     // 2048

    // --- pointeurs de palette (gloom.s:7571-7607, 12105) ---
    /** darktable : dc.l 0 (gloom.s:7571) — table profondeur→luminosité (maxz mots). */
    public static final int darktable;
    /** palette : dc.l 0 (gloom.s:7587) — la palette courante. */
    public static final int palette;
    /** palettes : ds.l 16 (gloom.s:7589) — 16 palettes (une par niveau de luminosité). */
    public static final int palettes;
    /** palettesw : ds.l 16 (gloom.s:7590) — versions « flash blanc ». */
    public static final int palettesw;
    /** palettesr : ds.l 16 (gloom.s:7591) — versions « flash rouge ». */
    public static final int palettesr;

    /** map_rgbs : dc.l 0 (gloom.s:7597) — début de la palette de base (luminosité 0). */
    public static final int map_rgbs;
    /** map_rgbsw : dc.l 0 (gloom.s:7598) — buffer parallèle « blanc ». */
    public static final int map_rgbsw;
    /** map_rgbsr : dc.l 0 (gloom.s:7599) — buffer parallèle « rouge ». */
    public static final int map_rgbsr;
    /** map_rgbsat : dc.l 0 (gloom.s:7607) — fin de la palette de base / début des versions sombres. */
    public static final int map_rgbsat;
    /** map_rgbsend : dc.l 0 (gloom.s:12105) — fin de toutes les palettes. */
    public static final int map_rgbsend;

    // --- chargement de map / textures (gloom.s:7569, 7593-7610, 7893-7894, 11866-11867) ---
    /** maptable : dc.l 0 (gloom.s:7569) — pointeur vers la table de remap couleur (256 octets). */
    public static final int maptable;
    /** map_map : dc.l 0 (gloom.s:7593) — base du blob map chargé. */
    public static final int map_map;
    public static final int map_grid;      // dc.l 0 (7594)
    public static final int map_poly;      // zones
    public static final int map_ppnt;      // index poly par cellule
    public static final int map_txts;      // noms de textures
    public static final int map_anim;      // anims de texture
    public static final int map_events;    // table d'événements
    /** map_rgbsfrom / map_rgbsfrom2 : dc.l 0 (gloom.s:7609-7610) — bases d'append palette. */
    public static final int map_rgbsfrom;
    public static final int map_rgbsfrom2;
    /** textscrns : ds.l 8 (gloom.s:7893) — bases des 8 écrans de texture chargés. */
    public static final int textscrns;
    /** textures : ds.l 160 (gloom.s:7894) — 8×20 pointeurs de colonnes de texture (64×65). */
    public static final int textures;
    /** loadmem : dc.l 0 (gloom.s:11867) — destination fixe de chargement (0 = allouer). */
    public static final int loadmem;
    /** fileheader : ds.b 14 (gloom.s:11866) — en-tête lu pour la détection de crunch. */
    public static final int fileheader;

    // --- équates de listes (gloom.s:28-32) ---
    public static final int maxobjects = 256;
    public static final int maxdoors = 16;
    public static final int maxblood = 128;
    public static final int maxgore = 128;
    public static final int maxrotpolys = 32;

    // --- en-têtes de listes chaînées (alloclist, gloom.s:9478-9482) ---
    public static final int objects;
    public static final int doors;
    public static final int blood;
    public static final int gore;
    public static final int rotpolys;

    // --- globaux d'événements (gloom.s:2187,2222-2224,5296,7472,7484,7542-7543) ---
    public static final int doorsfxflag;   // dc 0 (2187)
    public static final int changedtxt;    // dc 0 (2222)
    public static final int deftxt;        // dc.l 0 + defgfxtxt dc.l 0 (2223-2224)
    public static final int doorsfx;       // dc.l 0 (7472)
    public static final int telesfx;       // dc.l 0 (7484)
    /** Échantillons SFX (adresses Mem [période][long-mots][PCM]), chargés par Sfx.loadSamples (09). */
    public static int shootsfx, shootsfx2, shootsfx3, shootsfx4, shootsfx5,
            diesfx, splatsfx, tokensfx, footstepsfx, robodiesfx, gruntsfx;
    public static final int eventobj;      // dc.l 0 (5296)
    public static final int finished;      // dc 0 (7542)
    public static final int finished2;     // dc 0 (7543)
    public static final int mode;          // dc 0 (7462) : mode d'affichage ; 0 → chunklogic2 (pas de gore au sol)
    public static final int sucking;       // dc.l 0 (3411) : joueur dont l'âme est aspirée ; 0 = aucun
    public static final int sucker;        // dc.l 0 (3410) : la deathhead qui aspire (cible des particules d'âme)
    public static final int p1x, p1z, p1r; // dc 0 (7510) : position de spawn du joueur (pour respawn)

    // --- caméra (gloom.s:7613-7628) ---
    public static final int camx, camy, camz, camr;     // dc 0 (mots)
    /** Matrice caméra cm1..cm4 (4 mots consécutifs, gloom.s:7619). */
    public static final int cm, cm1, cm2, cm3, cm4;
    /** Matrice inverse icm1..icm4 (gloom.s:7625). */
    public static final int icm, icm1, icm2, icm3, icm4;

    // --- listes de rendu par frame (gloom.s:7572-7584) ---
    public static final int inlist, inlistf, outlist, outlistf, shapelist;
    /** Arène mémoire de frame (gloom.s:7582-7583). */
    public static final int memory, memat;

    // --- bornes écran (gloom.s:7640-7645 ; midx=maxx, midy=maxy) ---
    public static final int minx, maxx, miny, maxy;
    public static final int midx, midy;

    /** frame : dc 0,0 (gloom.s:12152) — compteur de frame pour le dédup zo_done. */
    public static final int frame;

    // --- affichage / framebuffer (gloom.s:7634-7658) ---
    /** vertdraws : dc.l 0 — pointeur vers le tableau de `vd` (un par colonne). */
    public static final int vertdraws;
    /** cop : dc.l 0 — base du framebuffer (remplace la copperlist). */
    public static final int cop;
    public static final int copmod;        // dc 0 — stride vertical (octets/ligne)
    public static final int width;         // dc 0
    public static final int hite;          // dc 0
    public static final int wdiv32;        // dc 0
    public static final int wrem32;        // dc 0
    /** coloffs : ds.l 320 — offset de chaque colonne dans le framebuffer (= col*2). */
    public static final int coloffs;

    // --- sols/plafonds & effets (gloom.s:1637,1773-1775,7496-7552) ---
    public static final int floorflag;    // dc 1  (-1 noir / 0 split / 1 texturé)
    public static final int roofflag;     // dc 1
    public static final int floor;        // dc.l 0  (texture sol 128×128)
    public static final int roof;         // dc.l 0  (texture plafond)
    public static final int pixsize;      // dc 0
    public static final int flatcam;      // dc.l 0
    public static final int flatyadd;     // dc 0
    public static final int flatyadd2;    // dc 0
    public static final int qstrip;       // dc.l 0
    public static final int qstripbot;    // dc.l 0
    public static final int qcols;        // dc.b 1,2
    public static final int con0poke;     // dc $100,0

    // --- sprites / sang (gloom.s:7440,7527,7566-7567) ---
    public static final int thermo;       // dc 0  (vision thermique)
    public static final int infra;        // dc 0
    public static final int scrnblood;    // sang à l'écran ?
    /** stripands : 7 masques + le label pointe APRÈS (index par header négatif : stripands+d6*2). */
    public static final int stripandsTab; // base des 7 mots
    public static final int stripands;    // = stripandsTab + 14
    /** blcols : 16 niveaux de gris pour le sang, indexés par darktable[Z]. */
    public static final int blcols;

    // --- contrôle joueur (gloom.s:5087-5090, 11242, 5643) ---
    public static final int joyx, joyy, joyb, joys;   // dc 0 (left/rite, for/back, fire, strafe)
    /** joyx0 : tableau d'input par contrôleur (8 octets/contrôleur : joyx,joyy,joyb,joys). */
    public static final int joyx0;
    /** checkoffs : 9 paires (x,z) des cellules à tester pour la collision (gloom.s:5643). */
    public static final int checkoffs;
    public static final int paused;                   // dc $ff00
    public static final int framecnt;                 // compteur de frame (logique 1/2)

    static {
        // darktable : pointeur + buffer de maxz mots. Dans l'original, initmain alloue ce
        // buffer ; ici on le pré-alloue (initmain reporté au sous-système hôte).
        darktable = Mem.alloc(4);
        Mem.wl(darktable, Mem.alloc(maxz * 2));

        palette = Mem.alloc(4);                 // dc.l 0
        palettes = Mem.alloc(16 * 4);           // ds.l 16
        palettesw = Mem.alloc(16 * 4);          // ds.l 16
        palettesr = Mem.alloc(16 * 4);          // ds.l 16

        map_rgbs = Mem.alloc(4);                // dc.l 0 (rempli au chargement du niveau)
        map_rgbsw = Mem.alloc(4);
        map_rgbsr = Mem.alloc(4);
        map_rgbsat = Mem.alloc(4);
        map_rgbsend = Mem.alloc(4);

        // maptable : pointeur + buffer 256 octets (l'original l'alloue dans initmain, reporté).
        maptable = Mem.alloc(4);
        Mem.wl(maptable, Mem.alloc(256));

        map_map = Mem.alloc(4);
        map_grid = Mem.alloc(4);
        map_poly = Mem.alloc(4);
        map_ppnt = Mem.alloc(4);
        map_txts = Mem.alloc(4);
        map_anim = Mem.alloc(4);
        map_events = Mem.alloc(4);
        map_rgbsfrom = Mem.alloc(4);
        map_rgbsfrom2 = Mem.alloc(4);

        textscrns = Mem.alloc(8 * 4);           // ds.l 8
        textures = Mem.alloc(160 * 4);          // ds.l 160
        loadmem = Mem.alloc(4);                 // dc.l 0
        fileheader = Mem.alloc(14);             // ds.b 14

        objects = Lists.allocHeader();
        doors = Lists.allocHeader();
        blood = Lists.allocHeader();
        gore = Lists.allocHeader();
        rotpolys = Lists.allocHeader();

        doorsfxflag = Mem.alloc(2);
        changedtxt = Mem.alloc(2);
        deftxt = Mem.alloc(8);                  // deftxt + defgfxtxt (2 longs)
        doorsfx = Mem.alloc(4);
        telesfx = Mem.alloc(4);
        eventobj = Mem.alloc(4);
        finished = Mem.alloc(2);
        finished2 = Mem.alloc(2);
        mode = Mem.alloc(2);
        sucking = Mem.alloc(4);
        sucker = Mem.alloc(4);
        p1x = Mem.alloc(2);
        p1z = Mem.alloc(2);
        p1r = Mem.alloc(2);

        camx = Mem.alloc(2);
        camy = Mem.alloc(2);
        camz = Mem.alloc(2);
        camr = Mem.alloc(2);
        cm = Mem.alloc(8);  cm1 = cm; cm2 = cm + 2; cm3 = cm + 4; cm4 = cm + 6;
        icm = Mem.alloc(8); icm1 = icm; icm2 = icm + 2; icm3 = icm + 4; icm4 = icm + 6;

        inlist = Mem.alloc(4);
        inlistf = Mem.alloc(4);
        outlist = Mem.alloc(4);
        outlistf = Mem.alloc(4);
        shapelist = Mem.alloc(4);
        memory = Mem.alloc(4);
        memat = Mem.alloc(4);

        minx = Mem.alloc(2);
        maxx = Mem.alloc(2); midx = maxx;
        miny = Mem.alloc(2);
        maxy = Mem.alloc(2); midy = maxy;

        frame = Mem.alloc(4);   // dc 0,0

        vertdraws = Mem.alloc(4);
        cop = Mem.alloc(4);
        copmod = Mem.alloc(2);
        width = Mem.alloc(2);
        hite = Mem.alloc(2);
        wdiv32 = Mem.alloc(2);
        wrem32 = Mem.alloc(2);
        coloffs = Mem.alloc(320 * 4);

        floorflag = Mem.alloc(2); Mem.ww(floorflag, 1);   // dc 1
        roofflag = Mem.alloc(2);  Mem.ww(roofflag, 1);    // dc 1
        floor = Mem.alloc(4);
        roof = Mem.alloc(4);
        pixsize = Mem.alloc(2);
        flatcam = Mem.alloc(4);
        flatyadd = Mem.alloc(2);
        flatyadd2 = Mem.alloc(2);
        qstrip = Mem.alloc(4);
        qstripbot = Mem.alloc(4);
        qcols = Mem.dcB(1, 2);                            // dc.b 1,2
        con0poke = Mem.alloc(4); Mem.ww(con0poke, 0x100); // dc $100,0

        thermo = Mem.alloc(2);
        infra = Mem.alloc(2);
        scrnblood = Mem.alloc(2);
        // dc $f0ff,$ff0f,$fff0,$f00f,$f0f0,$ff00,$ffff ; stripands (label après)
        stripandsTab = Mem.dcW(0xf0ff, 0xff0f, 0xfff0, 0xf00f, 0xf0f0, 0xff00, 0xffff);
        stripands = stripandsTab + 14;                    // index par header<0 : stripands+d6*2
        // blcols dc $ccc,$bbb,...,$111
        blcols = Mem.dcW(0xccc, 0xbbb, 0xaaa, 0x999, 0x888, 0x777, 0x666, 0x555,
                         0x444, 0x333, 0x222, 0x111, 0x111, 0x111, 0x111, 0x111);

        joyx = Mem.alloc(2); joyy = Mem.alloc(2); joyb = Mem.alloc(2); joys = Mem.alloc(2);
        joyx0 = Mem.alloc(8 * 8);                 // 8 contrôleurs max
        // checkoffs : dc 0,0,-gs,0,gs,0,0,-gs,0,gs,-gs,-gs,gs,-gs,-gs,gs,gs,gs  (gs=256)
        int gs = 256;
        checkoffs = Mem.dcW(0, 0, -gs, 0, gs, 0, 0, -gs, 0, gs, -gs, -gs, gs, -gs, -gs, gs, gs, gs);
        paused = Mem.alloc(2);
        framecnt = Mem.alloc(2);
    }

    /**
     * Bloc alloclist d'initmain (gloom.s:9478-9482) : alloue les pools des listes
     * chaînées. À appeler une fois avant d'utiliser objects/doors/.../rotpolys.
     * (Extrait d'initmain, lui-même reporté au sous-système hôte.)
     */
    public static void initLists() {
        Lists.alloclist(objects, maxobjects, Defs.ob_size);
        Lists.alloclist(doors, maxdoors, Defs.do_size);
        Lists.alloclist(blood, maxblood, Defs.bl_size);
        Lists.alloclist(gore, maxgore, Defs.go_size);
        Lists.alloclist(rotpolys, maxrotpolys, Defs.rp_size);
    }

    private Vars() {
    }
}
