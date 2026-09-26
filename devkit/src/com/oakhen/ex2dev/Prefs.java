package com.oakhen.ex2dev;

import android.content.Context;
import android.content.SharedPreferences;

/** คีย์ตั้งค่ากลางของ EX2 BY OAKHEN — ใช้ร่วมกันทั้ง SettingsActivity / DevService / FloatingBox */
public final class Prefs {
    public static final String PREF = "ex2dev";

    public static final String KEY_FLOATING  = "floating_enabled"; // กล่องลอย (master)
    public static final String KEY_AUTOSTART = "auto_start";       // โผล่เองหลังบูต
    public static final String KEY_BTN_OFF   = "btn_screenoff";    // ปุ่ม 🌚 ปิดจอ ในเมนูกล่อง
    public static final String KEY_BTN_DIM   = "btn_dim";          // ปุ่ม 🌙 หรี่จอ ในเมนูกล่อง
    public static final String KEY_WIFI_ON   = "wifi_always_on";   // Wi-Fi เปิดเสมอ + สแกนหาเครือข่ายเดิม (ไม่ขึ้นกับกล่องลอย)

    private Prefs() {}

    public static SharedPreferences of(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
    public static boolean get(Context c, String key, boolean def) {
        return of(c).getBoolean(key, def);
    }
}
