package gloom.tools;

import gloom.Mem;
import gloom.Vars;
import gloom.data.Tables;
import gloom.host.LevelScene;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Diagnostic : décode le sprite bullet1 (après remap) via la palette globale, sur deux niveaux
 * consécutifs, pour vérifier que les balles ont la bonne palette (et restent correctes niveau 2).
 */
public final class WeaponPalTest {
    public static void main(String[] args) throws Exception {
        new LevelScene().init(224, 160, "com1_1", "2");
        dumpBullet("bullet_l1.png");
        new LevelScene().init(224, 160, "com2_1", "2");   // 2e niveau → chemin addpal-seul
        dumpBullet("bullet_l2.png");
        System.out.println("[info] bullet_l1.png / bullet_l2.png");
        System.out.println("TOUT OK");
    }

    private static void dumpBullet(String name) throws Exception {
        int anim = Tables.bullet1;
        int frame = anim + Mem.l(anim + 12);                 // frame 0 (cf. drawshape_1)
        int w = Mem.uw(frame + 4), h = Mem.uw(frame + 6);    // [xh][yh][w][h]
        int pix = frame + 8;
        int shadePal = Mem.l(Mem.l(Vars.palette) + 0 * 4);   // palettes[0]
        int scale = 6;
        BufferedImage img = new BufferedImage(w * scale, h * scale, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int idx = Mem.ub(pix + x * h + y);           // colonne-majeur (stride h)
                int c = Mem.uw(shadePal + idx * 2);
                int rr = ((c >> 8) & 15) * 17, gg = ((c >> 4) & 15) * 17, bb = (c & 15) * 17;
                int rgb = (rr << 16) | (gg << 8) | bb;
                for (int dy = 0; dy < scale; dy++)
                    for (int dx = 0; dx < scale; dx++)
                        img.setRGB(x * scale + dx, y * scale + dy, rgb);
            }
        ImageIO.write(img, "png", new File(name));
        System.out.println("[info] " + name + " : " + w + "x" + h);
    }
}
