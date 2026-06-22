package gloom;

/**
 * Sous-système 04b — Chargement de map & textures.
 *
 * Traduction littérale de gloom.s :
 *  - initmap (gloom.s:12863) : résout les pointeurs d'en-tête du blob map ;
 *  - addpal (gloom.s:13007) : accumule la palette globale (map_rgbs) et construit la
 *    table de remap couleur (maptable) d'une texture ;
 *  - remap (gloom.s:12991) : applique maptable à un bloc de pixels ;
 *  - loadtxts (gloom.s:12910) : charge les 8 écrans de texture du niveau, fait le
 *    mapping couleur, et remplit la table `textures` (8×20 colonnes 64×65).
 *
 * Consomme gloom.Files (loadfile), gloom.Vars (map_*, textures, maptable),
 * gloom.Alloc. Format d'un fichier texture : [long: offset vers la palette]
 * [pixels : 20 textures de 64×65 octets][palette `pa` : numcols + couleurs].
 *
 * REPORTÉ : remapanim (gloom.s:12963, remap des sprites animés) ira avec le chargement
 * des objets ; freetxts avec la libération de niveau.
 */
public final class Map {

    /** Tampon scratch pour construire les noms "txts/<nom>" (équiv. .temp2/.temp). */
    private static final int scratch = Mem.alloc(96);

    private Map() {
    }

    /** initmap (gloom.s:12863) : résout les pointeurs d'en-tête depuis map_map. */
    public static void initmap() {
        int a0 = Mem.l(Vars.map_map);                 // move.l map_map,a0
        Mem.wl(Vars.map_grid, a0 + Mem.l(a0));        // map_grid = a0 + (a0)
        Mem.wl(Vars.map_poly, a0 + Mem.l(a0 + 4));    // map_poly = a0 + 4(a0)
        Mem.wl(Vars.map_ppnt, a0 + Mem.l(a0 + 8));    // map_ppnt = a0 + 8(a0)
        Mem.wl(Vars.map_anim, a0 + Mem.l(a0 + 12));   // map_anim = a0 + 12(a0)
        Mem.wl(Vars.map_txts, a0 + Mem.l(a0 + 16));   // map_txts = a0 + 16(a0)
        int d0 = Mem.l(a0);                           // move.l (a0),d0
        d0 -= 25 * 4;                                 // sub.l #25*4,d0
        d0 += a0;                                      // add.l a0,d0
        Mem.wl(Vars.map_events, d0);                  // map_events = a0 + ((a0)-100)
        Mem.wl(Vars.map_rgbsat, Mem.l(Vars.map_rgbsfrom2)); // map_rgbsat = map_rgbsfrom2
    }

    /** remap (gloom.s:12991) : a0..a1 = bloc d'octets ; chaque pixel b → maptable[b]. */
    public static void remap(int a0, int a1) {
        int a2 = Mem.l(Vars.maptable);                // move.l maptable,a2
        while (Integer.compareUnsigned(a0, a1) < 0) { // cmp.l a1,a0 ; bcc .done
            int d0 = Mem.ub(a0);                      // move.b (a0),d0
            Mem.wb(a0, Mem.ub(a2 + d0));              // move.b 0(a2,d0),(a0)+
            a0++;
        }
    }

    /** addpal (gloom.s:13007) : ajoute la palette en a0 à la fin de map_rgbs, remplit maptable. */
    public static void addpal(int a0) {
        int d0 = Mem.uw(a0); a0 += 2;                 // move (a0)+,d0  ; numcols
        d0 -= 1;                                      // subq #1,d0
        int a2 = Mem.l(Vars.map_rgbsat);              // end of rgbs
        int a3 = Mem.l(Vars.maptable);

        Mem.wb(a3, 0);                                // clr.b (a3)  ; 0=0
        for (int k = 255; k >= 249; k--) {            // maptable[249..255] = identité (couleurs flag)
            Mem.wb(a3 + k, k);
        }

        int d2 = 1;                                   // moveq #1,d2  ; colour 1
        do {                                          // .loop
            int d1 = Mem.uw(a0); a0 += 2;             // move (a0)+,d1
            if ((short) d1 >= 0) {                    // bmi .next  (couleur inutilisée)
                int a1 = Mem.l(Vars.map_rgbs);
                boolean found = false;
                while (Integer.compareUnsigned(a1, a2) < 0) { // .loop2 cmp.l a2,a1 ; bcc .no
                    int col = Mem.uw(a1); a1 += 2;    // cmp (a1)+,d1
                    if (col == d1) { found = true; break; } // beq .yes
                }
                if (!found) {                         // .no : la couleur n'existe pas → append
                    Mem.ww(a2, d1); a2 += 2;          // move d1,(a2)+
                    a1 = a2;                          // move.l a2,a1
                }
                // .yes
                a1 -= 2;                              // subq #2,a1
                int idx = a1 - Mem.l(Vars.map_rgbs);  // sub.l map_rgbs,a1
                idx = (idx & 0xffff) >>> 1;           // lsr #1,d1  ; real colour (.w)
                if (idx >= 256) {                     // cmp #256,d1 ; bcs .ok
                    System.err.println("[addpal] AVERTISSEMENT >256 couleurs (warn #$00f)");
                }
                Mem.wb(a3 + d2, idx);                 // move.b d1,0(a3,d2)
            }
            d2++;                                     // .next addq #1,d2
        } while (d0-- != 0);                          // dbf d0,.loop

        Mem.wl(Vars.map_rgbsat, a2);                  // move.l a2,map_rgbsat
    }

    /** loadtxts (gloom.s:12910) : charge les 8 écrans de texture du niveau. */
    public static void loadtxts() {
        int a4 = Vars.textures;                       // lea textures,a4
        int a5 = Mem.l(Vars.map_txts);                // texture names
        int a6 = Vars.textscrns;                      // lea textscrns,a6
        int d7 = 7;                                   // moveq #7,d7
        do {                                          // .ltl
            // copie du nom (terminé par 0) ; a5 avance au-delà du NUL
            int start = a5;
            while (Mem.ub(a5) != 0) a5++;
            String name = Mem.cstr(start);
            a5++;                                     // saute le NUL

            int d0;
            if (name.isEmpty()) {                     // cmp.l #.temp+1,a0 ; beq .notext
                d0 = 0;
            } else {
                int nameAddr = putScratch("txts/" + name); // .temp2 = "txts/"+name
                d0 = Files.loadfile(nameAddr, 1);     // moveq #1,d1 ; jsr loadfile
            }
            Mem.wl(a6, d0); a6 += 4;                  // move.l d0,(a6)+  ; textscrns[i]

            if (d0 != 0) {                            // beq .skip
                int a0 = d0 + Mem.l(d0);              // add.l (a0),a0  ; palette
                int a2 = a0;                          // move.l a0,a2
                addpal(a0);                           // bsr addpal

                remap(d0 + 4, a2);                    // a0=d0+4 (pixels), a1=a2 (palette) ; bsr remap

                a0 = d0 + 4;                          // pixels
                int d1 = 64 * 65;                     // move.l #64*65,d1
                int cnt = 19;                         // moveq #19,d0
                do {                                  // .mtxt
                    Mem.wl(a4, a0); a4 += 4;          // move.l a0,(a4)+
                    a0 += d1;                         // add.l d1,a0
                } while (cnt-- != 0);                 // dbf d0,.mtxt  (20 colonnes)
            }
            // .skip : a4 n'avance PAS si la texture est absente (fidèle à l'original)
        } while (d7-- != 0);                          // dbf d7,.ltl  (8 écrans)
    }

    /** Écrit s + NUL dans le tampon scratch et renvoie son adresse. */
    private static int putScratch(String s) {
        byte[] b = s.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        Mem.load(scratch, b);
        Mem.wb(scratch + b.length, 0);
        return scratch;
    }
}
