package gloom.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.File;

import gloom.Assets;
import gloom.Mem;
import gloom.Render;
import gloom.Sfx;
import gloom.Vars;
import gloom.host.Font;
import gloom.host.Game;
import gloom.host.LevelScene;
import gloom.host.Menu;

/**
 * Surface de jeu Android : remplace {@code host.Display} (GLFW/OpenGL) et la boucle de
 * {@code host.Main}, sans rien changer au moteur.
 *
 * Le rendu de Gloom est entièrement logiciel : la scène est écrite dans un framebuffer en
 * {@code Mem} à raison d'un mot {@code $0RGB} par pixel (l'héritage de la copperlist Amiga). Il
 * suffit donc de convertir ce framebuffer en {@link Bitmap} ARGB et de le tirer à l'échelle sur
 * la surface — en 320×240, c'est gratuit. L'habillage tactile est dessiné par-dessus, en pixels
 * écran, pour rester net quelle que soit la résolution interne.
 *
 * Le fil de jeu reprend la séquence exacte de {@code Main.runFullGame} : menu → partie →
 * écran de fin → menu, la logique tournant une frame sur deux comme dans l'original.
 */
public final class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    /** Résolution interne, celle du port desktop (plafonnée à 320 par la table castrots). */
    private static final int W = 320, H = 240;
    private static final long FRAME_NS = 1_000_000_000L / 60;

    private final TouchPad pad = new TouchPad();
    private final Paint blit = new Paint();
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap frame;
    private int[] argb;
    private final Rect dst = new Rect();

    private Thread thread;
    private volatile boolean running;
    private volatile boolean surfaceReady;
    private volatile String status;            // message plein écran (chargement / erreur)

    private final File assetRoot;
    private AndroidAudio audio;
    private Runnable onExit;                   // ferme l'activité quand la boucle se termine
    private Runnable onLaunch3d;               // démarre la vue 3D (autre processus)

    public GameView(Context ctx) {
        super(ctx);
        assetRoot = new File(ctx.getFilesDir(), "GloomAmiga");
        getHolder().addCallback(this);
        setFocusable(true);
        blit.setFilterBitmap(false);           // pixels francs, pas de lissage
        blit.setDither(false);
        text.setColor(Color.WHITE);
        text.setTextAlign(Paint.Align.CENTER);
    }

    // ------------------------------------------------------------------ cycle de vie

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceReady = true;
        if (thread == null) {
            running = true;
            thread = new Thread(this, "gloom-game");
            thread.start();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int fmt, int width, int height) {
        pad.resize(width, height);
        // letterbox : on garde le rapport 4:3 du framebuffer, centré
        float k = Math.min(width / (float) W, height / (float) H);
        int dw = Math.round(W * k), dh = Math.round(H * k);
        dst.set((width - dw) / 2, (height - dh) / 2, (width - dw) / 2 + dw, (height - dh) / 2 + dh);
        text.setTextSize(Math.max(14f, height * 0.035f));
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
    }

    /** Arrête le fil de jeu et l'audio (appelé par l'activité). */
    public void shutdown() {
        running = false;
        if (thread != null) {
            try { thread.join(1500); } catch (InterruptedException ignored) { }
            thread = null;
        }
        if (audio != null) { audio.shutdown(); audio = null; }
    }

    /** Action à exécuter quand le jeu se termine (l'activité s'y ferme). */
    public void setOnExit(Runnable r) {
        onExit = r;
    }

    /** Action qui démarre la vue 3D depuis le launcher. */
    public void setOnLaunch3d(Runnable r) {
        onLaunch3d = r;
    }

    /** Touche/geste RETOUR d'Android : même effet que le bouton MENU à l'écran. */
    public void onBack() {
        pad.tapMenu();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        pad.onTouch(e);
        return true;
    }

    // ------------------------------------------------------------------ fil de jeu

    @Override
    public void run() {
        try {
            if (!AssetSetup.ready(assetRoot)) {
                status = "Premier lancement\nRecuperation des assets\n" + AssetSetup.sourceLabel();
                paintStatus();
                // le callback repeint : le fil de jeu est bloque dans le telechargement
                AssetSetup.install(assetRoot, m -> { status = m; paintStatus(); });
            }
        } catch (Exception e) {
            status = "Assets indisponibles\n" + e.getMessage() + "\n\nVerifie ta connexion et relance.";
            while (running) { paintStatus(); sleep(100); }
            return;
        }

        // À partir d'ici seulement on touche au moteur : Font, Menu, Hud… chargent des assets
        // dans leurs initialiseurs statiques.
        Assets.root = assetRoot.toPath();
        status = null;

        frame = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        argb = new int[W * H];

        Sfx.loadSamples();
        startAudio();

        Render.setupFramebuffer(W, H);

        // launcher → menu titre → partie. Le bouton MENU (et la touche retour) recule d'un cran :
        // partie → menu, menu → launcher, launcher → on quitte.
        while (running) {
            if (!chooseEngine()) break;
            titleMenu();
        }
        if (onExit != null) onExit.run();
    }

    /**
     * Launcher, le même que sur desktop ({@code Main.chooseEngine}) : les deux moteurs du portage
     * sont dans l'APK et on choisit au démarrage.
     *
     * <ul>
     *   <li><b>GLOOM CLASSIC</b> — le rasteriseur 2D fidèle, qui tourne dans CE processus ;</li>
     *   <li><b>GLOOM REBIRTH</b> — la vue 3D jMonkeyEngine, lancée dans son propre processus
     *       ({@code RebirthActivity}), pour repartir d'un état mémoire vierge.</li>
     * </ul>
     *
     * @return false si on quitte l'application
     */
    private boolean chooseEngine() {
        final String[] name = { "GLOOM CLASSIC", "GLOOM REBIRTH" };
        final String[] sub = { "MOTEUR 2D D'ORIGINE", "VUE 3D" };
        int sel = 0;
        boolean prevUp = false, prevDown = false, prevFire = true;   // ignore un feu déjà tenu
        pad.consumeMenuTap();
        while (running) {
            if (pad.consumeMenuTap()) return false;                  // retour = quitter l'app
            boolean up = pad.up(), down = pad.down(), fire = pad.fire();
            if ((up && !prevUp) || (down && !prevDown)) sel ^= 1;
            prevUp = up; prevDown = down;

            int fb = Mem.l(Vars.cop);
            for (int i = 0; i < W * H; i++) Mem.ww(fb + i * 2, 0);
            Font.drawCenteredBig(fb, W, H, H / 2 - 66, "GLOOM", 0x96f);
            Font.drawCenteredBig(fb, W, H, H / 2 - 22, name[0], sel == 0 ? 0xfff : 0x70a);
            Font.drawCentered(fb, W, H, H / 2 - 8, sub[0], sel == 0 ? 0x0ff : 0x406);
            Font.drawCenteredBig(fb, W, H, H / 2 + 16, name[1], sel == 1 ? 0xfff : 0x70a);
            Font.drawCentered(fb, W, H, H / 2 + 30, sub[1], sel == 1 ? 0x0ff : 0x406);
            Font.drawCentered(fb, W, H, H - 16, "STICK + FIRE", 0x0ff);
            present();

            if (fire && !prevFire) {
                if (sel == 1) {
                    if (onLaunch3d != null) onLaunch3d.run();        // passe la main à jMonkeyEngine
                    prevFire = true;
                    continue;                                        // on reste au launcher au retour
                }
                return true;
            }
            prevFire = fire;
        }
        return false;
    }

    /** Menu titre et parties. Revient au launcher sur MENU ; {@code running=false} sur EXIT. */
    private void titleMenu() {
        while (running) {
            Menu menu = new Menu();
            menu.init(W, H);
            pad.consumeMenuTap();                        // ignore l'appui qui nous a amenés ici
            while (running && menu.selectedAction == Menu.Action.NONE) {
                if (pad.consumeMenuTap()) return;        // retour au launcher
                menu.update(pad.up(), pad.down(), pad.fire());
                menu.render();
                present();
            }
            if (!running) return;
            if (menu.selectedAction == Menu.Action.EXIT) { running = false; return; }
            if (menu.selectedAction == Menu.Action.ABOUT) { about(); continue; }

            playGame(menu.selectedAction == Menu.Action.CONTINUE ? menu.selectedCheckpoint : 0);
        }
    }

    /** Une partie. Revient au menu titre à la fin, à la mort, ou sur le bouton MENU. */
    private void playGame(int checkpoint) {
        Game game = new Game();
        game.boot(W, H, checkpoint);
        pad.consumeMenuTap();
        while (running) {
            if (pad.consumeMenuTap()) return;            // abandon → menu titre
            if (game.scene != null) {
                game.scene.setInput(pad.joyx(), pad.joyy(), pad.joyb(), pad.joys());
            }
            game.update(pad.fire());
            game.render();
            present();
            if (game.over) { waitFire(game); return; }   // écran de fin puis retour menu
        }
    }

    /** Écran de fin : redessine jusqu'au front montant du feu (ou au bouton MENU). */
    private void waitFire(Game game) {
        boolean prev = true;
        while (running) {
            if (pad.consumeMenuTap()) return;
            game.render();
            present();
            boolean f = pad.fire();
            if (f && !prev) return;
            prev = f;
        }
    }

    /** Écran « about », repris de Main.aboutScreen. */
    private void about() {
        boolean prev = true;
        pad.consumeMenuTap();
        while (running) {
            if (pad.consumeMenuTap()) return;
            int fb = Mem.l(Vars.cop);
            for (int i = 0; i < W * H; i++) Mem.ww(fb + i * 2, 0);
            Font.drawCenteredBig(fb, W, H, H / 2 - 40, "GLOOM", 0xf00);
            Font.drawCentered(fb, W, H, H / 2 - 4, "BLACK MAGIC SOFTWARE 1995", 0x0f0);
            Font.drawCentered(fb, W, H, H / 2 + 8, "PORTAGE JAVA", 0x0ff);
            Font.drawCenteredBig(fb, W, H, H - 24, "PRESS FIRE", 0xff0);
            present();
            boolean f = pad.fire();
            if (f && !prev) return;
            prev = f;
        }
    }

    private void startAudio() {
        if (audio != null) return;
        audio = new AndroidAudio();
        Sfx.setSink(audio);
        audio.playMusic(loadModule("sfxs/med1"));
    }

    private void stopAudio() {
        if (audio == null) return;
        audio.shutdown();
        audio = null;
        Sfx.setSink(null);
    }

    private static byte[] loadModule(String name) {
        try {
            return java.nio.file.Files.readAllBytes(Assets.resolve(name));
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ affichage

    private long nextFrame;

    /** Framebuffer Mem ($0RGB par pixel) → Bitmap → surface, puis l'habillage tactile. */
    private void present() {
        // app en arriere-plan (surface detruite) : on GELE le fil de jeu. Sans ca les monstres
        // continueraient a bouger pendant qu'on est ailleurs, et on reviendrait mort.
        if (!surfaceReady) {
            stopAudio();                         // ne pas jouer sous la 3D ni en arrière-plan
            while (!surfaceReady && running) sleep(50);
            nextFrame = 0;                       // on repart d'une cadence propre
            if (!running) return;
            startAudio();
        }
        int fb = Mem.l(Vars.cop);
        for (int i = 0; i < W * H; i++) {
            int c = Mem.uw(fb + i * 2);
            argb[i] = 0xff000000
                    | (((c >> 8) & 15) * 17) << 16
                    | (((c >> 4) & 15) * 17) << 8
                    | ((c & 15) * 17);
        }
        frame.setPixels(argb, 0, W, 0, 0, W, H);

        if (surfaceReady) {
            SurfaceHolder h = getHolder();
            Canvas c = h.lockCanvas();
            if (c != null) {
                try {
                    c.drawColor(Color.BLACK);
                    c.drawBitmap(frame, null, dst, blit);
                    pad.draw(c);
                } finally {
                    h.unlockCanvasAndPost(c);
                }
            }
        }
        pace();
    }

    /** Écran d'attente (téléchargement / erreur), avant que le moteur ne soit disponible. */
    private void paintStatus() {
        if (!surfaceReady) return;
        SurfaceHolder h = getHolder();
        Canvas c = h.lockCanvas();
        if (c == null) return;
        try {
            c.drawColor(Color.BLACK);
            String s = status == null ? "" : status;
            String[] lines = s.split("\n");
            float y = c.getHeight() / 2f - lines.length * text.getTextSize() / 2f;
            for (String line : lines) {
                c.drawText(line, c.getWidth() / 2f, y, text);
                y += text.getTextSize() * 1.4f;
            }
        } finally {
            h.unlockCanvasAndPost(c);
        }
    }

    /** Cadence à 60 images/s : la logique tourne une frame sur deux, comme sur Amiga. */
    private void pace() {
        long now = System.nanoTime();
        if (nextFrame == 0) nextFrame = now;
        nextFrame += FRAME_NS;
        long wait = nextFrame - now;
        if (wait > 0) {
            sleep(wait / 1_000_000L);
        } else if (wait < -FRAME_NS * 4) {
            nextFrame = now;                    // gros retard : on repart d'ici
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(Math.max(1, ms)); } catch (InterruptedException ignored) { }
    }
}
