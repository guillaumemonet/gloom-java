package gloom;

import gloom.boot.Disks;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Résolution des fichiers d'assets pour les directives incbin et les chargements à l'exécution.
 *
 * Trois sources, dans l'ordre :
 * <ol>
 *   <li><b>incbin de compilation</b> — les fichiers que gloom.s inclut à l'assemblage
 *       ({@code sqr.bin}, {@code bullet*.bin}, {@code castrots*.bin}…). Ils font partie des sources,
 *       pas des disquettes : ils sont versionnés dans {@code src/gloom/data/incbin/} et lus sur le
 *       classpath.</li>
 *   <li><b>les disquettes Gloom Deluxe</b> ({@link Disks}), montées en mémoire — source normale de
 *       toutes les données de jeu (maps, txts, objs, sfxs, pics, misc). Les fichiers compactés
 *       Crunch-Mania sont décompactés à la lecture ({@link #read}), sauf pour {@link Files#loadfile}
 *       qui reproduit lui-même le décompactage de l'original ({@link #readRaw}).</li>
 *   <li><b>un dossier</b> (mode développeur) — {@code -Dgloom.assets=chemin}, ex. le dépôt GloomAmiga.
 *       S'il est donné, il remplace les disquettes.</li>
 * </ol>
 * La recherche est insensible à la casse, comme le FS Amiga.
 *
 * Les illustrations que gloom.s incluait à l'assemblage sont, sur la disquette Deluxe, des fichiers
 * de {@code pics/} : {@code title} → {@code pics/title}, {@code gloombrush} → {@code pics/gloom}.
 */
public final class Assets {

    /** Dossier d'assets (mode développeur) — null en mode disquettes. Modifiable avant l'init. */
    public static Path root = resolveRoot();

    /** Disquettes montées (mode normal). */
    private static Disks disks;

    /** Où vivent les incbin de compilation sur le classpath. */
    private static final String INCBIN = "/gloom/data/incbin/";

    /** Anciens noms d'incbin devenus des fichiers de la disquette Deluxe. */
    private static final Map<String, String> ALIASES = Map.of(
            "title", "pics/title",
            "title.pal", "pics/title.pal",
            "gloombrush", "pics/gloom");

    private static Path resolveRoot() {
        String prop = System.getProperty("gloom.assets");
        return prop != null ? Paths.get(prop) : null;
    }

    /**
     * Mode tolérant : si un fichier incbin est introuvable, émet un avertissement
     * et n'alloue rien (adresse valide mais bloc vide) au lieu de stopper.
     */
    public static boolean lenient = true;

    private Assets() {
    }

    // ------------------------------------------------------------------ branchement des sources

    /** Branche les disquettes montées par {@link gloom.boot.Boot}. */
    public static void useDisks(Disks d) {
        disks = d;
    }

    /** true si une source de données est branchée (dossier ou disquettes). */
    public static boolean ready() {
        return root != null || disks != null;
    }

    /** Description lisible de la source (journal, harnais). */
    public static String describe() {
        if (root != null) return "dossier " + root.toAbsolutePath().normalize();
        if (disks != null) return "disquettes " + disks.sources();
        return "(aucune)";
    }

    /**
     * Sans amorçage explicite (harnais), on monte les disquettes trouvées dans les dossiers
     * habituels — sans jamais proposer de téléchargement.
     */
    private static void autoMount() {
        if (!ready()) {
            disks = Disks.locate();
            if (disks != null) System.out.println("[Assets] " + describe());
        }
    }

    // ------------------------------------------------------------------ API

    /**
     * incbin "path" : charge le fichier (décompacté) dans Mem à l'adresse courante d'allocation
     * et renvoie cette adresse.
     */
    public static int incbin(String path) {
        byte[] data;
        try {
            data = find(path);
        } catch (IOException e) {
            throw new IllegalStateException("incbin: erreur de lecture " + path, e);
        }
        if (data == null) {
            String msg = "incbin introuvable: " + path + " (" + describe() + ")";
            if (!lenient) throw new IllegalStateException(msg);
            System.err.println("[Assets] AVERTISSEMENT " + msg);
            return Mem.alloc(0);
        }
        data = Decrunch.unpack(data);
        int addr = Mem.alloc(data.length);
        Mem.load(addr, data);
        return addr;
    }

    /** Charge un fichier DÉCOMPACTÉ (chemin Amiga relatif, ex. "maps/com1_1"). */
    public static byte[] read(String path) throws IOException {
        return Decrunch.unpack(readRaw(path));
    }

    /** Charge un fichier tel qu'il est stocké (éventuellement compacté) — pour {@link Files#loadfile}. */
    public static byte[] readRaw(String path) throws IOException {
        byte[] d = find(path);
        if (d == null) throw new IOException("fichier introuvable: " + path + " (" + describe() + ")");
        return d;
    }

    /** true si le fichier existe dans l'une des sources. */
    public static boolean exists(String path) {
        try {
            return find(path) != null;
        } catch (IOException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ recherche

    private static byte[] find(String path) throws IOException {
        String clean = path.replace('\\', '/');
        int colon = clean.indexOf(':');
        if (colon >= 0) clean = clean.substring(colon + 1);       // retire un éventuel assign Amiga
        boolean hasDir = clean.indexOf('/') >= 0;

        // 1) incbin de compilation (sur le classpath)
        if (!hasDir) {
            try (InputStream in = Assets.class.getResourceAsStream(INCBIN + clean.toLowerCase(Locale.ROOT))) {
                if (in != null) return in.readAllBytes();
            }
        }

        // 3) dossier (mode développeur)
        if (root != null) {
            Path p = resolve(clean);
            if (p == null && ALIASES.containsKey(clean.toLowerCase(Locale.ROOT))) {
                p = resolve(ALIASES.get(clean.toLowerCase(Locale.ROOT)));
            }
            return p == null ? null : Files.readAllBytes(p);
        }

        // 2) disquettes
        autoMount();
        if (disks == null) return null;
        byte[] d = disks.read(clean);
        if (d != null) return d;
        String alias = ALIASES.get(clean.toLowerCase(Locale.ROOT));
        if (alias != null) return disks.read(alias);
        if (!hasDir) {                                            // repli par nom de base (cf. resolve)
            String full = disks.findByBasename(clean);
            if (full != null) return disks.read(full);
        }
        return null;
    }

    /**
     * Mode dossier : résout un chemin vers un fichier réel sous root :
     *  - essaie root/chemin (segment par segment, insensible à la casse) ;
     *  - dernier recours : recherche par nom de base n'importe où sous root.
     */
    public static Path resolve(String path) {
        if (root == null) return null;
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
                        basenameIndex.putIfAbsent(p.getFileName().toString().toLowerCase(Locale.ROOT), p));
            } catch (IOException e) {
                // index vide : on retombera sur le mode tolérant
            }
        }
        return basenameIndex.get(base.toLowerCase(Locale.ROOT));
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
