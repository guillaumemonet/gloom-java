package gloom.rebirth;

import java.nio.ByteBuffer;

/**
 * Le peu de {@link Rebirth} qui dépend réellement de la plateforme.
 *
 * La vue 3D est écrite contre {@code jme3-core}, commun au desktop et à Android ; seules TROIS
 * choses sortent de ce cadre et ne compileraient pas sur Android : le verrouillage du curseur
 * (GLFW), la taille réelle du framebuffer (GLFW, pour le plein écran et le DPI) et l'écriture de
 * la capture de diagnostic (AWT/ImageIO). Elles sont rassemblées ici derrière une interface dont
 * l'implémentation desktop ({@code DesktopGlue}) est simplement absente de l'APK.
 *
 * Toutes les méthodes ont un comportement neutre par défaut : sur Android il n'y a pas de curseur
 * à verrouiller, la taille du framebuffer vient de la caméra, et la capture n'a pas lieu d'être.
 */
public interface HostGlue {

    /** Verrouille le curseur dans la fenêtre (souris relative). Sans objet au toucher. */
    default void grabCursor() {
    }

    /**
     * Renseigne la taille RÉELLE du framebuffer dans {@code w[0]}/{@code h[0]}.
     * @return false si elle est inconnue — l'appelant se rabat alors sur la caméra
     */
    default boolean framebufferSize(int[] w, int[] h) {
        return false;
    }

    /** Backend audio de la plateforme (OpenAL sur desktop, AudioTrack sur Android). */
    default gloom.AudioBackend createAudio() {
        return null;
    }

    /** Écrit une capture PNG (diagnostic {@code -Dshot} du desktop) ; ailleurs, ne fait rien. */
    default void writePng(ByteBuffer rgba, int w, int h, String path) {
    }

    /** Implémentation desktop si elle est présente, sinon un jeu de no-ops (Android). */
    static HostGlue detect() {
        try {
            return load("gloom.rebirth.DesktopGlue");
        } catch (Throwable desktopAbsent) {
            try {
                return load("gloom.rebirth.AndroidGlue");
            } catch (Throwable androidAbsent) {
                return new HostGlue() { };
            }
        }
    }

    private static HostGlue load(String cls) throws Exception {
        return (HostGlue) Class.forName(cls).getDeclaredConstructor().newInstance();
    }
}
