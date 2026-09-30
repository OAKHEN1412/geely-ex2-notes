package com.oakhen.ex2dev;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * ช่องแบ่งจอที่ว่าง (แอพในช่องถูกปิด/crash/เปิดไม่ขึ้น) → แสดงตารางแอพในกรอบของช่องนั้น
 * แตะแอพ = เปิดแอพนั้นเข้าช่องเดิม · ✕ = ปิดตัวเลือก (ช่องว่างเป็นพื้นดำ)
 * รายชื่อ+ไอคอนโหลดเบื้องหลังครั้งเดียวแล้วเก็บไว้ (บนจอรถโหลด ~5 วิ)
 */
final class SlotPicker {
    interface OnPick { void pick(int slot, ComponentName cn); }

    private static final class Entry {
        final ComponentName cn; final Drawable icon; final String label;
        Entry(ComponentName cn, Drawable icon, String label) { this.cn = cn; this.icon = icon; this.label = label; }
    }

    private static final int BG = 0xFF1B1C1E, CARD = 0xFF2A2B2E, CLAY = 0xFFD97757, CREAM = 0xFFF2EFE9, MUTED = 0xFF9AA0A6;
    private static volatile List<Entry> sCache;
    private static boolean sLoading;
    private static final List<Runnable> sWaiting = new ArrayList<Runnable>();
    private static final SparseArray<View> sViews = new SparseArray<View>();
    private static Handler sMain;

    private SlotPicker() {}

    private static Handler main() {
        if (sMain == null) sMain = new Handler(Looper.getMainLooper());
        return sMain;
    }

    /** โหลดรายชื่อแอพล่วงหน้า (เรียกตอนเริ่มแบ่งจอ) — ไม่ใช้เธรด split เพื่อไม่ถ่วงการจัดหน้าต่าง */
    private static long sLoadedAt;

    static synchronized void preload(final Context app, final Runnable done) {
        if (sCache != null) {
            if (done != null) main().post(done);
            if (sLoading || System.currentTimeMillis() - sLoadedAt < 60000) return;
            // มีรายการเดิมใช้ไปก่อน แล้วโหลดใหม่เบื้องหลัง (แอพที่ลงใหม่/ลบไป)
        } else if (done != null) sWaiting.add(done);   // โหลดค้างอยู่ก็รอผลรอบเดียวกัน
        if (sLoading) return;
        sLoading = true;
        new Thread(new Runnable() {
            public void run() {
                PackageManager pm = app.getPackageManager();
                List<Entry> out = new ArrayList<Entry>();
                try {
                    for (ResolveInfo r : SplitScreen.launchables(app)) {
                        out.add(new Entry(new ComponentName(r.activityInfo.packageName, r.activityInfo.name),
                                r.loadIcon(pm), String.valueOf(r.loadLabel(pm))));
                    }
                } catch (Throwable ignored) {}
                List<Runnable> w;
                synchronized (SlotPicker.class) {
                    sCache = out; sLoading = false; sLoadedAt = System.currentTimeMillis();
                    w = new ArrayList<Runnable>(sWaiting); sWaiting.clear();
                }
                for (Runnable r : w) main().post(r);
            }
        }, "ex2-slot-load").start();
    }

    static boolean showing(int slot) { return sViews.get(slot) != null; }

    /** เรียกบน main thread · exclude = แพ็กเกจที่อยู่ในช่องอื่นแล้ว (ไม่ให้เลือกซ้ำ — จะไปดึงหน้าต่างเดิมมา) */
    static void show(final Context app, final int slot, final Rect r, final Set<String> exclude, final OnPick cb) {
        remove(app, slot);
        final WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
        FrameLayout root = new FrameLayout(app);
        root.setBackgroundColor(BG);
        LinearLayout col = new LinearLayout(app);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(app, 14), dp(app, 12), dp(app, 14), dp(app, 12));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout head = new LinearLayout(app);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(app);
        title.setText("เลือกแอพสำหรับช่องนี้");
        title.setTextColor(CLAY);
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView x = new TextView(app);
        x.setText("✕");
        x.setTextColor(CREAM);
        x.setTextSize(20);
        x.setGravity(Gravity.CENTER);
        x.setBackground(round(app, CARD));
        x.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { remove(app, slot); } });
        head.addView(x, new LinearLayout.LayoutParams(dp(app, 48), dp(app, 48)));
        col.addView(head);

        final ScrollView scroll = new ScrollView(app);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, 0, 1);
        sl.topMargin = dp(app, 10);
        col.addView(scroll, sl);

        final int cellW = dp(app, 112);
        final int cols = Math.max(1, (r.width() - dp(app, 28)) / (cellW + dp(app, 8)));
        final Runnable fill = new Runnable() {
            public void run() {
                scroll.removeAllViews();
                List<Entry> list = sCache;
                if (list == null) {
                    TextView t = new TextView(app);
                    t.setText("กำลังโหลดรายชื่อแอพ…");
                    t.setTextColor(MUTED);
                    t.setTextSize(15);
                    scroll.addView(t);
                    return;
                }
                GridLayout grid = new GridLayout(app);
                grid.setColumnCount(cols);
                for (final Entry e : list) {
                    if (exclude != null && exclude.contains(e.cn.getPackageName())) continue;
                    View cell = cell(app, e);
                    cell.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) { remove(app, slot); cb.pick(slot, e.cn); }
                    });
                    GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                    lp.width = cellW;
                    lp.setMargins(dp(app, 4), dp(app, 4), dp(app, 4), dp(app, 4));
                    grid.addView(cell, lp);
                }
                scroll.addView(grid);
            }
        };
        fill.run();
        if (sCache == null) preload(app, new Runnable() { public void run() { if (showing(slot)) fill.run(); } });

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(r.width(), r.height(),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = r.left;
        lp.y = r.top;
        try { wm.addView(root, lp); sViews.put(slot, root); TapExclude.apply(root, lp); } catch (Throwable ignored) {}
    }

    /** เรียกบน main thread */
    static void remove(Context app, int slot) {
        View v = sViews.get(slot);
        if (v == null) return;
        sViews.remove(slot);
        try { ((WindowManager) app.getSystemService(Context.WINDOW_SERVICE)).removeView(v); } catch (Throwable ignored) {}
    }

    /** เรียกบน main thread */
    static void clearAll(Context app) {
        WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
        for (int i = 0; i < sViews.size(); i++) { try { wm.removeView(sViews.valueAt(i)); } catch (Throwable ignored) {} }
        sViews.clear();
    }

    /** ซ่อน/แสดงทั้งหมดชั่วคราว (เช่น กดหน้าหลักทับแบ่งจอ) */
    static void setVisible(boolean on) {
        for (int i = 0; i < sViews.size(); i++) sViews.valueAt(i).setVisibility(on ? View.VISIBLE : View.GONE);
    }

    private static View cell(Context app, Entry e) {
        LinearLayout c = new LinearLayout(app);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding(dp(app, 6), dp(app, 10), dp(app, 6), dp(app, 8));
        c.setBackground(round(app, CARD));
        ImageView iv = new ImageView(app);
        iv.setImageDrawable(e.icon);
        c.addView(iv, new LinearLayout.LayoutParams(dp(app, 52), dp(app, 52)));
        TextView tv = new TextView(app);
        tv.setText(e.label);
        tv.setTextColor(CREAM);
        tv.setTextSize(13);
        tv.setGravity(Gravity.CENTER);
        tv.setMaxLines(2);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, -2);
        tl.topMargin = dp(app, 6);
        c.addView(tv, tl);
        return c;
    }

    private static GradientDrawable round(Context app, int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(app, 12));
        return g;
    }

    private static int dp(Context c, int v) { return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f); }
}
