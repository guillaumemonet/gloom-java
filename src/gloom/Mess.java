package gloom;

/**
 * message / printmess (gloom.s:1330 / 1360) — le bandeau de texte du jeu : « health bonus! »,
 * « new weapon! », « hyper! », et surtout les avertissements d'expiration (« thermo glasses
 * out... »), seul indice donné au joueur qu'un powerup va s'arrêter.
 *
 * L'original exploite l'adresse de retour du {@code bsr message} : la chaîne est écrite EN LIGNE
 * juste après l'appel, {@code ob_mess} la pointe et le {@code rts} saute par-dessus. Sans
 * équivalent en Java, le texte est gardé ici (un seul message à la fois, comme l'original qui n'en
 * affiche qu'un) et {@code ob_mess} reste nul ; en revanche le PROTOCOLE DE TIMER, qui est ce qui
 * pilote réellement l'affichage, est reproduit fidèlement :
 *
 * <ul>
 *   <li>{@link #message} pose {@code ob_messtimer = -127} — négatif = « à afficher » ;</li>
 *   <li>le bloc message de {@code drawscene} (gloom.s:2693), ici {@link #current}, incrémente ce
 *       timer négatif : s'il atteint 0 le message est terminé, sinon il le NIE (→ +127) et le
 *       texte est dessiné ;</li>
 *   <li>{@code playertimers} le décompte ensuite DE 2 EN 2 — et comme 127 est IMPAIR la suite
 *       atterrit sur -1, ce qui rebascule dans la branche négative et efface le message. Durée :
 *       64 frames de logique, soit environ 2 secondes.</li>
 * </ul>
 */
public final class Mess {

    private static String text = "";

    private Mess() {
    }

    /** message (gloom.s:1330) : arme l'affichage de {@code s} pour l'objet a5 (le joueur). */
    public static void message(int a5, String s) {
        if (a5 == 0) return;
        text = s;
        Mem.ww(a5 + Defs.ob_messlen, s.length());
        Mem.ww(a5 + Defs.ob_messtimer, -127);
    }

    /**
     * Bloc message de drawscene (gloom.s:2693) — à appeler UNE FOIS par frame rendue. Renvoie le
     * texte à afficher (centré, à un quart de la hauteur, cf. printmess) ou "" s'il n'y a rien.
     */
    public static String current(int a5) {
        if (a5 == 0) return "";
        int t = Mem.w(a5 + Defs.ob_messtimer);
        if (t >= 0) return t > 0 ? text : "";                 // bpl .mskip : positif = déjà affiché
        if (t + 1 == 0) {                                     // addq #1 ; beq .mdone : terminé
            Mem.ww(a5 + Defs.ob_messtimer, 0);
            text = "";
            return "";
        }
        Mem.ww(a5 + Defs.ob_messtimer, M68k.negw(t));         // neg ob_messtimer → devient positif
        return text;
    }

    /** Oublie le message courant (changement de niveau / nouvelle partie). */
    public static void clear() {
        text = "";
    }
}
