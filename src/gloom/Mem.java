package gloom;

/**
 * Mémoire plate 68k, big-endian.
 *
 * Toute la traduction littérale de l'ASM (gloom.s) repose sur cette classe :
 *  - les registres d'adresse a0-a6 sont des int (adresses dans RAM) ;
 *  - les registres de données d0-d7 sont des int (32 bits, comme sur 68k) ;
 *  - chaque variable BSS/DATA est une adresse allouée ici (voir gloom.bss.*, gloom.data.*).
 *
 * Conventions de lecture (mêmes sémantiques que move.b/.w/.l) :
 *  - b(a)  : byte signé   (move.b →  extension de signe comme ext.w/ext.l)
 *  - ub(a) : byte non signé (0..255)
 *  - w(a)  : word signé   (-32768..32767)
 *  - uw(a) : word non signé (0..65535)
 *  - l(a)  : long 32 bits (int Java, signé — identique au 68k)
 *
 * Écritures : wb/ww/wl — seuls les 8/16/32 bits de poids faible de la valeur
 * sont écrits, comme sur 68k (move.b d0,(a0) n'écrit que d0[7:0]).
 *
 * L'endianness est implémentée par décalages explicites sur byte[] : l'octet de
 * poids fort est à l'adresse la plus basse (big-endian Amiga). Les accès word/long
 * non alignés sont permis (le 68020+ les autorise, et le moteur en fait usage).
 *
 * Cette classe est copiée à l'identique du portage AB3D2-TKG (ab3d2.Mem) : même
 * infrastructure, même sémantique. Voir docs/ARCHITECTURE.md §2.
 */
public final class Mem {

    /** Taille de la RAM émulée. 64 Mo : largement assez pour code+données+assets dépackés. */
    public static final int RAM_SIZE = 64 << 20;

    public static final byte[] RAM = new byte[RAM_SIZE];

    /**
     * Allocateur "bump" : les sections BSS/DATA et les chargements de fichiers
     * réservent leurs blocs ici, dans l'ordre, au démarrage.
     * L'adresse 0 est volontairement laissée inutilisée (un pointeur nul 68k
     * lirait les premiers octets de la ROM ; ici on garde 4096 octets à zéro
     * pour détecter les usages, comme une page de garde).
     */
    private static int allocTop = 4096;

    private Mem() {
    }

    /**
     * Réserve size octets (zéro-initialisés par Java, comme une section BSS).
     * Renvoie l'adresse. AUCUN padding implicite : les allocations successives
     * sont contiguës, exactement comme des ds.b/ds.w/ds.l consécutifs — les
     * directives align de l'ASM se traduisent par un appel explicite à align().
     */
    public static int alloc(int size) {
        int addr = allocTop;
        allocTop += size;
        if (allocTop > RAM_SIZE) {
            throw new OutOfMemoryError("Mem: dépassement RAM (" + allocTop + " > " + RAM_SIZE + ")");
        }
        return addr;
    }

    /** Directive align n (puissance de 2) : avance allocTop à la prochaine frontière. */
    public static int align(int n) {
        allocTop = (allocTop + n - 1) & -n;
        return allocTop;
    }

    /** Réserve size octets alignés sur une frontière donnée (puissance de 2). */
    public static int allocAligned(int size, int align) {
        align(align);
        return alloc(size);
    }

    public static int allocTop() {
        return allocTop;
    }

    // ------------------------------------------------------------------
    // Lectures
    // ------------------------------------------------------------------

    /** move.b (a),dN avec extension de signe (équivalent ext.w/ext.l ensuite). */
    public static int b(int addr) {
        return RAM[addr];
    }

    /** Byte non signé : 0..255. */
    public static int ub(int addr) {
        return RAM[addr] & 0xFF;
    }

    /** Word signé big-endian : -32768..32767. */
    public static int w(int addr) {
        return (short) (((RAM[addr] & 0xFF) << 8) | (RAM[addr + 1] & 0xFF));
    }

    /** Word non signé big-endian : 0..65535. */
    public static int uw(int addr) {
        return ((RAM[addr] & 0xFF) << 8) | (RAM[addr + 1] & 0xFF);
    }

    /** Long 32 bits big-endian (signé, comme le 68k). */
    public static int l(int addr) {
        return ((RAM[addr] & 0xFF) << 24)
             | ((RAM[addr + 1] & 0xFF) << 16)
             | ((RAM[addr + 2] & 0xFF) << 8)
             |  (RAM[addr + 3] & 0xFF);
    }

    // ------------------------------------------------------------------
    // Écritures (seuls les bits de poids faible de value sont écrits)
    // ------------------------------------------------------------------

    /** move.b dN,(a) */
    public static void wb(int addr, int value) {
        RAM[addr] = (byte) value;
    }

    /** move.w dN,(a) */
    public static void ww(int addr, int value) {
        RAM[addr] = (byte) (value >> 8);
        RAM[addr + 1] = (byte) value;
    }

    /** move.l dN,(a) */
    public static void wl(int addr, int value) {
        RAM[addr] = (byte) (value >> 24);
        RAM[addr + 1] = (byte) (value >> 16);
        RAM[addr + 2] = (byte) (value >> 8);
        RAM[addr + 3] = (byte) value;
    }

    // ------------------------------------------------------------------
    // Utilitaires bloc (équivalents des boucles de copie/clear ASM,
    // à n'utiliser que pour le chargement de fichiers / inits)
    // ------------------------------------------------------------------

    /** Copie len octets de src vers dst (sémantique ascendante, comme (aN)+). */
    public static void copy(int src, int dst, int len) {
        System.arraycopy(RAM, src, RAM, dst, len);
    }

    /** Met len octets à zéro à partir de addr. */
    public static void clear(int addr, int len) {
        java.util.Arrays.fill(RAM, addr, addr + len, (byte) 0);
    }

    /** Charge un tableau d'octets (contenu de fichier) à addr. */
    public static void load(int addr, byte[] data) {
        System.arraycopy(data, 0, RAM, addr, data.length);
    }

    // ------------------------------------------------------------------
    // Directives dc.b / dc.w / dc.l / dcb.b — pour les sections .data.
    // Chaque appel alloue et écrit, et renvoie l'adresse du premier élément :
    // une ligne "label: dc.w 1,2,3" devient "label = Mem.dcW(1,2,3)".
    // ------------------------------------------------------------------

    /** dc.b v,v,... */
    public static int dcB(int... values) {
        int addr = alloc(values.length);
        for (int i = 0; i < values.length; i++) {
            RAM[addr + i] = (byte) values[i];
        }
        return addr;
    }

    /** dc.w v,v,... */
    public static int dcW(int... values) {
        int addr = alloc(values.length * 2);
        for (int i = 0; i < values.length; i++) {
            ww(addr + i * 2, values[i]);
        }
        return addr;
    }

    /** dc.l v,v,... */
    public static int dcL(int... values) {
        int addr = alloc(values.length * 4);
        for (int i = 0; i < values.length; i++) {
            wl(addr + i * 4, values[i]);
        }
        return addr;
    }

    /** dc.b "chaîne" — SANS terminateur (l'ASM n'en ajoute pas ; concaténer dcB(0) si besoin). */
    public static int dcStr(String s) {
        byte[] b = s.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        int addr = alloc(b.length);
        System.arraycopy(b, 0, RAM, addr, b.length);
        return addr;
    }

    /** dcb.b count,value — répète value sur count octets. */
    public static int dcbB(int count, int value) {
        int addr = alloc(count);
        java.util.Arrays.fill(RAM, addr, addr + count, (byte) value);
        return addr;
    }

    /** Lit une chaîne C (terminée par 0) — pour les noms de fichiers, etc. */
    public static String cstr(int addr) {
        int end = addr;
        while (RAM[end] != 0) {
            end++;
        }
        return new String(RAM, addr, end - addr, java.nio.charset.StandardCharsets.ISO_8859_1);
    }
}
