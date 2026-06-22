package gloom.tools;

import gloom.Sfx;
import gloom.Vars;
import gloom.host.Audio;

/** Smoke test OpenAL : init backend + joue quelques sons (tolère l'absence de périphérique audio). */
public final class AudioSmoke {
    public static void main(String[] args) throws Exception {
        Sfx.loadSamples();
        Audio audio = new Audio();
        Sfx.setSink(audio);
        System.out.println("[info] lecture shoot/die/door…");
        Sfx.playsfx(Vars.shootsfx, 64, 0); Thread.sleep(400);
        Sfx.playsfx(Vars.diesfx, 64, 2);   Thread.sleep(600);
        Sfx.playsfx(Mem(Vars.doorsfx), 64, 2); Thread.sleep(600);
        audio.shutdown();
        System.out.println("TOUT OK");
    }
    private static int Mem(int ptr) { return gloom.Mem.l(ptr); }
}
