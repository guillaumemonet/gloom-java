package gloom.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import gloom.boot.DiskFetch;

/**
 * Comment Android pose la question des disquettes : une boîte de dialogue, affichée sur le fil
 * principal pendant que le fil appelant (jeu ou chargement) attend la réponse.
 *
 * À n'appeler JAMAIS depuis le fil principal (il attendrait sa propre boîte de dialogue).
 */
public final class AndroidAsk implements DiskFetch.Ask {

    private static final String TAG = "Gloom";
    private final Activity act;

    public AndroidAsk(Activity act) {
        this.act = act;
    }

    @Override
    public boolean confirm(String message) {
        return dialog(message, true);
    }

    @Override
    public void info(String message) {
        Log.i(TAG, message);
        dialog(message, false);
    }

    @Override
    public void error(String message) {
        Log.e(TAG, message);
        dialog(message, false);
    }

    private boolean dialog(String message, boolean yesNo) {
        CountDownLatch done = new CountDownLatch(1);
        AtomicBoolean yes = new AtomicBoolean(false);
        act.runOnUiThread(() -> {
            AlertDialog.Builder b = new AlertDialog.Builder(act)
                    .setTitle("Gloom")
                    .setMessage(message)
                    .setCancelable(false)
                    .setPositiveButton(yesNo ? "Oui" : "OK", (d, w) -> { yes.set(true); done.countDown(); });
            if (yesNo) b.setNegativeButton("Non", (d, w) -> done.countDown());
            b.show();
        });
        try {
            done.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return yes.get();
    }
}
