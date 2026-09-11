package gloom.rebirth;

import gloom.AudioBackend;
import gloom.android.AndroidAudio;

/**
 * Implémentation Android de {@link HostGlue}, trouvée par réflexion au démarrage de
 * {@link Rebirth}. Il n'y a ici qu'une seule chose à fournir : le backend audio, puisque le
 * curseur ne se verrouille pas au toucher, que la taille du framebuffer vient de la caméra, et
 * que la capture de diagnostic est propre au desktop.
 */
public final class AndroidGlue implements HostGlue {

    @Override
    public AudioBackend createAudio() {
        return new AndroidAudio();
    }
}
