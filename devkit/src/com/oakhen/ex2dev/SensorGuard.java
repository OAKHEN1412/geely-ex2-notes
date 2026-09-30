package com.oakhen.ex2dev;

import android.content.Context;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.ResultReceiver;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * เข็มทิศ Google Maps หมุนไปมา: จอมีแต่ magnetometer ไม่ปรับเทียบ (UNCALI_MAG) + ROTATION_VECTOR ของ MTK
 * ในห้องโดยสาร (โลหะ/มอเตอร์) ค่าเข็มทิศเลยมั่ว → ตัดข้อมูลเซ็นเซอร์ต่อเนื่องเฉพาะแอพที่ระบุ
 * ให้ใช้ทิศจาก GPS แทน (แบบ Waze)
 *
 * วิธี: `sensorservice set-uid-state <pkg> idle` (Android 9 มีคำสั่งนี้สำหรับทดสอบข้อจำกัดเซ็นเซอร์เบื้องหลัง)
 *  - uid idle = SensorService ไม่ส่ง event เซ็นเซอร์ต่อเนื่องให้ uid นั้น (แอพอื่นไม่กระทบ)
 *  - ค่า override อยู่ในหน่วยความจำ → หายเมื่อรีบูต → DevService สั่งใหม่ทุกครั้งที่เริ่ม
 *  - Android 9: ตอน uid เปลี่ยนจาก idle → active จริง (เช่น แอพกลับมาหน้าจอ) SensorService เปิดสิทธิ์คืนโดยไม่ดู override
 *    และสั่ง idle ซ้ำเฉย ๆ ไม่มีผล (ค่าไม่เปลี่ยน) → ต้อง reset แล้ว set ใหม่ → ทำซ้ำทุก REAPPLY_MS (2 binder call เล็ก ๆ)
 *  - เรียกผ่าน binder SHELL_COMMAND ของ sensorservice (ต้องมี MANAGE_SENSORS — uid system ผ่าน)
 */
final class SensorGuard {
    static final String TAG = "EX2Sensor";
    static final long REAPPLY_MS = 2000;   // วัดบนเครื่องจำลอง: ย่อแอพ ~75 วิแล้วเปิดกลับ = ได้เซ็นเซอร์คืน → ต้องสั่งซ้ำถี่
    private static Runnable sLoop;

    private SensorGuard() {}

    /** แพ็กเกจที่ตัดเซ็นเซอร์ (Prefs KEY_COMPASS_PKGS, คั่นด้วย ,) */
    static String[] targets(Context c) {
        String s = Prefs.of(c).getString(Prefs.KEY_COMPASS_PKGS, Prefs.DEF_COMPASS_PKGS);
        return s.trim().isEmpty() ? new String[0] : s.split("\\s*,\\s*");
    }

    /** เริ่ม/หยุดตามสวิตช์ · เรียกได้จากทุกเธรด */
    static void apply(final Context ctx) {
        final Context app = ctx.getApplicationContext();
        final Handler h = SplitScreen.worker();
        if (sLoop != null) h.removeCallbacks(sLoop);
        final boolean on = Prefs.get(app, Prefs.KEY_COMPASS_OFF, true);
        if (!on) {
            h.post(new Runnable() { public void run() { for (String p : targets(app)) cmd("reset-uid-state", p); } });
            sLoop = null;
            return;
        }
        Log.i(TAG, "ตัดเซ็นเซอร์ต่อเนื่องของ " + java.util.Arrays.toString(targets(app)) + " (สั่งซ้ำทุก " + REAPPLY_MS + " ms)");
        sLoop = new Runnable() {
            public void run() {
                for (String p : targets(app)) {
                    cmd("reset-uid-state", p);
                    cmd("set-uid-state", p, "idle");
                }
                h.postDelayed(this, REAPPLY_MS);
            }
        };
        h.post(sLoop);
    }

    /** สถานะปัจจุบัน (ใช้ตรวจ) */
    static String state(String pkg) { return cmd("get-uid-state", pkg); }

    /** เทียบเท่า `cmd sensorservice <args>` · คืนข้อความที่ service พิมพ์ */
    static String cmd(String... args) {
        ParcelFileDescriptor[] outPipe = null;
        ParcelFileDescriptor devNull = null;
        try {
            IBinder b = (IBinder) Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String.class).invoke(null, "sensorservice");
            if (b == null) return "no sensorservice";
            outPipe = ParcelFileDescriptor.createPipe();
            devNull = ParcelFileDescriptor.open(new java.io.File("/dev/null"), ParcelFileDescriptor.MODE_READ_ONLY);
            Class<?> cb = Class.forName("android.os.ShellCallback");
            b.getClass().getMethod("shellCommand", FileDescriptor.class, FileDescriptor.class, FileDescriptor.class,
                    String[].class, cb, ResultReceiver.class)
                    .invoke(b, devNull.getFileDescriptor(), outPipe[1].getFileDescriptor(), outPipe[1].getFileDescriptor(),
                            args, null, new ResultReceiver(null));
            outPipe[1].close(); outPipe[1] = null;
            InputStream in = new FileInputStream(outPipe[0].getFileDescriptor());
            StringBuilder sb = new StringBuilder();
            byte[] buf = new byte[512];
            int n;
            while ((n = in.read(buf)) > 0) sb.append(new String(buf, 0, n, "UTF-8"));
            String out = sb.toString().trim();
            if (!out.isEmpty() || !"reset-uid-state".equals(args[0]) && !"set-uid-state".equals(args[0]))   // สั่งซ้ำทุก 2 วิ — log เฉพาะที่มีข้อความตอบกลับ (ผิดพลาด)
                Log.i(TAG, "sensorservice " + java.util.Arrays.toString(args) + " -> " + out);
            return out;
        } catch (Throwable t) {
            Log.w(TAG, "sensorservice " + java.util.Arrays.toString(args), t);
            return "error: " + t;
        } finally {
            try { if (outPipe != null) { if (outPipe[0] != null) outPipe[0].close(); if (outPipe[1] != null) outPipe[1].close(); } } catch (Throwable ignored) {}
            try { if (devNull != null) devNull.close(); } catch (Throwable ignored) {}
        }
    }
}
