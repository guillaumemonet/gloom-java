package gloom;

/**
 * Décompression Crunch-Mania (Thomas Schwarz) — portage de `decrm.s`.
 *
 * Deux formats : `CrM!` (FastDecruncher, LZ simple) et `CrM2` (LZHDecruncher, LZ + Huffman
 * canonique). Les assets crunchés (ex. `txts/roof1`) commencent par l'un de ces magiques.
 *
 * En-tête (14 octets, cf. NormalDecrunch) : magic(4) + MinSecDist(2) + destLen(4) + srcLen(4).
 * Les deux décrunchers lisent le flux **à l'envers** (depuis la fin) et écrivent la sortie
 * **à l'envers** (depuis la fin du tampon destination).
 *
 * Port fidèle : registres 68k → variables Java ; les tables de travail (Cmp/Add/Real/AnzPerBits)
 * sont contiguës dans un bloc Mem pour préserver l'arithmétique d'offsets croisés de l'asm.
 */
public final class Decrunch {

    private static final int CrM2 = ('C' << 24) | ('r' << 16) | ('M' << 8) | '2';
    private static final int CrMbang = ('C' << 24) | ('r' << 16) | ('M' << 8) | '!';

    // offsets dans le bloc de tables (a6 = base), cf. decrm.s
    private static final int OCmpTab = 0, ORealTab = 128, OAnzPerBits = 1182;
    private static final int TABSZ = 1248;

    // état du lecteur de bits (registres 68k)
    private int a1;          // pointeur d'écriture (recule)
    private int a2;          // pointeur de lecture (recule)
    private long d6;         // tampon de bits (32 bits non signés)
    private int d7;          // bits restants dans le mot courant
    private int tab;         // a6 = base des tables (Mem)

    /**
     * Détecte le format et décrunche `srcHeader` (avec en-tête) vers un nouveau tampon.
     * Renvoie l'adresse Mem des données décrunchées, ou `srcHeader+14` si non crunché.
     */
    public static int decrunch(int srcHeader) {
        int magic = Mem.l(srcHeader);
        if (magic != CrM2 && magic != CrMbang) return srcHeader;   // pas crunché
        int destLen = Mem.l(srcHeader + 6);
        int srcLen = Mem.l(srcHeader + 10);
        int dest = Mem.alloc(destLen);
        Decrunch d = new Decrunch();
        d.a1 = dest;
        d.a2 = srcHeader + 14;                                     // données compressées
        if (magic == CrM2) d.lzh(destLen, srcLen);
        else d.fast(destLen, srcLen);
        return dest;
    }

    /** True si le fichier commence par un magique Crunch-Mania. */
    public static boolean isCrunched(int srcHeader) {
        int magic = Mem.l(srcHeader);
        return magic == CrM2 || magic == CrMbang;
    }

    // ------------------------------------------------------------------
    // rotations 32 bits (ror.l / rol.l)
    private static long ror32(long v, int n) {
        n &= 31; if (n == 0) return v & 0xffffffffL;
        return ((v >>> n) | (v << (32 - n))) & 0xffffffffL;
    }
    private static long rol32(long v, int n) {
        n &= 31; if (n == 0) return v & 0xffffffffL;
        return ((v << n) | (v >>> (32 - n))) & 0xffffffffL;
    }

    /** GetBits/GetBits2 (decrm.s) : extrait d1 bits du flux → d0. */
    private int getBits(int d1) {
        int d0 = (int) (d6 & 0xffff);                              // move.w d6,d0
        d6 = (d6 >>> d1) & 0xffffffffL;                            // lsr.l d1,d6
        d7 -= d1;
        if (d7 <= 0) {                                            // BGT GBNoLoop
            d7 += 16;                                            // add d3,d7
            d6 = ror32(d6, d7);                                   // ror.l d7,d6
            a2 -= 2;
            d6 = (d6 & 0xffff0000L) | (Mem.uw(a2) & 0xffffL);     // move.w -(a2),d6
            d6 = rol32(d6, d7);                                   // rol.l d7,d6
        }
        int mask = (d1 >= 16) ? 0xffff : ((1 << d1) - 1);         // AndData[d1-1]
        return d0 & mask;
    }

    // ==================================================================
    // CrM2 — LZHDecruncher (LZ + Huffman)
    // ==================================================================

    private void lzh(int destLen, int srcLen) {
        tab = Mem.alloc(TABSZ);                                    // bloc de tables (Tabbs)
        int a6 = tab + 2;                                         // a6 = Tabbs+2
        this.tab = a6;
        a1 += destLen;                                            // fin du tampon dest
        a2 += srcLen;                                            // fin des données src
        initBits();

        while (true) {                                           // BufLoop
            // efface AnzPerBits (16 longs = 32 mots)
            for (int i = 0; i < 32; i++) Mem.ww(a6 + OAnzPerBits + i * 2, 0);

            // lit les tables de longueurs de codes Huffman (distance puis longueur)
            readTab(a6 + OAnzPerBits + 32, a6 + ORealTab + 30, 9);
            readTab(a6 + OAnzPerBits, a6 + ORealTab, 4);

            // construit les tables de comparaison canoniques
            calcCmpTab(a6 + OAnzPerBits + 32, a6 + OCmpTab - 2);
            calcCmpTab(a6 + OAnzPerBits, a6 + OCmpTab + 30);

            int d5 = getBits(16);                                // nb de symboles du bloc - 1
            int a0len = a6 + ORealTab + 30;                      // table des longueurs
            int a5dist = a0len - 30;                             // table des distances

            do {                                                 // decrloop2
                int d0 = readIt(a6, a0len);                      // décode un symbole (longueur)
                if ((d0 & 0x100) == 0) {                         // bit 8 = 0 → séquence
                    int d4 = d0;                                 // longueur - 1
                    int dd = readIt(a6 + OCmpTab + 32, a5dist);  // décode la distance (table dist)
                    int d1 = dd, d2 = dd;
                    if (dd == 0) { d1 = 1; d2 = 16; }            // sc1
                    int bits = getBits(d1);
                    bits |= (1 << d2);                           // BSET d2,d0
                    int a3 = a1 + (short) bits + 1;              // LEA 1(a1,d0.w),a3
                    for (int i = 0; i <= d4; i++) { a3--; a1--; Mem.wb(a1, Mem.b(a3)); } // sloop
                    a3--; a1--; Mem.wb(a1, Mem.b(a3));           // MOVE.b -(a3),-(a1)
                    a3--; d0 = Mem.b(a3) & 0xff;                 // MOVE.b -(a3),d0
                }
                a1--; Mem.wb(a1, d0);                            // skip: MOVE.b d0,-(a1)
            } while (d5-- != 0);

            if (getBits(1) == 0) return;                         // bit de continuation
        }
    }

    /** initialise le lecteur de bits pour la lecture à l'envers (LZH/Fast). */
    private void initBits() {
        a2 -= 2;
        int d0 = Mem.uw(a2);                                     // nb de bits dans le dernier mot
        a2 -= 4;
        d6 = Mem.l(a2) & 0xffffffffL;                            // 1er long
        d7 = 16 - d0;
        d6 = (d6 >>> d7) & 0xffffffffL;                          // amène les 1ers bits au début
        d7 = d0;
    }

    /** ReadIt (decrm.s:289) : décode un symbole Huffman canonique. a4=CmpTab base, a0=RealTab. */
    private int readIt(int a4, int a0) {
        int d1 = 0;                                             // code accumulé
        while (true) {                                          // RIloop
            int carry;
            d7 -= 1;
            if (d7 == 0) {                                      // BTLoop : recharge un mot
                d7 = 16;
                int d0 = (int) (d6 & 0xffff);
                d6 = (d6 >>> 1) & 0xffffffffL;
                d6 = ror32(d6, 16);                            // swap
                a2 -= 2;
                d6 = (d6 & 0xffff0000L) | (Mem.uw(a2) & 0xffffL);
                d6 = ror32(d6, 16);                            // swap
                carry = d0 & 1;                                // lsr.w #1,d0 → carry
            } else {                                           // BTEnd
                carry = (int) (d6 & 1);                        // lsr.l #1,d6 → carry
                d6 = (d6 >>> 1) & 0xffffffffL;
            }
            d1 = ((d1 << 1) | carry) & 0xffff;                 // roxl.w #1,d1
            int thr = Mem.uw(a4); a4 += 2;                      // move.w (a4)+,d0
            if (Integer.compareUnsigned(thr, d1) > 0) {        // CMP d1,d0 ; BLS RIloop (loop si d0<=d1)
                d1 = (d1 + Mem.uw(a4 + 62)) & 0xffff;          // add.w 62(a4),d1  (a4 post-incrément)
                return Mem.uw(a0 + ((d1 << 1) & 0xffff));      // move.w 0(a0,d1*2),d0
            }
        }
    }

    /** ReadTab (decrm.s:336) : lit les comptes AnzPerBits (a0) et les symboles RealTab (a4). */
    private void readTab(int a0, int a4, int d2) {
        int d5 = getBits(4) - 1;                               // nb de groupes de longueurs - 1
        int d4 = 0;
        int sum = 0;                                           // a3 = total de symboles
        for (int i = 0; i <= d5; i++) {                        // RTlop
            d4 += 1;
            int d1 = Math.min(d4, d2);
            int d0 = getBits(d1);
            Mem.ww(a0, d0); a0 += 2;
            sum += d0;
        }
        for (int i = 0; i < sum; i++) {                        // RTlp2
            int d0 = getBits(d2);
            Mem.ww(a4, d0); a4 += 2;
        }
    }

    /** CalcCmpTab (decrm.s:363) : construit la table de seuils (CmpTab) + bases (AddTab à +64). */
    private void calcCmpTab(int a3, int a4) {
        Mem.ww(a4, 0); a4 += 2;                                // clr.w (a4)+
        int d2 = 0, d3 = 0, d1 = 1;
        for (int loop = 0; loop < 15; loop++) {                // CClop (15x)
            int d6w = Mem.uw(a3); a3 += 2;                     // AnzPerBits[bitlen]
            Mem.ww(a4 + 64, d3);                               // AddTab base
            int d0 = Mem.uw(a4 - 2);                           // CmpTab précédent
            d0 = (d0 + d0) & 0xffff;
            Mem.ww(a4 + 64, M68k.w(Mem.uw(a4 + 64) - d0));     // AddTab adjust
            d3 = (d3 + d6w) & 0xffff;
            d6w = (d6w * d1) & 0xffff;                         // MULU d1,d6 (d1=1)
            d2 = (d2 + d6w) & 0xffff;
            Mem.ww(a4, d2); a4 += 2;                           // seuil CmpTab
            d2 = (d2 << 1) & 0xffff;                           // LSL.w #1,d2
        }
    }

    // ==================================================================
    // CrM! — FastDecruncher (LZ simple)
    // ==================================================================

    private void fast(int destLen, int srcLen) {
        int a5 = a1;                                           // début décrunché (fin du décrunch)
        a1 += destLen;
        a2 += srcLen;
        initBits();

        while (Integer.compareUnsigned(a1, a5) > 0) {          // DecrLoop (a1>a5)
            if (bitTest()) {                                   // 1er bit 1 → octets isolés
                int d4 = 0;
                // InsertSeq : compte les bits identiques
                int d1, sumStart;
                if (!bitTest()) { d1 = 1; sumStart = 1; }
                else if (!bitTest()) { d1 = 2; sumStart = 3; }
                else if (!bitTest()) { d1 = 4; sumStart = 7; }
                else { d1 = 8; sumStart = 0x17; }
                d4 = (getBits(d1) + sumStart) & 0xffff;        // longueur séquence
                if (d4 == 22) { fastInsertBytes(specialInsertCount()); continue; } // SpecialInsert
                if (d4 > 22) d4 -= 1;
                // distance de la séquence
                int d2v;
                int db;
                if (!bitTest()) { db = 9; d2v = 0x20; }
                else if (!bitTest()) { db = 5; d2v = 0; }
                else { db = 14; d2v = 0x220; }
                int d0 = (getBits(db) + d2v) & 0xffff;
                int a3 = a1 + (short) d0;                      // LEA 0(a1,d0.w),a3
                for (int i = 0; i <= d4; i++) { a3--; a1--; Mem.wb(a1, Mem.b(a3)); } // InsSeqLoop
            } else {
                fastInsertBytes(0);                            // InsertBytes (d4=0 → 1 octet)
            }
        }
    }

    /** SpecialInsert (decrm.s:105) : nombre supplémentaire d'octets isolés. */
    private int specialInsertCount() {
        int d4 = 14;
        int d1 = bitTest() ? 5 : 14;
        d4 = (d4 + getBits(d1)) & 0xffff;
        return d4;
    }

    /** InsertBytes (decrm.s:98) : insère d4+1 octets littéraux. */
    private void fastInsertBytes(int d4) {
        for (int i = 0; i <= d4; i++) { a1--; Mem.wb(a1, getBits(8)); }
    }

    /** BitTest (decrm.s:170) : extrait 1 bit (renvoie son état). */
    private boolean bitTest() {
        d7 -= 1;
        int carry;
        if (d7 == 0) {                                         // recharge
            d7 = 16;
            int d0 = (int) (d6 & 0xffff);
            d6 = (d6 >>> 1) & 0xffffffffL;
            d6 = ror32(d6, 16);
            a2 -= 2;
            d6 = (d6 & 0xffff0000L) | (Mem.uw(a2) & 0xffffL);
            d6 = ror32(d6, 16);
            carry = d0 & 1;
        } else {
            carry = (int) (d6 & 1);
            d6 = (d6 >>> 1) & 0xffffffffL;
        }
        return carry != 0;
    }
}
