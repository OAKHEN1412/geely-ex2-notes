package com.oakhen.ex2dev;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.List;

/**
 * เส้นแบ่งแนวตั้งของชุด CarPlay (ซ้ายเต็มสูง | CarPlay ขวาบน + ช่องขวาล่าง)
 * ลาก = เปลี่ยนความกว้าง CarPlay · ความสูง CarPlay ตามสัดส่วน 1920:968 เสมอ · ช่องล่างได้ที่เหลือ
 * ปล่อยนิ้ว = จำความกว้างเป็นค่าเริ่มต้นครั้งต่อไป (Prefs K_CP_W) แล้วจัดปุ่ม/ตัวเลือกแอพใหม่
 */
final class CarPlayDivider {
    static final int MIN_CP_W = 720, MIN_LEFT = 420, MIN_BOTTOM = 220, GAP = 4;
    private static View sView;
    private static WindowManager.LayoutParams sLp;

    private CarPlayDivider() {}

    /** เรียกบน main thread · tasks = task ของแต่ละช่อง (-1 = ช่องว่าง) */
    static void show(final Context app, final List<android.content.ComponentName> apps, final int[] tasks, final Rect[] rects) {
        clear(app);
        final Rect full = new Rect(rects[0].left, rects[0].top, rects[1].right, rects[0].bottom);
        final Rect[] cur = {new Rect(rects[0]), new Rect(rects[1]), new Rect(rects[2])};
        FrameLayout v = new FrameLayout(app);
        View line = new View(app);
        line.setBackgroundColor(0xFF3C4043);
        v.addView(line, new FrameLayout.LayoutParams(dp(app, 6), -1, Gravity.CENTER_HORIZONTAL));
        TextView grip = new TextView(app);
        grip.setText("↔");
        grip.setTextColor(0xFFF2EFE9);
        grip.setTextSize(22);
        grip.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFD97757);
        bg.setShape(GradientDrawable.OVAL);
        grip.setBackground(bg);
        v.addView(grip, new FrameLayout.LayoutParams(dp(app, 46), dp(app, 46), Gravity.CENTER));
        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(dp(app, 46), 10,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        place(app, lp, cur);
        v.setOnTouchListener(new View.OnTouchListener() {
            float downX; int startX; boolean dragging; long lastSent;
            public boolean onTouch(View view, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX(); startX = cur[1].left; dragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (!dragging && Math.abs(e.getRawX() - downX) > 12) dragging = true;
                        if (dragging) {
                            compute(full, full.right - (startX + (int) (e.getRawX() - downX)), cur);
                            place(app, lp, cur);
                            try { wm(app).updateViewLayout(sView, lp); } catch (Throwable ignored) {}
                            long now = System.currentTimeMillis();
                            if (now - lastSent > 60) { lastSent = now; push(tasks, cur); }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (!dragging) return true;
                        push(tasks, cur);
                        Prefs.of(app).edit().putInt(SplitScreen.K_CP_W, cur[1].width()).apply();
                        final Rect[] done = {new Rect(cur[0]), new Rect(cur[1]), new Rect(cur[2])};
                        // จัดปุ่ม ◀ / ตัวเลือกแอพช่องว่าง ตามกรอบใหม่ (หลัง resize เสร็จบนเธรด split)
                        SplitScreen.worker().postDelayed(new Runnable() {
                            public void run() { SplitBackButtons.show(app, apps, done); }
                        }, 150);
                        return true;
                }
                return false;
            }
        });
        try { wm(app).addView(v, lp); sView = v; sLp = lp; } catch (Throwable ignored) {}
    }

    /** กรอบ 3 ช่องจากความกว้าง CarPlay (บีบให้อยู่ในขอบเขต) */
    static void compute(Rect full, int cpW, Rect[] out) {
        int maxByBottom = (full.height() - GAP - MIN_BOTTOM) * SplitScreen.CP_W / SplitScreen.CP_H;
        int maxByLeft = full.width() - MIN_LEFT - GAP;
        cpW = Math.max(MIN_CP_W, Math.min(Math.min(maxByBottom, maxByLeft), cpW));
        int cpH = cpW * SplitScreen.CP_H / SplitScreen.CP_W;
        int x = full.right - cpW;
        out[0].set(full.left, full.top, x - GAP, full.bottom);
        out[1].set(x, full.top, full.right, full.top + cpH);
        out[2].set(x, full.top + cpH + GAP, full.right, full.bottom);
    }

    private static void push(int[] tasks, Rect[] cur) {
        final int[] ts = tasks.clone();
        final Rect[] rs = {new Rect(cur[0]), new Rect(cur[1]), new Rect(cur[2])};
        SplitScreen.worker().post(new Runnable() {
            public void run() { for (int i = 0; i < 3; i++) if (ts[i] >= 0) SplitScreen.resize(ts[i], rs[i]); }
        });
    }

    private static void place(Context app, WindowManager.LayoutParams lp, Rect[] cur) {
        int cap = dp(app, 44);
        lp.x = (cur[0].right + cur[1].left) / 2 - lp.width / 2;
        lp.y = cur[0].top + cap;
        lp.height = Math.max(dp(app, 60), cur[0].height() - cap);
    }

    static void setVisible(boolean on) { if (sView != null) sView.setVisibility(on ? View.VISIBLE : View.GONE); }

    /** เรียกบน main thread */
    static void clear(Context app) {
        if (sView != null) { try { wm(app).removeView(sView); } catch (Throwable ignored) {} }
        sView = null; sLp = null;
    }

    private static WindowManager wm(Context c) { return (WindowManager) c.getSystemService(Context.WINDOW_SERVICE); }
    private static int dp(Context c, int v) { return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f); }
}
