package gloom.data;

import gloom.Assets;
import gloom.Mem;

/**
 * Sous-système 02 — Tables précalculées et données embarquées (incbin).
 *
 * Traduction littérale des déclarations de données de gloom.s :
 *  - tables math/rendu précalculées : sqr, castrots(64), camrots(256), camrots2(1024),
 *    gridoffs(4) ;
 *  - sprites d'armes : bullet1-5 / sparks1-5 + table weapon1-5 (paires de pointeurs) ;
 *  - divers : gloombrush, title/title.pal, chatfont ;
 *  - code relocatable embarqué (medplayer, decrm) — chargé tel quel mais NON utilisable
 *    sans relocation de hunks (cf. ARCHITECTURE §7.2/§11) ; à réimplémenter/remplacer ;
 *  - buffers BSS : rgbs16, map_rgbs_.
 *
 * Chaque table est une adresse dans Mem (Assets.incbin charge le fichier du dépôt
 * GloomAmiga et renvoie l'adresse). Les pointeurs sqr/castrots/camrots reproduisent
 * les dc.l d'origine (gloom.s:7570/7630/7631), dont l'offset +8*160 de castrots.
 */
public final class Tables {

    // --- incbin : tables précalculées (gloom.s:13942-13972, 6133) ---
    /** sqrinc : incbin "sqr.bin" (gloom.s:13942) */
    public static final int sqrinc;
    /** castrotsinc : incbin "castrots64.bin" (gloom.s:13970) — variante gloom.s, focshft 6 */
    public static final int castrotsinc;
    /** castrots128inc : incbin "castrots128.bin" — variante gloom2.s (planaire/AGA), focshft 7 */
    public static final int castrots128inc;
    /** camrotsinc : incbin "camrots.bin" — 256 angles (gloom.s:13971) */
    public static final int camrotsinc;
    /** camrots2inc : incbin "camrots2.bin" — 1024 angles (gloom.s:13972) */
    public static final int camrots2inc;
    /** gridoffs : incbin "gridoffs4.bin" (gloom.s:6133) */
    public static final int gridoffs;
    /** Taille en octets de gridoffs (gridoffsf-gridoffs). */
    public static final int gridoffsLen;

    // --- incbin : sprites d'armes (gloom.s:13950-13960) ---
    // NON final : rechargés à chaque niveau (reloadWeapons) pour être remappés vers la palette
    // du niveau courant (remapanim est destructif → on repart de données fraîches).
    public static int bullet1, bullet2, bullet3, bullet4, bullet5;
    public static int sparks1, sparks2, sparks3, sparks4, sparks5;

    // --- table weapon1-5 : dc.l bulletN,sparksN (gloom.s:13944-13948) ---
    /** Adresse de la table de 5 paires (bullet,sparks) ; weapons + n*8. */
    public static final int weapons;

    // --- incbin : divers (gloom.s:13962-13973) ---
    public static final int gloombrush;
    /** gloom : incbin "title" (image titre) (gloom.s:13964) */
    public static final int gloomTitle;
    /** gloompal : incbin "title.pal" (gloom.s:13965) */
    public static final int gloompal;
    public static final int chatfont;

    // --- incbin : code relocatable (NON utilisable sans relocation) ---
    public static final int medplayer;
    public static final int decrm;

    // --- pointeurs de tables (gloom.s:7570/7630/7631) ---
    /** sqr : dc.l sqrinc */
    public static final int sqr;
    /** castrots : dc.l castrotsinc+8*160 (gloom.s, focshft 6) */
    public static final int castrots;
    /** castrots128 : castrots128inc+8*160 (gloom2.s, focshft 7) */
    public static final int castrots128;
    /** camrots : dc.l camrotsinc */
    public static final int camrots;
    /** camrots2 : dc.l camrots2inc (gloom.s:7632) */
    public static final int camrots2;

    // --- BSS (gloom.s:13975-13976) ---
    /** rgbs16 : ds.l 16 */
    public static final int rgbs16;
    /** map_rgbs_ : ds.w 256*16 */
    public static final int map_rgbs_;

    static {
        Mem.align(4);

        sqrinc = Assets.incbin("sqr.bin");

        bullet1 = Assets.incbin("bullet1.bin");
        bullet2 = Assets.incbin("bullet2.bin");
        bullet3 = Assets.incbin("bullet3.bin");
        bullet4 = Assets.incbin("bullet4.bin");
        bullet5 = Assets.incbin("bullet5.bin");

        sparks1 = Assets.incbin("sparks1.bin");
        sparks2 = Assets.incbin("sparks2.bin");
        sparks3 = Assets.incbin("sparks3.bin");
        sparks4 = Assets.incbin("sparks4.bin");
        sparks5 = Assets.incbin("sparks5.bin");

        gloombrush = Assets.incbin("gloombrush");
        gloomTitle = Assets.incbin("title");
        gloompal = Assets.incbin("title.pal");

        medplayer = Assets.incbin("medplay");
        decrm = Assets.incbin("decrm");

        castrotsinc = Assets.incbin("castrots64.bin");
        castrots128inc = Assets.incbin("castrots128.bin");
        camrotsinc = Assets.incbin("camrots.bin");
        camrots2inc = Assets.incbin("camrots2.bin");
        chatfont = Assets.incbin("chatfont.bin");

        gridoffs = Assets.incbin("gridoffs4.bin");
        gridoffsLen = Mem.allocTop() - gridoffs;     // gridoffsf - gridoffs

        // weapon1-5 : dc.l bulletN,sparksN
        weapons = Mem.dcL(bullet1, sparks1,
                          bullet2, sparks2,
                          bullet3, sparks3,
                          bullet4, sparks4,
                          bullet5, sparks5);

        // pointeurs de tables
        sqr = Mem.dcL(sqrinc);                       // sqr dc.l sqrinc
        castrots = Mem.dcL(castrotsinc + 8 * 160);   // castrots dc.l castrotsinc+8*160
        castrots128 = Mem.dcL(castrots128inc + 8 * 160); // idem pour la table focshft 7
        camrots = Mem.dcL(camrotsinc);               // camrots dc.l camrotsinc
        camrots2 = Mem.dcL(camrots2inc);             // camrots2 dc.l camrots2inc

        // BSS
        rgbs16 = Mem.alloc(16 * 4);                  // ds.l 16
        map_rgbs_ = Mem.alloc(256 * 16 * 2);         // ds.w 256*16
    }

    private Tables() {
    }

    /**
     * Recharge les sprites d'armes depuis le disque (données fraîches, indices locaux) et reconstruit
     * la table weapons. Appelé à chaque niveau avant remapanim (remap vers la palette du niveau).
     */
    public static void reloadWeapons() {
        bullet1 = Assets.incbin("bullet1.bin");
        bullet2 = Assets.incbin("bullet2.bin");
        bullet3 = Assets.incbin("bullet3.bin");
        bullet4 = Assets.incbin("bullet4.bin");
        bullet5 = Assets.incbin("bullet5.bin");
        sparks1 = Assets.incbin("sparks1.bin");
        sparks2 = Assets.incbin("sparks2.bin");
        sparks3 = Assets.incbin("sparks3.bin");
        sparks4 = Assets.incbin("sparks4.bin");
        sparks5 = Assets.incbin("sparks5.bin");
        int[] w = {bullet1, sparks1, bullet2, sparks2, bullet3, sparks3, bullet4, sparks4, bullet5, sparks5};
        for (int i = 0; i < w.length; i++) Mem.wl(weapons + i * 4, w[i]);
    }
}
