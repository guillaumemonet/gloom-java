package gloom;

import java.io.IOException;

/**
 * Sous-système 04b — Chargeur de fichiers (gloom.s:11869-11968).
 *
 * Traduction littérale de loadfile/loadfileabs/loadfile_/loadit. C'est la passerelle
 * d'accès aux données du jeu à l'exécution (maps, textures, objets, sons).
 *
 * Portage : dos.library Open/Read/Seek/Close → Mem + gloom.Assets (lecture BRUTE, depuis les
 * disquettes) ; Exec via gloom.Alloc.allocmem2_. Le chemin de **décrunch** (signatures
 * `CrM2`/`CrM!`) est reproduit : le fichier compacté est chargé en Mem puis décompacté par
 * gloom.Decrunch (portage de `decrm.s`). Sur les disquettes Deluxe, presque tout est compacté.
 */
public final class Files {

    private static final int CRM2 = ('C' << 24) | ('r' << 16) | ('M' << 8) | '2'; // 'CrM2'
    private static final int CRMB = ('C' << 24) | ('r' << 16) | ('M' << 8) | '!'; // 'CrM!'

    private Files() {
    }

    /** loadfile (gloom.s:11872) : a0=name, d1=memtype → d0=pointeur (0 si échec). Alloue. */
    public static int loadfile(int nameAddr, int memtype) {
        Mem.wl(Vars.loadmem, 0);                      // clr.l loadmem
        return loadfile_(nameAddr, memtype);
    }

    /** loadfileabs (gloom.s:11869) : charge à une adresse fixe a1 (pas d'allocation). */
    public static int loadfileabs(int nameAddr, int a1, int memtype) {
        Mem.wl(Vars.loadmem, a1);                     // move.l a1,loadmem
        return loadfile_(nameAddr, memtype);
    }

    /** loadfile_ (gloom.s:11874) : a0=name, d1=memtype. */
    public static int loadfile_(int nameAddr, int d1) {
        int d5 = d1;                                  // move.l d1,d5  ; save memtype
        String name = Mem.cstr(nameAddr);             // (dos Open -30)
        byte[] data;
        try {
            data = Assets.readRaw(name);              // open + seek-to-end + read (brut : on décrunche ici)
        } catch (IOException e) {
            return 0;                                 // .err : ouverture impossible → d0=0
        }

        // Détection de crunch (en-tête de 14 octets ; signature 4 octets CrM2/CrM!).
        if (data.length >= 14) {
            int sig = ((data[0] & 0xff) << 24) | ((data[1] & 0xff) << 16)
                    | ((data[2] & 0xff) << 8) | (data[3] & 0xff);
            if (sig == CRM2 || sig == CRMB) {                                // .crunch
                int destLen = ((data[6] & 0xff) << 24) | ((data[7] & 0xff) << 16)
                            | ((data[8] & 0xff) << 8) | (data[9] & 0xff);
                int d4 = data.length;                 // longueur du fichier
                if (Integer.compareUnsigned(d4, destLen) < 0) d4 = destLen;  // cmp.l fileheader+6,d4 ; bcc .skip
                int d6 = 14 + (((data[4] & 0xff) << 8) | (data[5] & 0xff));  // moveq #14,d6 ; add fileheader+4,d6
                int src = loadit(data, d4, d5, d6);   // bsr loadit → d0 = src
                int dest = src + d6;                  // add.l d6,d0 ; move.l d0,a1
                Decrunch.decrunchTo(src, dest);       // jsr decrm+32 (sur place)
                return dest;                          // = bloc alloué par allocmem2
            }
        }

        // .nocrunch → loadit avec d6=0
        return loadit(data, data.length, d5, 0);
    }

    /** loadit (gloom.s:11948) : alloue (d4=len, d5=memtype, offset d6) ou utilise loadmem, lit, renvoie base. */
    private static int loadit(byte[] data, int d4, int d5, int d6) {
        // d4 = longueur à allouer (lecture : le fichier entier, ≤ d4)
        int dest = Mem.l(Vars.loadmem);               // move.l loadmem,d0 ; bne .noalloc
        if (dest == 0) {
            dest = Alloc.allocmem2_(d4, d5, d6, 0);   // allocmem2 loadfile
            dest -= d6;                               // sub.l d6,d0
        }
        Mem.load(dest, data);                         // read d4 bytes
        return dest;                                  // (close)
    }
}
