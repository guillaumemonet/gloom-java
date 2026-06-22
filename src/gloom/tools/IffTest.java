package gloom.tools;

import gloom.Assets;
import gloom.Iff;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Diagnostic : décode l'image titre (title + title.pal) → title.png pour vérifier le pipeline IFF. */
public final class IffTest {
    public static void main(String[] args) throws Exception {
        int img = Assets.incbin("title");
        int pal = Assets.incbin("title.pal");
        Iff o = Iff.decode(img, pal);
        System.out.println("[info] title : " + o.width + "x" + o.height + " depth=" + o.depth);

        BufferedImage png = new BufferedImage(o.width, o.height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < o.height; y++)
            for (int x = 0; x < o.width; x++)
                png.setRGB(x, y, Iff.rgb24(o.rgb[y * o.width + x]));
        ImageIO.write(png, "png", new File("title.png"));
        System.out.println("[info] PNG : title.png");
        System.out.println("TOUT OK");
    }
}
