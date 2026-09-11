package gloom.android;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Mise à disposition des assets du jeu, équivalent Android de {@code fetch-assets.bat}.
 *
 * Les graphismes, sons et maps de Gloom ne sont PAS redistribuables (seules les sources .s/.bb2
 * d'origine sont dans le domaine public) : l'APK est donc vierge, exactement comme le paquet
 * jpackage. Au premier lancement, l'app télécharge le dépôt de préservation
 * {@code github.com/earok/GloomAmiga} dans son stockage privé, puis n'y retouche plus.
 *
 * Le ZIP GitHub préfixe tout par {@code GloomAmiga-master/} : on retire ce premier segment pour
 * retrouver l'arborescence attendue par {@link gloom.Assets} ({@code sfxs/}, {@code misc/},
 * {@code data/maps/}, {@code txts/}, {@code objs/}…).
 */
public final class AssetSetup {

    private static final String ZIP_URL =
            "https://github.com/earok/GloomAmiga/archive/refs/heads/master.zip";

    /** Quelques fichiers sans lesquels le jeu ne démarre pas — sert de témoin d'installation. */
    private static final String[] WITNESS = {
            "misc/script", "misc/smallfont.bin", "sfxs/med1", "sfxs/shoot.bin", "data/maps/map1_1"
    };

    /** Retour de progression vers l'écran de chargement. */
    public interface Progress {
        void onProgress(String message);
    }

    private AssetSetup() {
    }

    /** true si les assets sont déjà en place (témoins présents). */
    public static boolean ready(File root) {
        for (String w : WITNESS) {
            if (!new File(root, w).isFile()) return false;
        }
        return true;
    }

    /**
     * Télécharge et déploie les assets dans {@code root}. Lance une IOException en cas d'échec
     * (pas de réseau, dépôt indisponible) : l'appelant affiche le message et s'arrête là.
     */
    public static void install(File root, Progress cb) throws IOException {
        cb.onProgress("Connexion a github.com...");
        HttpURLConnection cx = (HttpURLConnection) new URL(ZIP_URL).openConnection();
        cx.setConnectTimeout(20000);
        cx.setReadTimeout(30000);
        cx.setInstanceFollowRedirects(true);
        cx.connect();
        int code = cx.getResponseCode();
        if (code / 100 != 2) throw new IOException("HTTP " + code + " sur " + ZIP_URL);

        File tmp = new File(root.getParentFile(), root.getName() + ".part");
        deleteTree(tmp);
        if (!tmp.mkdirs()) throw new IOException("impossible de creer " + tmp);

        long total = 0;
        int files = 0;
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(cx.getInputStream(), 1 << 16))) {
            byte[] buf = new byte[1 << 16];
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                String name = strip(e.getName());
                if (name == null) continue;                       // l'entrée racine elle-même
                File out = new File(tmp, name);
                if (!out.getCanonicalPath().startsWith(tmp.getCanonicalPath())) {
                    continue;                                     // garde-fou zip-slip
                }
                if (e.isDirectory()) {
                    out.mkdirs();
                    continue;
                }
                File parent = out.getParentFile();
                if (parent != null) parent.mkdirs();
                try (OutputStream os = new FileOutputStream(out)) {
                    int n;
                    while ((n = zip.read(buf)) > 0) {
                        os.write(buf, 0, n);
                        total += n;
                    }
                }
                if ((++files & 63) == 0) {
                    cb.onProgress("Telechargement... " + (total >> 20) + " Mo  (" + files + " fichiers)");
                }
            }
        } catch (IOException io) {
            deleteTree(tmp);
            throw io;
        }

        cb.onProgress("Installation...");
        deleteTree(root);
        if (!tmp.renameTo(root)) {
            deleteTree(tmp);
            throw new IOException("impossible de renommer " + tmp + " en " + root);
        }
        if (!ready(root)) {
            throw new IOException("assets incomplets apres telechargement (" + files + " fichiers)");
        }
    }

    /** Retire le premier segment du chemin ({@code GloomAmiga-master/}) ; null si rien ne reste. */
    private static String strip(String entry) {
        String s = entry.replace('\\', '/');
        int slash = s.indexOf('/');
        if (slash < 0 || slash + 1 >= s.length()) return null;
        return s.substring(slash + 1);
    }

    private static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }

    /** Rend la taille approximative à annoncer avant le téléchargement. */
    public static String sourceLabel() {
        return "github.com/earok/GloomAmiga (~25 Mo)";
    }
}
