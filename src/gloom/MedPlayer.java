package gloom;

/**
 * Lecteur de modules OctaMED MMD0/MMD1 (musique de Gloom — sfxs/med1, med2), porté en Java.
 *
 * L'original utilise le replayer 68k `medplay` (blob hunk Amiga, non portable). Ici on réécrit
 * un séquenceur + mixeur 4 voies « façon Paula » qui parse le module et produit du PCM 16 bits
 * mono à {@link #RATE} Hz (mixé côté hôte au lieu des registres audio Amiga — divergence
 * sanctionnée, comme l'affichage). Instruments = échantillons 8 bits signés (type 0) ; les sons
 * synthétiques MED ne sont pas gérés (Gloom n'en utilise pas).
 *
 * Effets gérés : 00 (rien/arpège), 0C (volume hex), 0D (slide volume), 0F (tempo), 09 (vitesse),
 * 03 (portamento approx). Les autres sont ignorés (rares dans les modules de Gloom).
 */
public final class MedPlayer {

    public static final int RATE = 22050;          // fréquence de sortie du mixeur (Hz)
    private static final int PAULA = 3546895;       // horloge Paula PAL
    private static final int CHANS = 4;

    // --- module parsé ---
    private byte[] mod;
    private int songOff, blockArrOff, smplArrOff;
    private int numBlocks, songLen, defTempo, speed, flags, flags2;
    private int playTransp, masterVol;
    private final int[] playSeq = new int[256];

    private static final class Instr {
        byte[] data; int loopStart, loopLen, svol, strans;
    }
    private final Instr[] instr = new Instr[64];

    // --- état de lecture ---
    private int seqPos, blockRow, tick, ticksPerRow;
    private int curBlock, curLines, curTracks;
    private int samplesPerTick, tickSampleLeft;
    private boolean finished;

    private static final class Chan {
        Instr ins; double pos; double step; int vol; int period;
        int volSlideUp, volSlideDn; boolean active;
    }
    private final Chan[] ch = new Chan[CHANS];

    public MedPlayer(byte[] module) {
        mod = module;
        for (int i = 0; i < CHANS; i++) ch[i] = new Chan();
        parse();
        rewind();
    }

    public boolean valid() { return mod != null && numBlocks > 0; }

    // ------------------------------------------------------------------ parse
    private int u8(int o)  { return mod[o] & 0xff; }
    private int s8(int o)  { return mod[o]; }
    private int u16(int o) { return ((mod[o] & 0xff) << 8) | (mod[o + 1] & 0xff); }
    private int u32(int o) { return (u16(o) << 16) | u16(o + 2); }

    private void parse() {
        String id = "" + (char) u8(0) + (char) u8(1) + (char) u8(2) + (char) u8(3);
        if (!id.equals("MMD0") && !id.equals("MMD1")) { mod = null; return; }
        boolean mmd1 = id.equals("MMD1");
        songOff     = u32(8);
        blockArrOff = u32(16);
        smplArrOff  = u32(24);

        numBlocks = u16(songOff + 504);
        songLen   = u16(songOff + 506);
        for (int i = 0; i < 256; i++) playSeq[i] = u8(songOff + 508 + i);
        defTempo  = u16(songOff + 764);
        playTransp = s8(songOff + 766);
        flags     = u8(songOff + 767);
        flags2    = u8(songOff + 768);
        speed     = u8(songOff + 769); if (speed == 0) speed = 6;
        masterVol = u8(songOff + 786); if (masterVol == 0) masterVol = 64;

        // instruments (échantillons type 0) + méta (rep/replen/svol/strans depuis la song)
        for (int i = 0; i < 63; i++) {
            int p = u32(smplArrOff + i * 4);
            if (p == 0 || p + 6 > mod.length) continue;
            int len = u32(p);
            int type = (short) u16(p + 4);
            if (type != 0 || len <= 0 || p + 6 + len > mod.length) continue;   // type 0 = sample brut
            Instr in = new Instr();
            in.data = new byte[len];
            System.arraycopy(mod, p + 6, in.data, 0, len);
            int meta = songOff + i * 8;
            int rep = u16(meta), replen = u16(meta + 2);
            in.loopStart = rep * 2;
            in.loopLen   = replen * 2;
            in.svol   = u8(meta + 6);
            in.strans = s8(meta + 7);
            instr[i + 1] = in;                         // notes référencent l'instrument en 1-based
        }
        this.mmd1 = mmd1;
    }
    private boolean mmd1;

    // ------------------------------------------------------------ timing/tempo
    /** Réglage manuel du tempo (×). 1.0 = défaut ; <1 ralentit, >1 accélère. -Dmedspeed= */
    private static final double SPEED_TUNE = Double.parseDouble(System.getProperty("medspeed", "1.0"));

    /**
     * Échantillons par tick depuis tempo/vitesse. En MED « non-BPM » (cas de Gloom), la valeur de
     * tempo programmait directement la fréquence d'interruption du timer CIA → on l'utilise comme
     * fréquence de tick en Hz (deftempo Hz). En mode BPM explicite, relation standard bpm*2/5.
     */
    private void recalcTiming() {
        ticksPerRow = speed;
        double bpm;
        if (defTempo <= 10) {                          // ancien tempo « primaire » MED (rare)
            bpm = 125; ticksPerRow = Math.max(1, defTempo);
        } else {                                       // tempo MED par défaut 33 ≈ 125 BPM standard
            bpm = defTempo * 125.0 / 33.0;
        }
        double ticksPerSec = bpm * 2.0 / 5.0 * SPEED_TUNE;   // relation standard (125 BPM → 50 Hz)
        samplesPerTick = (int) Math.round(RATE / ticksPerSec);
        if (samplesPerTick < 1) samplesPerTick = 1;
    }

    public void rewind() {
        seqPos = 0; blockRow = 0; tick = 0; finished = false;
        recalcTiming();
        tickSampleLeft = 0;
        for (Chan c : ch) { c.active = false; c.vol = 0; c.pos = 0; c.ins = null; }
        loadBlock();
    }

    private void loadBlock() {
        curBlock = playSeq[seqPos % Math.max(1, songLen)];
        int bp = u32(blockArrOff + curBlock * 4);
        // MMD1 : en-tête = numtracks(w) @+0, lines(w) @+2, info(l) @+4 ; notes @+8.
        if (mmd1) { curTracks = u16(bp); curLines = u16(bp + 2) + 1; }
        else      { curTracks = u8(bp);  curLines = u8(bp + 1) + 1; }
    }

    // ------------------------------------------------------------------ mixage
    /** Remplit `out` (mono 16 bits) de `n` échantillons. Renvoie false quand la chanson boucle. */
    public void mix(short[] out, int n) {
        for (int i = 0; i < n; i++) {
            if (tickSampleLeft <= 0) { doTick(); tickSampleLeft = samplesPerTick; }
            tickSampleLeft--;

            int acc = 0;
            for (Chan c : ch) {
                if (!c.active || c.ins == null) continue;
                int ip = (int) c.pos;
                byte[] d = c.ins.data;
                if (ip >= d.length) { c.active = false; continue; }
                acc += d[ip] * c.vol;                  // 8 bits signé × vol(0..64)
                c.pos += c.step;
                // bouclage / fin
                if (c.ins.loopLen > 2) {
                    int end = c.ins.loopStart + c.ins.loopLen;
                    if (c.pos >= end) c.pos -= c.ins.loopLen;
                } else if ((int) c.pos >= d.length) {
                    c.active = false;
                }
            }
            acc = acc * masterVol / 64;
            acc = (acc * 3) / 4;                        // marge anti-saturation (4 voies)
            if (acc > 32767) acc = 32767; else if (acc < -32768) acc = -32768;
            out[i] = (short) acc;
        }
    }

    private void doTick() {
        if (tick == 0) rowTrigger();
        else perTickEffects();
        tick++;
        if (tick >= ticksPerRow) {
            tick = 0;
            blockRow++;
            dbgRowsPlayed++;
            if (blockRow >= curLines) {
                blockRow = 0;
                dbgBlocksPlayed++;
                seqPos++;
                if (seqPos >= songLen) { seqPos = 0; finished = true; }
                loadBlock();
            }
        }
    }

    private int noteOff(int row, int track) {
        int bp = u32(blockArrOff + curBlock * 4);
        if (mmd1) return bp + 8 + (row * curTracks + track) * 4;
        return bp + 2 + (row * curTracks + track) * 3;
    }

    /** Tick 0 d'une ligne : déclenche les notes + effets « set ». */
    private void rowTrigger() {
        for (int t = 0; t < curTracks && t < CHANS; t++) {
            int o = noteOff(blockRow, t);
            int note, ins, cmd, arg;
            if (mmd1) { note = u8(o); ins = u8(o + 1); cmd = u8(o + 2); arg = u8(o + 3); }
            else {                                     // MMD0 : 3 octets compactés
                int b0 = u8(o), b1 = u8(o + 1), b2 = u8(o + 2);
                note = b0 & 0x3f;
                ins = ((b0 & 0x80) >> 2) | ((b0 & 0x40) >> 1) | (b1 >> 4);
                cmd = b1 & 0x0f; arg = b2;
            }
            Chan c = ch[t];
            c.volSlideUp = c.volSlideDn = 0;
            if (note > 0 && ins > 0 && instr[ins] != null) {
                c.ins = instr[ins];
                c.pos = 0;
                c.vol = c.ins.svol;
                c.active = true;
                setPeriodFromNote(c, note);
            } else if (note > 0 && c.ins != null) {
                c.pos = 0; c.active = true;
                setPeriodFromNote(c, note);
            }
            applyEffect(c, cmd, arg, true);
        }
    }

    private void perTickEffects() {
        for (int t = 0; t < curTracks && t < CHANS; t++) {
            int o = noteOff(blockRow, t);
            int cmd, arg;
            if (mmd1) { cmd = u8(o + 2); arg = u8(o + 3); }
            else { cmd = u8(o + 1) & 0x0f; arg = u8(o + 2); }
            applyEffect(ch[t], cmd, arg, false);
        }
    }

    private void applyEffect(Chan c, int cmd, int arg, boolean firstTick) {
        switch (cmd) {
            case 0x0C -> { if (firstTick) c.vol = clampVol(arg); }     // set volume (hex)
            case 0x0D -> {                                            // volume slide
                if (firstTick) { c.volSlideUp = arg >> 4; c.volSlideDn = arg & 0x0f; }
                else c.vol = clampVol(c.vol + c.volSlideUp - c.volSlideDn);
            }
            case 0x0F -> {                                           // set tempo — MAIS 0xF1..0xFF
                if (firstTick && arg >= 1 && arg <= 0xF0) {          // = fonctions spéciales par note,
                    defTempo = arg; recalcTiming();                  //   PAS un tempo (ex. 0xF8) → ignorées
                }
            }
            case 0x09 -> { if (firstTick && arg >= 1 && arg <= 0x20) { speed = arg; ticksPerRow = arg; } } // vitesse
            // 0x00 (arpège), 0x03 (porta), autres : non gérés → laissés tels quels
            default -> { }
        }
    }

    private int clampVol(int v) { return v < 0 ? 0 : Math.min(v, 64); }

    private void setPeriodFromNote(Chan c, int note) {
        int n = note - 1 + c.ins.strans + playTransp;          // demi-tons depuis C-1
        double period = 856.0 * Math.pow(2.0, -n / 12.0);      // table de périodes Amiga
        if (period < 14) period = 14;
        c.period = (int) Math.round(period);
        double rate = (double) PAULA / c.period;               // Hz de lecture de l'échantillon
        c.step = rate / RATE;                                  // pas en unités-échantillon / frame
    }

    public boolean finished() { return finished; }

    // --- diagnostics ---
    public int dbgSongLen() { return songLen; }
    public int dbgNumBlocks() { return numBlocks; }
    public int dbgSpeed() { return speed; }
    public int dbgDefTempo() { return defTempo; }
    public int dbgSamplesPerTick() { return samplesPerTick; }
    public int dbgRowsPlayed, dbgBlocksPlayed;
}
