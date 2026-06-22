package gloom.tools;

import gloom.Assets;
import gloom.MedPlayer;

import java.io.DataOutputStream;
import java.io.FileOutputStream;

/**
 * Harnais : lecteur de modules MED. Charge sfxs/med1, mixe quelques secondes, exporte un WAV
 * (med1.wav) pour écoute, et vérifie que l'audio n'est pas silencieux. `gradle medTest`.
 */
public final class MedTest {
    public static void main(String[] args) throws Exception {
        String name = System.getProperty("mod", "sfxs/med1");
        int seconds = Integer.getInteger("sec", 20);
        byte[] mod = Assets.read(name);
        MedPlayer p = new MedPlayer(mod);
        if (!p.valid()) { System.out.println("ECHEC : module MED invalide " + name); System.exit(1); }

        int rate = MedPlayer.RATE;
        int total = rate * seconds;
        short[] pcm = new short[total];
        int chunk = 4096;
        for (int off = 0; off < total; off += chunk) {
            int n = Math.min(chunk, total - off);
            short[] buf = new short[n];
            p.mix(buf, n);
            System.arraycopy(buf, 0, pcm, off, n);
        }

        // stats
        long sumSq = 0; int peak = 0, nonZero = 0;
        for (short s : pcm) {
            sumSq += (long) s * s; int a = Math.abs(s);
            if (a > peak) peak = a; if (a > 64) nonZero++;
        }
        double rms = Math.sqrt((double) sumSq / total);
        System.out.printf("[info] %s : %ds @%dHz | RMS=%.0f peak=%d non-silence=%.0f%%%n",
                name, seconds, rate, rms, peak, 100.0 * nonZero / total);

        // mesure objective : durée d'UNE boucle complète de la chanson (jusqu'à finished)
        MedPlayer p2 = new MedPlayer(mod);
        int frames = 0, cap = rate * 600;
        short[] one = new short[2048];
        while (!p2.finished() && frames < cap) { p2.mix(one, one.length); frames += one.length; }
        System.out.printf("[info] durée d'une boucle ≈ %.1f s | songLen=%d numBlocks=%d speed=%d defTempo=%d "
                + "samplesPerTick=%d | blocs joués=%d lignes jouées=%d%n",
                frames / (double) rate, p2.dbgSongLen(), p2.dbgNumBlocks(), p2.dbgSpeed(), p2.dbgDefTempo(),
                p2.dbgSamplesPerTick(), p2.dbgBlocksPlayed, p2.dbgRowsPlayed);

        String wav = name.replaceAll(".*/", "") + ".wav";
        writeWav(wav, pcm, rate);
        System.out.println("[info] WAV écrit : " + wav);

        if (rms > 200 && peak > 2000) System.out.println("TOUT OK");
        else { System.out.println("ECHEC : audio quasi silencieux (parsing/mixage ?)"); System.exit(1); }
    }

    private static void writeWav(String path, short[] pcm, int rate) throws Exception {
        int dataLen = pcm.length * 2;
        try (DataOutputStream o = new DataOutputStream(new FileOutputStream(path))) {
            o.writeBytes("RIFF"); le32(o, 36 + dataLen); o.writeBytes("WAVE");
            o.writeBytes("fmt "); le32(o, 16); le16(o, 1); le16(o, 1);       // PCM mono
            le32(o, rate); le32(o, rate * 2); le16(o, 2); le16(o, 16);
            o.writeBytes("data"); le32(o, dataLen);
            for (short s : pcm) le16(o, s & 0xffff);
        }
    }
    private static void le16(DataOutputStream o, int v) throws Exception { o.write(v & 0xff); o.write((v >> 8) & 0xff); }
    private static void le32(DataOutputStream o, int v) throws Exception { le16(o, v & 0xffff); le16(o, (v >> 16) & 0xffff); }
}
