package com.oakhen.ex2dev;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * หน้า "แบ่งจอ": เลือก 2 หรือ 3 แอพ → เปิดเรียงเต็มจอเป็นคอลัมน์ (โหมดหน้าต่างอิสระ)
 * ปุ่ม "ชุดล่าสุด" เปิดชุดเดิมได้ทันที · "คืนเต็มจอ" ย้ายทุกหน้าต่างกลับเต็มจอ
 */
public class SplitActivity extends Activity {

    private static final int BG = 0xFF1B1C1E, CARD = 0xFF2A2B2E, CLAY = 0xFFD97757, CREAM = 0xFFF2EFE9, MUTED = 0xFF9AA0A6;
    private static final String K_COUNT = "split_count", K_LAST = "split_last";

    private SharedPreferences sp;
    private int count = 2;
    private final ArrayList<ComponentName> picked = new ArrayList<ComponentName>();
    private final ArrayList<View> cells = new ArrayList<View>();
    private final ArrayList<ComponentName> cellCn = new ArrayList<ComponentName>();
    private Button go, two, three;
    private TextView hint;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = Prefs.of(this);
        count = sp.getInt(K_COUNT, 2);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(24));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("แบ่งจอ");
        title.setTextColor(CLAY);
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        if (!SplitScreen.freeformReady(this)) {
            addSetupCard(root);
            setContentView(scroll);
            return;
        }

        // แถวปุ่มหลัก
        LinearLayout bar = row(root);
        two = chip(bar, "2 จอ", new View.OnClickListener() { public void onClick(View v) { setCount(2); } });
        three = chip(bar, "3 จอ", new View.OnClickListener() { public void onClick(View v) { setCount(3); } });
        final String last = sp.getString(K_LAST, "");
        if (last.length() > 0) {
            chip(bar, "▶ ชุดล่าสุด", new View.OnClickListener() { public void onClick(View v) { launch(parse(last)); } });
        }
        chip(bar, "⤢ คืนเต็มจอ", new View.OnClickListener() {
            public void onClick(View v) {
                final Context app = getApplicationContext();
                SplitScreen.worker().post(new Runnable() { public void run() { SplitScreen.exitAll(app); } });
                toast("คืนเต็มจอแล้ว");
            }
        });

        hint = new TextView(this);
        hint.setTextColor(MUTED);
        hint.setTextSize(15);
        root.addView(hint, top(dp(14)));

        // ตารางแอพ
        final GridLayout grid = new GridLayout(this);
        grid.setColumnCount(6);
        root.addView(grid, top(dp(10)));
        // โหลดรายชื่อ+ไอคอนแอพเบื้องหลัง (บนจอรถใช้ ~5 วิ ถ้าทำบน main จะค้างเป็นจอว่าง)
        final Context ctx = getApplicationContext();
        new Thread(new Runnable() {
            public void run() {
                final PackageManager pm = ctx.getPackageManager();
                final List<ResolveInfo> list = SplitScreen.launchables(ctx);
                final List<android.graphics.drawable.Drawable> icons = new ArrayList<android.graphics.drawable.Drawable>();
                final List<String> labels = new ArrayList<String>();
                for (ResolveInfo r : list) { icons.add(r.loadIcon(pm)); labels.add(String.valueOf(r.loadLabel(pm))); }
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (isFinishing()) return;
                        for (int i = 0; i < list.size(); i++) {
                            ResolveInfo r = list.get(i);
                            final ComponentName cn = new ComponentName(r.activityInfo.packageName, r.activityInfo.name);
                            View cell = appCell(icons.get(i), labels.get(i));
                            cell.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { toggle(cn); } });
                            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                            lp.width = dp(140); lp.setMargins(dp(4), dp(4), dp(4), dp(4));
                            grid.addView(cell, lp);
                            cells.add(cell); cellCn.add(cn);
                        }
                        refresh();
                    }
                });
            }
        }, "ex2-split-load").start();

        go = new Button(this);
        go.setAllCaps(false);
        go.setTextSize(18);
        go.setTextColor(0xFF1B1C1E);
        go.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { launch(new ArrayList<ComponentName>(picked)); } });
        root.addView(go, top(dp(16)));

        setCount(count);
        setContentView(scroll);
    }

    // ---------- ตั้งค่าครั้งแรก ----------
    private void addSetupCard(LinearLayout root) {
        TextView t = new TextView(this);
        t.setText("ยังไม่ได้เปิดโหมดแบ่งจอ\nกดปุ่มด้านล่าง แล้วจอจะรีบูต 1 ครั้ง (ครั้งเดียว) จากนั้นใช้ได้ตลอด");
        t.setTextColor(CREAM);
        t.setTextSize(17);
        t.setPadding(dp(16), dp(16), dp(16), dp(16));
        t.setBackground(bg(CARD));
        root.addView(t, top(dp(16)));
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText("เปิดโหมดแบ่งจอ + รีบูตจอ");
        b.setTextSize(18);
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                SplitScreen.enableFreeform(SplitActivity.this);
                new AlertDialog.Builder(SplitActivity.this)
                        .setTitle("รีบูตจอตอนนี้?")
                        .setMessage("ตั้งค่าเรียบร้อย ต้องรีบูตจอ 1 ครั้งให้มีผล\n(จอดรถก่อน — ระหว่างรีบูตจอจะดับประมาณ 1 นาที)")
                        .setPositiveButton("รีบูต", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int w) {
                                try { ((PowerManager) getSystemService(Context.POWER_SERVICE)).reboot(null); }
                                catch (Throwable e) { toast("รีบูตเองไม่ได้ — ปิด/เปิดจอเอง"); }
                            }
                        })
                        .setNegativeButton("ไว้ทีหลัง", null)
                        .show();
            }
        });
        root.addView(b, top(dp(14)));
    }

    // ---------- เลือกแอพ ----------
    private void setCount(int n) {
        count = n;
        sp.edit().putInt(K_COUNT, n).apply();
        while (picked.size() > n) picked.remove(picked.size() - 1);
        two.setBackground(bg(n == 2 ? CLAY : CARD));
        three.setBackground(bg(n == 3 ? CLAY : CARD));
        refresh();
    }

    private void toggle(ComponentName cn) {
        if (picked.contains(cn)) picked.remove(cn);
        else if (samePkgPicked(cn)) toast("เลือกแอพนี้ไปแล้ว (แพ็กเกจเดียวกันอยู่ได้ช่องเดียว)");
        else if (picked.size() < count) picked.add(cn);
        else toast("เลือกได้ " + count + " แอพ — แตะแอพที่เลือกไว้เพื่อยกเลิก");
        refresh();
    }

    /** แพ็กเกจเดียวกันหลายไอคอน (เช่น วิทยุ/เพลง USB ในแอพเดียว) = task เดียว → แบ่งสองช่องไม่ได้ */
    private boolean samePkgPicked(ComponentName cn) {
        for (ComponentName p : picked) if (p.getPackageName().equals(cn.getPackageName())) return true;
        return false;
    }

    private void refresh() {
        for (int i = 0; i < cells.size(); i++) {
            int idx = picked.indexOf(cellCn.get(i));
            FrameLayout f = (FrameLayout) cells.get(i);
            TextView badge = (TextView) f.getChildAt(1);
            f.setBackground(bg(idx >= 0 ? 0xFF4A3A33 : CARD));
            badge.setVisibility(idx >= 0 ? View.VISIBLE : View.GONE);
            badge.setText(String.valueOf(idx + 1));
        }
        hint.setText(cells.isEmpty() ? "กำลังโหลดรายชื่อแอพ…"
                : "แตะเลือก " + count + " แอพ ตามลำดับ ซ้าย → ขวา  (เลือกแล้ว " + picked.size() + "/" + count + ")");
        boolean ready = picked.size() == count;
        go.setEnabled(ready);
        go.setText(ready ? "เปิดแบ่งจอ " + count + " แอพ" : "เลือกให้ครบ " + count + " แอพ");
        go.setBackground(bg(ready ? CLAY : CARD));
    }

    // ---------- เปิด ----------
    private void launch(final List<ComponentName> apps) {
        if (apps.isEmpty()) return;
        final Context app = getApplicationContext();
        finish();
        overridePendingTransition(0, 0);   // ปิดหน้านี้ทันที ไม่มีแอนิเมชันค้างบังหน้าต่าง
        SplitScreen.launchSet(app, apps);
    }

    private static List<ComponentName> parse(String s) { return SplitScreen.parse(s); }

    // ---------- UI helpers ----------
    private View appCell(android.graphics.drawable.Drawable icon, String label) {
        FrameLayout f = new FrameLayout(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(8), dp(12), dp(8), dp(10));
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(icon);
        col.addView(iv, new LinearLayout.LayoutParams(dp(56), dp(56)));
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(CREAM);
        tv.setTextSize(13);
        tv.setGravity(Gravity.CENTER);
        tv.setMaxLines(2);
        col.addView(tv, top(dp(6)));
        f.addView(col);
        TextView badge = new TextView(this);
        badge.setTextColor(0xFF1B1C1E);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(bg(CLAY));
        badge.setVisibility(View.GONE);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.TOP | Gravity.END);
        bl.setMargins(0, dp(4), dp(4), 0);
        f.addView(badge, bl);
        f.setBackground(bg(CARD));
        return f;
    }

    private LinearLayout row(LinearLayout parent) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(r, top(dp(14)));
        return r;
    }

    private Button chip(LinearLayout parent, String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextColor(CREAM);
        b.setTextSize(16);
        b.setBackground(bg(CARD));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(52));
        lp.setMargins(0, 0, dp(10), 0);
        b.setPadding(dp(18), 0, dp(18), 0);
        parent.addView(b, lp);
        return b;
    }

    private GradientDrawable bg(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(12));
        return g;
    }

    private LinearLayout.LayoutParams top(int m) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = m;
        return lp;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
}
