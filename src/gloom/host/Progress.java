package gloom.host;

import gloom.Assets;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Progression / sauvegarde de la campagne (points de contrôle « rest_ » du script).
 *
 * Le script (misc/script) marque chaque début d'épisode par une ligne `rest_<label>` (ex.
 * « gothic tomb », « hell »). Atteindre un de ces points pendant la partie le déverrouille ;
 * le menu propose alors « CONTINUE FROM <LABEL> ». La progression (indice du dernier point
 * déverrouillé, 0 = aucun) est persistée dans ~/.gloom-java/progress.
 */
public final class Progress {

    private Progress() {}

    private static Path file() {
        return Path.of(System.getProperty("user.home"), ".gloom-java", "progress");
    }

    /** Labels des points de contrôle, dans l'ordre du script (ex. ["GOTHIC TOMB", "HELL"]). */
    public static List<String> labels() {
        List<String> out = new ArrayList<>();
        try {
            String s = new String(Assets.read("misc/script"), StandardCharsets.ISO_8859_1);
            for (String line : s.split("[\\n\\r]")) {
                if (line.startsWith("rest_")) out.add(line.substring(5).trim().toUpperCase());
            }
        } catch (IOException e) {
            // pas de script lisible → aucun point de contrôle
        }
        return out;
    }

    /** Indice du dernier point déverrouillé (0 = aucun, 1 = 1er « rest_ », …). */
    public static int loadUnlocked() {
        try {
            return Integer.parseInt(Files.readString(file()).trim());
        } catch (Exception e) {
            return 0;
        }
    }

    /** Déverrouille le point `checkpoint` (ne régresse jamais). */
    public static void unlock(int checkpoint) {
        if (checkpoint <= loadUnlocked()) return;
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), Integer.toString(checkpoint));
        } catch (IOException e) {
            System.err.println("[Progress] sauvegarde impossible : " + e.getMessage());
        }
    }
}
