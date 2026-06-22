package gloom.host;

import gloom.Mem;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * Couche hôte d'affichage (LWJGL 3 : GLFW + OpenGL).
 *
 * Remplace la copperlist Amiga : prend le framebuffer chunky $0RGB (mots dans Mem,
 * base = Vars.cop), le convertit en texture RGB et l'affiche dans une fenêtre
 * agrandie. Fournit aussi la lecture clavier.
 */
public final class Display {

    private final long window;
    private final int tex;
    private final int w, h;
    private final ByteBuffer pixels;   // RGB888, w*h*3

    public Display(int w, int h, int scale, String title) {
        this.w = w;
        this.h = h;
        this.pixels = MemoryUtil.memAlloc(w * h * 3);

        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) {
            throw new IllegalStateException("Impossible d'initialiser GLFW");
        }
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);
        window = glfwCreateWindow(w * scale, h * scale, title, MemoryUtil.NULL, MemoryUtil.NULL);
        if (window == MemoryUtil.NULL) {
            throw new IllegalStateException("Impossible de créer la fenêtre GLFW");
        }
        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);                 // vsync
        GL.createCapabilities();

        tex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, tex);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glEnable(GL_TEXTURE_2D);
        glfwShowWindow(window);
    }

    public boolean shouldClose() {
        return glfwWindowShouldClose(window);
    }

    public boolean key(int glfwKey) {
        return glfwGetKey(window, glfwKey) == GLFW_PRESS;
    }

    /** Bouton souris enfoncé (GLFW_MOUSE_BUTTON_*). */
    public boolean mouseButton(int btn) {
        return glfwGetMouseButton(window, btn) == GLFW_PRESS;
    }

    /** Convertit le framebuffer $0RGB (à l'adresse Mem fbAddr) et l'affiche. */
    public void present(int fbAddr) {
        for (int i = 0; i < w * h; i++) {
            int c = Mem.uw(fbAddr + i * 2);
            pixels.put(i * 3, (byte) (((c >> 8) & 15) * 17));
            pixels.put(i * 3 + 1, (byte) (((c >> 4) & 15) * 17));
            pixels.put(i * 3 + 2, (byte) ((c & 15) * 17));
        }
        glBindTexture(GL_TEXTURE_2D, tex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, w, h, 0, GL_RGB, GL_UNSIGNED_BYTE, pixels);

        glClear(GL_COLOR_BUFFER_BIT);
        glBegin(GL_QUADS);                    // quad plein écran (haut→bas inversé en V)
        glTexCoord2f(0, 0); glVertex2f(-1, 1);
        glTexCoord2f(1, 0); glVertex2f(1, 1);
        glTexCoord2f(1, 1); glVertex2f(1, -1);
        glTexCoord2f(0, 1); glVertex2f(-1, -1);
        glEnd();

        glfwSwapBuffers(window);
        glfwPollEvents();
    }

    public void destroy() {
        MemoryUtil.memFree(pixels);
        glfwDestroyWindow(window);
        glfwTerminate();
    }
}
