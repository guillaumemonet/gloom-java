package gloom.tools;

import gloom.Mem;
import gloom.Sfx;
import gloom.Vars;

import java.util.ArrayList;
import java.util.List;

/**
 * Harnais 09 : effets sonores (couche logique, sans OpenAL). Vérifie le chargement des échantillons
 * SFX, le format ([période][longueur][PCM]), et la distribution via le Sink branchable. `gradle audioTest`.
 */
public final class AudioTest {
    private static int failures = 0;
    private static final int PAULA = 3546895;

    public static void main(String[] args) {
        Sfx.loadSamples();
        checkTrue("shoot chargé", Vars.shootsfx != 0);
        checkTrue("die chargé", Vars.diesfx != 0);
        checkTrue("splat chargé", Vars.splatsfx != 0);
        checkTrue("footstep chargé", Vars.footstepsfx != 0);
        checkTrue("door chargé", Mem.l(Vars.doorsfx) != 0);
        checkTrue("teleport chargé", Mem.l(Vars.telesfx) != 0);

        // format : [période(2)][longueur-mots(2)][PCM 8 bits]
        int s = Vars.shootsfx;
        int period = Mem.uw(s), lenWords = Mem.uw(s + 2);
        int freq = period > 0 ? PAULA / period : 0;
        System.out.println("[info] shoot.bin : période=" + period + " (" + freq + " Hz) longueur="
                + lenWords + " mots (" + (lenWords * 2) + " octets)");
        checkTrue("période plausible", period > 0 && period < 4096);
        checkTrue("fréquence plausible (4..28 kHz)", freq > 4000 && freq < 28000);
        checkTrue("longueur > 0", lenWords > 0);

        // distribution via Sink branchable
        List<int[]> calls = new ArrayList<>();
        Sfx.setSink((sample, vol, pri) -> calls.add(new int[]{sample, vol, pri}));
        Sfx.playsfx(Vars.diesfx, 64, 2);
        Sfx.playsfx(0, 64, 2);                          // sample 0 → ignoré (silencieux)
        System.out.println("[info] appels reçus par le Sink : " + calls.size());
        checkTrue("le Sink reçoit l'échantillon valide", calls.size() == 1 && calls.get(0)[0] == Vars.diesfx);
        checkTrue("vol/priorité transmis", calls.get(0)[1] == 64 && calls.get(0)[2] == 2);
        Sfx.setSink(null);                              // pas de backend → no-op
        Sfx.playsfx(Vars.diesfx, 64, 2);
        checkTrue("sans backend = no-op", calls.size() == 1);

        if (failures == 0) System.out.println("TOUT OK");
        else { System.out.println(failures + " ECHEC(S)"); System.exit(1); }
    }

    private static void checkTrue(String name, boolean cond) {
        if (!cond) { System.out.println("ECHEC " + name); failures++; }
    }
}
