package gloom.host;

import gloom.Assets;
import gloom.Mem;

/**
 * Une animation au format Gloom (*anim*), lue côté hôte pour les surimpressions Deluxe
 * (panneau {@code misc/smallfont2.bin}, arme {@code misc/gun.bin}).
 *
 * Format : {@code rotshft.w frames.w maxw.w maxh.w palofs.l}, puis {@code frames<<rotshft}
 * offsets (longs, depuis le début du fichier). Image : {@code hx.w hy.w w.w h.w} puis w×h octets
 * PAR COLONNES. Pixel 0 = transparent, pixel p ≥ 1 = couleur p−1 de la palette (cf. addpal/remap).
 * Palette : {@code n.w} puis n mots RGB12.
 *
 * Le framebuffer du portage est en $0RGB : la palette s'y écrit directement, sans table de remap.
 */
public final class Anim {

    /** Marqueurs hors $0RGB pour une surimpression : pixel transparent, et ombre (noir à 50 %). */
    public static final int TRANSPARENT = 0x8000, SHADOW = 0x9000;

    public final int[] hx, hy, w, h;
    private final byte[][] px;
    /** rgb[p] = couleur $0RGB du pixel p (rgb[0] inutilisé : transparent) ; -1 = couleur inutilisée. */
    private final int[] rgb = new int[256];

    private Anim(byte[] b) {
        int rs = s16(b, 0), fr = s16(b, 2), palofs = s32(b, 8);
        int n = fr << rs;
        hx = new int[n]; hy = new int[n]; w = new int[n]; h = new int[n];
        px = new byte[n][];
        for (int i = 0; i < n; i++) {
            int o = s32(b, 12 + i * 4);
            hx[i] = s16(b, o); hy[i] = s16(b, o + 2); w[i] = s16(b, o + 4); h[i] = s16(b, o + 6);
            px[i] = java.util.Arrays.copyOfRange(b, o + 8, o + 8 + w[i] * h[i]);
        }
        java.util.Arrays.fill(rgb, -1);
        int nc = s16(b, palofs);
        for (int c = 0; c < nc && c < 255; c++) {
            int v = s16(b, palofs + 2 + c * 2);
            rgb[c + 1] = v < 0 ? -1 : (v & 0x0fff);
        }
    }

    /** Charge une animation depuis les données du jeu ; null si le fichier est absent ou illisible. */
    public static Anim load(String path) {
        try {
            return new Anim(Assets.read(path));
        } catch (Exception e) {
            return null;
        }
    }

    public int frames() {
        return px.length;
    }

    /**
     * Dessine l'image f, coin haut-gauche en (x, y), sans mise à l'échelle (drawchunky Deluxe :
     * les handles sont ignorés). Découpage au rectangle [0,clipW[×[clipY0,clipY1[.
     */
    public void blit(int fb, int stride, int clipW, int clipY0, int clipY1, int f, int x, int y) {
        int fw = w[f], fh = h[f];
        byte[] p = px[f];
        for (int c = 0; c < fw; c++) {
            int sx = x + c;
            if (sx < 0 || sx >= clipW) continue;
            for (int r = 0; r < fh; r++) {
                int v = p[c * fh + r] & 0xff;
                if (v == 0 || rgb[v] < 0) continue;
                int sy = y + r;
                if (sy < clipY0 || sy >= clipY1) continue;
                Mem.ww(fb + (sy * stride + sx) * 2, rgb[v]);
            }
        }
    }

    /**
     * Sprite projeté (L_004fe0 Deluxe) : image f placée en (X, Y, Z) dans l'espace caméra, échelle
     * {@code scale} (256 = 1). x_écran = midx + (X − hx·s)·F/Z, y_écran = midy + (Y − hy·s)·F/Z, taille
     * ×s·F/Z, F = {@code focal} (Deluxe : largeur/2). {@code darken} : au lieu de peindre, assombrit le
     * fond sous les pixels (rendu « invisible », drawobjinvs) ; sur un fond marqué transparent
     * ({@link #TRANSPARENT}, surimpression Rebirth) il écrit {@link #SHADOW}. Découpage à
     * [0,viewW[×[0,viewH[.
     */
    public void project(int fb, int stride, int viewW, int viewH, double focal, int f,
                        int X, int Y, int Z, int scale, boolean darken) {
        double s = scale / 256.0, F = focal;
        double k = s * F / Z;                                        // pixels écran par pixel image
        double x0 = viewW / 2.0 + (X - hx[f] * s) * F / Z;
        double y0 = viewH / 2.0 + (Y - hy[f] * s) * F / Z;
        int fw = w[f], fh = h[f];
        int sx0 = (int) Math.floor(x0), sy0 = (int) Math.floor(y0);
        int sx1 = (int) Math.ceil(x0 + fw * k), sy1 = (int) Math.ceil(y0 + fh * k);
        byte[] p = px[f];
        for (int sx = Math.max(0, sx0); sx < Math.min(viewW, sx1); sx++) {
            int c = (int) ((sx + 0.5 - x0) / k);
            if (c < 0 || c >= fw) continue;
            for (int sy = Math.max(0, sy0); sy < Math.min(viewH, sy1); sy++) {
                int r = (int) ((sy + 0.5 - y0) / k);
                if (r < 0 || r >= fh) continue;
                int v = p[c * fh + r] & 0xff;
                if (v == 0 || rgb[v] < 0) continue;
                int a = fb + (sy * stride + sx) * 2;
                if (!darken) Mem.ww(a, rgb[v]);
                else {
                    int old = Mem.uw(a);
                    Mem.ww(a, (old & TRANSPARENT) != 0 ? SHADOW : (old >> 1) & 0x777);
                }
            }
        }
    }

    private static int s16(byte[] b, int o) {
        return (short) (((b[o] & 0xff) << 8) | (b[o + 1] & 0xff));
    }

    private static int s32(byte[] b, int o) {
        return ((b[o] & 0xff) << 24) | ((b[o + 1] & 0xff) << 16) | ((b[o + 2] & 0xff) << 8) | (b[o + 3] & 0xff);
    }
}
