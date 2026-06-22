package gloom.host;

import gloom.Assets;
import gloom.Defs;
import gloom.Iff;
import gloom.Mem;
import gloom.Player;
import gloom.Render;
import gloom.Vars;

/**
 * Sous-système 10 + 06 — Flot de jeu : interprète le script (`misc/script`) qui enchaîne **écrans
 * d'histoire** et **niveaux**, lance chaque niveau via {@link LevelScene}, affiche le **HUD**, et
 * réagit au code `finished` (1=quitter, 2=mort, 3=niveau terminé, 4=combat).
 *
 * Machine à états : STORY (image+texte, attend feu) / PLAYING (un niveau) / OVER (fin).
 *
 * Traduit la logique de gloom.s (`execscript` 9945, `scriptplay`, `scriptpict`/`text`/`wait`,
 * `mainloop`/`levelover` 10590/10638). L'affichage IFF/fonte (origine : fenêtres/copper/blitter)
 * est rendu côté hôte (divergence sanctionnée). Reporté : musique MED, Defender (gametype 2).
 */
public final class Game {

    public enum Phase { STORY, PLAYING, OVER }

    private static final int CMD_PICT = cmd("pict"), CMD_TEXT = cmd("text"), CMD_WAIT = cmd("wait"),
            CMD_PLAY = cmd("play"), CMD_DONE = cmd("done"), CMD_DARK = cmd("dark"),
            CMD_LOOP = cmd("loop"), CMD_TILE = cmd("tile");

    private static final int IRELOAD = 5;

    private int scriptBase, scriptat;
    private int width, height;
    private String currentTile = "1";
    private int p1health, p1lives, p1weapon, p1reload;

    public Phase phase = Phase.STORY;
    public LevelScene scene;                 // niveau courant (null hors PLAYING)
    public boolean over;                     // = (phase == OVER)
    public String endReason = "";
    public String currentMap = "";

    private Iff bg;                          // image de fond d'histoire (null = noir)
    private boolean darkBg;                  // dark_ : fond assombri (texte sur noir)
    private String storyText = "";
    private boolean prevFire;                // détection de front montant du feu

    /** Démarre une nouvelle partie : framebuffer + script + 1er segment. */
    public void boot(int w, int h) {
        width = w; height = h;
        Render.setupFramebuffer(w, h);                  // framebuffer pour écrans d'histoire/menu
        scriptBase = Assets.incbin("misc/script");
        scriptat = scriptBase;
        p1health = 0; p1lives = 5; p1weapon = 0; p1reload = IRELOAD;
        advance();
    }

    /** Interprète le script jusqu'à un état bloquant (STORY/PLAYING) ou la fin (OVER). */
    private void advance() {
        while (true) {
            int c = parseCommand();
            if (c == -1 || c == CMD_DONE) { setOver("Fin de la partie"); return; }
            else if (c == CMD_TILE) currentTile = fetchrest().trim();
            else if (c == CMD_PICT) loadPic(fetchrest().trim());
            else if (c == CMD_TEXT) storyText = fetchrest().trim();
            else if (c == CMD_DARK) { darkBg = true; }
            else if (c == CMD_LOOP) scriptat = scriptBase;
            else if (c == CMD_WAIT) { phase = Phase.STORY; prevFire = true; return; } // attend le relâché+appui
            else if (c == CMD_PLAY) { loadLevel(fetchrest().trim()); phase = Phase.PLAYING; storyText = ""; return; }
            else fetchrest();                            // draw/show/hide/rest : cosmétique
        }
    }

    private void loadPic(String name) {
        try {
            int img = Assets.incbin("pics/" + name);
            int pal = Assets.incbin("pics/" + name + ".pal");
            bg = Iff.decode(img, pal);
            darkBg = false;
        } catch (RuntimeException e) {
            bg = null;                                   // image absente → fond noir
        }
    }

    /** scriptplay : charge un niveau et restaure les stats reportées du joueur. */
    private void loadLevel(String map) {
        currentMap = map;
        scene = new LevelScene();
        scene.init(width, height, map, currentTile);
        int p = scene.player;
        Mem.ww(p + Defs.ob_lives, p1lives);
        if (p1health == 0) { p1health = 25; p1reload = IRELOAD; }
        Mem.ww(p + Defs.ob_hitpoints, p1health);
        Mem.ww(p + Defs.ob_weapon, p1weapon);
        Mem.wb(p + Defs.ob_reload, p1reload);
        Player.resetplayer(p);
        Mem.ww(Vars.p1x, Mem.w(p + Defs.ob_x));
        Mem.ww(Vars.p1z, Mem.w(p + Defs.ob_z));
        Mem.ww(Vars.p1r, Mem.w(p + Defs.ob_rot));
        Mem.ww(Vars.finished, 0);
        Mem.ww(Vars.finished2, 0);
    }

    private void saveStats() {
        int p = scene.player;
        p1health = Mem.w(p + Defs.ob_hitpoints);
        p1lives = Mem.w(p + Defs.ob_lives);
        p1weapon = Mem.w(p + Defs.ob_weapon);
        p1reload = Mem.b(p + Defs.ob_reload);
    }

    /** mainloop : dispatch de `finished` à la fin d'un niveau. */
    private void onFinished(int code) {
        code &= 127;
        switch (code) {
            case 3 -> { saveStats(); scene = null; advance(); }    // stats AVANT de libérer la scène
            case 2 -> setOver("Game over");                        // mort
            case 1 -> setOver("Abandon");                          // quitter
            default -> setOver("Partie terminée");
        }
    }

    private void setOver(String why) { phase = Phase.OVER; over = true; scene = null; endReason = why; }

    /** Une frame : `fire` = bouton feu enfoncé (pour valider les écrans d'histoire). */
    public void update(boolean fire) {
        switch (phase) {
            case PLAYING -> {
                if (scene == null) return;
                scene.tick();
                int f = Mem.w(Vars.finished);
                if (f != 0) onFinished(f);
            }
            case STORY -> {
                if (fire && !prevFire) advance();                  // front montant → écran suivant
                prevFire = fire;
            }
            case OVER -> { }
        }
    }

    /** Rend la frame courante dans le framebuffer ($0RGB). */
    public void render() {
        switch (phase) {
            case PLAYING -> { if (scene != null) { scene.renderFrame(); Hud.draw(Mem.l(Vars.cop), width, height, scene.player); } }
            case STORY -> renderStory();
            case OVER -> renderOver();
        }
    }

    private void renderStory() {
        int fb = Mem.l(Vars.cop);
        if (bg != null && !darkBg) bg.blitTo(fb, width, height);
        else fillBlack(fb);
        if (!storyText.isEmpty()) drawWrapped(fb, storyText, 0x0ff);   // texte d'histoire en cyan (cf. original)
        Font.drawCenteredBig(fb, width, height, 4, "PRESS FIRE", 0xff0);
    }

    private void renderOver() {
        int fb = Mem.l(Vars.cop);
        fillBlack(fb);
        Font.drawCenteredBig(fb, width, height, height / 2 - Font.CH * 2, endReason.toUpperCase(), 0xf00);
        Font.drawCenteredBig(fb, width, height, height / 2 + 8, "PRESS FIRE", 0xff0);
    }

    private void fillBlack(int fb) {
        for (int i = 0; i < width * height; i++) Mem.ww(fb + i * 2, 0);
    }

    /** Affiche un texte multi-ligne centré (découpe sur les mots, '\' = saut de ligne explicite). */
    private void drawWrapped(int fb, String text, int color) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        int maxChars = width / Font.CW;
        for (String para : text.split("\\\\")) {
            StringBuilder line = new StringBuilder();
            for (String word : para.trim().split(" ")) {
                if (line.length() > 0 && line.length() + 1 + word.length() > maxChars) {
                    lines.add(line.toString()); line = new StringBuilder();
                }
                if (line.length() > 0) line.append(' ');
                line.append(word);
            }
            if (line.length() > 0) lines.add(line.toString());
        }
        int y = height - lines.size() * (Font.CH + 2) - 4;        // ancré en bas de l'écran (cf. original)
        for (String l : lines) { Font.drawCentered(fb, width, height, y, l, color); y += Font.CH + 2; }
    }

    /** Redémarre une partie (depuis l'écran de fin). */
    public void restart() {
        scriptat = scriptBase;
        currentTile = "1"; bg = null; darkBg = false; storyText = "";
        p1health = 0; p1lives = 5; p1weapon = 0; p1reload = IRELOAD;
        over = false; scene = null;
        advance();
    }

    // ------------------------------------------------------------------
    // Parseur de script (execscript, gloom.s:9945)
    // ------------------------------------------------------------------

    private int parseCommand() {
        int a0 = scriptat;
        while (true) {
            int c = Mem.ub(a0++);
            if (c == 0) { scriptat = a0 - 1; return -1; }
            if (c == 10) continue;
            if ((c & 31) == 0 || (c & 31) >= 27) { a0 = skipLine(a0); continue; }
            int d0 = (c & 31) + 96;
            for (int i = 0; i < 3; i++) d0 = (d0 << 8) | ((Mem.ub(a0++) & 31) + 96);
            a0++;
            scriptat = a0;
            return d0;
        }
    }

    private int skipLine(int a0) {
        int c;
        while ((c = Mem.ub(a0++)) != 10 && c != 0) { }
        return a0;
    }

    private String fetchrest() {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = Mem.ub(scriptat++)) != 10 && c != 0) sb.append((char) c);
        return sb.toString();
    }

    private static int cmd(String s) {
        return (s.charAt(0) << 24) | (s.charAt(1) << 16) | (s.charAt(2) << 8) | s.charAt(3);
    }
}
