package gloom.boot;

import gloom.Assets;

/**
 * Amorçage des données : trouve (ou propose de télécharger) les disquettes, puis les branche sur
 * {@link Assets}. À appeler une fois, avant tout chargement.
 *
 * Mode développeur : {@code -Dgloom.assets=<dossier>} (ex. le dépôt GloomAmiga) court-circuite les
 * disquettes, comme avant.
 */
public final class Boot {

    private Boot() {
    }

    /**
     * @return true si les données sont prêtes ; false sinon (l'utilisateur a été prévenu par {@code ask})
     */
    public static boolean ensure(DiskFetch.Ask ask) {
        if (Assets.ready()) return true;
        Disks d = Disks.locate();
        if (d == null && DiskFetch.offer(downloadDir(), ask)) {
            d = Disks.locate();
        }
        if (d == null) {
            ask.error("Disquettes de Gloom Deluxe introuvables.\n\nDossiers examines :\n"
                    + String.join("\n", Disks.candidates().stream()
                            .map(p -> "  " + p.toAbsolutePath().normalize()).toList())
                    + "\n\nIl faut les deux images (Disk 1 of 2 et Disk 2 of 2).");
            return false;
        }
        Assets.useDisks(d);
        return true;
    }

    /** Où déposer les disquettes téléchargées : {@code -Dgloom.adf=} s'il est donné, sinon {@link Disks#defaultDir()}. */
    public static java.nio.file.Path downloadDir() {
        String prop = System.getProperty("gloom.adf");
        return prop != null ? java.nio.file.Paths.get(prop) : Disks.defaultDir();
    }

    /** {@code gradle checkDisks} : monte les disquettes et dit ce qu'on y trouve, sans fenêtre. */
    public static void main(String[] args) {
        if (!ensure(DiskFetch.CONSOLE)) System.exit(1);
        System.out.println("[disquettes] montees : " + Assets.describe());
    }
}
