package com.oakhen.ex2dev;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * เส้นแบ่งระหว่างหน้าต่างแบ่งจอ
 *  - ลาก = ปรับขนาดสองหน้าต่างข้าง ๆ (resizeTask ทั้งคู่ — ทั้งสองเป็น freeform อยู่แล้ว เลยปรับได้ในคำสั่งเดียว ไม่กระโดด)
 *  - แตะปุ่ม ⇄ = สลับตำแหน่งสองแอพ
 * ขนาดต่ำสุดต่อหน้าต่าง MIN_W px · resize ระหว่างลากจำกัดทุก ~60ms (ไม่ถล่ม binder)
 */
final class SplitDividers {
    static final int MIN_W = 420, GAP = 4;
    private static final List<View> sViews = new ArrayList<View>();
    private static final List<WindowManager.LayoutParams> sLps = new ArrayList<WindowManager.LayoutParams>();
    private static int[] sTasks;
    private static Rect[] sRects;
    private static Handler sMain;

    private SplitDividers() {}

    private static Handler main() {
        if (sMain == null) sMain = new Handler(Looper.getMainLooper());
        return sMain;
    }

    /** เรียกบน main thread · tasks/rects เรียงซ้าย → ขวา */
    static void show(final Context app, int[] tasks, Rect[] rects) {
        clearNow(app);
        if (tasks == null || tasks.length < 2) return;
        sTasks = tasks.clone();
        sRects = new Rect[rects.length];
        for (int i = 0; i < rects.length; i++) sRects[i] = new Rect(rects[i]);
        WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
        for (int i = 0; i < sTasks.length - 1; i++) {
            final int idx = i;
            FrameLayout v = new FrameLayout(app);
            View line = new View(app);
            line.setBackgroundColor(0xFF3C4043);
            v.addView(line, new FrameLayout.LayoutParams(dp(app, 6), FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER_HORIZONTAL));
            TextView grip = new TextView(app);
            grip.setText("⇄");
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
            place(app, idx, lp);
            v.setOnTouchListener(new DragListener(app, idx));
            try { wm.addView(v, lp); } catch (Throwable t) { continue; }
            sViews.add(v); sLps.add(lp);
        }
    }

    static void clear(final Context ctx) {
        main().post(new Runnable() { public void run() { clearNow(ctx.getApplicationContext()); } });
    }

    private static void clearNow(Context app) {
        WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
        for (View v : sViews) { try { wm.removeView(v); } catch (Throwable ignored) {} }
        sViews.clear(); sLps.clear(); sTasks = null; sRects = null;
    }

    /** วางเส้นแบ่งที่ขอบระหว่าง rect[i] กับ rect[i+1] · ใต้แถบบนของหน้าต่าง (ไม่บังปุ่ม ✕/◀) */
    private static void place(Context app, int i, WindowManager.LayoutParams lp) {
        int boundary = (sRects[i].right + sRects[i + 1].left) / 2;
        int cap = dp(app, 44);
        lp.x = boundary - lp.width / 2;
        lp.y = sRects[i].top + cap;
        lp.height = Math.max(dp(app, 60), sRects[i].bottom - sRects[i].top - cap);
    }

    private static final class DragListener implements View.OnTouchListener {
        final Context app; final int i;
        float downX; int startBoundary; boolean dragging; long lastSent;
        DragListener(Context app, int i) { this.app = app; this.i = i; }

        public boolean onTouch(View v, MotionEvent e) {
            if (sRects == null || i + 1 >= sRects.length) return false;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX(); dragging = false;
                    startBoundary = (sRects[i].right + sRects[i + 1].left) / 2;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!dragging && Math.abs(e.getRawX() - downX) > 12) dragging = true;
                    if (dragging) {
                        setBoundary(startBoundary + (int) (e.getRawX() - downX));
                        long now = System.currentTimeMillis();
                        if (now - lastSent > 60) { lastSent = now; pushPair(i); }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (dragging) pushPair(i); else swap(i);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    if (dragging) pushPair(i);
                    return true;
            }
            return false;
        }

        private void setBoundary(int b) {
            int lo = sRects[i].left + MIN_W, hi = sRects[i + 1].right - MIN_W;
            b = Math.max(lo, Math.min(hi, b));
            sRects[i].right = b - GAP / 2;
            sRects[i + 1].left = b + GAP / 2;
            relayout(app, i);
        }

        private void swap(int i) {
            int t = sTasks[i]; sTasks[i] = sTasks[i + 1]; sTasks[i + 1] = t;
            // ความกว้างย้ายตามแอพ: แอพที่ไปทางซ้ายได้ความกว้างเดิมของมัน
            int wLeft = sRects[i + 1].width();
            int start = sRects[i].left, end = sRects[i + 1].right;
            sRects[i].right = Math.max(start + MIN_W, Math.min(end - MIN_W - GAP, start + wLeft));
            sRects[i + 1].left = sRects[i].right + GAP;
            relayout(app, i);
            pushPair(i);
        }
    }

    private static void relayout(Context app, int i) {
        if (i >= sViews.size()) return;
        WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
        place(app, i, sLps.get(i));
        try { wm.updateViewLayout(sViews.get(i), sLps.get(i)); } catch (Throwable ignored) {}
    }

    /** ส่งกรอบใหม่ของสองหน้าต่างไปเธรดเบื้องหลัง */
    private static void pushPair(int i) {
        if (sTasks == null) return;
        final int ta = sTasks[i], tb = sTasks[i + 1];
        final Rect ra = new Rect(sRects[i]), rb = new Rect(sRects[i + 1]);
        SplitScreen.worker().post(new Runnable() {
            public void run() { SplitScreen.resize(ta, ra); SplitScreen.resize(tb, rb); }
        });
    }

    private static int dp(Context c, int v) { return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f); }
}
