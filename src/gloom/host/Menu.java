package gloom.host;

import gloom.Assets;
import gloom.Iff;
import gloom.Mem;
import gloom.Render;
import gloom.Vars;

import java.util.ArrayList;
import java.util.List;

/**
 * Écran-titre / menu (sous-système 06 : dointro/initmenu/selmenu de gloom.s).
 *
 * Affiche l'image titre (IFF) et une liste d'options navigable. Version mono-joueur : les options
 * liées au 2 joueurs / lien série / Defender sont omises (hors périmètre). « CONTINUE FROM <épisode> »
 * apparaît pour chaque point de contrôle déverrouillé (cf. {@link Progress}). Rendu côté hôte.
 */
public final class Menu {

    /** Action choisie par l'utilisateur. */
    public enum Action { NONE, NEW_GAME, CONTINUE, ABOUT, OPTIONS, EXIT }

    private static final class Entry {
        final String label; final Action action; final int checkpoint;
        Entry(String label, Action action, int checkpoint) {
            this.label = label; this.action = action; this.checkpoint = checkpoint;
        }
    }

    private int width, height;
    private Iff title;
    private int curr = 0;
    private boolean pUp, pDown, pFire;
    private final List<Entry> entries = new ArrayList<>();

    public Action selectedAction = Action.NONE;   // action validée
    public int selectedCheckpoint = 0;            // point de contrôle pour CONTINUE

    public void init(int w, int h) {
        init(w, h, false);
    }

    /** {@code withOptions} ajoute l'entrée OPTIONS (mode 3D ; le 2D Classic ne l'utilise pas). */
    public void init(int w, int h, boolean withOptions) {
        width = w; height = h;
        Render.setupFramebuffer(w, h);
        try {
            title = Iff.decode(Assets.incbin("title"), Assets.incbin("title.pal"));
        } catch (RuntimeException e) { title = null; }

        // construit le menu : nouvelle partie, reprises déverrouillées, à propos, quitter.
        entries.clear();
        entries.add(new Entry("ONE PLAYER GAME", Action.NEW_GAME, 0));
        List<String> labels = Progress.labels();
        int unlocked = Progress.loadUnlocked();
        for (int i = 1; i <= unlocked && i <= labels.size(); i++) {
            entries.add(new Entry("CONTINUE FROM " + labels.get(i - 1), Action.CONTINUE, i));
        }
        if (withOptions) entries.add(new Entry("OPTIONS", Action.OPTIONS, 0));
        entries.add(new Entry("ABOUT GLOOM", Action.ABOUT, 0));
        entries.add(new Entry("EXIT GLOOM", Action.EXIT, 0));

        selectedAction = Action.NONE; selectedCheckpoint = 0;
        curr = 0; pUp = pDown = pFire = true;   // ignore les touches déjà tenues
    }

    /** Navigation (fronts montants) ; valide l'option sur appui-feu. */
    public void update(boolean up, boolean down, boolean fire) {
        int n = entries.size();
        if (up && !pUp) curr = (curr + n - 1) % n;
        if (down && !pDown) curr = (curr + 1) % n;
        if (fire && !pFire) {
            selectedAction = entries.get(curr).action;
            selectedCheckpoint = entries.get(curr).checkpoint;
        }
        pUp = up; pDown = down; pFire = fire;
    }

    public void render() {
        int fb = Mem.l(Vars.cop);
        if (title != null) title.blitTo(fb, width, height);
        else for (int i = 0; i < width * height; i++) Mem.ww(fb + i * 2, 0);
        int lh = Font.BH + 2;                                // hauteur de ligne (grande fonte 8×10)
        int y = height - entries.size() * lh - 8;
        for (int i = 0; i < entries.size(); i++) {
            String s = (i == curr ? "> " : "  ") + entries.get(i).label;
            // couleurs d'origine : texte violet (sélection plus claire), comme l'écran-titre Gloom
            Font.drawCenteredBig(fb, width, height, y, s, i == curr ? 0xd9f : 0x96f);
            y += lh;
        }
    }
}
