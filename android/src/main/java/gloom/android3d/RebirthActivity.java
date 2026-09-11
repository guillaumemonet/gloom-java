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

    /** Mettre à true pour tenter ombres + brouillard + bloom (voir la note ci-dessus). */
    private static final boolean POST_FX = false;

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
        Rebirth.postFx = POST_FX;

        super.onCreate(state);                // crée le contexte GL et instancie Rebirth

        // commandes tactiles PAR-DESSUS la surface GL
        Touch3d pad = new Touch3d(this, () -> (app instanceof Rebirth) ? (Rebirth) app : null);
        addContentView(pad, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }
}
