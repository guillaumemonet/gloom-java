package gloom.tools;

import gloom.Defs;
import gloom.Mem;
import gloom.Objects;
import gloom.Player;
import gloom.Vars;
import gloom.host.LevelScene;

/**
 * Harnais : mort du joueur. Vérifie la chaîne playerdie → playerdeath (caméra au sol, −1 vie) →
 * playerdead → waitrestart (respawn sur appui-feu) → playerlogic0 (invincibilité) → playerlogic,
 * et le game over (finished=2) quand les vies sont épuisées. `gradle deathTest`.
 */
public final class DeathTest {
    private static int failures = 0;

    public static void main(String[] args) {
        LevelScene scene = new LevelScene();
        scene.init(224, 160, System.getProperty("map", "com1_1"), System.getProperty("tile", "2"));
        int p = scene.player;
        Mem.ww(p + Defs.ob_cntrl, 0);                    // contrôleur 0 (joyx0)
        Mem.ww(p + Defs.ob_lives, 2);                    // 2 vies pour tester respawn + game over
        // point de spawn (normalement posé par Game.loadLevel)
        Mem.ww(Vars.p1x, Mem.w(p + Defs.ob_x));
        Mem.ww(Vars.p1z, Mem.w(p + Defs.ob_z));
        Mem.ww(Vars.p1r, Mem.w(p + Defs.ob_rot));
        int spawnX = Mem.w(Vars.p1x);

        // --- 1re mort -> doit décompter une vie et attendre le respawn ---
        Player.playerdie(p);
        checkTrue("playerdie → logique = playerdeath", Mem.l(p + Defs.ob_logic) == Objects.L_PLAYERDEATH);
        checkTrue("PV = 0 à la mort", Mem.w(p + Defs.ob_hitpoints) == 0);

        setFire(false);
        int frames = step(scene, p, Objects.L_WAITRESTART, 300);
        System.out.println("[info] mort→waitrestart en " + frames + " frames | vies=" + Mem.w(p + Defs.ob_lives));
        checkTrue("atteint waitrestart (attente respawn)", Mem.l(p + Defs.ob_logic) == Objects.L_WAITRESTART);
        checkTrue("une vie décomptée (2→1)", Mem.w(p + Defs.ob_lives) == 1);

        // --- appui-feu -> respawn ---
        setFire(true);
        Objects.obj_loop();                              // waitrestart voit le front montant
        System.out.println("[info] après feu : logique=" + Mem.l(p + Defs.ob_logic)
                + " PV=" + Mem.w(p + Defs.ob_hitpoints) + " x=" + Mem.w(p + Defs.ob_x));
        checkTrue("respawn → playerlogic0 (invincibilité)", Mem.l(p + Defs.ob_logic) == Objects.L_PLAYERLOGIC0);
        checkTrue("PV restaurés (25)", Mem.w(p + Defs.ob_hitpoints) == 25);
        checkTrue("repositionné au spawn", Mem.w(p + Defs.ob_x) == spawnX);
        checkTrue("invincible (colltype=0)", Mem.w(p + Defs.ob_colltype) == 0);

        // --- invincibilité expire -> redevient jouable et vulnérable ---
        setFire(false);
        int frames2 = step(scene, p, Objects.L_PLAYER, 200);
        System.out.println("[info] invincibilité " + frames2 + " frames | colltype=" + Mem.w(p + Defs.ob_colltype));
        checkTrue("redevient playerlogic après invincibilité", Mem.l(p + Defs.ob_logic) == Objects.L_PLAYER);
        checkTrue("colltype restauré (vulnérable)", Mem.w(p + Defs.ob_colltype) != 0);

        // --- 2e mort (dernière vie) -> game over ---
        Mem.ww(Vars.finished, 0);
        Player.playerdie(p);
        setFire(false);
        int frames3 = 0;
        while (Mem.w(Vars.finished) == 0 && frames3 < 300) { Objects.obj_loop(); frames3++; }
        System.out.println("[info] dernière vie : finished=" + Mem.w(Vars.finished)
                + " vies=" + Mem.w(p + Defs.ob_lives) + " en " + frames3 + " frames");
        checkTrue("plus de vies → finished=2 (game over)", Mem.w(Vars.finished) == 2);

        if (failures == 0) System.out.println("TOUT OK");
        else { System.out.println(failures + " ECHEC(S)"); System.exit(1); }
    }

    /** Fait tourner obj_loop jusqu'à ce que ob_logic == cible (ou timeout). Renvoie le nb de frames. */
    private static int step(LevelScene scene, int p, int targetLogic, int max) {
        int n = 0;
        while (Mem.l(p + Defs.ob_logic) != targetLogic && n < max) { Objects.obj_loop(); n++; }
        return n;
    }

    private static void setFire(boolean on) {
        Mem.ww(Vars.joyx0 + 4, on ? -1 : 0);             // joyb (feu) dans le buffer du contrôleur 0
    }

    private static void checkTrue(String name, boolean cond) {
        if (!cond) { System.out.println("ECHEC " + name); failures++; }
    }
}
