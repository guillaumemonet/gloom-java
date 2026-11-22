package gloom.boot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Lecture d'une image de disquette Amiga (.adf) — système de fichiers OFS/FFS.
 *
 * <p>Une disquette double densité fait 880 Ko : 1760 blocs de 512 octets. Le bloc d'amorçage
 * (0-1) porte le type DOS ; le bit 0 distingue OFS (données précédées d'un en-tête de 24 octets
 * dans chaque bloc) de FFS (blocs de données pleins). Le bloc racine est au milieu, en 880.
 *
 * <p>Un bloc d'en-tête (racine, répertoire ou fichier) contient une table de hachage de 72
 * entrées en +24, le nom en +432 (BSTR : longueur puis caractères), le maillon de collision en
 * +496 et le type secondaire en +508 : 1 racine, 2 répertoire, -3 fichier. Pour un fichier, la
 * taille est en +324, la table des blocs de données occupe la même zone que la table de hachage
 * mais À L'ENVERS, et +504 chaîne les blocs d'extension quand le fichier dépasse 72 blocs.
 *
 * <p>Les deux disquettes de Gloom Deluxe sont en {@code DOS\0}, donc OFS.
 *
 * <p>Reprise telle quelle du portage Alien Breed 3D II ({@code ab3d2.full.boot.Adf}).
 */
public final class Adf {

    private static final int BS = 512;
    private static final int NBLOCKS = 1760;
    /** Entrées de la table de hachage d'un bloc de 512 octets. */
    private static final int HT_SIZE = BS / 4 - 56;   // 72

    private final byte[] img;
    private final boolean ffs;
    private final int rootBlock;
    private final String volume;
    private final Path source;

    public Adf(Path file) throws IOException {
        this.source = file;
        this.img = Files.readAllBytes(file);
        if (img.length < NBLOCKS * BS) {
            throw new IOException("image trop courte : " + file);
        }
        this.ffs = (img[3] & 1) != 0;
        this.rootBlock = NBLOCKS / 2;
        this.volume = name(rootBlock);
    }

    public String volume() {
        return volume;
    }

    public Path source() {
        return source;
    }

    /** Une entrée du système de fichiers. */
    public record Entry(String path, int header, int size, boolean dir) { }

    private int be32(int off) {
        return (img[off] & 0xFF) << 24 | (img[off + 1] & 0xFF) << 16
             | (img[off + 2] & 0xFF) << 8 | (img[off + 3] & 0xFF);
    }

    private int word(int block, int off) {
        return be32(block * BS + off);
    }

    private String name(int block) {
        int base = block * BS + 432;
        int n = img[base] & 0xFF;
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append((char) (img[base + 1 + i] & 0xFF));
        }
        return sb.toString();
    }

    /** Tous les en-têtes fils d'un répertoire, chaînes de collision comprises. */
    private List<Integer> children(int header) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < HT_SIZE; i++) {
            int n = word(header, 24 + i * 4);
            while (n > 0 && n < NBLOCKS) {
                out.add(n);
                n = word(n, 496);                      // maillon de collision
            }
        }
        return out;
    }

    /** Parcours récursif, chemins en {@code /a/b/c}. */
    public List<Entry> list() {
        List<Entry> out = new ArrayList<>();
        walk(rootBlock, "", out);
        return out;
    }

    private void walk(int header, String path, List<Entry> out) {
        for (int e : children(header)) {
            int st = word(e, 508);
            String p = path + "/" + name(e);
            if (st == 2) {                             // ST_USERDIR
                out.add(new Entry(p, e, 0, true));
                walk(e, p, out);
            } else if (st == -3) {                     // ST_FILE
                out.add(new Entry(p, e, word(e, 324), false));
            }
        }
    }

    /** Contenu brut d'un fichier (toujours packé s'il l'était sur la disquette). */
    public byte[] read(Entry f) {
        int size = f.size();
        byte[] out = new byte[size];
        int written = 0;
        int cur = f.header();
        while (cur > 0 && written < size) {
            int used = word(cur, 8);                   // high_seq : pointeurs renseignés
            for (int i = 0; i < used && written < size; i++) {
                // La table des blocs de données est À L'ENVERS de la table de hachage.
                int d = word(cur, 24 + (HT_SIZE - 1 - i) * 4);
                if (d <= 0 || d >= NBLOCKS) {
                    continue;
                }
                int from = d * BS;
                int len;
                if (ffs) {
                    len = Math.min(BS, size - written);
                } else {
                    len = Math.min(word(d, 12), size - written);   // OFS : en-tête de 24 octets
                    from += 24;
                }
                System.arraycopy(img, from, out, written, len);
                written += len;
            }
            cur = word(cur, 504);                      // bloc d'extension
        }
        return out;
    }
}
