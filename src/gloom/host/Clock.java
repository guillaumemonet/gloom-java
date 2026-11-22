package gloom.host;

/**
 * Cadence de la simulation : 50 pas par seconde, le balayage vertical PAL de l'Amiga (l'original
 * avance d'un pas par VBL et fait sa logique un pas sur deux, cf. LevelScene.tick).
 *
 * L'affichage, lui, suit la fréquence de l'écran (60, 144 Hz…) : à chaque image on demande
 * {@link #steps()} pas de jeu à exécuter (0, 1 ou plusieurs), si bien que la vitesse du jeu ne
 * dépend plus de l'écran. Avant, la boucle 2D faisait un pas par image : 2,9× trop vite à 144 Hz.
 */
public final class Clock {

    /** Pas de jeu par seconde (VBL PAL). */
    public static final double HZ = 50.0;
    /** Au-delà, on ne rattrape pas (fenêtre déplacée, pause du débogueur…). */
    private static final int MAX_STEPS = 6;

    private long last = -1;
    private double acc;

    /** Nombre de pas de jeu à exécuter maintenant (au premier appel : 1). */
    public int steps() {
        long now = System.nanoTime();
        if (last < 0) { last = now; return 1; }
        acc += Math.min((now - last) / 1e9, 0.25);
        last = now;
        int n = (int) (acc * HZ);
        acc -= n / HZ;
        if (n > MAX_STEPS) { n = MAX_STEPS; acc = 0; }
        return n;
    }

    /** Repart de zéro (après un écran de menu, un chargement…). */
    public void reset() {
        last = -1;
        acc = 0;
    }
}
