package gloom.tools;

import gloom.Mem;
import gloom.Vars;
import gloom.host.Game;
import gloom.host.Menu;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Diagnostic : dump menu.png / story.png / hud.png pour vérifier l'UI (menu, écran d'histoire, HUD). */
public final class UiShotTest {
    private static int W = 224, H = 160;

    public static void main(String[] args) throws Exception {
        W = Integer.getInteger("w", 320);
        H = Integer.getInteger("h", 240);
        Menu menu = new Menu();
        menu.init(W, H);
        menu.render();
        dump("menu.png");

        Game game = new Game();
        game.boot(W, H);
        game.render();                       // écran d'histoire d'intro
        dump("story.png");

        int guard = 0;
        while (game.phase == Game.Phase.STORY && guard++ < 30) { game.update(false); game.update(true); }
        // quelques frames de jeu pour stabiliser le rendu
        for (int i = 0; i < 4; i++) { game.update(false); }
        game.render();                       // niveau + HUD
        dump("hud.png");

        System.out.println("[info] menu.png / story.png / hud.png");
        System.out.println("TOUT OK");
    }

    private static void dump(String name) throws Exception {
        int fb = Mem.l(Vars.cop);
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                int c = Mem.uw(fb + (y * W + x) * 2);
                int r = ((c >> 8) & 15) * 17, g = ((c >> 4) & 15) * 17, b = (c & 15) * 17;
                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        ImageIO.write(img, "png", new File(name));
    }
}
