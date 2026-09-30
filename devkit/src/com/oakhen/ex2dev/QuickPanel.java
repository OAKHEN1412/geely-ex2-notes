package com.oakhen.ex2dev;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * แผงควบคุมด่วน (เปิดจากกล่องลอย ☰): โหมดขับ · รีเจน · A/C · อุณหภูมิ · พัดลม
 * อ่านค่าจริงจากรถตอนเปิดและหลังกดทุกครั้ง (ไม่เชื่อค่าที่จำไว้เอง — รถเปลี่ยนเองได้จากปุ่มจริง/ตอนสตาร์ท)
 * แตะนอกแผง = ปิด
 */
final class QuickPanel {
    private static final int BG = 0xF21B1C1E, CARD = 0xFF2A2B2E, ON = 0xFFD97757, CREAM = 0xFFF2EFE9, MUTED = 0xFF9AA0A6;
    private static View sView;
    private static Handler sMain;

    private static TextView[] sDrive, sRegen;
    private static TextView sAc, sTemp, sFan, sStatus, sWash, sWashHint;
    private static Integer sWashVal;
    private static View sWashRow, sWashLabel;
    private static String sWashStatus;
    private static Integer sGear;
    private static Double sKmh;
    private static long sWashArmAt;
    private static Float sTempVal;
    private static Integer sFanVal;
    private static Boolean sAcVal;

    private QuickPanel() {}

    private static Handler main() {
        if (sMain == null) sMain = new Handler(Looper.getMainLooper());
        return sMain;
    }

    static boolean showing() { return sView != null; }

    /** เรียกบน main thread */
    static void toggle(Context ctx) {
        if (sView != null) { hide(ctx); return; }
        final Context app = ctx.getApplicationContext();
        LinearLayout root = new LinearLayout(app);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(BG);
        bg.setCornerRadius(dp(app, 18));
        root.setBackground(bg);
        int p = dp(app, 18);
        root.setPadding(p, p, p, p);

        LinearLayout head = new LinearLayout(app);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(app, "ควบคุมรถ", 20, ON);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView x = pill(app, "✕", false);
        x.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { hide(app); } });
        head.addView(x, new LinearLayout.LayoutParams(dp(app, 52), dp(app, 52)));
        root.addView(head);

        // โหมดขับ
        root.addView(label(app, "โหมดขับ"));
        final int[] driveVals = {Vhal.DRIVE_ECO, Vhal.DRIVE_COMFORT, Vhal.DRIVE_DYNAMIC};
        sDrive = segment(root, app, new String[]{"ECO", "COMFORT", "SPORT"}, new Seg() {
            public void pick(final int i) {
                run(app, new Runnable() { public void run() { Vhal.setInt(app, Vhal.DRIVE_MODE, driveVals[i], Vhal.DRIVE_AREAS); } });
            }
        });
        // รีเจน
        root.addView(label(app, "รีเจน (หน่วงชาร์จกลับ)"));
        final int[] regenVals = {Vhal.REGEN_LOW, Vhal.REGEN_MID, Vhal.REGEN_HIGH};
        sRegen = segment(root, app, new String[]{"ต่ำ", "กลาง", "สูง"}, new Seg() {
            public void pick(final int i) {
                run(app, new Runnable() { public void run() { Vhal.setInt(app, Vhal.REGEN, regenVals[i], Vhal.REGEN_AREAS); } });
            }
        });
        // แอร์
        root.addView(label(app, "แอร์"));
        LinearLayout hv = new LinearLayout(app);
        hv.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(hv, top(app, 6));
        sAc = pill(app, "A/C", false);
        sAc.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                final boolean to = sAcVal == null || !sAcVal;
                run(app, new Runnable() { public void run() { Vhal.setBool(app, Vhal.HVAC_AC_ON, to, Vhal.HVAC_AREAS); } });
            }
        });
        hv.addView(sAc, new LinearLayout.LayoutParams(dp(app, 96), dp(app, 56)));
        sTemp = stepper(hv, app, new Step() {
            public void step(final int d) {
                if (sTempVal == null) return;
                final float to = Math.max(16f, Math.min(32f, Math.round((sTempVal + d * 0.5f) * 2f) / 2f));
                sTempVal = to; sTemp.setText(fmtTemp(to));
                run(app, new Runnable() { public void run() { Vhal.setFloat(app, Vhal.HVAC_TEMP, to, Vhal.TEMP_AREAS); } });
            }
        });
        sFan = stepper(hv, app, new Step() {
            public void step(final int d) {
                if (sFanVal == null) return;
                final int to = Math.max(1, Math.min(7, sFanVal + d));
                sFanVal = to; sFan.setText("พัดลม " + to);
                run(app, new Runnable() { public void run() { Vhal.setInt(app, Vhal.HVAC_FAN, to, Vhal.HVAC_AREAS); } });
            }
        });

        // จอดซ้อนคัน = โหมดล้างรถแบบสายพาน (MOVING) ของรถเอง: ล็อครถแล้วยังค้างเกียร์ N
        sWashLabel = label(app, "จอดซ้อนคัน (ค้างเกียร์ N ตอนล็อครถ)");
        sWashLabel.setVisibility(View.GONE);
        root.addView(sWashLabel);
        LinearLayout wr = new LinearLayout(app);
        sWashRow = wr;
        wr.setVisibility(View.GONE);
        wr.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(wr, top(app, 6));
        sWash = pill(app, "…", false);
        sWash.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (sWashVal == null || !"active".equals(sWashStatus)) return;
                final boolean on = sWashVal == EcarxFn.WASH_MOVING;
                // เงื่อนไขเดียวกับแอปของรถ (AutoSceneDirector beforeSetOn): ต้องเกียร์ N + รถหยุด
                if (!on && (sGear == null || sGear != 1)) { sWashHint.setText("เข้าเกียร์ N ก่อน แล้วแตะอีกครั้ง"); sWashHint.setTextColor(ON); return; }
                if (!on && sKmh != null && sKmh > 3) { sWashHint.setText("หยุดรถก่อน"); sWashHint.setTextColor(ON); return; }
                if (!on && System.currentTimeMillis() - sWashArmAt > 4000) {   // เปิด = แตะ 2 ครั้ง (กันแตะพลาด)
                    sWashArmAt = System.currentTimeMillis();
                    sWash.setText("แตะอีกครั้งเพื่อยืนยัน");
                    return;
                }
                sWashArmAt = 0;
                run(app, new Runnable() {
                    public void run() { EcarxFn.set(app, EcarxFn.WASH_MODE, on ? EcarxFn.WASH_OFF : EcarxFn.WASH_MOVING); }
                });
            }
        });
        wr.addView(sWash, new LinearLayout.LayoutParams(dp(app, 230), dp(app, 56)));
        sWashHint = text(app, "โหมดล้างรถแบบสายพานของรถเอง · จอดพื้นราบ เข้าเกียร์ N ก่อน · ล้อไม่ล็อก รถเข็นได้", 12, MUTED);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(0, -2, 1);
        hl.leftMargin = dp(app, 12);
        wr.addView(sWashHint, hl);

        sStatus = text(app, "กำลังอ่านค่าจากรถ…", 13, MUTED);
        root.addView(sStatus, top(app, 12));

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(dp(app, 560), WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.CENTER;
        root.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_OUTSIDE) { hide(app); return true; }
                return false;
            }
        });
        try {
            ((WindowManager) app.getSystemService(Context.WINDOW_SERVICE)).addView(root, lp);
            sView = root;
        } catch (Throwable t) { return; }
        refresh(app);
    }

    static void hide(Context ctx) {
        if (sView == null) return;
        try { ((WindowManager) ctx.getApplicationContext().getSystemService(Context.WINDOW_SERVICE)).removeView(sView); } catch (Throwable ignored) {}
        sView = null;
    }

    /** สั่งบนเธรด VHAL แล้วอ่านค่าจริงกลับมาแสดง */
    private static void run(final Context app, final Runnable r) {
        Vhal.thread().post(r);
        Vhal.thread().postDelayed(new Runnable() { public void run() { read(app); } }, 400);
    }

    private static void refresh(final Context app) {
        Vhal.thread().post(new Runnable() { public void run() { read(app); } });
    }

    /** เธรด VHAL */
    private static void read(final Context app) {
        final Integer drive = Vhal.getInt(app, Vhal.DRIVE_MODE, Vhal.DRIVE_AREAS);
        final Integer regen = Vhal.getInt(app, Vhal.REGEN, Vhal.REGEN_AREAS);
        final Boolean ac = Vhal.getBool(app, Vhal.HVAC_AC_ON, Vhal.HVAC_AREAS);
        final Float temp = Vhal.getFloat(app, Vhal.HVAC_TEMP, Vhal.TEMP_AREAS);
        final Integer fan = Vhal.getInt(app, Vhal.HVAC_FAN, Vhal.HVAC_AREAS);
        final String washSt = EcarxFn.status(app, EcarxFn.WASH_MODE);
        final Integer wash = washSt != null ? EcarxFn.get(app, EcarxFn.WASH_MODE) : null;
        final Integer gear = Vhal.getInt(app, TripLog.GEAR, TripLog.GLOBAL);
        final Float spd = Vhal.getFloat(app, TripLog.SPEED, TripLog.GLOBAL);
        android.util.Log.i(EcarxFn.TAG, "wash status=" + washSt + " value=" + (wash == null ? null : EcarxFn.hex(wash))
                + " supported=" + java.util.Arrays.toString(EcarxFn.supportedValues(app, EcarxFn.WASH_MODE)) + " gear=" + gear);
        final boolean car = Vhal.cpm(app) != null;
        main().post(new Runnable() {
            public void run() {
                if (sView == null) return;
                highlight(sDrive, drive == null ? -1 : drive - Vhal.DRIVE_ECO);
                highlight(sRegen, regen == null ? -1 : regen - Vhal.REGEN_LOW);
                sAcVal = ac;
                paint(sAc, ac != null && ac);
                sAc.setText(ac == null ? "A/C ?" : ac ? "A/C เปิด" : "A/C ปิด");
                sTempVal = temp;
                sTemp.setText(temp == null ? "– °C" : fmtTemp(temp));
                sFanVal = fan;
                sFan.setText(fan == null ? "พัดลม –" : "พัดลม " + fan);
                sWashVal = wash; sWashStatus = washSt; sGear = gear; sKmh = spd == null ? null : (double) Math.abs(spd);
                // EX2 (30 ก.ย.): notavailable ทั้งโหมดล้างรถ, TRSM_PARK_LOCK, P_GEAR_UNLOCK → ไม่มีทางค้าง N ผ่านระบบรถ → ซ่อนแถว
                boolean show = washSt != null && !"notavailable".equals(washSt);
                sWashRow.setVisibility(show ? View.VISIBLE : View.GONE);
                sWashLabel.setVisibility(show ? View.VISIBLE : View.GONE);
                if (washSt == null) { sWash.setText("ไม่มีระบบ ECarX"); paint(sWash, false); }
                else if (!"active".equals(washSt)) { sWash.setText("รถไม่รองรับ (" + washSt + ")"); paint(sWash, false); }
                else if (wash == null) { sWash.setText("อ่านสถานะไม่ได้"); paint(sWash, false); }
                else if (System.currentTimeMillis() - sWashArmAt > 4000) {
                    boolean on = wash == EcarxFn.WASH_MOVING;
                    sWash.setText(on ? "เปิดอยู่ · แตะเพื่อปิด" : "ปิดอยู่ · แตะเพื่อเปิด");
                    paint(sWash, on);
                }
                sStatus.setText(!car ? "เชื่อมต่อระบบรถไม่ได้ (เครื่องนี้ไม่มี VHAL?)"
                        : (drive == null && regen == null && ac == null) ? "อ่านค่าจากรถไม่ได้ — ปุ่มอาจไม่ทำงาน"
                        : "ค่าที่แสดง = ค่าจริงจากรถ");
            }
        });
    }

    // ---------- UI helpers ----------
    private interface Seg { void pick(int i); }
    private interface Step { void step(int d); }

    private static TextView[] segment(LinearLayout parent, Context app, String[] names, final Seg cb) {
        LinearLayout row = new LinearLayout(app);
        parent.addView(row, top(app, 6));
        TextView[] out = new TextView[names.length];
        for (int i = 0; i < names.length; i++) {
            final int k = i;
            TextView t = pill(app, names[i], false);
            t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { cb.pick(k); } });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(app, 56), 1);
            if (i > 0) lp.leftMargin = dp(app, 8);
            row.addView(t, lp);
            out[i] = t;
        }
        return out;
    }

    private static TextView stepper(LinearLayout parent, Context app, final Step cb) {
        TextView minus = pill(app, "−", false), plus = pill(app, "+", false);
        TextView val = text(app, "–", 17, CREAM);
        val.setGravity(Gravity.CENTER);
        minus.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { cb.step(-1); } });
        plus.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { cb.step(1); } });
        LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(dp(app, 52), dp(app, 56));
        b.leftMargin = dp(app, 10);
        parent.addView(minus, b);
        parent.addView(val, new LinearLayout.LayoutParams(dp(app, 96), dp(app, 56)));
        parent.addView(plus, new LinearLayout.LayoutParams(dp(app, 52), dp(app, 56)));
        return val;
    }

    private static void highlight(TextView[] seg, int idx) { for (int i = 0; i < seg.length; i++) paint(seg[i], i == idx); }

    private static void paint(TextView t, boolean on) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(on ? ON : CARD);
        g.setCornerRadius(t.getResources().getDisplayMetrics().density * 12);
        t.setBackground(g);
        t.setTextColor(on ? 0xFF1B1C1E : CREAM);
    }

    private static TextView pill(Context app, String s, boolean on) {
        TextView t = text(app, s, 17, CREAM);
        t.setGravity(Gravity.CENTER);
        paint(t, on);
        return t;
    }

    private static TextView label(Context app, String s) {
        TextView t = text(app, s, 14, MUTED);
        t.setPadding(0, dp(app, 14), 0, 0);
        return t;
    }

    private static TextView text(Context app, String s, int sp, int color) {
        TextView t = new TextView(app);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        return t;
    }

    private static String fmtTemp(float v) { return String.format(java.util.Locale.US, "%.1f °C", v); }

    private static LinearLayout.LayoutParams top(Context app, int m) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(app, m);
        return lp;
    }

    private static int dp(Context c, int v) { return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f); }
}
