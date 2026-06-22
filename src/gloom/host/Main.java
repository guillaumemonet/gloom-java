package gloom.host;

import gloom.Mem;
import gloom.Vars;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Point d'entrée de la boucle hôte (équivalent de entrypoint/mainloop de gloom.s).
 *
 * Deux modes :
 *   gradle run                      → partie complète pilotée par le script (sous-système 10),
 *                                      qui enchaîne les niveaux (map1_1, map1_2, …).
 *   gradle run -Dmap=com2_1 -Dtile=1 → un seul niveau (test/visite libre).
 *
 * Contrôles : W/S ou ↑/↓ avancer/reculer, A/D pas de côté, ←/→ tourner, Ctrl/Espace tirer, Échap quitter.
 */
public final class Main {

    public static void main(String[] args) {
        // Résolution interne configurable. La largeur est plafonnée à 320 (limite de la table
        // castrots ; au-delà il faudrait régénérer les rayons). Défaut 320×240 (≈ pleine résolution
        // « 1x1 » Gloom Deluxe RTG) au lieu de l'ancien 224×160. `-Dw=` `-Dh=` `-Dscale=`.
        int W = Math.min(320, Integer.getInteger("w", 320));
        int H = Math.min(256, Integer.getInteger("h", 240));
        int scale = Integer.getInteger("scale", 3);     // fenêtre = W*scale × H*scale (960×720 par défaut)
        String map = System.getProperty("map");         // null → mode script (partie complète)
        Display disp = new Display(W, H, scale, "Gloom");

        gloom.Sfx.loadSamples();                        // charge les échantillons SFX (09)
        Audio audio = new Audio();                      // backend OpenAL
        gloom.Sfx.setSink(audio);
        AUDIO = audio;
        audio.playMusic(loadModule("sfxs/med1"));       // musique MED (jouée tout au long)

        try {
            if (map != null) {
                LevelScene scene = new LevelScene();    // mode « un seul niveau » (test/visite)
                scene.init(W, H, map, System.getProperty("tile", "2"));
                while (!disp.shouldClose() && !disp.key(GLFW_KEY_ESCAPE)) {
                    scene.setInput(readJoyx(disp), readJoyy(disp), readJoyb(disp), readJoys(disp));
                    scene.tick();
                    scene.renderFrame();
                    present(disp, Mem.l(Vars.cop));
                }
            } else {
                runFullGame(disp, W, H);                // menu → partie complète (script) → fin → menu
            }
        } finally {
            audio.shutdown();
            disp.destroy();
        }
    }

    /** Partie complète : écran-titre/menu → jeu (script, écrans d'histoire, HUD) → fin → retour menu. */
    private static void runFullGame(Display disp, int W, int H) {
        while (!disp.shouldClose() && !disp.key(GLFW_KEY_ESCAPE)) {
            // --- menu ---
            Menu menu = new Menu();
            menu.init(W, H);
            while (!disp.shouldClose() && !disp.key(GLFW_KEY_ESCAPE) && menu.selected < 0) {
                menu.update(keyUp(disp), keyDown(disp), readJoyb(disp) != 0);
                menu.render();
                present(disp, Mem.l(Vars.cop));
            }
            if (disp.shouldClose() || disp.key(GLFW_KEY_ESCAPE) || menu.selected == Menu.EXIT) return;
            if (menu.selected == Menu.ABOUT) { aboutScreen(disp, W, H); continue; }

            // --- partie (ONE_PLAYER) ---
            Game game = new Game();
            game.boot(W, H);
            while (!disp.shouldClose() && !disp.key(GLFW_KEY_ESCAPE)) {
                if (game.scene != null) {
                    game.scene.setInput(readJoyx(disp), readJoyy(disp), readJoyb(disp), readJoys(disp));
                }
                game.update(readJoyb(disp) != 0);
                game.render();
                present(disp, Mem.l(Vars.cop));
                if (game.over) {                          // écran de fin : attend le feu puis retour menu
                    if (waitFire(disp, game)) break;
                    return;
                }
            }
        }
    }

    /** Affiche l'écran de fin jusqu'à l'appui-feu. Renvoie true si on retourne au menu, false si quitter. */
    private static boolean waitFire(Display disp, Game game) {
        boolean prev = true;
        while (!disp.shouldClose() && !disp.key(GLFW_KEY_ESCAPE)) {
            game.render();
            disp.present(Mem.l(Vars.cop));
            boolean fire = readJoyb(disp) != 0;
            if (fire && !prev) return true;
            prev = fire;
        }
        return false;
    }

    private static void aboutScreen(Display disp, int W, int H) {
        boolean prev = true;
        while (!disp.shouldClose() && !disp.key(GLFW_KEY_ESCAPE)) {
            int fb = Mem.l(Vars.cop);
            for (int i = 0; i < W * H; i++) Mem.ww(fb + i * 2, 0);
            Font.drawCenteredBig(fb, W, H, H / 2 - 40, "GLOOM", 0xf00);
            Font.drawCentered(fb, W, H, H / 2 - 4, "BLACK MAGIC SOFTWARE 1995", 0x0f0);
            Font.drawCentered(fb, W, H, H / 2 + 8, "PORTAGE JAVA", 0x0ff);
            Font.drawCenteredBig(fb, W, H, H - 24, "PRESS FIRE", 0xff0);
            present(disp, fb);
            boolean fire = readJoyb(disp) != 0;
            if (fire && !prev) return;
            prev = fire;
        }
    }

    /** Backend audio courant (pour pomper le streaming musique à chaque frame). */
    private static Audio AUDIO;

    /** Présente une frame ET fait avancer le streaming de la musique MED (sans thread). */
    private static void present(Display disp, int fb) {
        if (AUDIO != null) AUDIO.updateMusic();
        disp.present(fb);
    }

    /** Charge un module musical (MMD0/MMD1) ; null si introuvable. */
    private static byte[] loadModule(String name) {
        try {
            return gloom.Assets.read(name);
        } catch (Exception e) {
            System.err.println("[Main] musique introuvable : " + name);
            return null;
        }
    }

    private static boolean keyUp(Display d) { return d.key(GLFW_KEY_W) || d.key(GLFW_KEY_UP); }
    private static boolean keyDown(Display d) { return d.key(GLFW_KEY_S) || d.key(GLFW_KEY_DOWN); }

    // clavier → contrôle façon Gloom (joyx tourne/strafe, joyy avance, joyb feu, joys mode strafe)
    private static int readJoyx(Display d) {
        if (d.key(GLFW_KEY_A)) return -1;
        if (d.key(GLFW_KEY_D)) return 1;
        if (d.key(GLFW_KEY_LEFT)) return -1;
        if (d.key(GLFW_KEY_RIGHT)) return 1;
        return 0;
    }
    private static int readJoyy(Display d) {
        if (d.key(GLFW_KEY_W) || d.key(GLFW_KEY_UP)) return -1;
        if (d.key(GLFW_KEY_S) || d.key(GLFW_KEY_DOWN)) return 1;
        return 0;
    }
    private static int readJoyb(Display d) {
        // Feu : clic gauche souris (recommandé sous Windows) OU Ctrl/Espace OU Entrée.
        // (Sous Windows, Espace/Ctrl peuvent interférer avec le focus ; la souris est le plus fiable.)
        return (d.mouseButton(GLFW_MOUSE_BUTTON_LEFT) || d.key(GLFW_KEY_LEFT_CONTROL)
                || d.key(GLFW_KEY_SPACE) || d.key(GLFW_KEY_ENTER) || d.key(GLFW_KEY_RIGHT_CONTROL)) ? -1 : 0;
    }
    private static int readJoys(Display d) {
        return (d.key(GLFW_KEY_A) || d.key(GLFW_KEY_D)) ? -1 : 0;
    }
}
