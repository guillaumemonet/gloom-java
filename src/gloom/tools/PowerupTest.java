package gloom.tools;

import gloom.Defs;
import gloom.Mem;
import gloom.Objects;
import gloom.Vars;
import gloom.data.ObjInfo;
import gloom.host.LevelScene;

/**
 * Harnais : powerups. Spawne chaque pickup sur le joueur, exécute obj_loop, et vérifie que l'effet
 * est appliqué (thermo, invisibilité, bouncy, invincibilité/hyper, changement d'arme), que les
 * MESSAGES du jeu s'arment et s'effacent (message/printmess), que les objets au sol s'animent
 * (weaponlogic/bouncylogic) et que l'aspiration de la deathhead traîne le joueur (checksuck).
 * `gradle powerupTest`.
 */
public final class PowerupTest {
    private static int failures = 0;

    public static void main(String[] args) {
        LevelScene scene = new LevelScene();
        scene.init(224, 160, System.getProperty("map", "com1_1"), System.getProperty("tile", "2"));
        int p = scene.player;
        int px = Mem.w(p + Defs.ob_x), pz = Mem.w(p + Defs.ob_z);

        // thermo (type 4) → ob_thermo > 0
        Mem.ww(p + Defs.ob_thermo, 0);
        pickup(4, px, pz);
        System.out.println("[info] thermo = " + Mem.w(p + Defs.ob_thermo));
        checkTrue("thermo glasses → ob_thermo > 0", Mem.w(p + Defs.ob_thermo) > 0);

        // invisibilité (type 6) → ob_invisible > 0
        Mem.ww(p + Defs.ob_invisible, 0);
        pickup(6, px, pz);
        System.out.println("[info] invisible = " + Mem.w(p + Defs.ob_invisible));
        checkTrue("invisibilité → ob_invisible > 0", Mem.w(p + Defs.ob_invisible) > 0);

        // bouncy (type 9) → ob_bouncecnt incrémenté
        Mem.ww(p + Defs.ob_bouncecnt, 0);
        pickup(9, px, pz);
        System.out.println("[info] bouncecnt = " + Mem.w(p + Defs.ob_bouncecnt));
        checkTrue("balles rebondissantes → ob_bouncecnt = 1", Mem.w(p + Defs.ob_bouncecnt) == 1);

        // invincibilité/hyper (type 7) → ob_hyper != 0
        Mem.ww(p + Defs.ob_hyper, 0);
        pickup(7, px, pz);
        System.out.println("[info] hyper = " + Mem.w(p + Defs.ob_hyper));
        checkTrue("invincibilité → ob_hyper activé", Mem.w(p + Defs.ob_hyper) != 0);

        // arme (type 16 = weapon1) → ob_weapon change
        Mem.ww(p + Defs.ob_weapon, 3);
        pickup(16, px, pz);
        System.out.println("[info] weapon = " + Mem.w(p + Defs.ob_weapon) + " (était 3)");
        checkTrue("pickup d'arme → ob_weapon change", Mem.w(p + Defs.ob_weapon) != 3);


        // ---- messages (message/printmess, gloom.s:1330/1360) --------------------------------
        // Chaque ramassage arme ob_messtimer à -127 ; Mess.current (= le bloc message de
        // drawscene) le nie et rend le texte, puis playertimers le décompte de 2 en 2.
        Mem.ww(p + Defs.ob_messtimer, 0);
        Mem.ww(p + Defs.ob_thermo, 0);
        pickup(4, px, pz);
        int armed = Mem.w(p + Defs.ob_messtimer);
        String shown = gloom.Mess.current(p);
        System.out.println("[info] message : timer armé = " + armed + ", texte = « " + shown + " »"
                + ", timer après affichage = " + Mem.w(p + Defs.ob_messtimer));
        checkTrue("le ramassage arme le message (ob_messtimer = -127)", armed == -127);
        checkTrue("le message affiché est celui du thermo", shown.equals("got the thermo glasses!"));
        checkTrue("le timer devient positif après affichage", Mem.w(p + Defs.ob_messtimer) > 0);
        int frames = 0;
        while (!gloom.Mess.current(p).isEmpty() && frames < 200) { gloom.Player.playertimers(p); frames++; }
        System.out.println("[info] message effacé après " + frames + " frames de logique");
        checkTrue("le message s'efface tout seul (~64 frames)", frames >= 60 && frames <= 70);

        // ---- expiration d'un powerup : le message d'avertissement -----------------------------
        Mem.ww(p + Defs.ob_thermo, 2);                          // sur le point d'expirer
        Mem.ww(p + Defs.ob_messtimer, 0);
        gloom.Player.playertimers(p);                           // 2 → 1 : rien
        checkTrue("pas de message tant que le timer court", gloom.Mess.current(p).isEmpty());
        gloom.Player.playertimers(p);                           // 1 → 0 : message
        String out = gloom.Mess.current(p);
        System.out.println("[info] expiration thermo → « " + out + " »");
        checkTrue("l'expiration prévient le joueur", out.equals("thermo glasses out..."));

        // ---- arme au sol : flotte, tourne et scintille (weaponlogic, gloom.s:4382) ------------
        int w = spawn(16, px + 300, pz);                         // weapon1, hors de portée de ramassage
        checkTrue("arme au sol spawnée", w != 0);
        int y0 = Mem.w(w + Defs.ob_y), f0 = Mem.uw(w + Defs.ob_frame);
        boolean moved = false, spun = false;
        int sparks = 0;
        for (int i = 0; i < 40; i++) {
            Objects.weaponlogic(w);
            if (Mem.w(w + Defs.ob_y) != y0) moved = true;
            if (Mem.uw(w + Defs.ob_frame) != f0) spun = true;
            sparks = countLogic(Objects.L_SPARKS);
        }
        System.out.println("[info] arme au sol : flotte = " + moved + ", tourne = " + spun
                + ", étincelles = " + sparks);
        checkTrue("l'arme au sol flotte (ob_y oscille)", moved);
        checkTrue("l'arme au sol tourne (ob_frame avance)", spun);
        checkTrue("l'arme au sol scintille (étincelles sparkslogic)", sparks > 0);

        // ---- bonus rebond : pulse sur les frames 3,4,3,5 (bouncylogic, gloom.s:4739) ----------
        int b = spawn(9, px + 300, pz + 120);
        checkTrue("bonus rebond spawné", b != 0);
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (int i = 0; i < 16; i++) { Objects.bouncylogic(b); seen.add(Mem.w(b + Defs.ob_frame)); }
        System.out.println("[info] bonus rebond : frames vues = " + new java.util.TreeSet<>(seen));
        checkTrue("le bonus rebond pulse (frames 3,4,5)", seen.equals(java.util.Set.of(3, 4, 5)));

        // ---- checksuck (gloom.s:5092) : la deathhead traîne le joueur -------------------------
        Mem.wl(Vars.sucking, 0);
        int before = Mem.l(p + Defs.ob_x);
        gloom.Player.playerlogic(p);                            // pas d'aspiration → pas de traction
        Mem.wl(Vars.sucking, p);                                // c'est CE joueur qu'on aspire
        Mem.wl(Vars.suckangle, Mem.l(gloom.data.Tables.camrots) + 64 * 8);   // cap arbitraire
        int x0 = Mem.l(p + Defs.ob_x), z0 = Mem.l(p + Defs.ob_z);
        for (int i = 0; i < 8; i++) gloom.Player.playerlogic(p);
        long moved2 = Math.abs((long) Mem.l(p + Defs.ob_x) - x0) + Math.abs((long) Mem.l(p + Defs.ob_z) - z0);
        System.out.println("[info] aspiration : déplacement du joueur = " + moved2 + " (16.16)");
        checkTrue("la deathhead traîne le joueur (checksuck)", moved2 > 0);
        Mem.wl(Vars.sucking, 0);

        if (failures == 0) System.out.println("TOUT OK");
        else { System.out.println(failures + " ECHEC(S)"); System.exit(1); }
    }


    /** Spawne un objet de `type` à (x,z) SANS le ramasser (renvoie l'objet). */
    private static int spawn(int type, int x, int z) {
        Objects.loadanobj(type);
        Mem.wl(ObjInfo.dummy, 0);
        int ev = Mem.alloc(12);
        Mem.ww(ev, type);
        Mem.ww(ev + 2, x); Mem.ww(ev + 4, 0); Mem.ww(ev + 6, z); Mem.ww(ev + 8, 0);
        Objects.exec_addobj(ev);
        return Mem.l(ObjInfo.dummy);
    }

    private static int countLogic(int logicId) {
        int n = 0, o = Mem.l(Vars.objects);
        while (Mem.l(o) != 0) { if (Mem.l(o + Defs.ob_logic) == logicId) n++; o = Mem.l(o); }
        return n;
    }

    /** Spawne un pickup de `type` à (x,z) et exécute une frame de logique (ramassage). */
    private static void pickup(int type, int x, int z) {
        Objects.loadanobj(type);
        Mem.wl(ObjInfo.dummy, 0);
        int ev = Mem.alloc(12);
        Mem.ww(ev, type);
        Mem.ww(ev + 2, x); Mem.ww(ev + 4, 0); Mem.ww(ev + 6, z); Mem.ww(ev + 8, 0);
        Objects.exec_addobj(ev);
        Mem.wl(scenePlayerWashit(), 0);     // évite le verrou « déjà touché »
        Objects.obj_loop();
    }

    // le joueur est player1 ; on remet ob_washit à 0 pour autoriser la collision
    private static int scenePlayerWashit() {
        return Mem.l(ObjInfo.player1) + Defs.ob_washit;
    }

    private static void checkTrue(String name, boolean cond) {
        if (!cond) { System.out.println("ECHEC " + name); failures++; }
    }
}
