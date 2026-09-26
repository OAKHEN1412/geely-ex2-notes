package com.oakhen.ex2dev;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * ลดกระจกแบบค่อย ๆ ทีละสเต็ปตอนเปิดประตู (แทนการสั่ง WINDOW_POS ครั้งเดียว)
 *
 * เรียกจาก smali ที่ patch ไว้ใน AutoWindowController (ดู devkit/patch_autowindow.sh):
 *   dropWindowOnDoorOpen   -> WindowStepper.drop(doorWindow, corner, current%, target%)  true = รับงานแล้ว
 *   closeWindowOnDoorClose -> WindowStepper.cancel(corner)  ก่อนสั่งปิด (ปิดประตูกลางทาง = หยุดสเต็ปทันที)
 *
 * ปรับที่จอได้ไม่ต้อง build ใหม่ (adb shell setprop):
 *   persist.ex2.win.step  = % ต่อสเต็ป (default 4)  · 0 = ปิดฟีเจอร์ กลับไปสั่งครั้งเดียวแบบเดิม
 *   persist.ex2.win.ms    = เว้นระหว่างสเต็ป ms (default 300)
 *
 * ไม่มี stub ของคลาส Geely ตอนคอมไพล์ -> เรียก setWindowPercent ผ่าน reflection
 */
public final class WindowStepper {
    private static final String TAG = "EX2WinStep";
    private static final Map<Object, Integer> GEN = new HashMap<>();
    private static Handler handler;

    private WindowStepper() {}

    private static synchronized Handler handler() {
        if (handler == null) {
            HandlerThread t = new HandlerThread("ex2-winstep");
            t.start();
            handler = new Handler(t.getLooper());
        }
        return handler;
    }

    private static int bump(Object corner) {
        synchronized (GEN) {
            Integer g = GEN.get(corner);
            int next = (g == null ? 0 : g) + 1;
            GEN.put(corner, next);
            return next;
        }
    }

    private static boolean alive(Object corner, int gen) {
        synchronized (GEN) {
            Integer g = GEN.get(corner);
            return g != null && g == gen;
        }
    }

    // ล็อกร่วมระหว่าง "เช็ก alive + เขียนสเต็ป" กับ cancel():
    // ไม่งั้นสเต็ปที่ผ่านเช็กไปแล้วอาจเขียนทับ "หลัง" คำสั่งปิดกระจกตอนปิดประตู -> กระจกค้างเปิดทั้งที่ประตูปิด
    // cancel() ถูกเรียกจาก closeWindowOnDoorClose ก่อนสั่งปิด => รอสเต็ปที่กำลังเขียนให้เสร็จก่อน แล้วค่อยปิด
    private static final Object WRITE_LOCK = new Object();

    public static void cancel(Object corner) {
        if (corner == null) return;
        synchronized (WRITE_LOCK) { bump(corner); }
    }

    public static boolean drop(final Object doorWindow, final Object corner, final int from, final int target) {
        final int step = sysInt("persist.ex2.win.step", 4);
        final int ms = sysInt("persist.ex2.win.ms", 300);
        if (doorWindow == null || corner == null || step <= 0 || ms <= 0 || from >= target) return false;
        if (step >= target - from) return false; // สเต็ปเดียวก็ถึง = สั่งแบบเดิม

        final int gen;
        synchronized (WRITE_LOCK) { gen = bump(corner); }
        Log.i(TAG, corner + ": " + from + "% -> " + target + "% step " + step + "% / " + ms + "ms");
        handler().post(new Runnable() {
            private int cur = from;

            @Override
            public void run() {
                synchronized (WRITE_LOCK) {
                    if (!alive(corner, gen)) {
                        Log.i(TAG, corner + ": ยกเลิกที่ " + cur + "%");
                        return;
                    }
                    cur = Math.min(cur + step, target);
                    if (!write(doorWindow, corner, cur)) {
                        // เขียนไม่ผ่าน: พยายามส่ง target ครั้งเดียวให้จบ (พฤติกรรมเดิม) แล้วเลิก
                        if (cur != target) write(doorWindow, corner, target);
                        bump(corner);
                        return;
                    }
                }
                if (cur < target) handler().postDelayed(this, ms);
            }
        });
        return true;
    }

    private static boolean write(Object doorWindow, Object corner, int percent) {
        try {
            Method m = null;
            for (Method c : doorWindow.getClass().getMethods()) {
                if (c.getName().equals("setWindowPercent") && c.getParameterTypes().length == 2) { m = c; break; }
            }
            if (m == null) { Log.w(TAG, "ไม่พบ setWindowPercent"); return false; }
            Object probe = m.invoke(doorWindow, corner, percent);
            boolean ok = true;
            if (probe != null) {
                Object r = probe.getClass().getMethod("getOk").invoke(probe);
                ok = Boolean.TRUE.equals(r);
            }
            Log.i(TAG, corner + " -> " + percent + "% " + (ok ? "ok" : "FAIL"));
            return ok;
        } catch (Throwable t) {
            Log.w(TAG, "write " + percent + "% error", t);
            return false;
        }
    }

    private static int sysInt(String key, int def) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            return (Integer) sp.getMethod("getInt", String.class, int.class).invoke(null, key, def);
        } catch (Throwable t) {
            return def;
        }
    }
}
