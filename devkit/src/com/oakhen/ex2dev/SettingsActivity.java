package com.oakhen.ex2dev;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/**
 * หน้าตั้งค่าของ EX2 BY OAKHEN (แยกจาก Compose UI เดิม) — มีไอคอนของตัวเองในลิ้นชักแอป
 * สวิตช์ "กล่องลอย" เปิด/ปิดฟีเจอร์ + จำค่าใน SharedPreferences (DevService เช็คตอนบูตก่อนโชว์กล่อง)
 */
public class SettingsActivity extends Activity {

    static final String PREF = "ex2dev";
    static final String KEY_FLOATING = "floating_enabled";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        final Context app = getApplicationContext();
        final SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
        float d = getResources().getDisplayMetrics().density;
        int pad = (int) (20 * d);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF202124);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("EX2 BY OAKHEN — ตั้งค่า");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        root.addView(title);

        final Switch sw = new Switch(this);
        sw.setText("  กล่องลอย (Floating Box)");
        sw.setTextColor(Color.WHITE);
        sw.setTextSize(18);
        sw.setChecked(sp.getBoolean(KEY_FLOATING, true));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = pad;
        sw.setLayoutParams(lp);
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton btn, boolean on) {
                sp.edit().putBoolean(KEY_FLOATING, on).apply();
                if (on) FloatingBox.show(app); else FloatingBox.hide(app);
            }
        });
        root.addView(sw);

        TextView hint = new TextView(this);
        hint.setText("เปิด = กล่อง ☰ ลอยบนจอ (เด้งเองหลังบูต)\nปิด = ซ่อนกล่อง\n\nในกล่อง: 🌚 ปิดจอ (มืดทั้งจอ แตะปลุก) · 🌙 หรี่จอ (สำรอง แตะปลุก)");
        hint.setTextColor(0xFFAAAAAA);
        hint.setTextSize(14);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hp.topMargin = pad;
        hint.setLayoutParams(hp);
        root.addView(hint);

        setContentView(root);
    }
}
