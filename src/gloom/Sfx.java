package gloom;

/**
 * Sous-système 09 — Effets sonores (Paula → backend hôte).
 *
 * L'original joue des échantillons 8 bits signés sur les 4 voies DMA de Paula (`playsfx`,
 * gloom.s:898 ; format `[période(2)][longueur-mots(2)][PCM]`). Côté hôte on délègue à un
 * {@link Sink} branchable ({@code gloom.host.Audio} via OpenAL) ; sans backend (tests headless)
 * c'est un no-op. Le chargement des échantillons (port du sous-ensemble SFX de `loadfiles`,
 * gloom.s:9281) remplit les pointeurs de {@link Vars}.
 *
 * La musique MED (replayer `medplay`, blob 68k) n'est pas portée.
 */
public final class Sfx {

    /** Backend de lecture (implémenté côté hôte par OpenAL). */
    public interface Sink {
        /** sample = adresse Mem [période][longueur-mots][PCM 8 bits signé] ; vol 0..64 ; priorité. */
        void play(int sample, int vol, int pri);
    }

    private static Sink sink;

    private Sfx() {
    }

    public static void setSink(Sink s) { sink = s; }

    /** playsfx (gloom.s:898) : a0=échantillon, d0=volume, d1=priorité. */
    public static void playsfx(int sample, int vol, int pri) {
        if (sink != null && sample != 0) sink.play(sample, vol, pri);
    }

    /** Charge les échantillons SFX du jeu et remplit les pointeurs de Vars (sous-ensemble de loadfiles). */
    public static void loadSamples() {
        Vars.shootsfx = load("sfxs/shoot.bin");
        Vars.shootsfx2 = load("sfxs/shoot2.bin");
        Vars.shootsfx3 = load("sfxs/shoot3.bin");
        Vars.shootsfx4 = load("sfxs/shoot4.bin");
        Vars.shootsfx5 = load("sfxs/shoot5.bin");
        Mem.wl(Vars.doorsfx, load("sfxs/door.bin"));
        Mem.wl(Vars.telesfx, load("sfxs/teleport.bin"));
        Vars.diesfx = load("sfxs/die.bin");
        Vars.splatsfx = load("sfxs/splat.bin");
        Vars.tokensfx = load("sfxs/token.bin");
        Vars.footstepsfx = load("sfxs/footstep.bin");
        Vars.robodiesfx = load("sfxs/robodie.bin");
        Vars.gruntsfx = load("sfxs/grunt.bin");
    }

    private static int load(String path) {
        int a = Mem.alloc(path.length() + 1);
        for (int i = 0; i < path.length(); i++) Mem.wb(a + i, path.charAt(i));
        Mem.wb(a + path.length(), 0);
        return Files.loadfile(a, 1);
    }
}
