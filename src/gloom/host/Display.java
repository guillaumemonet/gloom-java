package gloom.host;

import gloom.Mem;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

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
    private final IntBuffer pixels;    // RGBA8888, w*h ints
    /** Table $0RGB (12 bits) → RGBA empaqueté (octets R,G,B,A en mémoire LE). Précalculée une fois. */
    private static final int[] LUT = buildLut();

    private static int[] buildLut() {
        int[] t = new int[4096];
        for (int c = 0; c < 4096; c++) {
            int r = ((c >> 8) & 15) * 17, g = ((c >> 4) & 15) * 17, b = (c & 15) * 17;
            t[c] = r | (g << 8) | (b << 16) | (0xff << 24);   // ordre mémoire LE : R,G,B,A
        }
        return t;
    }

    public Display(int w, int h, int scale, String title) {
        this.w = w;
        this.h = h;
        this.pixels = MemoryUtil.memAllocInt(w * h);

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

    /** Convertit le framebuffer $0RGB (à l'adresse Mem fbAddr) et l'affiche (via LUT 4096 entrées). */
    public void present(int fbAddr) {
        for (int i = 0; i < w * h; i++) {
            pixels.put(i, LUT[Mem.uw(fbAddr + i * 2) & 0x0fff]);   // 1 lookup, 1 écriture / pixel
        }
        glBindTexture(GL_TEXTURE_2D, tex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);

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
