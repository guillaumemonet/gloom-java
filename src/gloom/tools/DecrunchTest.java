package gloom.tools;

import gloom.Decrunch;
import gloom.Files;
import gloom.Mem;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Harnais : décompression Crunch-Mania (CrM2). Décrunche `txts/roof1` (380 o → 16448 o) et le
 * décode en PNG via sa palette locale pour vérifier visuellement. `gradle decrunchTest`.
 */
public final class DecrunchTest {
    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        String name = "txts/roof1";
        int p = Files.loadfile(nameAddr(name), 1);
        System.out.println("[info] " + name + " → adresse " + p + " (crunché=" + true + ")");
        checkTrue("roof1 décrunché (adresse non nulle)", p != 0);
        if (p == 0) { finish(); return; }

        // roof1 décrunché = 128×128 pixels + palette locale (32 couleurs) à +16384.
        int pal = p + 128 * 128;
        // contrôle : la sortie n'est pas uniforme (décrunch a produit des données variées)
        java.util.HashSet<Integer> distinct = new java.util.HashSet<>();
        for (int i = 0; i < 128 * 128; i++) distinct.add(Mem.ub(p + i));
        System.out.println("[info] valeurs de pixel distinctes : " + distinct.size());
        checkTrue("données variées (pas un bloc uniforme)", distinct.size() > 4);

        int W = 128, H = 128;
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                int idx = Mem.ub(p + x * 128 + y);            // 128×128, colonne-majeur (cf. flat)
                int c = Mem.uw(pal + (idx & 31) * 2);         // $0RGB (palette locale 32 couleurs)
                int r = ((c >> 8) & 15) * 17, g = ((c >> 4) & 15) * 17, b = (c & 15) * 17;
                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        ImageIO.write(img, "png", new File("roof1.png"));
        System.out.println("[info] PNG : roof1.png");

        // re-décrunch déterministe : deux décrunchs donnent le même résultat
        int p2 = Files.loadfile(nameAddr(name), 1);
        boolean same = true;
        for (int i = 0; i < 16448 && same; i++) if (Mem.b(p + i) != Mem.b(p2 + i)) same = false;
        checkTrue("décrunch déterministe (2 passes identiques)", same);

        finish();
    }

    private static int nameAddr(String s) {
        int a = Mem.alloc(s.length() + 1);
        for (int i = 0; i < s.length(); i++) Mem.wb(a + i, s.charAt(i));
        Mem.wb(a + s.length(), 0);
        return a;
    }

    private static void checkTrue(String name, boolean cond) {
        if (!cond) { System.out.println("ECHEC " + name); failures++; }
    }

    private static void finish() {
        if (failures == 0) System.out.println("TOUT OK");
        else { System.out.println(failures + " ECHEC(S)"); System.exit(1); }
    }
}
