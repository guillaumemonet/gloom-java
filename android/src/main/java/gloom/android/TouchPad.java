package gloom.android;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;

/**
 * Commandes tactiles → les QUATRE entiers que le moteur attend
 * ({@code LevelScene.setInput(joyx, joyy, joyb, joys)}, port de {@code getcntrl}).
 *
 * C'est tout ce que le jeu lit en entrée, donc rien du gameplay n'est touché.
 *
 * <ul>
 *   <li><b>pouce gauche</b> — stick virtuel, qui se centre là où le doigt se pose : haut/bas
 *       avance et recule, gauche/droite TOURNE. Sert aussi à naviguer dans les menus ;</li>
 *   <li><b>pouce droit</b> — deux boutons de PAS DE CÔTÉ (◀ ▶) et le bouton TIR, qui valide aussi
 *       menus et écrans d'histoire ;</li>
 *   <li><b>haut-droite</b> — bouton MENU, l'équivalent de la touche Échap.</li>
 * </ul>
 *
 * Multi-touch par identifiant de pointeur : stick, strafe et tir tiennent ensemble.
 *
 * NOTE sur le strafe : dans le moteur d'origine, {@code joys != 0} fait que {@code joyx} veut dire
 * « pas de côté » AU LIEU de « tourner » ({@code rotplayer} ne tourne que si {@code joys == 0}).
 * Tourner et straffer en même temps est donc impossible — c'est une contrainte du jeu, pas du
 * portage : tant qu'un bouton de strafe est tenu, il prend la main sur la rotation du stick.
 */
public final class TouchPad {

    /** Zone morte du stick, en fraction de son rayon. */
    private static final float DEAD = 0.30f;

    private int w, h;
    private float stickR, btnR;
    private float fireX, fireY;
    private float strafeLX, strafeRX, strafeY, strafeR;
    private float menuX, menuY, menuR;

    private int stickId = -1;                 // pointeur qui tient le stick (-1 = aucun)
    private float stickCx, stickCy, stickX, stickY;
    private int fireId = -1, sLeftId = -1, sRightId = -1;
    private boolean menuTap;                  // appui sur MENU, consommé une seule fois

    public void resize(int width, int height) {
        w = width;
        h = height;
        float u = Math.min(w, h);
        stickR = u * 0.18f;
        btnR = u * 0.12f;
        fireX = w - btnR * 1.4f;
        fireY = h - btnR * 1.4f;
        strafeR = btnR * 0.72f;
        strafeY = h - strafeR * 1.5f;
        strafeRX = fireX - btnR * 1.35f - strafeR * 1.2f;
        strafeLX = strafeRX - strafeR * 2.3f;
        menuR = btnR * 0.5f;
        menuX = w - menuR * 1.6f;             // coin haut-droit, loin des pouces
        menuY = menuR * 1.6f;
    }

    // ------------------------------------------------------------------ état lu par la boucle

    /** Rotation — ou pas de côté si un bouton de strafe est tenu (cf. note de classe). */
    public int joyx() {
        if (sLeftId >= 0) return -1;          // le strafe prend la main sur la rotation
        if (sRightId >= 0) return 1;
        if (stickId < 0) return 0;
        float dx = (stickX - stickCx) / stickR;
        return Math.abs(dx) < DEAD ? 0 : (dx < 0 ? -1 : 1);
    }

    /** Avancer / reculer. */
    public int joyy() {
        if (stickId < 0) return 0;
        float dy = (stickY - stickCy) / stickR;
        return Math.abs(dy) < DEAD ? 0 : (dy < 0 ? -1 : 1);   // écran : Y croît vers le bas
    }

    public boolean fire() { return fireId >= 0; }
    public int joyb()     { return fireId >= 0 ? -1 : 0; }
    /** Mode « pas de côté » : joyx devient un strafe. */
    public int joys()     { return (sLeftId >= 0 || sRightId >= 0) ? -1 : 0; }

    /** Déclenche MENU de l'extérieur (touche RETOUR d'Android). */
    public void tapMenu() { menuTap = true; }

    /** Appui sur MENU depuis le dernier appel (équivalent d'Échap côté desktop). */
    public boolean consumeMenuTap() { boolean t = menuTap; menuTap = false; return t; }

    /** Navigation de menu : le stick vers le haut / vers le bas. */
    public boolean up()   { return joyy() < 0; }
    public boolean down() { return joyy() > 0; }

    // ------------------------------------------------------------------ événements

    public void onTouch(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int i = e.getActionIndex();
                assign(e.getPointerId(i), e.getX(i), e.getY(i));
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                for (int i = 0; i < e.getPointerCount(); i++) {
                    if (e.getPointerId(i) == stickId) {
                        stickX = e.getX(i);
                        stickY = e.getY(i);
                    }
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                release(e.getPointerId(e.getActionIndex()));
                break;
            }
            case MotionEvent.ACTION_CANCEL: {
                stickId = fireId = sLeftId = sRightId = -1;
                break;
            }
            default:
                break;
        }
    }

    private void assign(int id, float x, float y) {
        if (hit(x, y, menuX, menuY, menuR * 1.7f)) { menuTap = true; return; }
        if (hit(x, y, fireX, fireY, btnR * 1.25f)) { fireId = id; return; }
        if (hit(x, y, strafeLX, strafeY, strafeR * 1.3f)) { sLeftId = id; return; }
        if (hit(x, y, strafeRX, strafeY, strafeR * 1.3f)) { sRightId = id; return; }
        if (x < w * 0.5f && stickId < 0) {          // moitié gauche : le stick se centre ici
            stickId = id;
            stickCx = stickX = x;
            stickCy = stickY = y;
        }
    }

    private void release(int id) {
        if (id == stickId) stickId = -1;
        if (id == fireId) fireId = -1;
        if (id == sLeftId) sLeftId = -1;
        if (id == sRightId) sRightId = -1;
    }

    private static boolean hit(float x, float y, float cx, float cy, float r) {
        float dx = x - cx, dy = y - cy;
        return dx * dx + dy * dy <= r * r;
    }

    // ------------------------------------------------------------------ rendu de l'habillage

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();

    /** Dessine stick et boutons PAR-DESSUS l'image du jeu, en pixels écran (pas dans le framebuffer). */
    public void draw(Canvas c) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, btnR * 0.06f));

        if (stickId >= 0) {                                  // socle + tête du stick
            paint.setColor(Color.argb(70, 255, 255, 255));
            c.drawCircle(stickCx, stickCy, stickR, paint);
            float dx = stickX - stickCx, dy = stickY - stickCy;
            float d = (float) Math.hypot(dx, dy);
            if (d > stickR) { dx = dx / d * stickR; dy = dy / d * stickR; }
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(90, 255, 255, 255));
            c.drawCircle(stickCx + dx, stickCy + dy, stickR * 0.38f, paint);
            paint.setStyle(Paint.Style.STROKE);
        }

        button(c, fireX, fireY, btnR, fireId >= 0, 255, 60, 40);
        button(c, strafeLX, strafeY, strafeR, sLeftId >= 0, 90, 170, 255);
        button(c, strafeRX, strafeY, strafeR, sRightId >= 0, 90, 170, 255);
        chevron(c, strafeLX, strafeY, strafeR, true, sLeftId >= 0);
        chevron(c, strafeRX, strafeY, strafeR, false, sRightId >= 0);
        menuIcon(c);
    }

    private void button(Canvas c, float x, float y, float r, boolean held, int cr, int cg, int cb) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(held ? 120 : 50, cr, cg, cb));
        c.drawCircle(x, y, r, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.argb(held ? 200 : 110, cr, cg, cb));
        c.drawCircle(x, y, r, paint);
    }

    /** Chevron de pas de côté : ◀ à gauche, ▶ à droite. */
    private void chevron(Canvas c, float x, float y, float r, boolean left, boolean held) {
        float s = r * 0.45f, dir = left ? 1f : -1f;   // pointe vers l'EXTERIEUR : < a gauche, > a droite
        arrow.reset();
        arrow.moveTo(x + dir * s, y - s);
        arrow.lineTo(x - dir * s * 0.4f, y);
        arrow.lineTo(x + dir * s, y + s);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.argb(held ? 230 : 150, 210, 235, 255));
        c.drawPath(arrow, paint);
    }

    /** Bouton MENU : trois barres, discret, en haut à droite. */
    private void menuIcon(Canvas c) {
        button(c, menuX, menuY, menuR, false, 220, 220, 220);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(190, 230, 230, 230));
        float bw = menuR * 0.9f, bh = Math.max(2f, menuR * 0.13f);
        for (int i = -1; i <= 1; i++) {
            float cy = menuY + i * menuR * 0.36f;
            c.drawRect(menuX - bw / 2, cy - bh / 2, menuX + bw / 2, cy + bh / 2, paint);
        }
        paint.setStyle(Paint.Style.STROKE);
    }
}
