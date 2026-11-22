package gloom.tools;

import gloom.Defs;
import gloom.Mem;
import gloom.Mess;
import gloom.Objects;
import gloom.Vars;
import gloom.host.Gun;
import gloom.host.LevelScene;
import gloom.host.Panel;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Panneau HUD + arme à l'écran de Gloom Deluxe (étapes C/D) : rend une vraie scène 320×240 dans
 * plusieurs états et les assemble dans deluxe_hud.png (×2) :
 * repos, repos en marchant (balancement), tir de chaque arme (5), invisible, message défilant.
 *
 * Vérifie : panneau et arme disponibles (disquettes Deluxe), vue réduite à 224 lignes, panneau
 * dessiné sur les 16 dernières lignes, arme visible dans le bas de la vue, flamme plus large que
 * l'arme seule, rien de dessiné quand le joueur est mort.
 */
public final class DeluxeHudTest {

    private static final int W = 320, H = 240;
    private static int failures;

    public static void main(String[] args) throws Exception {
        LevelScene scene = new LevelScene();
        scene.init(W, H, System.getProperty("map", "map1_1"), System.getProperty("tile", "1"));
        int p = scene.player;
        checkTrue("panneau Deluxe disponible", Panel.available());
        checkTrue("arme Deluxe disponible", Gun.available());
        check("hauteur de vue", scene.viewHeight, H - Panel.HEIGHT);
        check("Vars.hite", Mem.uw(Vars.hite), H - Panel.HEIGHT);

        // stats contrôlées
        Mem.ww(p + Defs.ob_hitpoints, 18);
        Mem.ww(p + Defs.ob_lives, 3);
        Mem.ww(p + Defs.ob_weapon, 2);
        Mem.wb(p + Defs.ob_reload, 2);            // 6-2 = 4 icônes de puissance
        Mem.ww(p + Defs.ob_mega, 900);            // jauge mega

        int[][] shots = new int[10][];
        String[] labels = new String[10];
        int k = 0;

        Objects.gunFlash = 0;
        Mem.ww(p + Defs.ob_hitpoints, 0);         // référence : même vue, sans arme (joueur à 0 PV)
        render(scene);                            // le 1er rendu après init place la caméra : échauffement
        baseline = render(scene);
        Mem.ww(p + Defs.ob_hitpoints, 18);
        shots[k] = render(scene); labels[k++] = "repos";
        int idle = gunPixels(shots[0]);

        Mem.ww(p + Defs.ob_bounce, 64);           // phase de marche → balancement
        shots[k] = render(scene); labels[k++] = "marche";
        Mem.ww(p + Defs.ob_bounce, 0);

        int flashMax = 0;
        for (int w = 0; w < 5; w++) {
            Mem.ww(p + Defs.ob_weapon, w);
            Objects.gunFlash = 3;
            shots[k] = render(scene); labels[k++] = "tir arme " + (w + 1);
            flashMax = Math.max(flashMax, gunPixels(shots[k - 1]));
        }
        Objects.gunFlash = 0;
        Mem.ww(p + Defs.ob_weapon, 2);

        Mem.ww(p + Defs.ob_invisible, 1);
        shots[k] = render(scene); labels[k++] = "invisible";
        Mem.ww(p + Defs.ob_invisible, 0);

        Mess.message(p, "health bonus!");
        for (int i = 0; i < 12; i++) Panel.tick();
        shots[k] = render(scene); labels[k++] = "message";

        Mem.ww(p + Defs.ob_hitpoints, 0);
        int[] dead = render(scene);
        shots[k] = dead; labels[k++] = "mort";

        // --- vérifications ---
        int panelPx = 0;
        for (int y = H - Panel.HEIGHT; y < H; y++)
            for (int x = 0; x < W; x++) if (shots[0][y * W + x] != 0) panelPx++;
        checkTrue("panneau peint (" + panelPx + " px)", panelPx > W * Panel.HEIGHT / 2);
        checkTrue("arme visible au repos (" + idle + " px)", idle > 1500);
        checkTrue("flamme plus large que l'arme (" + flashMax + " > " + idle + ")", flashMax > idle);
        int deadPx = gunPixels(dead);
        checkTrue("pas d'arme une fois mort", deadPx < idle / 4);
        checkTrue("le balancement déplace l'arme", !sameRegion(shots[0], shots[1]));
        checkTrue("le message s'affiche dans le panneau", !samePanel(shots[0], shots[k - 2]));

        writeMontage(shots, k, "deluxe_hud.png");
        System.out.println("[info] PNG : deluxe_hud.png (" + String.join(" | ", java.util.Arrays.copyOf(labels, k)) + ")");
        if (failures == 0) System.out.println("TOUT OK");
        else { System.out.println(failures + " ECHEC(S)"); System.exit(1); }
    }

    /** Rend une frame et rend le framebuffer en $0RGB. */
    private static int[] render(LevelScene scene) {
        scene.renderFrame();
        int fb = Mem.l(Vars.cop);
        int[] out = new int[W * H];
        for (int i = 0; i < W * H; i++) out[i] = Mem.uw(fb + i * 2);
        return out;
    }

    /** Image de référence sans arme (même vue). */
    private static int[] baseline;

    /** Pixels de la vue qui diffèrent de la référence sans arme : la surface occupée par l'arme. */
    private static int gunPixels(int[] img) {
        int n = 0, vh = H - Panel.HEIGHT;
        for (int i = 0; i < W * vh; i++) if (img[i] != baseline[i]) n++;
        return n;
    }

    private static boolean sameRegion(int[] a, int[] b) {
        int vh = H - Panel.HEIGHT;
        for (int i = W * (vh / 2); i < W * vh; i++) if (a[i] != b[i]) return false;
        return true;
    }

    private static boolean samePanel(int[] a, int[] b) {
        for (int i = W * (H - Panel.HEIGHT); i < W * H; i++) if (a[i] != b[i]) return false;
        return true;
    }

    private static void writeMontage(int[][] shots, int n, String name) throws Exception {
        int cols = 3, rows = (n + cols - 1) / cols, gap = 4;
        BufferedImage img = new BufferedImage(cols * (W * 2 + gap), rows * (H * 2 + gap), BufferedImage.TYPE_INT_RGB);
        for (int s = 0; s < n; s++) {
            int ox = (s % cols) * (W * 2 + gap), oy = (s / cols) * (H * 2 + gap);
            for (int y = 0; y < H * 2; y++)
                for (int x = 0; x < W * 2; x++) {
                    int c = shots[s][(y / 2) * W + x / 2];
                    int r = ((c >> 8) & 15) * 17, g = ((c >> 4) & 15) * 17, b = (c & 15) * 17;
                    img.setRGB(ox + x, oy + y, (r << 16) | (g << 8) | b);
                }
        }
        ImageIO.write(img, "png", new File(name));
    }

    private static void check(String what, int got, int exp) {
        if (got != exp) { System.out.println("ECHEC " + what + " : attendu " + exp + ", obtenu " + got); failures++; }
    }

    private static void checkTrue(String what, boolean ok) {
        if (!ok) { System.out.println("ECHEC " + what); failures++; }
    }
}
