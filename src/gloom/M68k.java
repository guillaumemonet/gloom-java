package gloom;

/**
 * Idiomes 68k récurrents pour la traduction littérale.
 *
 * Un registre dN est un int Java. Les opérations .w/.b ne modifient que les
 * bits de poids faible du registre : utiliser setw/setb pour préserver le
 * reste, exactement comme le ferait le CPU.
 *
 * Copié à l'identique du portage AB3D2-TKG (ab3d2.M68k). Voir docs/ARCHITECTURE.md §2.
 */
public final class M68k {

    private M68k() {
    }

    /** swap dN : échange les deux mots du long. */
    public static int swap(int d) {
        return (d << 16) | (d >>> 16);
    }

    /** Écrit une valeur .w dans un registre sans toucher le mot fort (move.w v,dN). */
    public static int setw(int reg, int v) {
        return (reg & 0xFFFF0000) | (v & 0xFFFF);
    }

    /** Écrit une valeur .b dans un registre sans toucher les bits 8..31 (move.b v,dN). */
    public static int setb(int reg, int v) {
        return (reg & 0xFFFFFF00) | (v & 0xFF);
    }

    /** Mot faible signé d'un registre (lecture dN.w comme source signée). */
    public static int w(int reg) {
        return (short) reg;
    }

    /** Mot faible non signé d'un registre. */
    public static int uw(int reg) {
        return reg & 0xFFFF;
    }

    /** Octet faible signé d'un registre. */
    public static int b(int reg) {
        return (byte) reg;
    }

    /** Octet faible non signé d'un registre. */
    public static int ub(int reg) {
        return reg & 0xFF;
    }

    /** ext.w dN : étend l'octet faible en mot, mot fort préservé. */
    public static int extw(int reg) {
        return (reg & 0xFFFF0000) | (((byte) reg) & 0xFFFF);
    }

    /** ext.l dN : étend le mot faible en long. */
    public static int extl(int reg) {
        return (short) reg;
    }

    /** muls.w src,dN : produit signé 16x16 → 32 bits (remplace tout le registre). */
    public static int muls(int dN, int src) {
        return (short) dN * (short) src;
    }

    /** mulu.w src,dN : produit non signé 16x16 → 32 bits. */
    public static int mulu(int dN, int src) {
        return (dN & 0xFFFF) * (src & 0xFFFF);
    }

    /**
     * divs.w src,dN : division signée 32/16.
     * Résultat : quotient dans le mot faible, reste dans le mot fort
     * (le reste a le signe du dividende).
     * Comportement 68k reproduit : en cas d'overflow du quotient, le registre
     * destination reste INCHANGÉ (V positionné, pas d'exception) ; la division
     * par zéro, elle, déclenche une trap → ArithmeticException Java.
     */
    public static int divs(int dN, int src) {
        int divisor = (short) src;
        int q = dN / divisor; // ArithmeticException si divisor == 0, comme la trap 68k
        int r = dN % divisor;
        if (q < -32768 || q > 32767) {
            return dN; // overflow : opérande inchangée
        }
        return (r << 16) | (q & 0xFFFF);
    }

    /** divu.w src,dN : division non signée 32/16, mêmes règles que divs. */
    public static int divu(int dN, int src) {
        long dividend = dN & 0xFFFFFFFFL;
        int divisor = src & 0xFFFF;
        if (divisor == 0) {
            throw new ArithmeticException("divu par zéro");
        }
        long q = dividend / divisor;
        long r = dividend % divisor;
        if (q > 0xFFFF) {
            return dN; // overflow : opérande inchangée
        }
        return (int) ((r << 16) | q);
    }

    /** asr.w #n,dN : décalage arithmétique du mot faible seul, mot fort préservé. */
    public static int asrw(int reg, int n) {
        return setw(reg, ((short) reg) >> n);
    }

    /** lsr.w #n,dN : décalage logique du mot faible seul. */
    public static int lsrw(int reg, int n) {
        return setw(reg, (reg & 0xFFFF) >>> n);
    }

    /** lsl.w / asl.w #n,dN : décalage gauche du mot faible seul. */
    public static int lslw(int reg, int n) {
        return setw(reg, (reg & 0xFFFF) << n);
    }

    /** addq/add.w src,dN : addition sur le mot faible seul, mot fort préservé. */
    public static int addw(int reg, int v) {
        return setw(reg, reg + v);
    }

    /** sub.w src,dN : soustraction sur le mot faible seul. */
    public static int subw(int reg, int v) {
        return setw(reg, reg - v);
    }

    /** neg.w dN. */
    public static int negw(int reg) {
        return setw(reg, -(short) reg);
    }
}
