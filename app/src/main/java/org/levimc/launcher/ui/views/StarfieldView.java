package org.levimc.launcher.ui.views;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Lightweight decorative background: a handful of small twinkling dots plus a
 * few slow-drifting glow particles, in the same blue palette as the rest of
 * the SpaceVisuals theme. Pure drawing, no game/mod logic — purely cosmetic.
 */
public class StarfieldView extends View {

    private static final int STAR_COUNT = 46;
    private static final int DRIFT_COUNT = 8;

    private final List<Star> stars = new ArrayList<>();
    private final List<Drift> drifts = new ArrayList<>();
    private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint driftPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private ValueAnimator animator;
    private boolean seeded = false;

    public StarfieldView(Context context) {
        super(context);
        init();
    }

    public StarfieldView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        starPaint.setColor(Color.WHITE);
        driftPaint.setColor(Color.parseColor("#8BD4FF"));
    }

    private static class Star {
        float x, y, radius, phase, speed;
    }

    private static class Drift {
        float x, y, radius, driftX, driftY, progress, duration;
    }

    private void seed(int w, int h) {
        if (seeded || w <= 0 || h <= 0) return;
        seeded = true;
        stars.clear();
        for (int i = 0; i < STAR_COUNT; i++) {
            Star s = new Star();
            s.x = random.nextFloat() * w;
            s.y = random.nextFloat() * h;
            s.radius = 1f + random.nextFloat() * 1.6f;
            s.phase = random.nextFloat() * (float) Math.PI * 2f;
            s.speed = 0.6f + random.nextFloat() * 0.8f;
            stars.add(s);
        }
        drifts.clear();
        for (int i = 0; i < DRIFT_COUNT; i++) {
            drifts.add(newDrift(w, h, random.nextFloat()));
        }
    }

    private Drift newDrift(int w, int h, float startProgress) {
        Drift d = new Drift();
        d.x = random.nextFloat() * w;
        d.y = h * (0.6f + random.nextFloat() * 0.4f);
        d.radius = 2.5f + random.nextFloat() * 3f;
        d.driftX = (random.nextFloat() - 0.5f) * w * 0.3f;
        d.driftY = -h * (0.5f + random.nextFloat() * 0.4f);
        d.duration = 9000f + random.nextFloat() * 8000f;
        d.progress = startProgress;
        return d;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        seed(w, h);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(16);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            for (Drift d : drifts) {
                d.progress += 16f / d.duration;
                if (d.progress >= 1f) {
                    Drift fresh = newDrift(getWidth(), getHeight(), 0f);
                    d.x = fresh.x; d.y = fresh.y; d.radius = fresh.radius;
                    d.driftX = fresh.driftX; d.driftY = fresh.driftY;
                    d.duration = fresh.duration; d.progress = 0f;
                }
            }
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!seeded) seed(getWidth(), getHeight());
        long t = System.currentTimeMillis();

        for (Star s : stars) {
            float twinkle = (float) (0.35 + 0.35 * Math.sin(t / 1000.0 * s.speed + s.phase));
            starPaint.setAlpha((int) (twinkle * 255));
            canvas.drawCircle(s.x, s.y, s.radius, starPaint);
        }

        for (Drift d : drifts) {
            float fadeIn = Math.min(1f, d.progress / 0.08f);
            float fadeOut = Math.min(1f, (1f - d.progress) / 0.15f);
            float alpha = Math.max(0f, Math.min(fadeIn, fadeOut));
            if (alpha <= 0f) continue;
            driftPaint.setAlpha((int) (alpha * 130));
            float cx = d.x + d.driftX * d.progress;
            float cy = d.y + d.driftY * d.progress;
            canvas.drawCircle(cx, cy, d.radius, driftPaint);
        }
    }
}
