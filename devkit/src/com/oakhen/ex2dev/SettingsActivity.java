package com.oakhen.ex2dev;

import android.app.Activity;
import android.content.Context;
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

import java.util.ArrayList;

/**
 * ศูนย์ควบคุม EX2 BY OAKHEN — สวิตช์เปิด/ปิด "แต่ละฟีเจอร์" (จำค่าใน SharedPreferences)
 *   • กล่องลอย (master)           -> โชว์/ซ่อนกล่องทันที + gate ฟีเจอร์ลูก
 *   • เริ่มอัตโนมัติหลังบูต        -> DevService เช็คตอนบูตก่อนโผล่กล่อง
 *   • ปุ่ม 🌚 ปิดจอ / 🌙 หรี่จอ    -> FloatingBox เช็คตอนกางเมนูว่าจะโชว์ปุ่มไหน
 *   • กระจกไว 300ms               -> ฝังในแอป (แถวข้อมูล ไม่ใช่สวิตช์ — ไม่ทำสวิตช์หลอก)
 */
public class SettingsActivity extends Activity {

    // สีแบรนด์
    private static final int BG    = 0xFF1B1C1E;
    private static final int CARD  = 0xFF2A2B2E;
    private static final int CLAY  = 0xFFD97757;
    private static final int CREAM = 0xFFF2EFE9;
    private static final int MUTED = 0xFF9AA0A6;

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
        root.setPadding(pad, dp(24), pad, dp(24));
        scroll.addView(root);

        // ---------- header ----------
        TextView title = new TextView(this);
        title.setText("EX2 BY OAKHEN");
        title.setTextColor(CLAY);
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("ศูนย์ควบคุมฟีเจอร์");
        sub.setTextColor(MUTED);
        sub.setTextSize(14);
        root.addView(sub, marginTop(dp(2)));

        // ---------- master: กล่องลอย ----------
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

        // ---------- ฟีเจอร์ลูก ----------
        Switch autostart = child(root, "เริ่มอัตโนมัติหลังบูต",
                "โผล่กล่องเองเมื่อสตาร์ทรถ/รีบูตจอ",
                Prefs.KEY_AUTOSTART, true);
        autostart.setOnCheckedChangeListener(saver(Prefs.KEY_AUTOSTART));

        Switch btnOff = child(root, "ปุ่ม 🌚 ปิดจอ",
                "ในเมนูกล่อง — ดับ backlight ทั้งจอ แตะปลุก",
                Prefs.KEY_BTN_OFF, true);
        btnOff.setOnCheckedChangeListener(saver(Prefs.KEY_BTN_OFF));

        Switch btnDim = child(root, "ปุ่ม 🌙 หรี่จอ",
                "ในเมนูกล่อง — หรี่จอ (สำรอง) แตะปลุก",
                Prefs.KEY_BTN_DIM, true);
        btnDim.setOnCheckedChangeListener(saver(Prefs.KEY_BTN_DIM));

        // ---------- แถวข้อมูล (ไม่ใช่สวิตช์) ----------
        infoRow(root, "กระจกไว 300ms",
                "ลดกระจกเร็วขึ้นเมื่อเปิดประตูรถจอด · ทำงานอัตโนมัติ (ฝังในแอป)");

        setChildrenEnabled(master.isChecked());
        setContentView(scroll);
    }

    // ---------- builders ----------

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
