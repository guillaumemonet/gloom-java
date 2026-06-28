package gloom.tools;

import gloom.Defs;
import gloom.Mem;
import gloom.Vars;
import gloom.host.LevelScene;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

/**
 * Extrait les textures d'un niveau Gloom en PNG (résolution de base) pour servir d'ENTRÉE à
 * l'upscale IA → {@code hd_src/}. Le script {@code scripts/upscale-textures.py} les agrandit
 * ensuite vers {@code hd/}, où le moteur 3D (rebirth) les charge automatiquement.
 *
 * `gradle dumpTextures -Dmap=map1_3 -Dtile=1 -Dout=hd_src`
 *
 * Orientation : PNG ligne r = ligne Gloom r (sans flip). Le chargeur HD de rebirth utilise
 * flipY=true (défaut JME) → la ligne 0 du PNG devient le HAUT de la texture, comme le procédural.
 */
public final class TextureDump {

    public static void main(String[] args) throws IOException {
        String map = System.getProperty("map", "map1_3");
        String tile = System.getProperty("tile", "1");
        // sortie par ÉPISODE par défaut (ex. map1_3 → hd_src/map1) : les textures sont partagées par épisode.
        String episode = map.indexOf('_') > 0 ? map.substring(0, map.indexOf('_')) : map;
        String out = System.getProperty("out", "hd_src/" + episode);

        LevelScene scene = new LevelScene();
        scene.init(320, 240, map, tile);                 // charge map + textures + palette
        File dir = new File(out);
        dir.mkdirs();
        int rgbs = Mem.l(Vars.map_rgbs);

        Set<Integer> texNums = usedTextures();           // textures référencées par les zones
        texNums.addAll(animatedSlots());                 // + toutes les frames d'animation (sinon manquantes)

        int n = 0;
        for (int t : texNums) {
            int base = Mem.l(Vars.textures + t * 4);
            if (base == 0) continue;
            BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
            for (int col = 0; col < 64; col++) {
                int cb = base + col * 65 + 1;            // +1 : saute l'octet d'en-tête de colonne
                for (int row = 0; row < 64; row++)
                    img.setRGB(col, row, colorOf(rgbs, Mem.ub(cb + row)));
            }
            ImageIO.write(img, "png", new File(dir, "wall_" + t + ".png"));
            n++;
        }
        dumpTile(Mem.l(Vars.floor), rgbs, new File(dir, "floor.png"));
        dumpTile(Mem.l(Vars.roof), rgbs, new File(dir, "roof.png"));
        System.out.println("Dump : " + n + " textures de murs + sol/plafond → " + dir.getAbsolutePath());
    }

    /** Numéros de texture (zo_t) référencés par les zones de la grille (= ce que rebirth affiche). */
    private static Set<Integer> usedTextures() {
        int grid = Mem.l(Vars.map_grid), ppnt = Mem.l(Vars.map_ppnt), poly = Mem.l(Vars.map_poly);
        Set<Integer> tex = new TreeSet<>();
        for (int cell = 0; cell < 32 * 32; cell++) {
            int a0 = grid + cell * 8;
            int num = Mem.w(a0);
            if (num < 0) continue;
            int pp = ppnt + Mem.uw(a0 + 2) * 2;
            for (int i = 0; i <= num; i++) {
                int z = Mem.uw(pp); pp += 2;
                tex.add(Mem.ub(poly + z * Defs.zo_size + Defs.zo_t));
            }
        }
        return tex;
    }

    /** Slots des plages d'animation (doanims) : chaque frame est une entrée distincte de textures[]. */
    private static Set<Integer> animatedSlots() {
        Set<Integer> slots = new TreeSet<>();
        int a0 = Mem.l(Vars.map_anim);
        if (a0 == 0) return slots;
        while (true) {
            int frames = Mem.uw(a0); a0 += 2;
            if (frames == 0) break;
            int first = Mem.uw(a0); a0 += 6;             // [premier(2)][delai(2)][compteur(2)]
            for (int k = 0; k < frames; k++) slots.add(first + k);
        }
        return slots;
    }

    private static void dumpTile(int base, int rgbs, File f) throws IOException {
        if (base == 0) return;
        BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 128; x++)
            for (int z = 0; z < 128; z++)
                img.setRGB(x, z, colorOf(rgbs, Mem.ub(base + x * 128 + z)));
        ImageIO.write(img, "png", f);
    }

    /** $0RGB (12 bits) → RGB 24 bits (chaque nibble ×17), comme le rendu. */
    private static int colorOf(int rgbs, int idx) {
        int c = Mem.uw(rgbs + idx * 2) & 0x0fff;
        return (((c >> 8) & 15) * 17 << 16) | (((c >> 4) & 15) * 17 << 8) | ((c & 15) * 17);
    }

    private TextureDump() {
    }
}
