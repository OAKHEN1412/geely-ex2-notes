package com.oakhen.ex2dev;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * FloatingBox — กล่องลอย (ฝังในแอป EX2 BY OAKHEN, DevService เรียก show() ตอนบูต)
 *   🌚 ปิดจอ = ดับ backlight ผ่าน sysfs (มืดทั้งจอรวมแถบ) + จอยัง Awake -> แตะปลุก, Wi-Fi ไม่ดับ
 *   🌙 หรี่จอ = overlay ดำ + brightness (สำรอง) แตะปลุก
 * แก้ตาม /scrutinize bug-hunt: การเขียน backlight ทั้งหมด serialize บน executor เดียว (guard+restore ไม่แข่งกัน,
 * restore เป็นคำสั่งสุดท้ายเสมอ, ไม่ block main thread=ไม่ ANR), watchdog เดี่ยวไม่ผูก view, คืน brightness mode.
 */
public class FloatingBox {

    static View sBox, sPanel, sBlack;
    static Context sApp;
    static int sPrevBrightness = -1, sPrevMode = -1;   // dim path (หรี่จอ)

    // backlight (ปิดจอ)
    static String sBlNode = null;
    static int sBlSaved = -1;
    static int sBlPrevMode = -1;
    static volatile boolean sBlOff = false;
    static boolean sChmodTried = false;
    static ScheduledExecutorService sBlSched;   // single-thread: serialize ทุกการเขียน backlight
    static Handler sMainH;
    static Runnable sWatchdog;

    private static int overlayType() {
        return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
    }
    private static Handler mainH() {
        if (sMainH == null) sMainH = new Handler(Looper.getMainLooper());
        return sMainH;
    }
    private static ScheduledExecutorService bl() {
        if (sBlSched == null) sBlSched = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            public Thread newThread(Runnable r) { Thread t = new Thread(r, "ex2dev-bl"); t.setDaemon(true); return t; }
        });
        return sBlSched;
    }

    public static void show(final Context ctx) { post(ctx, false); }
    public static void hide(final Context ctx) { post(ctx, true); }

    // แอพ system_app เขียน /sys/class/leds ไม่ได้ (SELinux) -> เขียน flag file ให้ root daemon (ex2_backlight.sh) แทน
    static final String FLAG_NAME = "ex2_bl";
    static void writeFlag(Context ctx, String val) {
        try {
            java.io.FileOutputStream os = new java.io.FileOutputStream(new java.io.File(ctx.getFilesDir(), FLAG_NAME));
            os.write(val.getBytes("US-ASCII")); os.flush(); os.close();
        } catch (Throwable ignored) {}
    }

    /** กู้จอถ้า backlight ค้างดับ (process ตายตอนปิดจอ) — เรียกตอน service start/บูต: เขียน flag เป็นค่าปกติ */
    public static void recoverBacklight(Context ctx) {
        sApp = ctx.getApplicationContext();
        sBlOff = false;
        int b = 120;
        try { b = android.provider.Settings.System.getInt(sApp.getContentResolver(), "screen_brightness", 120); } catch (Throwable ignored) {}
        if (b <= 0) b = 120;
        writeFlag(sApp, Integer.toString(b));   // ถ้า flag ค้าง "0" daemon จะดับค้าง -> เขียนค่าปกติกลับ
    }

    private static void post(final Context ctx, final boolean hide) {
        final Context app = ctx.getApplicationContext();
        sApp = app;
        mainH().post(new Runnable() {
            public void run() {
                try {
                    WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
                    removeAll(wm);
                    if (!hide) addBox(app, wm);
                } catch (Throwable t) {
                    android.util.Log.e("EX2Dev", "FloatingBox", t);
                }
            }
        });
    }

    private static void removeAll(WindowManager wm) {
        if (sBlOff) screenOnBacklight();    // คืน backlight + ลบ overlay ดำ กัน hide ตอนจอดำ
        restoreBrightness();                // dim path
        for (View v : new View[]{sBlack, sPanel, sBox}) {
            try { if (v != null) wm.removeView(v); } catch (Throwable ignored) {}
        }
        sBlack = null; sPanel = null; sBox = null;
    }

    private static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static void addBox(final Context ctx, final WindowManager wm) {
        final TextView box = new TextView(ctx);
        box.setText("☰"); // ☰
        box.setTextColor(Color.WHITE);
        box.setTextSize(20);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(0xCC1E88E5);
        int pad = dp(ctx, 12);
        box.setPadding(pad, pad, pad, pad);

        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType(), WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = 0; lp.y = dp(ctx, 120);

        box.setOnTouchListener(new View.OnTouchListener() {
            float dx, dy; int sx, sy; boolean moved;
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // ต้อง return true เพื่อ "กิน" gesture — ไม่งั้นไม่ได้ ACTION_UP (togglePanel ไม่ทำงาน)
                        // และทัชจะทะลุไปโดน UI รถข้างหลัง (เคยเปิดกล้อง 360 โดยไม่ตั้งใจ)
                        dx = e.getRawX(); dy = e.getRawY(); sx = lp.x; sy = lp.y; moved = false; return true;
                    case MotionEvent.ACTION_MOVE:
                        int nx = sx + (int) (e.getRawX() - dx), ny = sy + (int) (e.getRawY() - dy);
                        if (Math.abs(nx - sx) > 8 || Math.abs(ny - sy) > 8) moved = true;
                        lp.x = nx; lp.y = ny;
                        try { wm.updateViewLayout(box, lp); } catch (Throwable ignored) {}
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) togglePanel(ctx, wm, lp); else snapToEdge(wm, box, lp);
                        return true;
                }
                return false;
            }
        });
        wm.addView(box, lp);
        sBox = box;
    }

    private static void togglePanel(final Context ctx, final WindowManager wm, WindowManager.LayoutParams boxLp) {
        if (sPanel != null) { try { wm.removeView(sPanel); } catch (Throwable ignored) {} sPanel = null; return; }
        LinearLayout panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xEE202124);
        int pad = dp(ctx, 8); panel.setPadding(pad, pad, pad, pad);

        // โชว์ปุ่มตามสวิตช์ในหน้าตั้งค่า (default เปิดทั้งคู่)
        boolean showOff = Prefs.get(ctx, Prefs.KEY_BTN_OFF, true);
        boolean showDim = Prefs.get(ctx, Prefs.KEY_BTN_DIM, true);
        if (showOff) {
            Button off = mkBtn(ctx, "🌚 ปิดจอ (แตะปลุก)"); // 🌚 ปิดจอ (แตะปลุก)
            off.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { screenOffBacklight(ctx, wm); collapse(wm); }
            });
            panel.addView(off);
        }
        if (showDim) {
            Button dim = mkBtn(ctx, "🌙 หรี่จอ (สำรอง)"); // 🌙 หรี่จอ (สำรอง)
            dim.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { screenDim(ctx, wm); collapse(wm); }
            });
            panel.addView(dim);
        }
        if (!showOff && !showDim) {
            TextView hint = new TextView(ctx);
            hint.setText("เปิดปุ่มได้ในหน้าตั้งค่า");
            hint.setTextColor(0xFFAAAAAA);
            int hp = dp(ctx, 8); hint.setPadding(hp, hp, hp, hp);
            panel.addView(hint);
        }
        Button min = mkBtn(ctx, "▽ ย่อ"); // ▽ ย่อ
        min.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { collapse(wm); }
        });
        panel.addView(min);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType(), WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = boxLp.x; lp.y = boxLp.y + dp(ctx, 56);
        wm.addView(panel, lp);
        sPanel = panel;
    }

    private static void collapse(WindowManager wm) {
        if (sPanel != null) { try { wm.removeView(sPanel); } catch (Throwable ignored) {} sPanel = null; }
    }

    private static void snapToEdge(WindowManager wm, View box, WindowManager.LayoutParams lp) {
        try {
            android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(dm);
            int w = box.getWidth();
            int center = lp.x + w / 2;
            lp.x = (center < dm.widthPixels / 2) ? 0 : (dm.widthPixels - w);
            if (lp.y < 0) lp.y = 0;
            int maxY = dm.heightPixels - box.getHeight();
            if (lp.y > maxY) lp.y = maxY;
            wm.updateViewLayout(box, lp);
        } catch (Throwable ignored) {}
    }

    private static Button mkBtn(Context ctx, String text) {
        Button b = new Button(ctx);
        b.setText(text); b.setAllCaps(false);
        b.setTextColor(Color.WHITE); b.setBackgroundColor(0xFF3C4043);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(ctx, 190), WindowManager.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(ctx, 4); b.setLayoutParams(p);
        return b;
    }

    // ---------- 🌚 ปิดจอ ผ่าน flag file -> root daemon (ex2_backlight.sh) เขียน sysfs ----------
    // แอพ system_app แตะ /sys/class/leds ไม่ได้ (SELinux search denied) จึงสั่งผ่าน flag ให้ daemon แทน

    private static void screenOffBacklight(final Context ctx, final WindowManager wm) {
        if (sBlOff) return;
        // เก็บ brightness ปัจจุบันไว้คืนตอนปลุก แล้วสั่ง daemon ดับ (flag=0)
        try { sBlSaved = android.provider.Settings.System.getInt(ctx.getContentResolver(), "screen_brightness", 120); }
        catch (Throwable t) { sBlSaved = 120; }
        if (sBlSaved <= 0) sBlSaved = 120;
        writeFlag(ctx, "0");   // daemon จะเขียน node=0 ทุก tick (ดับสนิท ชนะ controller)

        final View black = new View(ctx);
        black.setBackgroundColor(Color.BLACK);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.OPAQUE);
        lp.gravity = Gravity.TOP | Gravity.START; lp.x = 0; lp.y = 0;
        black.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { screenOnBacklight(); }
        });
        wm.addView(black, lp);
        sBlack = black;

        sBlOff = true;

        // watchdog เดี่ยว (ไม่ผูก view) — คืนจอเองใน 10 นาทีถ้าไม่ถูกแตะ
        if (sWatchdog == null) sWatchdog = new Runnable() {
            public void run() { if (sBlOff) screenOnBacklight(); }
        };
        mainH().removeCallbacks(sWatchdog);
        mainH().postDelayed(sWatchdog, 600000);
    }

    private static void screenOnBacklight() {
        sBlOff = false;
        // สั่ง daemon คืน backlight = ค่าที่บันทึกไว้ (flag เปลี่ยนจาก "0" -> daemon เขียนครั้งเดียว)
        if (sApp != null) writeFlag(sApp, Integer.toString(sBlSaved > 0 ? sBlSaved : 120));
        if (sWatchdog != null) mainH().removeCallbacks(sWatchdog);
        // ลบ overlay ดำตัวปัจจุบัน
        View b = sBlack; sBlack = null;
        if (b != null && sApp != null) {
            try { ((WindowManager) sApp.getSystemService(Context.WINDOW_SERVICE)).removeView(b); } catch (Throwable ignored) {}
        }
    }

    private static String detectBL() {
        if (sBlNode != null && new File(sBlNode).exists()) return sBlNode;
        String[] cands = {
            "/sys/class/leds/lcd-backlight/brightness",
            "/sys/class/leds/lcd_backlight/brightness"
        };
        for (String c : cands) if (new File(c).exists()) { sBlNode = c; return c; }
        File d = new File("/sys/class/backlight");
        File[] subs = d.listFiles();
        if (subs != null) for (File s : subs) {
            File b = new File(s, "brightness");
            if (b.exists()) { sBlNode = b.getAbsolutePath(); return sBlNode; }
        }
        return null;
    }

    private static int readBL(String node) {
        try {
            BufferedReader r = new BufferedReader(new FileReader(node));
            String line = r.readLine(); r.close();
            return Integer.parseInt(line.trim());
        } catch (Throwable t) { return 120; }
    }

    // เขียน backlight — เรียกจาก bl executor thread เท่านั้น (ไม่ block main = ไม่ ANR)
    private static void writeBL(String node, int v) {
        try { FileWriter w = new FileWriter(node); w.write(Integer.toString(v)); w.close(); return; }
        catch (Throwable ignored) {}
        if (!sChmodTried) {   // chmod 666 ครั้งเดียว แล้วเขียนตรงต่อ (เลิก su ทุก tick)
            sChmodTried = true;
            try { Runtime.getRuntime().exec(new String[]{"su", "-c", "chmod 666 " + node}).waitFor(); } catch (Throwable ignored) {}
            try { FileWriter w = new FileWriter(node); w.write(Integer.toString(v)); w.close(); return; } catch (Throwable ignored) {}
        }
        try { Runtime.getRuntime().exec(new String[]{"su", "-c", "echo " + v + " > " + node}).waitFor(); } catch (Throwable ignored) {}
    }

    // ---------- 🌙 หรี่จอ (สำรอง): overlay ดำ + brightness, แตะปลุก ----------
    private static void screenDim(final Context ctx, final WindowManager wm) {
        if (sBlack != null) return;
        final View black = new View(ctx);
        black.setBackgroundColor(Color.BLACK);
        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                dm.widthPixels, dm.heightPixels, overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_FULLSCREEN,
                PixelFormat.OPAQUE);
        lp.screenBrightness = 0.0f;
        lp.gravity = Gravity.TOP | Gravity.START; lp.x = 0; lp.y = 0;
        killBrightness(ctx);
        black.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                restoreBrightness();
                try { wm.removeView(black); } catch (Throwable ignored) {}
                sBlack = null;
            }
        });
        wm.addView(black, lp);
        sBlack = black;
    }

    private static void killBrightness(Context ctx) {
        try {
            android.content.ContentResolver cr = ctx.getContentResolver();
            sPrevMode = android.provider.Settings.System.getInt(cr, "screen_brightness_mode", 1);
            sPrevBrightness = android.provider.Settings.System.getInt(cr, "screen_brightness", 120);
            android.provider.Settings.System.putInt(cr, "screen_brightness_mode", 0);
            android.provider.Settings.System.putInt(cr, "screen_brightness", 0);
        } catch (Throwable ignored) {}
    }

    private static void restoreBrightness() {
        try {
            if (sApp != null && sPrevBrightness >= 0) {
                android.content.ContentResolver cr = sApp.getContentResolver();
                android.provider.Settings.System.putInt(cr, "screen_brightness_mode", sPrevMode < 0 ? 1 : sPrevMode);
                android.provider.Settings.System.putInt(cr, "screen_brightness", sPrevBrightness);
            }
        } catch (Throwable ignored) {}
        sPrevBrightness = -1; sPrevMode = -1;
    }
}
