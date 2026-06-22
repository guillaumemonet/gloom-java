package gloom;

import java.io.IOException;

/**
 * Sous-système 04b — Chargeur de fichiers (gloom.s:11869-11968).
 *
 * Traduction littérale de loadfile/loadfileabs/loadfile_/loadit. C'est la passerelle
 * d'accès aux données du jeu à l'exécution (maps, textures, objets, sons).
 *
 * Portage : dos.library Open/Read/Seek/Close → Mem + gloom.Assets ; Exec via
 * gloom.Alloc.allocmem2_. Le chemin de **décrunch** (signatures `CrM2`/`CrM!` →
 * `decrm`) n'est PAS porté (decrm est du code relocatable, cf. ARCHITECTURE §7.2/§11) ;
 * il lève une exception. Les données du dépôt GloomAmiga ne sont pas crunchées.
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
            data = Assets.read(name);                 // open + seek-to-end + read
        } catch (IOException e) {
            return 0;                                 // .err : ouverture impossible → d0=0
        }

        // Détection de crunch (en-tête de 14 octets ; signature 4 octets CrM2/CrM!).
        if (data.length >= 14) {
            int sig = ((data[0] & 0xff) << 24) | ((data[1] & 0xff) << 16)
                    | ((data[2] & 0xff) << 8) | (data[3] & 0xff);
            if (sig == CRM2 || sig == CRMB) {
                // charge le fichier crunché en Mem puis décrunche (Crunch-Mania, cf. Decrunch).
                int raw = Mem.alloc(data.length);
                Mem.load(raw, data);
                return Decrunch.decrunch(raw);
            }
        }

        // .nocrunch → loadit avec d6=0
        return loadit(data, d5, 0);
    }

    /** loadit (gloom.s:11948) : alloue (d4=len, d5=memtype, offset d6) ou utilise loadmem, lit, renvoie base. */
    private static int loadit(byte[] data, int d5, int d6) {
        int d4 = data.length;                         // longueur à lire/allouer
        int dest = Mem.l(Vars.loadmem);               // move.l loadmem,d0 ; bne .noalloc
        if (dest == 0) {
            dest = Alloc.allocmem2_(d4, d5, d6, 0);   // allocmem2 loadfile
            dest -= d6;                               // sub.l d6,d0
        }
        Mem.load(dest, data);                         // read d4 bytes
        return dest;                                  // (close)
    }
}
