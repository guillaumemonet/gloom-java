package gloom.rebirth;

import com.jme3.input.KeyInput;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Options du mode 3D (Rebirth) : affichage, contrôles, réglages de confort. Persistées dans
 * {@code ~/.gloom-java/rebirth.properties}. Les valeurs par défaut reprennent les anciennes
 * propriétés système (-Dfov / -Dmousesens / -Dplayerspeed) pour la compatibilité.
 */
public final class Options {

    // --- affichage ---
    public int width = 960, height = 720;
    public boolean fullscreen = false;
    public boolean vsync = true;
    // --- confort ---
    public float fov = 100f;
    public float mouseSens = 0.18f;
    public float playerSpeed = 1.5f;
    // --- contrôles (codes JME KeyInput) ---
    public int kForward = KeyInput.KEY_W;
    public int kBack = KeyInput.KEY_S;
    public int kLeft = KeyInput.KEY_LEFT;
    public int kRight = KeyInput.KEY_RIGHT;
    public int kStrafeL = KeyInput.KEY_A;
    public int kStrafeR = KeyInput.KEY_D;
    public int kFire = KeyInput.KEY_LCONTROL;

    /** Résolutions proposées dans le menu (largeur, hauteur). */
    public static final int[][] RESOLUTIONS = {
            {640, 480}, {800, 600}, {960, 720}, {1280, 960}, {1366, 768}, {1600, 900}, {1920, 1080}
    };

    private static Path file() {
        return Path.of(System.getProperty("user.home"), ".gloom-java", "rebirth.properties");
    }

    public static Options load() {
        Options o = new Options();
        o.fov = sysFloat("fov", o.fov);                  // défauts depuis -D (compat)
        o.mouseSens = sysFloat("mousesens", o.mouseSens);
        o.playerSpeed = sysFloat("playerspeed", o.playerSpeed);
        try {
            Path f = file();
            if (Files.exists(f)) {
                Properties p = new Properties();
                try (var in = Files.newInputStream(f)) { p.load(in); }
                o.width = pInt(p, "width", o.width);
                o.height = pInt(p, "height", o.height);
                o.fullscreen = pBool(p, "fullscreen", o.fullscreen);
                o.vsync = pBool(p, "vsync", o.vsync);
                o.fov = pFloat(p, "fov", o.fov);
                o.mouseSens = pFloat(p, "mousesens", o.mouseSens);
                o.playerSpeed = pFloat(p, "playerspeed", o.playerSpeed);
                o.kForward = pInt(p, "kForward", o.kForward);
                o.kBack = pInt(p, "kBack", o.kBack);
                o.kLeft = pInt(p, "kLeft", o.kLeft);
                o.kRight = pInt(p, "kRight", o.kRight);
                o.kStrafeL = pInt(p, "kStrafeL", o.kStrafeL);
                o.kStrafeR = pInt(p, "kStrafeR", o.kStrafeR);
                o.kFire = pInt(p, "kFire", o.kFire);
            }
        } catch (IOException e) {
            System.err.println("[Options] lecture impossible : " + e);
        }
        return o;
    }

    public void save() {
        Properties p = new Properties();
        p.setProperty("width", "" + width);
        p.setProperty("height", "" + height);
        p.setProperty("fullscreen", "" + fullscreen);
        p.setProperty("vsync", "" + vsync);
        p.setProperty("fov", "" + fov);
        p.setProperty("mousesens", "" + mouseSens);
        p.setProperty("playerspeed", "" + playerSpeed);
        p.setProperty("kForward", "" + kForward);
        p.setProperty("kBack", "" + kBack);
        p.setProperty("kLeft", "" + kLeft);
        p.setProperty("kRight", "" + kRight);
        p.setProperty("kStrafeL", "" + kStrafeL);
        p.setProperty("kStrafeR", "" + kStrafeR);
        p.setProperty("kFire", "" + kFire);
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            try (var out = Files.newOutputStream(f)) { p.store(out, "Gloom Rebirth - options"); }
        } catch (IOException e) {
            System.err.println("[Options] écriture impossible : " + e);
        }
    }

    /** Index de la résolution courante dans RESOLUTIONS (-1 si non listée). */
    public int resolutionIndex() {
        for (int i = 0; i < RESOLUTIONS.length; i++)
            if (RESOLUTIONS[i][0] == width && RESOLUTIONS[i][1] == height) return i;
        return -1;
    }

    /** Nom lisible d'une touche (codes JME KeyInput). Couvre l'usuel ; sinon « KEY <code> ». */
    public static String keyName(int code) {
        switch (code) {
            case KeyInput.KEY_UP: return "UP";
            case KeyInput.KEY_DOWN: return "DOWN";
            case KeyInput.KEY_LEFT: return "LEFT";
            case KeyInput.KEY_RIGHT: return "RIGHT";
            case KeyInput.KEY_SPACE: return "SPACE";
            case KeyInput.KEY_RETURN: return "ENTER";
            case KeyInput.KEY_LCONTROL: return "LCTRL";
            case KeyInput.KEY_RCONTROL: return "RCTRL";
            case KeyInput.KEY_LSHIFT: return "LSHIFT";
            case KeyInput.KEY_RSHIFT: return "RSHIFT";
            case KeyInput.KEY_LMENU: return "LALT";
            case KeyInput.KEY_RMENU: return "RALT";
            case KeyInput.KEY_TAB: return "TAB";
            default:
                // lettres A-Z
                for (char c = 'A'; c <= 'Z'; c++)
                    if (code == letterCode(c)) return "" + c;
                // chiffres 0-9
                for (char c = '0'; c <= '9'; c++)
                    if (code == digitCode(c)) return "" + c;
                return "KEY " + code;
        }
    }

    private static int letterCode(char c) {
        switch (c) {
            case 'A': return KeyInput.KEY_A; case 'B': return KeyInput.KEY_B; case 'C': return KeyInput.KEY_C;
            case 'D': return KeyInput.KEY_D; case 'E': return KeyInput.KEY_E; case 'F': return KeyInput.KEY_F;
            case 'G': return KeyInput.KEY_G; case 'H': return KeyInput.KEY_H; case 'I': return KeyInput.KEY_I;
            case 'J': return KeyInput.KEY_J; case 'K': return KeyInput.KEY_K; case 'L': return KeyInput.KEY_L;
            case 'M': return KeyInput.KEY_M; case 'N': return KeyInput.KEY_N; case 'O': return KeyInput.KEY_O;
            case 'P': return KeyInput.KEY_P; case 'Q': return KeyInput.KEY_Q; case 'R': return KeyInput.KEY_R;
            case 'S': return KeyInput.KEY_S; case 'T': return KeyInput.KEY_T; case 'U': return KeyInput.KEY_U;
            case 'V': return KeyInput.KEY_V; case 'W': return KeyInput.KEY_W; case 'X': return KeyInput.KEY_X;
            case 'Y': return KeyInput.KEY_Y; case 'Z': return KeyInput.KEY_Z;
        }
        return -1;
    }

    private static int digitCode(char c) {
        switch (c) {
            case '0': return KeyInput.KEY_0; case '1': return KeyInput.KEY_1; case '2': return KeyInput.KEY_2;
            case '3': return KeyInput.KEY_3; case '4': return KeyInput.KEY_4; case '5': return KeyInput.KEY_5;
            case '6': return KeyInput.KEY_6; case '7': return KeyInput.KEY_7; case '8': return KeyInput.KEY_8;
            case '9': return KeyInput.KEY_9;
        }
        return -1;
    }

    private static float sysFloat(String k, float def) {
        try { String v = System.getProperty(k); return v != null ? Float.parseFloat(v) : def; }
        catch (NumberFormatException e) { return def; }
    }

    private static int pInt(Properties p, String k, int def) {
        try { return p.containsKey(k) ? Integer.parseInt(p.getProperty(k)) : def; }
        catch (NumberFormatException e) { return def; }
    }

    private static float pFloat(Properties p, String k, float def) {
        try { return p.containsKey(k) ? Float.parseFloat(p.getProperty(k)) : def; }
        catch (NumberFormatException e) { return def; }
    }

    private static boolean pBool(Properties p, String k, boolean def) {
        return p.containsKey(k) ? Boolean.parseBoolean(p.getProperty(k)) : def;
    }
}
