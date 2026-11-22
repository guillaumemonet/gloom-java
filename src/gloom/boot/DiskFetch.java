package gloom.boot;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * LES DISQUETTES, au premier lancement.
 *
 * Ce dépôt ne redistribue aucune donnée du jeu : il lui faut les deux images {@code .adf} de
 * Gloom Deluxe. Quand elles manquent, on propose de les prendre sur <b>Amiga Museum</b>
 * (amigamuseum.emu-france.info), une archive par disquette.
 *
 * <b>Rien ne part sans un oui explicite.</b> La question n'est pas posée ici : cette classe ne sait
 * pas ce qu'est une fenêtre. Elle reçoit un {@link Ask}, et c'est la plateforme (boîte de dialogue
 * Swing, terminal, écran Android) qui décide comment demander.
 *
 * Réglages : {@code -Dgloom.adfUrl1=} / {@code -Dgloom.adfUrl2=} changent la source,
 * {@code -Dgloom.noDownload} supprime la proposition (harnais).
 *
 * Reprise de {@code ab3d2.full.boot.DiskFetch} (portage Alien Breed 3D II).
 */
public final class DiskFetch {

    private static final String BASE = "https://amigamuseum.emu-france.info/Fichiers/ADF/G/";
    public static final String URL_DISK1 = BASE
            + "Gloom%20Deluxe%20(1996)(Guildhall)(Disk%201%20of%202)%5bcr%20DLM%5d%5ba%5d%5b68020%5d.zip";
    public static final String URL_DISK2 = BASE
            + "Gloom%20Deluxe%20(1996)(Guildhall)(Disk%202%20of%202)%5bcr%20DLM%5d%5ba%5d%5b68020%5d.zip";

    /** Nom que le client donne de lui-même : générique, il ne désigne personne. */
    private static final String UA = "Gloom-Java (portage non commercial)";

    private static final int TIMEOUT_MS = 30_000;

    /** Comment la plateforme pose la question, et rend compte. */
    public interface Ask {

        /** @return vrai si l'utilisateur accepte le téléchargement */
        boolean confirm(String message);

        void info(String message);

        void error(String message);
    }

    /** Une plateforme muette : elle refuse, et n'affiche rien. */
    public static final Ask SILENT = new Ask() {
        @Override public boolean confirm(String message) { return false; }
        @Override public void info(String message) { }
        @Override public void error(String message) { }
    };

    /** Le terminal (harnais, mode sans écran). */
    public static final Ask CONSOLE = new Ask() {
        @Override
        public boolean confirm(String message) {
            System.out.println(message);
            System.out.print("Telecharger ? [o/N] ");
            try {
                int c = System.in.read();
                return c == 'o' || c == 'O' || c == 'y' || c == 'Y';
            } catch (IOException e) {
                return false;
            }
        }
        @Override public void info(String message) { System.out.println("[disquettes] " + message); }
        @Override public void error(String message) { System.err.println("[disquettes] " + message); }
    };

    static final String MESSAGE = """
            Les donnees du jeu sont absentes.

            Ce portage ne contient aucune donnee de Gloom : il lui faut les deux
            disquettes de Gloom Deluxe (fichiers .adf).

            Les telecharger depuis Amiga Museum (amigamuseum.emu-france.info) ?
            (deux archives, environ 1,4 Mo)

            Elles seront installees dans :
            %s

            Si vous possedez deja les images, repondez NON et copiez-les vous-meme
            dans ce dossier (ou indiquez-le avec -Dgloom.adf=...).""";

    private DiskFetch() {
    }

    /**
     * Propose le téléchargement des deux disquettes dans {@code adfDir}.
     * @return vrai si elles ont été installées
     */
    public static boolean offer(Path adfDir, Ask ask) {
        if (System.getProperty("gloom.noDownload") != null) return false;
        if (!ask.confirm(MESSAGE.formatted(adfDir.toAbsolutePath()))) return false;
        try {
            int n = download(System.getProperty("gloom.adfUrl1", URL_DISK1), adfDir)
                  + download(System.getProperty("gloom.adfUrl2", URL_DISK2), adfDir);
            ask.info(n + " disquette(s) installee(s) dans\n" + adfDir.toAbsolutePath());
            return n > 0;
        } catch (IOException e) {
            ask.error("Le telechargement a echoue :\n" + e.getMessage()
                    + "\n\nPlacez les fichiers .adf vous-meme dans :\n" + adfDir.toAbsolutePath());
            return false;
        }
    }

    /** Télécharge une archive et en extrait les {@code .adf} ; rend le nombre d'images écrites. */
    public static int download(String url, Path adfDir) throws IOException {
        Files.createDirectories(adfDir);
        System.out.println("[disquettes] telechargement depuis " + url);
        HttpURLConnection c = open(url);
        int n = 0;
        try (InputStream in = c.getInputStream(); ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String nom = e.getName().replace('\\', '/');
                nom = nom.substring(nom.lastIndexOf('/') + 1);           // on aplatit
                if (!nom.toLowerCase(Locale.ROOT).endsWith(".adf")) continue;
                Path out = adfDir.resolve(nom);
                Files.copy(zip, out, StandardCopyOption.REPLACE_EXISTING);
                n++;
                System.out.printf("[disquettes]   %-70s %d octets%n", nom, Files.size(out));
            }
        }
        if (n == 0) throw new IOException("aucun fichier .adf dans " + url);
        return n;
    }

    /** Ouvre la connexion en suivant les redirections, changement de protocole compris. */
    private static HttpURLConnection open(String url) throws IOException {
        String courant = url;
        for (int saut = 0; saut < 5; saut++) {
            URL u = URI.create(courant).toURL();
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setRequestProperty("User-Agent", UA);
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setInstanceFollowRedirects(false);
            int code = c.getResponseCode();
            if (code / 100 == 3) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null) throw new IOException("redirection sans Location (HTTP " + code + ")");
                courant = URI.create(courant).resolve(loc).toString();
                continue;
            }
            if (code != 200) {
                c.disconnect();
                throw new IOException("HTTP " + code + " sur " + courant);
            }
            return c;
        }
        throw new IOException("trop de redirections depuis " + url);
    }
}
