package gloom.tools;

import gloom.host.Game;
import gloom.host.Progress;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Harnais : sauvegarde / reprise par point de contrôle. Vérifie les labels d'épisode, le
 * round-trip de progression, et que boot(skip=1) reprend bien au 2e épisode (map3_1). `gradle saveTest`.
 *
 * Sauvegarde et restaure le fichier de progression réel pour ne pas perturber l'utilisateur.
 */
public final class SaveTest {
    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        // 1) labels des points de contrôle
        List<String> labels = Progress.labels();
        System.out.println("[info] points de contrôle : " + labels);
        check("au moins 2 points de contrôle", labels.size() >= 2);
        check("1er = GOTHIC TOMB", !labels.isEmpty() && labels.get(0).contains("GOTHIC"));
        check("2e = HELL", labels.size() >= 2 && labels.get(1).contains("HELL"));

        // 2) round-trip de progression (avec sauvegarde/restauration du fichier réel)
        Path f = Path.of(System.getProperty("user.home"), ".gloom-java", "progress");
        String backup = Files.exists(f) ? Files.readString(f) : null;
        try {
            Files.deleteIfExists(f);
            check("aucune progression au départ", Progress.loadUnlocked() == 0);
            Progress.unlock(2);
            check("unlock(2) → 2", Progress.loadUnlocked() == 2);
            Progress.unlock(1);                              // ne régresse pas
            check("unlock(1) ne régresse pas (reste 2)", Progress.loadUnlocked() == 2);
        } finally {
            if (backup != null) { Files.createDirectories(f.getParent()); Files.writeString(f, backup); }
            else Files.deleteIfExists(f);
        }

        // 3) reprise : boot(skip=1) saute l'épisode 1 → s'arrête à l'écran d'histoire de l'épisode 2,
        //    puis (appui-feu) lance son 1er niveau (map3_1).
        Game g = new Game();
        g.boot(224, 160, 1);
        check("reprise → écran d'histoire (épisode 2)", g.phase == Game.Phase.STORY);
        // simule des appuis-feu (front montant) pour franchir les écrans d'histoire jusqu'au niveau
        for (int i = 0; i < 10 && g.phase == Game.Phase.STORY; i++) { g.update(false); g.update(true); }
        System.out.println("[info] reprise au point 1 → niveau = " + g.currentMap);
        check("reprise saute à l'épisode 2 (map3_1)", "map3_1".equals(g.currentMap));

        if (failures == 0) System.out.println("TOUT OK");
        else { System.out.println(failures + " ECHEC(S)"); System.exit(1); }
    }

    private static void check(String name, boolean cond) {
        if (!cond) { System.out.println("ECHEC " + name); failures++; }
    }
}
