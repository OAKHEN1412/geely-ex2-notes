package com.oakhen.ex2dev;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * ศูนย์ควบคุม EX2 BY OAKHEN — hub เดียวรวม 2 ส่วน:
 *   A) ฟีเจอร์ EX2 (เพิ่มโดยเรา)  — สวิตช์เปิด/ปิดต่อฟีเจอร์ (กล่องลอย/เริ่มอัตโนมัติ/ปุ่มปิดจอ/ปุ่มหรี่จอ) + กระจกไว
 *   B) ฟีเจอร์รถ (แอพเดิม Geely)  — การ์ดลัดเปิดหน้าฟีเจอร์เดิม (เปิด MainActivity + best-effort deep-link route)
 * UI เดิม (Compose) ไม่ถูกแตะ — ยังทำงานครบ; hub นี้เป็นประตูเดียวที่รวมทั้งของเราและของเดิม
 */
public class SettingsActivity extends Activity {

    // สีแบรนด์
    private static final int BG    = 0xFF1B1C1E;
    private static final int CARD  = 0xFF2A2B2E;
    private static final int CLAY  = 0xFFD97757;
    private static final int CREAM = 0xFFF2EFE9;
    private static final int MUTED = 0xFF9AA0A6;

    private static final String CAR_MAIN = "com.geely.ex2.tools.MainActivity";

    private SharedPreferences sp;
    private final ArrayList<Switch> children = new ArrayList<Switch>(); // สวิตช์ลูก (ปิดตอน master ปิด)

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        final Context app = getApplicationContext();
        sp = Prefs.of(this);
        int pad = dp(16);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(24), pad, dp(28));
        scroll.addView(root);

        // ---------- header ----------
        TextView title = new TextView(this);
        title.setText("EX2 BY OAKHEN");
        title.setTextColor(CLAY);
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("ศูนย์ควบคุม");
        sub.setTextColor(MUTED);
        sub.setTextSize(14);
        root.addView(sub, marginTop(dp(2)));

        // ========== A) ฟีเจอร์ EX2 (ของเรา) ==========
        sectionLabel(root, "ฟีเจอร์ EX2 · เพิ่มโดยเรา");

        final Switch master = row(root, "กล่องลอย (Floating Box)",
                "กล่อง ☰ ลอยบนจอ · แตะเพื่อกางเมนูปิด/หรี่จอ",
                Prefs.KEY_FLOATING, true, false);
        master.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton btn, boolean on) {
                sp.edit().putBoolean(Prefs.KEY_FLOATING, on).apply();
                if (on) FloatingBox.show(app); else FloatingBox.hide(app);
                setChildrenEnabled(on);
            }
        });

        Switch autostart = child(root, "เริ่มอัตโนมัติหลังบูต",
                "โผล่กล่องเองเมื่อสตาร์ทรถ/รีบูตจอ", Prefs.KEY_AUTOSTART, true);
        autostart.setOnCheckedChangeListener(saver(Prefs.KEY_AUTOSTART));

        Switch btnOff = child(root, "ปุ่ม 🌚 ปิดจอ",
                "ในเมนูกล่อง — ดับ backlight ทั้งจอ แตะปลุก", Prefs.KEY_BTN_OFF, true);
        btnOff.setOnCheckedChangeListener(saver(Prefs.KEY_BTN_OFF));

        Switch btnDim = child(root, "ปุ่ม 🌙 หรี่จอ",
                "ในเมนูกล่อง — หรี่จอ (สำรอง) แตะปลุก", Prefs.KEY_BTN_DIM, true);
        btnDim.setOnCheckedChangeListener(saver(Prefs.KEY_BTN_DIM));

        infoRow(root, "กระจกไว 300ms",
                "ลดกระจกเร็วขึ้นเมื่อเปิดประตูรถจอด · ทำงานอัตโนมัติ (ฝังในแอป)");

        // ========== B) ฟีเจอร์รถ (แอพเดิม Geely) ==========
        sectionLabel(root, "ฟีเจอร์รถ · แอพเดิม (Geely)");
        TextView hint = new TextView(this);
        hint.setText("แตะเพื่อเปิดหน้าเดิมของแอพ Geely");
        hint.setTextColor(MUTED); hint.setTextSize(13);
        root.addView(hint, marginTop(dp(2)));

        // การ์ด 2 คอลัมน์ (emoji + label -> เปิด MainActivity + route)
        String[][] feats = {
            {"🏠", "ภาพรวม",            "home"},
            {"🪟", "ปิดกระจกอัตโนมัติ",   "auto_window"},
            {"🔊", "เสียง AVAS",         "avas"},
            {"💡", "ไฟ Ambient",        "ambient_light"},
            {"🔋", "แบตเตอรี่",          "battery"},
            {"🚗", "การขับขี่",          "driving"},
            {"❄️", "ปรับอากาศ",         "hvac"},
            {"⚙️", "ตั้งค่าเดิม",         "settings"},
        };
        LinearLayout rowLay = null;
        for (int i = 0; i < feats.length; i++) {
            if (i % 2 == 0) {
                rowLay = new LinearLayout(this);
                rowLay.setOrientation(LinearLayout.HORIZONTAL);
                root.addView(rowLay, marginTop(dp(12)));
            }
            rowLay.addView(carCard(feats[i][0], feats[i][1], feats[i][2]),
                    cellParams(i % 2 == 0));
        }

        // ปุ่มเปิดแอพเดิมเต็ม (เผื่อ route ไม่ตรง)
        LinearLayout openAll = new LinearLayout(this);
        openAll.setBackground(cardBg());
        openAll.setGravity(Gravity.CENTER);
        int op = dp(16); openAll.setPadding(op, op, op, op);
        openAll.setClickable(true);
        TextView oa = new TextView(this);
        oa.setText("↗  เปิดแอพเดิมเต็ม (ทุกฟีเจอร์รถ)");
        oa.setTextColor(CLAY); oa.setTextSize(16);
        openAll.addView(oa);
        openAll.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openCar(null); }
        });
        root.addView(openAll, marginTop(dp(12)));

        setChildrenEnabled(master.isChecked());
        setContentView(scroll);
    }

    /** เปิด MainActivity ของแอพเดิม + ยิง route extra แบบ best-effort หลาย key (ไม่ตรงก็เปิดหน้าหลัก) */
    private void openCar(String route) {
        try {
            Intent i = new Intent();
            i.setClassName(getPackageName(), CAR_MAIN);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            if (route != null) {
                for (String k : new String[]{"route", "screen", "destination",
                        "start_destination", "nav_route", "tab"}) {
                    i.putExtra(k, route);
                }
            }
            startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(this, "เปิดแอพเดิมไม่ได้", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------- builders ----------

    private void sectionLabel(LinearLayout parent, String text) {
        TextView t = new TextView(this);
        t.setText(text.toUpperCase());
        t.setTextColor(CLAY);
        t.setTextSize(12);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams lp = marginTop(dp(26));
        parent.addView(t, lp);
    }

    /** การ์ดฟีเจอร์รถ (แตะเปิด MainActivity) */
    private View carCard(String emoji, String label, final String route) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(cardBg());
        int p = dp(14); card.setPadding(p, p, p, p);
        card.setClickable(true);

        TextView ic = new TextView(this);
        ic.setText(emoji); ic.setTextSize(20);
        card.addView(ic);

        TextView t = new TextView(this);
        t.setText("  " + label); t.setTextColor(CREAM); t.setTextSize(15);
        card.addView(t);

        card.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openCar(route); }
        });
        return card;
    }

    private LinearLayout.LayoutParams cellParams(boolean first) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        if (!first) lp.leftMargin = dp(12);
        return lp;
    }

    /** แถวสวิตช์ในการ์ด; child=true จะถูกปิดใช้งานเมื่อ master ปิด */
    private Switch row(LinearLayout parent, String title, String desc, String key, boolean def, boolean child) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(cardBg());
        int p = dp(14);
        card.setPadding(p, p, p, p);
        parent.addView(card, marginTop(dp(12)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        card.addView(texts, tlp);

        TextView t = new TextView(this);
        t.setText(title); t.setTextColor(CREAM); t.setTextSize(17);
        texts.addView(t);

        TextView d = new TextView(this);
        d.setText(desc); d.setTextColor(MUTED); d.setTextSize(13);
        texts.addView(d, marginTop(dp(2)));

        Switch sw = new Switch(this);
        sw.setChecked(sp.getBoolean(key, def));
        tintSwitch(sw);
        card.addView(sw);

        if (child) children.add(sw);
        return sw;
    }

    private Switch child(LinearLayout parent, String title, String desc, String key, boolean def) {
        return row(parent, title, desc, key, def, true);
    }

    private void infoRow(LinearLayout parent, String title, String desc) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(cardBg());
        int p = dp(14);
        card.setPadding(p, p, p, p);
        parent.addView(card, marginTop(dp(12)));

        TextView t = new TextView(this);
        t.setText("● " + title); t.setTextColor(CLAY); t.setTextSize(16);
        card.addView(t);

        TextView d = new TextView(this);
        d.setText(desc); d.setTextColor(MUTED); d.setTextSize(13);
        card.addView(d, marginTop(dp(2)));
    }

    private CompoundButton.OnCheckedChangeListener saver(final String key) {
        return new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton btn, boolean on) {
                sp.edit().putBoolean(key, on).apply();
            }
        };
    }

    private void setChildrenEnabled(boolean on) {
        for (Switch s : children) {
            s.setEnabled(on);
            View card = (View) s.getParent();
            if (card != null) card.setAlpha(on ? 1f : 0.4f);
        }
    }

    // ---------- style helpers ----------
    private GradientDrawable cardBg() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(dp(14));
        return g;
    }
    private void tintSwitch(Switch sw) {
        int[][] states = { { android.R.attr.state_checked }, {} };
        int[] thumb = { CLAY, 0xFFBDC1C6 };
        int[] track = { 0x80D97757, 0x55FFFFFF };
        sw.setThumbTintList(new ColorStateList(states, thumb));
        sw.setTrackTintList(new ColorStateList(states, track));
    }
    private LinearLayout.LayoutParams marginTop(int m) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = m;
        return lp;
    }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
}
