package gloom.android;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

/**
 * Point d'entrée Android — l'équivalent de {@code host.Main} côté desktop.
 *
 * Tout le jeu vit dans {@link GameView} (fil dédié) : l'activité ne fait que créer la surface,
 * garder l'écran allumé, passer en plein écran immersif et arrêter proprement le fil quand on
 * quitte. Aucune ligne de moteur ici.
 */
public final class GloomActivity extends Activity {

    private GameView view;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        view = new GameView(this);
        // finish() depuis le fil de jeu : on repasse par le fil principal, c'est son contrat
        view.setOnExit(() -> runOnUiThread(this::finish));
        view.setOnLaunch3d(() -> runOnUiThread(() ->
                startActivity(new android.content.Intent(this, gloom.android3d.RebirthActivity.class))));
        setContentView(view);
        immersive();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) immersive();
    }

    /** Plein écran sans barres système (elles mangeraient la zone des commandes tactiles). */
    private void immersive() {
        View d = getWindow().getDecorView();
        d.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    /** RETOUR : recule d'un cran dans le jeu (partie → menu → launcher → quitter). */
    @Override
    public void onBackPressed() {
        if (view != null) view.onBack();
    }

    @Override
    protected void onDestroy() {
        if (view != null) view.shutdown();
        super.onDestroy();
    }
}
