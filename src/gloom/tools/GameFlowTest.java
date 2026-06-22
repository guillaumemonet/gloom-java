package gloom.tools;

import gloom.Defs;
import gloom.Mem;
import gloom.Vars;
import gloom.host.Game;

/**
 * Harnais 10 : flot de jeu (script → écrans d'histoire + enchaînement des niveaux). Vérifie le
 * démarrage par un écran d'histoire, le passage aux niveaux, l'avancement sur `finished=3` (avec
 * report des stats), et la fin sur `finished=2`. Headless. `gradle gameFlowTest`.
 */
public final class GameFlowTest {
    private static int failures = 0;
    private static final int W = 224, H = 160;

    public static void main(String[] args) {
        Game game = new Game();
        game.boot(W, H);
        System.out.println("[info] phase de départ : " + game.phase);
        checkTrue("démarre par un écran d'histoire (intro)", game.phase == Game.Phase.STORY);

        toPlaying(game);
        System.out.println("[info] 1er niveau : " + game.currentMap + " phase=" + game.phase);
        checkTrue("1er niveau = map1_1", "map1_1".equals(game.currentMap));
        checkTrue("en jeu (PLAYING)", game.phase == Game.Phase.PLAYING);
        int p = game.scene.player;
        checkTrue("PV de départ = 25", Mem.w(p + Defs.ob_hitpoints) == 25);

        // fin de niveau (finished=3) → écran d'histoire → niveau suivant, stats reportées
        Mem.ww(p + Defs.ob_weapon, 2);
        Mem.ww(p + Defs.ob_hitpoints, 17);
        Mem.ww(Vars.finished, 3);
        game.update(false);                                  // détecte finished → écran d'histoire
        System.out.println("[info] après niveau : phase=" + game.phase);
        checkTrue("après un niveau → écran d'histoire", game.phase == Game.Phase.STORY);
        toPlaying(game);
        System.out.println("[info] niveau suivant : " + game.currentMap);
        checkTrue("avance au 2e niveau (map1_2)", "map1_2".equals(game.currentMap));
        int p2 = game.scene.player;
        checkTrue("PV reportés (≤17, pas réinitialisés à 25)", Mem.w(p2 + Defs.ob_hitpoints) <= 17
                && Mem.w(p2 + Defs.ob_hitpoints) > 0);
        checkTrue("arme reportée (2)", Mem.w(p2 + Defs.ob_weapon) == 2);

        // enchaîne quelques niveaux
        for (String exp : new String[]{"map1_3", "map1_4"}) {
            Mem.ww(Vars.finished, 3);
            game.update(false);
            toPlaying(game);
            checkTrue("enchaînement → " + exp, exp.equals(game.currentMap));
        }

        // mort (finished=2) → fin de partie
        Mem.ww(Vars.finished, 2);
        game.update(false);
        System.out.println("[info] fin : phase=" + game.phase + " raison=" + game.endReason);
        checkTrue("finished=2 → fin de partie (OVER)", game.phase == Game.Phase.OVER && game.over);

        // restart → repart de l'intro puis map1_1
        game.restart();
        toPlaying(game);
        checkTrue("restart → retour à map1_1", "map1_1".equals(game.currentMap));

        if (failures == 0) System.out.println("TOUT OK");
        else { System.out.println(failures + " ECHEC(S)"); System.exit(1); }
    }

    /** Avance à travers les écrans d'histoire (simule appui/relâché du feu) jusqu'à PLAYING/OVER. */
    private static void toPlaying(Game g) {
        int guard = 0;
        while (g.phase == Game.Phase.STORY && guard++ < 30) {
            g.update(false);                                 // relâché
            g.update(true);                                  // appui (front montant) → écran suivant
        }
    }

    private static void checkTrue(String name, boolean cond) {
        if (!cond) { System.out.println("ECHEC " + name); failures++; }
    }
}
