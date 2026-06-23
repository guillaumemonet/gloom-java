package gloom.host;

import gloom.Assets;
import gloom.Iff;
import gloom.Mem;
import gloom.Render;
import gloom.Vars;

/**
 * Écran-titre / menu (sous-système 06 : dointro/initmenu/selmenu de gloom.s).
 *
 * Affiche l'image titre (IFF) et une liste d'options navigable. Version mono-joueur : les options
 * liées au 2 joueurs / lien série / Defender sont omises (hors périmètre). Rendu côté hôte.
 */
public final class Menu {

    public static final int ONE_PLAYER = 0, ABOUT = 1, EXIT = 2;
    private static final String[] OPTS = { "ONE PLAYER GAME", "ABOUT GLOOM", "EXIT GLOOM" };

    private int width, height;
    private Iff title;
    private int curr = 0;
    private boolean pUp, pDown, pFire;
    public int selected = -1;                // option choisie (>=0) une fois validée

    public void init(int w, int h) {
        width = w; height = h;
        Render.setupFramebuffer(w, h);
        try {
            title = Iff.decode(Assets.incbin("title"), Assets.incbin("title.pal"));
        } catch (RuntimeException e) { title = null; }
        selected = -1; curr = 0; pUp = pDown = pFire = true;   // ignore les touches déjà tenues
    }

    /** Navigation (fronts montants) ; valide l'option sur appui-feu. */
    public void update(boolean up, boolean down, boolean fire) {
        if (up && !pUp) curr = (curr + OPTS.length - 1) % OPTS.length;
        if (down && !pDown) curr = (curr + 1) % OPTS.length;
        if (fire && !pFire) selected = curr;
        pUp = up; pDown = down; pFire = fire;
    }

    public void render() {
        int fb = Mem.l(Vars.cop);
        if (title != null) title.blitTo(fb, width, height);
        else for (int i = 0; i < width * height; i++) Mem.ww(fb + i * 2, 0);
        int lh = Font.BH + 2;                                // hauteur de ligne (grande fonte 8×10)
        int y = height - OPTS.length * lh - 8;
        for (int i = 0; i < OPTS.length; i++) {
            String s = (i == curr ? "> " : "  ") + OPTS[i];
            // couleurs d'origine : texte violet (sélection plus claire), comme l'écran-titre Gloom
            Font.drawCenteredBig(fb, width, height, y, s, i == curr ? 0xd9f : 0x96f);
            y += lh;
        }
    }
}
