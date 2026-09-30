package com.oakhen.ex2dev;

import android.app.AlarmManager;
import android.content.Context;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import java.util.TimeZone;

/**
 * เวลาเครื่องให้ตรงเวลาไทย (สวิตช์ในหน้า OAKHEN · ค่าเริ่ม เปิด)
 * ที่เจอบนรถ 29 ก.ย.: จอใช้โซน CST (จีน) → นาฬิกาบนจอเร็วกว่าจริง 1 ชม. (iPhone ใน CarPlay ถูก)
 *  - โซนเวลา ≠ Asia/Bangkok → AlarmManager.setTimeZone (uid system มี SET_TIME_ZONE)
 *  - ตั้ง ntp_server = time.google.com + auto_time = 1 ให้ระบบซิงก์เองได้ (ค่าโรงงานอาจชี้ NTP ที่เข้าไม่ถึง)
 *  - ถามเวลาจาก time.google.com (SNTP, android.net.SntpClient ซ่อน → reflection) ถ้าคลาดเกิน 5 วิ → AlarmManager.setTime
 * ทำตอนบริการเริ่ม และทุก 6 ชม. (เธรด VHAL — งานเล็ก ไม่ถี่)
 */
final class TimeKeeper {
    static final String TAG = "EX2Time";
    static final String ZONE = "Asia/Bangkok", NTP = "time.google.com";
    static final long EVERY_MS = 6 * 3600 * 1000L;
    private static volatile String sLast = "ยังไม่ได้ตรวจ";
    private static Runnable sLoop;

    private TimeKeeper() {}

    static String last() { return sLast; }

    static void start(Context ctx) {
        final Context app = ctx.getApplicationContext();
        final Handler h = Vhal.thread();
        if (sLoop != null) h.removeCallbacks(sLoop);
        sLoop = new Runnable() {
            int tries;
            public void run() {
                boolean ok = false;
                try { ok = syncNow(app); } catch (Throwable t) { Log.w(TAG, "sync", t); }
                // เน็ตยังไม่มา (เพิ่งสตาร์ท/รอ Wi-Fi) → ลองใหม่เร็ว ๆ 10 รอบ แล้วค่อยรอบละ 6 ชม.
                h.postDelayed(this, ok || ++tries > 10 ? EVERY_MS : 60000);
                if (ok) tries = 0;
            }
        };
        h.postDelayed(sLoop, 15000);
    }

    /** true = ได้เวลาจากเน็ตแล้ว (ตั้งแล้วหรือตรงอยู่แล้ว) */
    static boolean syncNow(Context c) {
        if (!Prefs.get(c, Prefs.KEY_TIME_TH, true)) { sLast = "ปิดอยู่"; return true; }
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        String zone = TimeZone.getDefault().getID();
        if (!ZONE.equals(zone)) {
            try { am.setTimeZone(ZONE); Log.i(TAG, "โซนเวลา " + zone + " → " + ZONE); }
            catch (Throwable t) { Log.w(TAG, "setTimeZone", t); }
        }
        try {
            if (!NTP.equals(Settings.Global.getString(c.getContentResolver(), "ntp_server")))
                Settings.Global.putString(c.getContentResolver(), "ntp_server", NTP);
            if (Settings.Global.getInt(c.getContentResolver(), Settings.Global.AUTO_TIME, 0) != 1)
                Settings.Global.putInt(c.getContentResolver(), Settings.Global.AUTO_TIME, 1);
        } catch (Throwable t) { Log.w(TAG, "settings", t); }

        // ถาม 2 ครั้งต้องตรงกัน (±1 วิ) + ปีต้องสมเหตุผล — ตั้งเวลาผิดทั้งเครื่องแย่กว่าไม่ตั้ง (TLS/แอปพัง)
        Long net = ntpNow(c), net2 = net == null ? null : ntpNow(c);
        if (net == null || net2 == null) { sLast = "ต่อ " + NTP + " ไม่ได้ (ยังไม่มีเน็ต?)"; Log.i(TAG, sLast); return false; }
        long drift = Math.abs(net2 - net);
        if (drift > 1500 || net2 < 1767225600000L) {   // 2026-01-01
            sLast = "เวลาจากเน็ตไม่นิ่ง ไม่ตั้ง (ต่างกัน " + drift + " ms)"; Log.w(TAG, sLast); return false;
        }
        net = net2;
        long off = System.currentTimeMillis() - net;
        Log.i(TAG, "เครื่อง−เน็ต = " + off + " ms");
        if (Math.abs(off) > 5000) {
            try {
                am.setTime(net + 50);
                sLast = "ตั้งเวลาแล้ว (เดิมคลาด " + (off / 1000) + " วิ)";
                Log.i(TAG, sLast);
            } catch (Throwable t) { sLast = "ตั้งเวลาไม่ได้: " + t.getClass().getSimpleName(); Log.w(TAG, "setTime", t); }
        } else { sLast = "เวลาตรง (คลาด " + off + " ms)"; Log.i(TAG, sLast); }
        return true;
    }

    /** เวลาจริงจาก SNTP (ms) หรือ null */
    private static Long ntpNow(Context c) {
        try {
            Class<?> k = Class.forName("android.net.SntpClient");
            Object cl = k.getConstructor().newInstance();
            boolean ok;
            // Android 9: requestTime(host, timeout) แค่ log "Shame on you…" แล้วคืน false เสมอ (เห็นใน log เครื่องจำลอง)
            // → ใช้แบบระบุ Network เป็นหลัก · แบบ 2 พารามิเตอร์ไว้สำรองเฉพาะรุ่นที่ไม่มีแบบ 3
            try {
                android.net.Network n = ((android.net.ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetwork();
                if (n == null) return null;
                ok = (Boolean) k.getMethod("requestTime", String.class, int.class, android.net.Network.class).invoke(cl, NTP, 5000, n);
            } catch (NoSuchMethodException e) {
                ok = (Boolean) k.getMethod("requestTime", String.class, int.class).invoke(cl, NTP, 5000);
            }
            if (!ok) return null;
            long ntp = (Long) k.getMethod("getNtpTime").invoke(cl);
            long ref = (Long) k.getMethod("getNtpTimeReference").invoke(cl);
            return ntp + SystemClock.elapsedRealtime() - ref;
        } catch (Throwable t) {
            Log.w(TAG, "sntp: " + t);
            return null;
        }
    }
}
