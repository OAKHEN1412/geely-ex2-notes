package com.oakhen.plugins;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.oakhen.ex2dev.Plugin;

/**
 * FloatingBox — กล่องลอยบนจอ (overlay) แตะแล้วกางเป็นปุ่ม
 *   ปุ่ม "ปิดจอ" = ครอบ overlay ดำเต็มจอ + ดับ backlight (screenBrightness=0 เฉพาะตอนโชว์)
 *                 แอป/เสียงเบื้องหลังทำงานต่อ — แตะจอดำเพื่อเปิดจอกลับ
 * push:  py tools/push_plugin.py <IP> push com.oakhen.plugins.FloatingBox <dex> show|hide
 * ใช้ WindowManager ของแอป host (system app, มี SYSTEM_ALERT_WINDOW) → ไม่ต้องขอสิทธิ์ overlay
 */
public class FloatingBox implements Plugin {

    // เก็บ view ไว้กันสร้างซ้ำ + ให้ hide ได้
    static View sBox;
    static View sPanel;
    static View sBlack;
    static WindowManager sWm;
    static Context sApp;
    static int sPrevBrightness = -1;
    static int sPrevMode = -1;

    private static int overlayType() {
        // จอ API28 (>=26) → ใช้ TYPE_APPLICATION_OVERLAY เสมอ
        return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
    }

    @Override
    public String run(final Context ctx, final String arg) {
        final Context app = ctx.getApplicationContext();
        sApp = app;
        final Handler h = new Handler(Looper.getMainLooper());
        final boolean hide = "hide".equalsIgnoreCase(arg);
        h.post(new Runnable() {
            public void run() {
                try {
                    WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
                    sWm = wm;
                    removeAll(wm);
                    if (hide) return;
                    addBox(app, wm);
                } catch (Throwable t) {
                    android.util.Log.e("EX2Dev", "FloatingBox", t);
                }
            }
        });
        return hide ? "floating box hidden" : "floating box shown";
    }

    private static void removeAll(WindowManager wm) {
        restoreBrightness(); // กัน hide ตอนจอดำอยู่แล้วค้างมืด
        for (View v : new View[]{sBlack, sPanel, sBox}) {
            try { if (v != null) wm.removeView(v); } catch (Throwable ignored) {}
        }
        sBlack = null; sPanel = null; sBox = null;
    }

    private static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    // กล่องลอยเล็ก ๆ ลากได้ แตะเพื่อกางปุ่ม
    private static void addBox(final Context ctx, final WindowManager wm) {
        final TextView box = new TextView(ctx);
        box.setText("☰");
        box.setTextColor(Color.WHITE);
        box.setTextSize(20);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(0xCC1E88E5); // ฟ้าโปร่ง
        int pad = dp(ctx, 12);
        box.setPadding(pad, pad, pad, pad);

        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = 0;              // เริ่มชิดขอบซ้าย
        lp.y = dp(ctx, 120);

        box.setOnTouchListener(new View.OnTouchListener() {
            float dx, dy; int sx, sy; boolean moved;
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        dx = e.getRawX(); dy = e.getRawY(); sx = lp.x; sy = lp.y; moved = false;
                        return false;
                    case MotionEvent.ACTION_MOVE:
                        int nx = sx + (int) (e.getRawX() - dx);
                        int ny = sy + (int) (e.getRawY() - dy);
                        if (Math.abs(nx - sx) > 8 || Math.abs(ny - sy) > 8) moved = true;
                        lp.x = nx; lp.y = ny;
                        try { wm.updateViewLayout(box, lp); } catch (Throwable ignored) {}
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) togglePanel(ctx, wm, lp);
                        else snapToEdge(wm, box, lp);
                        return true;
                }
                return false;
            }
        });
        wm.addView(box, lp);
        sBox = box;
    }

    // แผงปุ่มที่กางออกมาข้างกล่อง
    private static void togglePanel(final Context ctx, final WindowManager wm, WindowManager.LayoutParams boxLp) {
        if (sPanel != null) { try { wm.removeView(sPanel); } catch (Throwable ignored) {} sPanel = null; return; }

        LinearLayout panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xEE202124);
        int pad = dp(ctx, 8);
        panel.setPadding(pad, pad, pad, pad);

        Button off = mkBtn(ctx, "🌙 ปิดจอ");
        off.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { screenOff(ctx, wm); collapse(wm); }
        });
        Button close = mkBtn(ctx, "▽ ย่อ");
        close.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { collapse(wm); }
        });
        panel.addView(off);
        panel.addView(close);
        // ลบกล่องทิ้งจริง = push arg "hide" ผ่าน ADB (กันกดพลาดแล้วหายถาวร)

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = boxLp.x; lp.y = boxLp.y + dp(ctx, 56);
        wm.addView(panel, lp);
        sPanel = panel;
    }

    private static void collapse(WindowManager wm) {
        if (sPanel != null) { try { wm.removeView(sPanel); } catch (Throwable ignored) {} sPanel = null; }
    }

    // ลาก box แล้วปล่อย -> เกาะขอบซ้าย/ขวาที่ใกล้สุด
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

    // ดับ backlight ทั้งพาเนล (global brightness=0) -> แถบระบบมืดตามด้วย
    private static void killBrightness(Context ctx) {
        try {
            android.content.ContentResolver cr = ctx.getContentResolver();
            sPrevMode = android.provider.Settings.System.getInt(cr, "screen_brightness_mode", 0);
            sPrevBrightness = android.provider.Settings.System.getInt(cr, "screen_brightness", 120);
            android.provider.Settings.System.putInt(cr, "screen_brightness_mode", 0); // manual
            android.provider.Settings.System.putInt(cr, "screen_brightness", 0);
        } catch (Throwable ignored) {}
    }

    private static void restoreBrightness() {
        try {
            if (sApp != null && sPrevBrightness >= 0) {
                android.content.ContentResolver cr = sApp.getContentResolver();
                android.provider.Settings.System.putInt(cr, "screen_brightness_mode", sPrevMode < 0 ? 0 : sPrevMode);
                android.provider.Settings.System.putInt(cr, "screen_brightness", sPrevBrightness);
            }
        } catch (Throwable ignored) {}
        sPrevBrightness = -1; sPrevMode = -1;
    }

    private static Button mkBtn(Context ctx, String text) {
        Button b = new Button(ctx);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(0xFF3C4043);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                dp(ctx, 160), WindowManager.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(ctx, 4);
        b.setLayoutParams(p);
        return b;
    }

    // จอดำเต็มจอ + ดับ backlight (screenBrightness=0 มีผลเฉพาะตอน window นี้อยู่ — แตะเพื่อคืนจอ)
    private static void screenOff(final Context ctx, final WindowManager wm) {
        if (sBlack != null) return;
        final View black = new View(ctx);
        black.setBackgroundColor(Color.BLACK);

        // ขนาดจอจริง (รวม status/nav bar) เพื่อครอบเต็มจริง ๆ
        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                dm.widthPixels, dm.heightPixels,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
                PixelFormat.OPAQUE);
        lp.screenBrightness = 0.0f; // เผื่อ backlight override
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = 0; lp.y = 0;

        killBrightness(ctx); // ดับไฟจอทั้งพาเนล (รวมแถบระบบ)

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
}
