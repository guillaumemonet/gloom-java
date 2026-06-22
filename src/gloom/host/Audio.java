package gloom.host;

import gloom.MedPlayer;
import gloom.Mem;
import gloom.Sfx;

import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALCCapabilities;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Backend audio hôte (OpenAL via LWJGL) — sous-système 09, voies Paula → sources OpenAL.
 *
 * Lit le format d'échantillon Gloom ([période(2)][longueur-mots(2)][PCM 8 bits signé]) : la période
 * Amiga donne la fréquence (horloge PAL 3546895/période), le PCM signé est converti en non-signé
 * (MONO8). Quatre « voies » (sources) avec priorité, comme `playsfx`. La musique MED n'est pas portée.
 */
public final class Audio implements Sfx.Sink {

    private static final int PAULA_CLOCK = 3546895;     // horloge Paula PAL (Hz)
    private static final int CHANNELS = 4;

    private long device, context;
    private boolean ok;
    private final int[] source = new int[CHANNELS];
    private final int[] chanPri = new int[CHANNELS];
    private final Map<Integer, Integer> bufferCache = new HashMap<>();

    // --- musique MED (streaming) ---
    private static final int MUS_BUFS = 4, MUS_FRAMES = 4096;
    private MedPlayer music;
    private int musicSource;
    private final int[] musicBufs = new int[MUS_BUFS];
    private final short[] musMix = new short[MUS_FRAMES];
    private boolean musicOn;

    public Audio() {
        try {
            device = ALC10.alcOpenDevice((ByteBuffer) null);
            if (device == 0L) return;
            ALCCapabilities caps = ALC.createCapabilities(device);
            context = ALC10.alcCreateContext(device, (int[]) null);
            ALC10.alcMakeContextCurrent(context);
            AL.createCapabilities(caps);
            for (int i = 0; i < CHANNELS; i++) { source[i] = AL10.alGenSources(); chanPri[i] = Integer.MIN_VALUE; }
            ok = true;
        } catch (Throwable t) {
            System.err.println("[Audio] OpenAL indisponible : " + t.getMessage());
            ok = false;
        }
    }

    /** playsfx : joue l'échantillon `sample` (vol 0..64) sur une voie libre / de plus basse priorité. */
    @Override
    public void play(int sample, int vol, int pri) {
        if (!ok || sample == 0) return;
        int buf = buffer(sample);
        if (buf == 0) return;

        // voie libre (arrêtée) ? sinon la voie de plus basse priorité si pri >= la sienne.
        int chan = -1;
        for (int i = 0; i < CHANNELS; i++) {
            int st = AL10.alGetSourcei(source[i], AL10.AL_SOURCE_STATE);
            if (st != AL10.AL_PLAYING) { chan = i; break; }
        }
        if (chan < 0) {
            int lo = 0;
            for (int i = 1; i < CHANNELS; i++) if (chanPri[i] < chanPri[lo]) lo = i;
            if (pri < chanPri[lo]) return;              // toutes les voies sont plus prioritaires
            chan = lo;
        }
        int src = source[chan];
        chanPri[chan] = pri;
        AL10.alSourceStop(src);
        AL10.alSourcei(src, AL10.AL_BUFFER, buf);
        AL10.alSourcef(src, AL10.AL_GAIN, Math.min(1f, Math.max(0f, vol / 64f)));
        AL10.alSourcePlay(src);
    }

    /** Construit (et met en cache) un buffer OpenAL depuis l'échantillon Gloom en Mem. */
    private int buffer(int sample) {
        Integer cached = bufferCache.get(sample);
        if (cached != null) return cached;
        int period = Mem.uw(sample);
        int lenWords = Mem.uw(sample + 2);
        int bytes = lenWords * 2;
        if (period == 0 || bytes <= 0) return 0;
        int freq = PAULA_CLOCK / period;

        ByteBuffer pcm = BufferUtils.createByteBuffer(bytes);
        for (int i = 0; i < bytes; i++) pcm.put((byte) (Mem.b(sample + 4 + i) + 128)); // signé → non-signé
        pcm.flip();

        int buf = AL10.alGenBuffers();
        AL10.alBufferData(buf, AL10.AL_FORMAT_MONO8, pcm, freq);
        bufferCache.put(sample, buf);
        return buf;
    }

    /** Démarre (ou remplace) la musique MED depuis un module MMD0/MMD1 brut. */
    public void playMusic(byte[] module) {
        if (!ok || module == null) return;
        stopMusic();
        MedPlayer p = new MedPlayer(module);
        if (!p.valid()) { System.err.println("[Audio] module MED invalide"); return; }
        music = p;
        musicSource = AL10.alGenSources();
        for (int i = 0; i < MUS_BUFS; i++) {
            musicBufs[i] = AL10.alGenBuffers();
            fillMusicBuffer(musicBufs[i]);
            AL10.alSourceQueueBuffers(musicSource, musicBufs[i]);
        }
        AL10.alSourcePlay(musicSource);
        musicOn = true;
    }

    /** À appeler chaque frame : recharge les buffers consommés (streaming sans thread). */
    public void updateMusic() {
        if (!musicOn) return;
        int processed = AL10.alGetSourcei(musicSource, AL10.AL_BUFFERS_PROCESSED);
        while (processed-- > 0) {
            int buf = AL10.alSourceUnqueueBuffers(musicSource);
            fillMusicBuffer(buf);
            AL10.alSourceQueueBuffers(musicSource, buf);
        }
        if (AL10.alGetSourcei(musicSource, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
            AL10.alSourcePlay(musicSource);                  // relance après un sous-débit
        }
    }

    private void fillMusicBuffer(int buf) {
        music.mix(musMix, MUS_FRAMES);
        ByteBuffer pcm = BufferUtils.createByteBuffer(MUS_FRAMES * 2);
        for (short s : musMix) { pcm.put((byte) (s & 0xff)); pcm.put((byte) ((s >> 8) & 0xff)); } // LE 16 bits
        pcm.flip();
        AL10.alBufferData(buf, AL10.AL_FORMAT_MONO16, pcm, MedPlayer.RATE);
    }

    public void stopMusic() {
        if (!musicOn) return;
        AL10.alSourceStop(musicSource);
        AL10.alSourcei(musicSource, AL10.AL_BUFFER, 0);
        for (int b : musicBufs) if (b != 0) AL10.alDeleteBuffers(b);
        AL10.alDeleteSources(musicSource);
        musicSource = 0; music = null; musicOn = false;
    }

    public void shutdown() {
        if (!ok) return;
        stopMusic();
        for (int s : source) AL10.alDeleteSources(s);
        for (int b : bufferCache.values()) AL10.alDeleteBuffers(b);
        ALC10.alcMakeContextCurrent(0L);
        if (context != 0L) ALC10.alcDestroyContext(context);
        if (device != 0L) ALC10.alcCloseDevice(device);
        ok = false;
    }
}
