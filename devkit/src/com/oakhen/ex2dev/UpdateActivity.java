package com.oakhen.ex2dev;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * หน้า "อัปเดตแอป": EX2 BY OAKHEN (GitHub Releases) + แอปที่ลงไว้ (APKPure, เลือกเวอร์ชันที่ลงเครื่องนี้ได้)
 * ตรวจเมื่อกดเท่านั้น (ไม่โหลดเองเบื้องหลัง — ใช้เน็ตจากมือถือ) · โหลดทีละแอป
 */
public class UpdateActivity extends Activity {
    private static final int BG = 0xFF1B1C1E, CARD = 0xFF2A2B2E, CLAY = 0xFFD97757, CREAM = 0xFFF2EFE9, MUTED = 0xFF9AA0A6;
    private LinearLayout list;
    private TextView checkAll;
    private volatile boolean busy;

    private final class Row {
        String pkg, label; TextView name, status, btn; AppUpdater.Candidate cand;
    }
    private final java.util.List<Row> rows = new java.util.ArrayList<Row>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(24));
        scroll.addView(root);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("อัปเดตแอป");
        title.setTextColor(CLAY); title.setTextSize(24); title.setTypeface(Typeface.DEFAULT_BOLD);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        checkAll = pill("ตรวจอัปเดตทั้งหมด", true);
        checkAll.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { checkAll(); } });
        head.addView(checkAll, new LinearLayout.LayoutParams(-2, dp(48)));
        root.addView(head);
        TextView note = new TextView(this);
        note.setText("แอปทั่วไปโหลดจาก APKPure — เลือกเวอร์ชันใหม่สุดที่ลง Android 9 ได้ และติดตั้งเฉพาะไฟล์ที่ลายเซ็นตรงกับแอปที่ลงอยู่ · ไฟล์ใหญ่ ใช้เน็ตมือถือ");
        note.setTextColor(MUTED); note.setTextSize(13);
        root.addView(note, top(6));

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list, top(10));
        addRow(getPackageName(), "EX2 BY OAKHEN");
        for (String[] a : AppUpdater.apps(this)) if (AppUpdater.installed(this, a[0]) != null) addRow(a[0], a[1]);
        setContentView(scroll);
    }

    private void addRow(String pkg, String label) {
        final Row r = new Row();
        r.pkg = pkg; r.label = label;
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(bg(CARD));
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        ImageView ic = new ImageView(this);
        try { Drawable d = getPackageManager().getApplicationIcon(pkg); ic.setImageDrawable(d); } catch (Throwable ignored) {}
        card.addView(ic, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(12), 0, dp(12), 0);
        PackageInfo pi = AppUpdater.installed(this, pkg);
        r.name = new TextView(this);
        r.name.setTextColor(CREAM); r.name.setTextSize(16);
        texts.addView(r.name);
        refreshName(r);
        r.status = new TextView(this);
        r.status.setText("กด \"ตรวจอัปเดตทั้งหมด\"");
        r.status.setTextColor(MUTED); r.status.setTextSize(13);
        texts.addView(r.status);
        card.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        r.btn = pill("อัปเดต", true);
        r.btn.setVisibility(View.GONE);
        r.btn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { update(r); } });
        card.addView(r.btn, new LinearLayout.LayoutParams(-2, dp(44)));
        list.addView(card, top(8));
        rows.add(r);
    }

    private void refreshName(Row r) {
        PackageInfo pi = AppUpdater.installed(this, r.pkg);
        r.name.setText(r.label + "   " + (r.pkg.equals(getPackageName()) ? "build " + AppUpdater.ownBuild(this) : (pi != null ? pi.versionName : "")));
    }

    private void checkAll() {
        if (busy) return;
        busy = true;
        checkAll.setText("กำลังตรวจ…");
        final Context app = getApplicationContext();
        new Thread(new Runnable() {
            public void run() {
                for (final Row r : rows) {
                    ui(r, "กำลังตรวจ…", false);
                    String[] msg = {""};
                    r.cand = r.pkg.equals(getPackageName()) ? AppUpdater.checkOakhen(app, msg) : AppUpdater.check(app, r.pkg, r.label, msg);
                    ui(r, msg[0], r.cand != null);
                }
                busy = false;
                runOnUiThread(new Runnable() { public void run() { checkAll.setText("ตรวจอีกครั้ง"); } });
            }
        }, "ex2-update-check").start();
    }

    private void update(final Row r) {
        if (busy || r.cand == null) return;
        busy = true;
        final Context app = getApplicationContext();
        ui(r, r.cand.oakhen ? "กำลังโหลด… (แอปนี้จะปิดแล้วเปิดใหม่เองตอนติดตั้ง)" : "กำลังโหลด…", false);
        new Thread(new Runnable() {
            public void run() {
                String[] msg = {""};
                boolean ok = AppUpdater.update(app, r.cand, new AppUpdater.Progress() {
                    public void on(String m) { ui(r, m, false); }
                }, msg);
                if (ok) r.cand = null;
                ui(r, msg[0], !ok && r.cand != null);
                busy = false;
            }
        }, "ex2-update").start();
    }

    private void ui(final Row r, final String msg, final boolean showBtn) {
        runOnUiThread(new Runnable() {
            public void run() {
                r.status.setText(msg);
                refreshName(r);
                r.status.setTextColor(showBtn ? CLAY : MUTED);
                r.btn.setVisibility(showBtn ? View.VISIBLE : View.GONE);
            }
        });
    }

    private TextView pill(String s, boolean primary) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(15); t.setGravity(Gravity.CENTER);
        t.setTextColor(primary ? BG : CREAM);
        t.setBackground(bg(primary ? CLAY : 0xFF3A3B3F));
        t.setPadding(dp(16), 0, dp(16), 0);
        return t;
    }

    private GradientDrawable bg(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(12));
        return g;
    }

    private LinearLayout.LayoutParams top(int m) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(m);
        return lp;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
}
