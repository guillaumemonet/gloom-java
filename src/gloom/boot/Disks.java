package gloom.boot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Les deux disquettes de Gloom Deluxe, montées EN MÉMOIRE : c'est la source des données du jeu.
 *
 * <pre>
 *   gloomprog  (Disk 1 of 2)  exécutables, misc/ (polices, palettes, gun.bin), sfxs/, objs/player,
 *                             pics/title, pics/gloom, pics/blackmagic
 *   gloomdata  (Disk 2 of 2)  maps/, txts/, objs/ (monstres), pics/ (histoire), misc/script
 * </pre>
 *
 * Les disquettes sont reconnues par leur NOM DE VOLUME, pas par leur empreinte : la version
 * « [a] » et la version « [cr DLM] » proposée au téléchargement ont exactement les mêmes fichiers
 * de jeu (seuls l'intro du crack, {@code startup-sequence} et {@code gloomcfg} diffèrent).
 *
 * Les fichiers rendus sont BRUTS (souvent compactés Crunch-Mania) ; c'est {@link gloom.Assets}
 * qui décide de les décompacter.
 */
public final class Disks {

    public static final String PROG = "gloomprog";
    public static final String DATA = "gloomdata";

    /** Chemin en minuscules sans « / » initial → (disquette, entrée). */
    private final Map<String, Hit> index = new HashMap<>();
    /** Nom de base en minuscules → premier chemin trouvé (repli pour les incbin sans dossier). */
    private final Map<String, String> byBase = new HashMap<>();
    private final List<Path> sources = new ArrayList<>();

    private record Hit(Adf adf, Adf.Entry entry) { }

    private Disks() {
    }

    /**
     * Monte les deux disquettes trouvées dans {@code dir}.
     * @throws IOException s'il en manque une (le message dit laquelle)
     */
    public static Disks mount(Path dir) throws IOException {
        Adf prog = null, data = null;
        for (Path p : adfFiles(dir)) {
            Adf a;
            try {
                a = new Adf(p);
            } catch (IOException e) {
                continue;                                   // image illisible : on l'ignore
            }
            String v = a.volume().toLowerCase(Locale.ROOT);
            if (v.equals(PROG) && prog == null) prog = a;
            else if (v.equals(DATA) && data == null) data = a;
        }
        if (prog == null || data == null) {
            throw new IOException("disquette" + (prog == null && data == null ? "s" : "")
                    + " manquante" + (prog == null && data == null ? "s" : "") + " dans " + dir.toAbsolutePath()
                    + " :" + (prog == null ? " « Disk 1 of 2 » (volume gloomprog)" : "")
                    + (data == null ? " « Disk 2 of 2 » (volume gloomdata)" : ""));
        }
        Disks d = new Disks();
        d.add(prog);
        d.add(data);
        return d;
    }

    private void add(Adf a) {
        sources.add(a.source());
        for (Adf.Entry e : a.list()) {
            if (e.dir()) continue;
            String key = e.path().substring(1).toLowerCase(Locale.ROOT);   // "/maps/map1_1" → "maps/map1_1"
            if (index.putIfAbsent(key, new Hit(a, e)) == null) {
                byBase.putIfAbsent(key.substring(key.lastIndexOf('/') + 1), key);
            }
        }
    }

    /** Contenu brut du fichier (chemin Amiga relatif, insensible à la casse), ou null s'il n'existe pas. */
    public byte[] read(String path) {
        Hit h = index.get(normalize(path));
        return h == null ? null : h.adf().read(h.entry());
    }

    /** true si le fichier existe sur l'une des disquettes. */
    public boolean exists(String path) {
        return index.containsKey(normalize(path));
    }

    /** Repli « nom de base » (même logique que pour un dossier) : chemin complet, ou null. */
    public String findByBasename(String base) {
        return byBase.get(base.toLowerCase(Locale.ROOT));
    }

    public List<Path> sources() {
        return sources;
    }

    private static String normalize(String path) {
        String p = path.replace('\\', '/');
        int colon = p.indexOf(':');
        if (colon >= 0) p = p.substring(colon + 1);         // assign Amiga (« gloomdata:maps/… »)
        while (p.startsWith("/")) p = p.substring(1);
        return p.toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------ où chercher les disquettes

    /** Dossier par défaut où l'on dépose les disquettes téléchargées : {@code ~/.gloom-java/adf}. */
    public static Path defaultDir() {
        return Paths.get(System.getProperty("user.home"), ".gloom-java", "adf");
    }

    /**
     * Dossiers examinés, dans l'ordre : {@code -Dgloom.adf=}, {@code <appli>/adf} (paquet natif,
     * {@code -Dgloom.appdir=}), {@code ./adf}, {@code ../adf} (le dossier {@code Gloom/adf} à côté
     * des dépôts), puis {@link #defaultDir()}.
     */
    public static List<Path> candidates() {
        List<Path> c = new ArrayList<>();
        String prop = System.getProperty("gloom.adf");
        if (prop != null) c.add(Paths.get(prop));
        String app = System.getProperty("gloom.appdir");
        if (app != null) c.add(Paths.get(app, "adf"));
        c.add(Paths.get("adf"));
        c.add(Paths.get("..", "adf"));
        c.add(defaultDir());
        return c;
    }

    /** Premier dossier candidat où les deux disquettes se montent, ou null. */
    public static Disks locate() {
        for (Path dir : candidates()) {
            if (adfFiles(dir).isEmpty()) continue;
            try {
                return mount(dir);
            } catch (IOException e) {
                System.err.println("[disquettes] " + e.getMessage());
            }
        }
        return null;
    }

    /** Les images {@code .adf} d'un dossier (vide s'il n'existe pas). */
    public static List<Path> adfFiles(Path dir) {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) return out;
        try (var s = Files.list(dir)) {
            s.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".adf"))
             .sorted()
             .forEach(out::add);
        } catch (IOException e) {
            // dossier illisible : aucune disquette
        }
        return out;
    }
}
