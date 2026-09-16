package gloom.android3d;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.jme3.app.AndroidHarness;

import java.io.File;

import gloom.Assets;
import gloom.rebirth.Rebirth;

/**
 * Hôte Android de la vue 3D. {@link AndroidHarness} est le pont fourni par jMonkeyEngine : il crée
 * le contexte OpenGL ES, instancie l'application nommée par {@code appClass} et la pilote au fil du
 * cycle de vie de l'Activity. Rien du moteur ni de {@link Rebirth} n'est réécrit ici.
 *
 * Trois réglages avant le démarrage :
 * <ul>
 *   <li>{@code Assets.root} → le dossier privé peuplé par {@link SetupActivity} ;</li>
 *   <li>{@code user.home} → le même dossier, pour qu'{@code Options} y écrive ses préférences au
 *       lieu de tenter la racine du système (où l'écriture échoue) ;</li>
 *   <li>{@code Rebirth.postFx} → coupé par défaut sur mobile : ombres, brouillard et bloom sont les
 *       passes les plus risquées en OpenGL ES. Le rendu du niveau n'en dépend pas, donc si l'écran
 *       reste noir on saura que le problème est ailleurs.</li>
 * </ul>
 */
public final class RebirthActivity extends AndroidHarness {

    /**
     * Effets actifs par défaut sur mobile : TOUS — mesure à l'appui.
     *
     * Le banc d'essai (scène figée, appareil de test) a montré que la géométrie ne compte pour rien
     * (204 triangles, 27 draw calls : c'est un jeu de 1995) et que TOUT le coût est par pixel :
     * 2,6 Mpx, un éclairage à 14 lumières, puis les passes plein écran. Le seul effet qui faisait
     * déborder le budget de 16,6 ms était le BLOOM en pleine résolution — 45,6 fps contre 55,4 sans
     * lui. En le calculant au quart de résolution on retombe à 55,9, soit le prix de ne pas l'avoir :
     * le flou est par nature basse fréquence, le sous-échantillonner ne se voit quasiment pas.
     */
    private static final int DEFAULT_FX = Rebirth.FX_SHADOWS | Rebirth.FX_FOG | Rebirth.FX_BLOOM;

    /** Bloom calculé au quart de résolution : même rendu, coût quasi nul (cf. ci-dessus). */
    private static final float DEFAULT_BLOOM_DOWNSAMPLE = 4f;

    public RebirthActivity() {
        appClass = "gloom.rebirth.Rebirth";
        eglBitsPerPixel = 24;
        eglAlphaBits = 0;
        eglDepthBits = 16;
        eglSamples = 0;
        eglStencilBits = 0;
        frameRate = -1;                       // pas de bridage : on veut mesurer ce que la machine donne
        mouseEventsEnabled = false;           // les commandes passent par l'overlay tactile
        joystickEventsEnabled = false;
        keyEventsEnabled = false;
        screenShowTitle = false;
        screenFullScreen = true;
        exitDialogTitle = "Quitter Gloom Rebirth ?";
        exitDialogMessage = "";
    }

    @Override
    public void onCreate(Bundle state) {
        File root = new File(getFilesDir(), "GloomAmiga");
        Assets.root = root.toPath();
        System.setProperty("user.home", root.getParentFile().getAbsolutePath());
        Rebirth.fxMask = getIntent().getIntExtra("fx", DEFAULT_FX);
        Rebirth.logFps = getIntent().getBooleanExtra("fps", false);
        Rebirth.bench = getIntent().getBooleanExtra("bench", false);
        Rebirth.bloomDownsample = getIntent().getFloatExtra("bd", DEFAULT_BLOOM_DOWNSAMPLE);
        Rebirth.maxLevelLights = getIntent().getIntExtra("lights", -1);
        Rebirth.bloomIntensity = getIntent().getFloatExtra("bi", Rebirth.bloomIntensity);
        Rebirth.bloomExposure = getIntent().getFloatExtra("be", Rebirth.bloomExposure);

        super.onCreate(state);                // crée le contexte GL et instancie Rebirth

        // Banc d'essai : rendre a une resolution PLUS BASSE, le compositeur reetire a l'ecran.
        // C'est le levier le plus direct quand le cout est par pixel (2,6 Mpx sur cet ecran).
        float rs = getIntent().getFloatExtra("rs", 1f);
        if (rs > 0f && rs < 0.999f && view != null) {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            view.getHolder().setFixedSize(Math.max(320, (int) (dm.widthPixels * rs)),
                                          Math.max(240, (int) (dm.heightPixels * rs)));
        }

        // commandes tactiles PAR-DESSUS la surface GL
        Touch3d pad = new Touch3d(this, () -> (app instanceof Rebirth) ? (Rebirth) app : null);
        addContentView(pad, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }
}
