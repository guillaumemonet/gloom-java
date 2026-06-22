package gloom.tools;

import gloom.Defs;
import gloom.Mem;
import gloom.Player;
import gloom.Vars;
import gloom.host.LevelScene;

/**
 * Harnais 08e : événements de zone (checkevent). Scanne com1_1 pour une zone-trigger, place le
 * joueur dedans, exécute checkevent, et vérifie que l'événement s'est déclenché (zone neutralisée,
 * + porte/objet créé selon le programme). `gradle gameEventTest`.
 */
public final class GameEventTest {
    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        // com1_1 n'a aucune zone-trigger ; map1_2 en a 14 (ev=2 → spawn d'objets).
        LevelScene scene = new LevelScene();
        scene.init(224, 160, System.getProperty("map", "map1_2"), System.getProperty("tile", "2"));
        int p = scene.player;

        // --- scan des zones-trigger (zo_ev >= 0) ---
        int polyBase = Mem.l(Vars.map_poly);
        int polyEnd = Mem.l(Vars.map_ppnt);
        int trig = 0, firstZone = 0, firstEv = -1;
        for (int z = polyBase; Integer.compareUnsigned(z, polyEnd) < 0; z += 32) {
            int ev = Mem.w(z + Defs.zo_ev);
            if (ev > 0) {
                trig++;
                if (firstZone == 0 && ev < 19) { firstZone = z; firstEv = ev; }
            }
        }
        System.out.println("[info] zones-trigger (zo_ev>0) : " + trig
                + " | 1re zone <19 : ev=" + firstEv);
        checkTrue("com1_1 a au moins une zone-trigger ev<19", firstZone != 0);
        if (firstZone == 0) { finish(); return; }

        // programme de cet événement (opcodes)
        int evprog = Mem.l(Vars.map_map) + Mem.l(Mem.l(Vars.map_events) + firstEv * 4);
        StringBuilder sb = new StringBuilder("[info] programme ev=" + firstEv + " : opcodes ");
        for (int a6 = evprog, guard = 0; guard < 12; guard++) {
            int op = Mem.uw(a6);
            sb.append(op).append(' ');
            if (op == 0) break;
            // saute les arguments selon l'opcode (longueurs connues)
            a6 += 2 + argLen(op, a6 + 2);
        }
        System.out.println(sb);

        // --- place le joueur au milieu du segment de la zone et déclenche ---
        int midx = (Mem.w(firstZone + Defs.zo_lx) + Mem.w(firstZone + Defs.zo_rx)) / 2;
        int midz = (Mem.w(firstZone + Defs.zo_lz) + Mem.w(firstZone + Defs.zo_rz)) / 2;
        Mem.wl(p + Defs.ob_x, midx << 16);
        Mem.wl(p + Defs.ob_z, midz << 16);
        Player.d6 = midx << 16;
        Player.d7 = midz << 16;

        int doorsBefore = listSize(Vars.doors);
        int objsBefore = listSize(Vars.objects);
        int rotsBefore = listSize(Vars.rotpolys);

        Player.checkevent(p);

        int evAfter = Mem.w(firstZone + Defs.zo_ev);
        System.out.println("[info] zo_ev : " + firstEv + " → " + evAfter
                + " | doors " + doorsBefore + "→" + listSize(Vars.doors)
                + " objs " + objsBefore + "→" + listSize(Vars.objects)
                + " rots " + rotsBefore + "→" + listSize(Vars.rotpolys));
        checkTrue("la zone-trigger est neutralisée (zo_ev négatif)", evAfter < 0);
        boolean somethingHappened = listSize(Vars.doors) > doorsBefore
                || listSize(Vars.objects) > objsBefore
                || listSize(Vars.rotpolys) > rotsBefore;
        checkTrue("l'événement a produit un effet (porte/objet/rotation)", somethingHappened);

        // --- re-déclenchement impossible (zone neutralisée) ---
        int objsNow = listSize(Vars.objects);
        Player.checkevent(p);
        checkTrue("pas de re-déclenchement (zone déjà neutralisée)", listSize(Vars.objects) == objsNow);

        // --- téléportation via playertimers (pixsizeadd → pixsize → déplacement) ---
        Mem.ww(Vars.finished2, 0); Mem.ww(Vars.finished, 0);
        Mem.ww(p + Defs.ob_pixsize, 0);
        Mem.ww(p + Defs.ob_pixsizeadd, 2);
        Mem.ww(p + Defs.ob_telex, 1000);
        Mem.ww(p + Defs.ob_telez, 1500);
        Mem.ww(p + Defs.ob_telerot, 64);
        for (int i = 0; i < 40 && Mem.w(p + Defs.ob_pixsizeadd) != 0; i++) Player.playertimers(p);
        int tx = (short) (Mem.l(p + Defs.ob_x) >> 16), tz = (short) (Mem.l(p + Defs.ob_z) >> 16);
        System.out.println("[info] téléport → (" + tx + "," + tz + ") rot=" + (Mem.uw(p + Defs.ob_rot) & 0xff)
                + " pixsize=" + Mem.w(p + Defs.ob_pixsize));
        checkTrue("téléport : position atteinte", tx == 1000 && tz == 1500);
        checkTrue("téléport : rotation appliquée", (Mem.uw(p + Defs.ob_rot) & 0xff) == 64);
        checkTrue("téléport : pixsize revenu à 0", Mem.w(p + Defs.ob_pixsize) == 0);

        // --- sortie de niveau (finished2=3 → finished quand pixsize atteint 24) ---
        Mem.ww(Vars.finished, 0); Mem.ww(Vars.finished2, 3);
        Mem.ww(p + Defs.ob_pixsize, 0); Mem.ww(p + Defs.ob_pixsizeadd, 1);
        for (int i = 0; i < 30 && Mem.w(Vars.finished) == 0; i++) Player.playertimers(p);
        System.out.println("[info] sortie : finished=" + Mem.w(Vars.finished));
        checkTrue("sortie de niveau : finished == finished2 (3)", Mem.w(Vars.finished) == 3);

        finish();
    }

    /** Longueur (octets) des arguments d'un opcode d'événement. */
    private static int argLen(int op, int argAddr) {
        return switch (op) {
            case 1 -> 10;           // addobj : type,x,y,z,rot
            case 2 -> 2;            // opendoor : door#
            case 3 -> 8;            // teleport : telex,lock,telez,telerot
            case 5 -> 4;            // changetxt : zone#, tex
            case 6 -> 8;            // rotpolys : poly,count,speed,flags
            case 4 -> {             // loadobjs : liste de mots jusqu'à un négatif
                int n = 0; while ((short) Mem.w(argAddr + n * 2) >= 0) n++;
                yield (n + 1) * 2;
            }
            default -> 0;
        };
    }

    private static int listSize(int header) {
        int n = 0, o = Mem.l(header);
        while (Mem.l(o) != 0) { n++; o = Mem.l(o); }
        return n;
    }

    private static void checkTrue(String name, boolean cond) {
        if (!cond) { System.out.println("ECHEC " + name); failures++; }
    }

    private static void finish() {
        if (failures == 0) System.out.println("TOUT OK");
        else { System.out.println(failures + " ECHEC(S)"); System.exit(1); }
    }
}
