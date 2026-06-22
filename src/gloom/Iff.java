package gloom;

/**
 * Décodage des images « trimmed IFF » de Gloom (titre, écrans d'histoire, fonds de menu).
 *
 * Port de `decodeiff` (gloom.s:12439) : en-tête (largeur, hauteur, profondeur, +6 octets) puis
 * données ByteRun1 (PackBits) en **bitplans entrelacés** (style ILBM interleaved). On décode vers
 * un bitmap planaire, puis on convertit planaire → chunky → RGB via la palette `.pal` ($0RGB).
 *
 * L'affichage d'origine passe par le système de fenêtres/copper Amiga (makeiff/makewindow) ; côté
 * hôte on rend directement en RGB (divergence sanctionnée, comme le framebuffer chunky du jeu).
 */
public final class Iff {

    public int width, height, depth;
    public int[] rgb;            // image décodée, width*height, format 0xRGB (4 bits/canal, comme $0RGB)

    /** Décode l'image `img` (Mem) avec la palette `pal` (Mem, mots $0RGB). */
    public static Iff decode(int img, int pal) {
        Iff o = new Iff();
        int a0 = img;
        int w = Mem.uw(a0); a0 += 2;
        int h = Mem.uw(a0); a0 += 2;
        if (h > 240) h = 240;
        int depth = Mem.uw(a0); a0 += 2;
        a0 += 6;                                       // skip header
        int byteW = w >> 3;                            // octets par plan-ligne (sans le stride 40)
        int stride = 40;                               // d7 : stride entre plan-lignes (320/8)

        // 1) ByteRun1 → bitmap planaire entrelacé (stride*depth par ligne d'écran)
        int planar = Mem.alloc(stride * depth * h);
        int a1 = planar;
        for (int row = 0; row < h; row++) {            // .loop5
            for (int pl = 0; pl < depth; pl++) {       // .loop4
                int a2 = a1;
                int d4 = byteW;                        // octets restants dans la ligne
                while (d4 > 0) {                       // .loop
                    int d3 = Mem.b(a0); a0++;          // octet de contrôle (signé)
                    if (d3 >= 0) {                     // run littéral : d3+1 octets
                        d4 -= d3 + 1;
                        for (int i = 0; i <= d3; i++) { Mem.wb(a2, Mem.b(a0)); a2++; a0++; }
                    } else {                           // .repeat
                        if (d3 == -128) continue;      // no-op
                        d3 = -d3;                      // neg.b
                        d4 -= d3 + 1;
                        int v = Mem.b(a0); a0++;
                        for (int i = 0; i <= d3; i++) { Mem.wb(a2, v); a2++; }
                    }
                }
                a1 += stride;                          // plan-ligne suivante
            }
        }

        // 2) planaire → chunky → RGB
        int[] out = new int[w * h];
        int rowBytes = stride * depth;
        for (int y = 0; y < h; y++) {
            int rowBase = planar + y * rowBytes;
            for (int x = 0; x < w; x++) {
                int byteIdx = x >> 3, bit = 7 - (x & 7), idx = 0;
                for (int pl = 0; pl < depth; pl++) {
                    int b = Mem.ub(rowBase + pl * stride + byteIdx);
                    idx |= ((b >> bit) & 1) << pl;
                }
                // .pal : 4 octets/entrée (palette AGA = nibbles hauts | nibbles bas) ; on prend le
                // mot haut (12 bits $0RGB) pour le framebuffer 12 bits. (makeiff ne recopie la palette
                // de la fonte sur 0..15 que si une fonte est chargée — pas à l'écran-titre.)
                out[y * w + x] = Mem.uw(pal + idx * 4) & 0x0fff;
            }
        }

        o.width = w; o.height = h; o.depth = depth; o.rgb = out;
        return o;
    }

    /** Recopie l'image (mise à l'échelle, nearest) dans le framebuffer $0RGB fb (largeur fbW). */
    public void blitTo(int fb, int fbW, int fbH) {
        for (int y = 0; y < fbH; y++) {
            int sy = y * height / fbH;
            for (int x = 0; x < fbW; x++) {
                int sx = x * width / fbW;
                Mem.ww(fb + (y * fbW + x) * 2, rgb[sy * width + sx]);
            }
        }
    }

    /** Convertit un $0RGB (4 bits/canal) en 0xRRGGBB 8 bits/canal. */
    public static int rgb24(int rgb12) {
        int r = ((rgb12 >> 8) & 15) * 17, g = ((rgb12 >> 4) & 15) * 17, b = (rgb12 & 15) * 17;
        return (r << 16) | (g << 8) | b;
    }
}
