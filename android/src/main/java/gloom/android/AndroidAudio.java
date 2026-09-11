package gloom.android;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import gloom.Mem;
import gloom.MedPlayer;
import gloom.Sfx;

/**
 * Backend audio Android — remplace {@code host.Audio} (OpenAL) sans toucher au moteur.
 *
 * La version desktop délègue le mélange à OpenAL : 4 sources pour les effets (les 4 voies DMA de
 * Paula) plus une source streamée pour la musique. Android n'offre pas d'équivalent direct à un
 * coût raisonnable, alors on fait ce que faisait Paula : **on mixe soi-même**, dans un unique
 * {@link AudioTrack} mono 16 bits à {@link MedPlayer#RATE} Hz.
 *
 * <ul>
 *   <li>Effets : 4 voies, échantillons 8 bits signés lus depuis {@code Mem} au format Gloom
 *       {@code [période(2)][longueur-mots(2)][PCM]}. La période Paula donne la fréquence
 *       ({@code 3546895 / période}), rejouée par un pas en virgule fixe 16.16 — c'est le
 *       rééchantillonnage que faisait le DMA.</li>
 *   <li>Attribution des voies : identique à l'original — une voie libre, sinon celle de plus
 *       basse priorité, et rien si toutes sont plus prioritaires.</li>
 *   <li>Musique : {@link MedPlayer} mixe déjà son propre PCM, on l'additionne simplement.</li>
 * </ul>
 *
 * Le mélange tourne sur son propre thread (l'écriture dans l'AudioTrack est bloquante et cadence
 * naturellement la boucle) ; {@link #play} est appelé depuis le thread de jeu, d'où la
 * synchronisation sur les voies.
 */
public final class AndroidAudio implements gloom.AudioBackend {

    private static final int PAULA_CLOCK = 3546895;     // horloge Paula PAL (Hz), comme host.Audio
    private static final int CHANNELS = 4;              // les 4 voies DMA
    private static final int RATE = MedPlayer.RATE;     // 22050 Hz
    private static final int FRAMES = 1024;             // taille d'un bloc de mélange

    /** Une voie d'effet en cours de lecture. */
    private static final class Voice {
        int base, len;          // adresse Mem du PCM et longueur en octets
        long pos, step;         // position et pas de lecture, en 16.16
        float gain;
        int pri = Integer.MIN_VALUE;
        boolean on;
    }

    private final Voice[] voices = new Voice[CHANNELS];
    private final short[] musBuf = new short[FRAMES];
    private final short[] mix = new short[FRAMES];

    private AudioTrack track;
    private MedPlayer music;
    private volatile boolean running;
    private Thread thread;

    public AndroidAudio() {
        for (int i = 0; i < CHANNELS; i++) voices[i] = new Voice();
        int min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        int size = Math.max(min, FRAMES * 2 * 4);
        track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setBufferSizeInBytes(size)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
        track.play();
        running = true;
        thread = new Thread(this::pump, "gloom-audio");
        thread.setPriority(Thread.NORM_PRIORITY + 1);
        thread.start();
    }

    // ------------------------------------------------------------------ effets (Sfx.Sink)

    /** playsfx : joue l'échantillon sur une voie libre, sinon sur la moins prioritaire. */
    @Override
    public void play(int sample, int vol, int pri) {
        if (sample == 0) return;
        int period = Mem.uw(sample);
        int bytes = Mem.uw(sample + 2) * 2;              // longueur en MOTS dans l'en-tête
        if (period == 0 || bytes <= 0) return;
        long step = ((long) (PAULA_CLOCK / period) << 16) / RATE;
        synchronized (voices) {
            int chan = -1;
            for (int i = 0; i < CHANNELS; i++) {
                if (!voices[i].on) { chan = i; break; }   // voie libre
            }
            if (chan < 0) {
                int lo = 0;
                for (int i = 1; i < CHANNELS; i++) if (voices[i].pri < voices[lo].pri) lo = i;
                if (pri < voices[lo].pri) return;         // toutes plus prioritaires : on renonce
                chan = lo;
            }
            Voice v = voices[chan];
            v.base = sample + 4;
            v.len = bytes;
            v.pos = 0;
            v.step = step;
            v.gain = Math.min(1f, Math.max(0f, vol / 64f));
            v.pri = pri;
            v.on = true;
        }
    }

    // ------------------------------------------------------------------ musique

    /** Démarre (ou remplace) la musique MED depuis un module MMD0/MMD1 brut. */
    public void playMusic(byte[] module) {
        if (module == null) return;
        MedPlayer p = new MedPlayer(module);
        if (!p.valid()) return;
        synchronized (this) { music = p; }
    }

    public void stopMusic() {
        synchronized (this) { music = null; }
    }

    /** Présent pour coller à l'API desktop : ici le streaming a son propre thread. */
    public void updateMusic() {
    }

    // ------------------------------------------------------------------ mélange

    private void pump() {
        byte[] out = new byte[FRAMES * 2];
        while (running) {
            MedPlayer m;
            synchronized (this) { m = music; }
            if (m != null) {
                m.mix(musBuf, FRAMES);
                System.arraycopy(musBuf, 0, mix, 0, FRAMES);
            } else {
                java.util.Arrays.fill(mix, (short) 0);
            }
            mixVoices();
            for (int i = 0; i < FRAMES; i++) {
                short s = mix[i];
                out[i * 2] = (byte) (s & 0xff);
                out[i * 2 + 1] = (byte) ((s >> 8) & 0xff);
            }
            try {
                track.write(out, 0, out.length);          // bloquant : cadence la boucle
            } catch (Exception e) {
                running = false;
            }
        }
    }

    /** Additionne les 4 voies dans {@code mix}, avec écrêtage — l'équivalent du sommateur Paula. */
    private void mixVoices() {
        synchronized (voices) {
            for (Voice v : voices) {
                if (!v.on) continue;
                for (int i = 0; i < FRAMES; i++) {
                    int idx = (int) (v.pos >> 16);
                    if (idx >= v.len) { v.on = false; v.pri = Integer.MIN_VALUE; break; }
                    int s = Mem.b(v.base + idx);          // PCM 8 bits SIGNÉ
                    int acc = mix[i] + (int) (s * 256 * v.gain);
                    mix[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, acc));
                    v.pos += v.step;
                }
            }
        }
    }

    public void shutdown() {
        running = false;
        if (thread != null) {
            try { thread.join(500); } catch (InterruptedException ignored) { }
        }
        if (track != null) {
            try { track.stop(); } catch (Exception ignored) { }
            track.release();
            track = null;
        }
    }
}
