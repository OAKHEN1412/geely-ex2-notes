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
    public static final String KEY_TIME_TH   = "time_thai";        // โซนเวลาไทย + ซิงก์เวลาเน็ต (TimeKeeper)
    public static final String KEY_COMPASS_OFF  = "compass_off";   // ตัดเซ็นเซอร์เข็มทิศให้แอพนำทาง (ใช้ทิศ GPS) — SensorGuard
    public static final String KEY_COMPASS_PKGS = "compass_pkgs";  // แพ็กเกจที่ตัด (คั่นด้วย ,)
    public static final String KEY_SIM_OFF   = "sim_off";          // ปิดรถจำลอง SimCar (ค่าเริ่ม = เปิดเองเฉพาะเมื่อไม่มี android.car · บนรถจริงไม่เคยเปิด)
    // เข็มทิศของ Maps มาจาก Play Services (บนรถ 30 ก.ย.: UNCALI_MAG+ไจโร ถูกอ่านโดย com.google.android.gms uid 10032
    // ส่วน Maps อ่านแค่ accelerometer) · ตัด gms แล้วลูกศรหยุดหมุน (ผู้ใช้ยืนยันในรถ)
    public static final String DEF_COMPASS_PKGS = "com.google.android.gms";

    private Prefs() {}

    public static SharedPreferences of(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
    public static boolean get(Context c, String key, boolean def) {
        return of(c).getBoolean(key, def);
    }
}
