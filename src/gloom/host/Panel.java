package gloom.host;

import gloom.Defs;
import gloom.Mem;
import gloom.Mess;

import java.util.Locale;

/**
 * Panneau de statut de Gloom Deluxe : bande 320×16 sous la vue, composée à partir des 50 images de
 * {@code misc/smallfont2.bin} (cf. rebirth-docs/gloom-java/docs/DELUXE_HUD_GUN.md §2).
 *
 * <ul>
 *   <li>fond : image 48 ;</li>
 *   <li>{@code showstats} (L_000f20) : vie, vies, arme × puissance, jauge mega ;</li>
 *   <li>défileur (L_000d8a) : les messages du jeu défilent au centre du panneau, au lieu de
 *       s'afficher sur la vue comme dans gloom.s.</li>
 * </ul>
 *
 * L'original ne redessine le panneau que quand les stats changent ; ici il est recomposé à chaque
 * image (même résultat à l'écran, et le framebuffer du portage est unique).
 *
 * Absent (fichier manquant, ou panneau GloomAmiga à 48 images en mode dossier) → {@link #available}
 * est faux et le jeu garde le HUD classique ({@link Hud}) sur une vue pleine hauteur.
 */
public final class Panel {

    /** Hauteur du panneau (image 48). */
    public static final int HEIGHT = 16;

    private static Anim anim;
    private static boolean tried;

    // défileur (L_000d3e / L_000d8a) : texte, position ($ae), fin ($ac = longueur + 32)
    private static String ticker = "";
    private static int pos, end;
    private static int seenSeq = -1;

    private Panel() {
    }

    /** true si le panneau Deluxe est disponible (smallfont2.bin à 50 images). */
    public static boolean available() {
        if (!tried) {
            tried = true;
            Anim a = Anim.load("misc/smallfont2.bin");
            anim = (a != null && a.frames() == 50 && a.w[48] == 320 && a.h[48] == HEIGHT) ? a : null;
        }
        return anim != null;
    }

    /** Oublie le message en cours (nouvelle partie / nouveau niveau). */
    public static void reset() {
        ticker = "";
        pos = 0;
        seenSeq = Mess.seq();
    }

    /**
     * Une passe logique : prend un nouveau message s'il y en a un (message → L_000d3e : $ae = 1,
     * $ac = longueur + 32) et fait avancer le défilement d'un caractère (L_000d8a : $ae++).
     */
    public static void tick() {
        if (Mess.seq() != seenSeq) {
            seenSeq = Mess.seq();
            ticker = Mess.lastText().toLowerCase(Locale.ROOT);
            pos = 1;
            end = ticker.length() + 32;
        } else if (pos != 0) {
            pos++;
            if (pos >= end) pos = 0;
        }
    }

    /** Dessine le panneau dans les {@link #HEIGHT} dernières lignes du framebuffer (fb, W×H). */
    public static void draw(int fb, int W, int H, int player) {
        draw(fb, W, 0, H, player);
    }

    /**
     * Idem, panneau (320 de large) posé à partir de la colonne {@code x0} d'un framebuffer de pas
     * {@code stride} (surimpression Rebirth, centrée sur un écran plus large).
     */
    public static void draw(int fb, int stride, int x0, int H, int player) {
        if (!available()) return;
        Panel.fb = fb; Panel.stride = stride; Panel.x0 = x0; Panel.y0 = H - HEIGHT; Panel.y1 = H;
        put(48, 0, 0);                                                // fond
        if (player != 0) showstats(player);
        if (pos != 0) drawTicker();
    }

    // cible du dessin en cours
    private static int fb, stride, x0, y0, y1;

    /** showstats Deluxe (L_000f20). */
    private static void showstats(int p) {
        // vie : un trait de 2 px par point à partir de x=267, couleur 45 → 46 (x=287) → 47 (x=303)
        int hp = Mem.w(p + Defs.ob_hitpoints);
        for (int i = 0, x = 267, f = 45; i < hp; i++, x += 2) {
            if (x == 287 || x == 303) f++;
            put(f, x, 4);
        }
        // vies : icône 44, de x = 211 + 9×(6−n), pas de 9
        int lives = Mem.w(p + Defs.ob_lives);
        for (int i = 0, x = 211 + 9 * (6 - lives); i < lives; i++, x += 9) put(44, x, 1);
        // arme : icône 39+arme répétée 6−reload fois (puissance), de x=55, pas de 11
        int n = 5 - Mem.b(p + Defs.ob_reload);
        if (n >= 0) {
            int f = 39 + Mem.w(p + Defs.ob_weapon);
            for (int i = 0, x = 55; i <= n; i++, x += 11) put(f, x, 3);
        }
        // mega : d = (ob_mega>>5)&~1 plafonné à 50 ; traits 46 de x=53−d, pas de 2
        int d = (Mem.uw(p + Defs.ob_mega) >> 5) & 0xfffe;
        if ((short) d > 0) {
            if (d > 50) d = 50;
            for (int i = 0, x = 53 - d; i < d / 2; i++, x += 2) put(46, x, 4);
        }
    }

    /** Défileur (L_000d8a) : fenêtre x ∈ [112, 208[, 6 px par caractère, y = 4. */
    private static void drawTicker() {
        put(49, 111, 4);                                // efface la zone (image 49)
        int x = 112, i = 0;
        int d0 = 16 - pos;
        if (d0 >= 0) x += d0 * 6;                                     // le texte entre par la droite
        else i = Math.min(ticker.length(), -d0);                      // on a déjà sauté -d0 caractères
        for (; i < ticker.length() && x < 208; i++, x += 6) {
            int g = glyph(ticker.charAt(i));
            if (g >= 0) put(g, x, 4);
        }
    }

    /** Correspondance caractère → image (L_000d8a). */
    private static int glyph(char c) {
        if (c == ' ') return -1;
        if (c == '!') return 36;
        if (c == '.') return 37;
        if (c == ':') return 38;
        if (c < 'a') return (c >= '0' && c <= '9') ? c - '0' : -1;
        return (c <= 'z') ? c - 'a' + 10 : -1;
    }

    /** Image f en (x, y) du panneau → framebuffer (découpé aux 320 colonnes et 16 lignes du panneau). */
    private static void put(int f, int x, int y) {
        anim.blit(fb, stride, Math.min(stride, x0 + 320), y0, y1, f, x0 + x, y0 + y);
    }
}
