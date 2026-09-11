package gloom.android3d;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;

import java.util.function.Supplier;

import gloom.rebirth.Rebirth;

/**
 * Commandes tactiles de la vue 3D, en surimpression de la surface OpenGL — même disposition que
 * celles du mode 2D :
 *
 * <ul>
 *   <li><b>pouce gauche</b> — stick virtuel qui se centre là où le doigt se pose : haut/bas avance
 *       et recule, gauche/droite TOURNE ;</li>
 *   <li><b>pouce droit</b> — deux boutons de PAS DE CÔTÉ (◀ ▶) et le bouton TIR ;</li>
 *   <li><b>haut-droite</b> — bouton MENU (l'Échap de Rebirth : partie → menu → quitter).</li>
 * </ul>
 *
 * Tout passe par {@link Rebirth#setTouchInput} et {@link Rebirth#pressEscape}, qui écrivent
 * exactement là où le clavier écrit sur desktop : aucun chemin de jeu ne diffère. Contrairement au
 * moteur 2D, rien n'empêche ici de tourner ET de straffer en même temps.
 */
public final class Touch3d extends View {

    private static final float DEAD = 0.28f;

    private final Supplier<Rebirth> appRef;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();

    private float stickR, btnR;
    private float fireX, fireY;
    private float strafeLX, strafeRX, strafeY, strafeR;
    private float menuX, menuY, menuR;

    private int stickId = -1, fireId = -1, sLeftId = -1, sRightId = -1;
    private float stickCx, stickCy, stickX, stickY;

    public Touch3d(Context ctx, Supplier<Rebirth> appRef) {
        super(ctx);
        this.appRef = appRef;
        setWillNotDraw(false);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
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
        menuX = w - menuR * 1.6f;
        menuY = menuR * 1.6f;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
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
                int id = e.getPointerId(e.getActionIndex());
                if (id == stickId) stickId = -1;
                if (id == fireId) fireId = -1;
                if (id == sLeftId) sLeftId = -1;
                if (id == sRightId) sRightId = -1;
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                stickId = fireId = sLeftId = sRightId = -1;
                break;
            default:
                break;
        }
        push();
        invalidate();
        return true;
    }

    private void assign(int id, float x, float y) {
        if (hit(x, y, menuX, menuY, menuR * 1.7f)) {
            Rebirth app = appRef.get();
            if (app != null) app.pressEscape();
            return;
        }
        if (hit(x, y, fireX, fireY, btnR * 1.25f)) { fireId = id; return; }
        if (hit(x, y, strafeLX, strafeY, strafeR * 1.3f)) { sLeftId = id; return; }
        if (hit(x, y, strafeRX, strafeY, strafeR * 1.3f)) { sRightId = id; return; }
        if (x < getWidth() * 0.5f && stickId < 0) {
            stickId = id;
            stickCx = stickX = x;
            stickCy = stickY = y;
        }
    }

    /** Pousse l'état courant dans Rebirth (no-op tant que l'app jME n'est pas prête). */
    private void push() {
        Rebirth app = appRef.get();
        if (app == null) return;
        float dx = 0f, dy = 0f;
        if (stickId >= 0) {
            dx = (stickX - stickCx) / stickR;
            dy = (stickY - stickCy) / stickR;
        }
        app.setTouchInput(
                dy < -DEAD, dy > DEAD,          // avancer / reculer
                dx < -DEAD, dx > DEAD,          // tourner gauche / droite
                sLeftId >= 0, sRightId >= 0,    // pas de côté
                fireId >= 0,
                0f);                            // la visée est au stick, pas au glissé
    }

    @Override
    protected void onDraw(Canvas c) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, btnR * 0.06f));

        if (stickId >= 0) {
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

    private static boolean hit(float x, float y, float cx, float cy, float r) {
        float dx = x - cx, dy = y - cy;
        return dx * dx + dy * dy <= r * r;
    }
}
