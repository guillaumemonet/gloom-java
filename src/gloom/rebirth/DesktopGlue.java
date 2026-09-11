package gloom.rebirth;

import com.jme3.util.Screenshots;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;

/**
 * Implémentation desktop de {@link HostGlue} : GLFW pour le curseur et le framebuffer, AWT/ImageIO
 * pour la capture. Ce fichier est EXCLU du build Android — c'est tout ce qui l'empêcherait de
 * compiler là-bas.
 */
public final class DesktopGlue implements HostGlue {

    private long window;

    @Override
    public gloom.AudioBackend createAudio() {
        return new gloom.host.Audio();
    }

    /**
     * Verrouille le curseur (GLFW_CURSOR_DISABLED = caché + mode relatif illimité).
     * {@code setCursorVisible(false)} ne fait que masquer, et JME ré-applique SON mode après
     * l'init → on force DISABLED à chaque frame (idempotent : seulement si l'état diffère).
     * Re-grab aussi après un alt-tab. {@code glfwGetCurrentContext()} = la fenêtre JME (son
     * contexte GL est courant sur ce thread de rendu).
     */
    @Override
    public void grabCursor() {
        if (window == 0L) window = org.lwjgl.glfw.GLFW.glfwGetCurrentContext();
        if (window != 0L
                && org.lwjgl.glfw.GLFW.glfwGetInputMode(window, org.lwjgl.glfw.GLFW.GLFW_CURSOR)
                   != org.lwjgl.glfw.GLFW.GLFW_CURSOR_DISABLED) {
            org.lwjgl.glfw.GLFW.glfwSetInputMode(window,
                    org.lwjgl.glfw.GLFW.GLFW_CURSOR, org.lwjgl.glfw.GLFW.GLFW_CURSOR_DISABLED);
        }
    }

    @Override
    public boolean framebufferSize(int[] w, int[] h) {
        if (window == 0L) window = org.lwjgl.glfw.GLFW.glfwGetCurrentContext();
        if (window == 0L) return false;
        org.lwjgl.glfw.GLFW.glfwGetFramebufferSize(window, w, h);
        return true;
    }

    @Override
    public void writePng(ByteBuffer rgba, int w, int h, String path) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_4BYTE_ABGR);
        Screenshots.convertScreenShot(rgba, img);
        for (int q = 0; q < w * h; q++) {                   // BGR → RGB
            int a = img.getRGB(q % w, q / w);
            img.setRGB(q % w, q / w, (a & 0xff00ff00) | ((a & 0xff) << 16) | ((a >> 16) & 0xff));
        }
        try {
            ImageIO.write(img, "png", new File(path));
        } catch (Exception e) {
            System.err.println("[Rebirth] screenshot: " + e);
        }
    }
}
