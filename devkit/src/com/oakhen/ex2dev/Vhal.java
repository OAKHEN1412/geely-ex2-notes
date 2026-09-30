package com.oakhen.ex2dev;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * อ่าน/สั่งค่ารถผ่าน VHAL (android.car CarPropertyManager) — เราเป็น uid system จึงเขียน property ของผู้ผลิตได้
 * ทางเชื่อมต่อเดียวกับ ReverseWatch (Car.createCar → connect → getCarManager("property")) · ใช้ reflection เพราะ
 * android.car ไม่อยู่ใน android.jar ที่คอมไพล์
 * รหัส property / area มาจาก CentralEXAuto ที่ใช้งานได้บนจอ IHU629G (ดู docs/15-feature-plan.md) — ต้องยืนยันบนรถ
 * ทุกเมธอดเป็น binder call → เรียกบนเธรด thread() เท่านั้น
 */
final class Vhal {
    static final String TAG = "EX2Vhal";

    // ---- โหมดขับ / รีเจน ----
    static final int DRIVE_MODE = 0x22010100;
    static final int DRIVE_ECO = 0x22010101, DRIVE_COMFORT = 0x22010102, DRIVE_DYNAMIC = 0x22010103;
    static final int[] DRIVE_AREAS = {0, 1, 0x1000000};
    static final int REGEN = 0x20020500;
    static final int REGEN_LOW = 0x20020501, REGEN_MID = 0x20020502, REGEN_HIGH = 0x20020503;
    static final int[] REGEN_AREAS = {0, 1};
    // ---- แอร์ ----
    static final int HVAC_AC_ON = 0x15600099;
    static final int HVAC_FAN = 0x15400500;
    static final int[] HVAC_AREAS = {1, 5, 0x31, 0};
    static final int HVAC_TEMP = 0x25401036;          // float °C · area 1 คนขับ, 4 ผู้โดยสาร
    static final int[] TEMP_AREAS = {1, 4};

    private static Object sCar, sCpm;
    private static boolean sNoCar;   // ไม่มี android.car (เครื่องจำลอง) → ไม่ต้องลองซ้ำ
    private static Handler sThread;

    private Vhal() {}

    static synchronized Handler thread() {
        if (sThread == null) {
            HandlerThread t = new HandlerThread("ex2-vhal");
            t.start();
            sThread = new Handler(t.getLooper());
        }
        return sThread;
    }

    /** CarPropertyManager หรือ null (ยังเชื่อมไม่เสร็จ/ไม่มีระบบรถ เช่นเครื่องจำลอง) · รอเชื่อมต่อสูงสุด ~2 วิ */
    static synchronized Object cpm(Context ctx) {
        if (sCpm != null) return sCpm;
        if (sNoCar) return null;
        try {
            Class<?> carCls = Class.forName("android.car.Car");
            if (sCar == null) {
                sCar = carCls.getMethod("createCar", Context.class).invoke(null, ctx.getApplicationContext());
                if (sCar == null) return null;
            }
            Method isConn = carCls.getMethod("isConnected");
            if (!(Boolean) isConn.invoke(sCar)) {
                try { carCls.getMethod("connect").invoke(sCar); } catch (Throwable ignored) {}   // กำลังเชื่อมอยู่ = IllegalStateException
                for (int i = 0; i < 20 && !(Boolean) isConn.invoke(sCar); i++) SystemClock.sleep(100);
                if (!(Boolean) isConn.invoke(sCar)) return null;
            }
            sCpm = carCls.getMethod("getCarManager", String.class).invoke(sCar, "property");
            return sCpm;
        } catch (ClassNotFoundException e) {
            sNoCar = true;
            Log.w(TAG, "ไม่มี android.car บนเครื่องนี้");
            return null;
        } catch (Throwable t) {
            Log.w(TAG, "car connect: " + t);
            return null;
        }
    }

    static Integer getInt(Context c, int prop, int... areas) {
        Object m = cpm(c);
        if (m == null) return null;
        for (int a : areas) {
            try { return (Integer) m.getClass().getMethod("getIntProperty", int.class, int.class).invoke(m, prop, a); }
            catch (Throwable ignored) {}
        }
        return null;
    }

    static Float getFloat(Context c, int prop, int... areas) {
        Object m = cpm(c);
        if (m == null) return null;
        for (int a : areas) {
            try { return (Float) m.getClass().getMethod("getFloatProperty", int.class, int.class).invoke(m, prop, a); }
            catch (Throwable ignored) {}
        }
        return null;
    }

    static Boolean getBool(Context c, int prop, int... areas) {
        Object m = cpm(c);
        if (m == null) return null;
        for (int a : areas) {
            try { return (Boolean) m.getClass().getMethod("getBooleanProperty", int.class, int.class).invoke(m, prop, a); }
            catch (Throwable ignored) {}
        }
        return null;
    }

    /** สั่งค่า int — ลองทีละ area จนกว่าจะผ่าน (แบบ CentralEXAuto) · true = มี area ที่รับ */
    static boolean setInt(Context c, int prop, int value, int... areas) {
        Object m = cpm(c);
        if (m == null) return false;
        for (int a : areas) {
            try {
                m.getClass().getMethod("setIntProperty", int.class, int.class, int.class).invoke(m, prop, a, value);
                Log.i(TAG, String.format("set 0x%08x area 0x%x = 0x%x", prop, a, value));
                return true;
            } catch (Throwable ignored) {}
        }
        Log.w(TAG, String.format("set 0x%08x = 0x%x ไม่ผ่านทุก area", prop, value));
        return false;
    }

    static boolean setFloat(Context c, int prop, float value, int... areas) {
        Object m = cpm(c);
        if (m == null) return false;
        boolean any = false;
        for (int a : areas) {   // อุณหภูมิ: ตั้งทุก area (คนขับ+ผู้โดยสาร) ไม่ใช่แค่ area แรก
            try {
                m.getClass().getMethod("setFloatProperty", int.class, int.class, float.class).invoke(m, prop, a, value);
                any = true;
            } catch (Throwable ignored) {}
        }
        Log.i(TAG, String.format("setFloat 0x%08x = %.1f %s", prop, value, any ? "ok" : "ไม่ผ่าน"));
        return any;
    }

    static boolean setBool(Context c, int prop, boolean value, int... areas) {
        Object m = cpm(c);
        if (m == null) return false;
        for (int a : areas) {
            try {
                m.getClass().getMethod("setBooleanProperty", int.class, int.class, boolean.class).invoke(m, prop, a, value);
                Log.i(TAG, String.format("setBool 0x%08x area 0x%x = %s", prop, a, value));
                return true;
            } catch (Throwable ignored) {}
        }
        return false;
    }
}
