package gloom;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Résolution des fichiers d'assets pour les directives incbin et les
 * chargements à l'exécution.
 *
 * Le source d'origine (gloom.s) résout les incbin relativement au dossier du
 * projet Devpac, et ouvre les fichiers de données (maps, txts, objs, sfxs…)
 * par chemins relatifs. Ici tout est mappé sur le dépôt source GloomAmiga,
 * avec recherche insensible à la casse (le FS Amiga l'était).
 *
 * Racine par défaut : ../GloomAmiga (relatif à la racine du projet gloom-java,
 * qui est le workingDir des tâches Gradle). Surchargeable via la propriété
 * système -Dgloom.assets=chemin.
 *
 * Copie adaptée de ab3d2.Assets.
 */
public final class Assets {

    /** Racine des assets — modifiable avant l'init (option de lancement). */
    public static Path root = resolveRoot();

    private static Path resolveRoot() {
        String prop = System.getProperty("gloom.assets");
        if (prop != null) {
            return Path.of(prop);
        }
        return Path.of("..", "GloomAmiga");
    }

    /**
     * Mode tolérant : si un fichier incbin est introuvable, émet un avertissement
     * et n'alloue rien (adresse valide mais bloc vide) au lieu de stopper.
     */
    public static boolean lenient = true;

    private Assets() {
    }

    /**
     * incbin "path" : charge le fichier dans Mem à l'adresse courante
     * d'allocation et renvoie cette adresse.
     */
    public static int incbin(String path) {
        Path p = resolve(path);
        if (p == null) {
            String msg = "incbin introuvable: " + path + " (racine " + root + ")";
            if (!lenient) {
                throw new IllegalStateException(msg);
            }
            System.err.println("[Assets] AVERTISSEMENT " + msg);
            return Mem.alloc(0);
        }
        try {
            byte[] data = Files.readAllBytes(p);
            int addr = Mem.alloc(data.length);
            Mem.load(addr, data);
            return addr;
        } catch (IOException e) {
            throw new IllegalStateException("incbin: erreur de lecture " + p, e);
        }
    }

    /** Charge un fichier (chemin relatif au dépôt, ex. "maps/com1_1"). */
    public static byte[] read(String path) throws IOException {
        Path p = resolve(path);
        if (p == null) {
            throw new IOException("fichier introuvable: " + path + " (racine " + root + ")");
        }
        return Files.readAllBytes(p);
    }

    /**
     * Résout un chemin vers un fichier réel sous root :
     *  - essaie root/chemin (segment par segment, insensible à la casse) ;
     *  - dernier recours : recherche par nom de base n'importe où sous root.
     */
    public static Path resolve(String path) {
        String clean = path.replace('\\', '/');
        int colon = clean.indexOf(':');
        if (colon >= 0) {
            clean = clean.substring(colon + 1); // retire un éventuel assign Amiga
        }

        Path direct = findCaseInsensitive(root, clean.split("/"));
        if (direct != null) {
            return direct;
        }
        // Repli par nom de base UNIQUEMENT si le chemin n'a pas de répertoire : un chemin avec
        // dossier (ex. objs/ghoul2) doit exister dans CE dossier, sinon échouer — sinon on matchait
        // par erreur un homonyme ailleurs (la racine ./ghoul2), chargeant des données fausses.
        if (clean.indexOf('/') >= 0) {
            return null;
        }
        return findByBasename(clean);
    }

    private static Map<String, Path> basenameIndex; // index paresseux nom-de-base → fichier

    private static Path findByBasename(String base) {
        if (basenameIndex == null) {
            basenameIndex = new HashMap<>();
            try (var stream = Files.walk(root)) {
                stream.filter(Files::isRegularFile).forEach(p ->
                        basenameIndex.putIfAbsent(p.getFileName().toString().toLowerCase(), p));
            } catch (IOException e) {
                // index vide : on retombera sur le mode tolérant
            }
        }
        return basenameIndex.get(base.toLowerCase());
    }

    private static Path findCaseInsensitive(Path dir, String[] segments) {
        Path current = dir;
        for (String seg : segments) {
            if (seg.isEmpty()) {
                continue;
            }
            if (!Files.isDirectory(current)) {
                return null;
            }
            Path next = null;
            try (var stream = Files.newDirectoryStream(current)) {
                for (Path candidate : stream) {
                    if (candidate.getFileName().toString().equalsIgnoreCase(seg)) {
                        next = candidate;
                        break;
                    }
                }
            } catch (IOException e) {
                return null;
            }
            if (next == null) {
                return null;
            }
            current = next;
        }
        return Files.isRegularFile(current) ? current : null;
    }
}
