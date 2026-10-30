package com.oakhen.ex2dev;

import android.content.Context;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * อ่านเกียร์จาก VHAL — ใช้ตอนปิดจอ: เข้าเกียร์ R = เปิดจอทันที (ให้เห็นกล้องถอย)
 *
 * ทางเชื่อมต่อ = ทางเดียวกับที่แอพ Geely เดิม (CarVhalBindings) ใช้ได้จริงบนจอนี้:
 *   Class.forName("android.car.Car").createCar(Context) -> connect() -> getCarManager("property") -> getIntProperty(prop, 0)
 * property id = ตัวเดียวกับที่ CentralEXAuto อ่านเกียร์บนรถคันนี้:
 *   GEAR_SELECTION 289408000 (0x11400400), CURRENT_GEAR 289408001 (0x11400401)
 *   ค่า VehicleGear: N=1, R=2, P=4, D=8 (CentralEXAuto ใช้ P=4, D=8 ตรงกัน)
 * เรียกจาก thread เบื้องหลังเท่านั้น (binder call)
 */
final class ReverseWatch {
    static final String TAG = "EX2Reverse";
    static final int GEAR_SELECTION = 289408000;
    static final int CURRENT_GEAR = 289408001;
    static final int GEAR_REVERSE = 2;

    private static Object sCar, sCpm;

    private ReverseWatch() {}

    /** ค่าเกียร์ปัจจุบัน หรือ null ถ้ายังอ่านไม่ได้ (ยังเชื่อมต่อไม่เสร็จ / ไม่มีค่า) */
    static synchronized Integer readGear(Context ctx) {
        if (Vhal.sim(ctx)) {   // เครื่องจำลอง: อ่านจากรถจำลอง (เกียร์ P/R/N/D ตั้งได้จาก carsim)
            for (int prop : new int[]{GEAR_SELECTION, CURRENT_GEAR}) {
                Integer v = Vhal.getInt(ctx, prop, 0);
                if (v != null && v != 0) return v;
            }
            return null;
        }
        Object cpm = cpm(ctx);
        if (cpm == null) return null;
        int errors = 0;
        for (int prop : new int[]{GEAR_SELECTION, CURRENT_GEAR}) {
            try {
                Method m = cpm.getClass().getMethod("getIntProperty", int.class, int.class);
                int v = (Integer) m.invoke(cpm, prop, 0);
                if (v != 0) return v;
            } catch (Throwable t) {
                errors++;   // prop นี้อ่านไม่ได้ ลองตัวถัดไป
            }
        }
        if (errors == 2) sCpm = null;   // พังทั้งคู่ = การเชื่อมต่อน่าจะหลุด -> tick ถัดไปขอ manager ใหม่
        return null;
    }

    static boolean isReverse(Integer gear) {
        return gear != null && gear == GEAR_REVERSE;
    }

    private static Object cpm(Context ctx) {
        if (sCpm != null) return sCpm;
        try {
            Class<?> carCls = Class.forName("android.car.Car");
            if (sCar == null) {
                sCar = carCls.getMethod("createCar", Context.class).invoke(null, ctx.getApplicationContext());
                if (sCar == null) return null;
            }
            boolean connected = false;
            try { connected = (Boolean) carCls.getMethod("isConnected").invoke(sCar); } catch (Throwable ignored) {}
            if (!connected) {
                // เรียกซ้ำได้: กำลังเชื่อมอยู่ = IllegalStateException (จับทิ้ง) · หลุด = ต่อใหม่
                try { carCls.getMethod("connect").invoke(sCar); } catch (Throwable ignored) {}
                return null;   // เชื่อมต่อแบบ async — tick ถัดไปลองใหม่
            }
            sCpm = carCls.getMethod("getCarManager", String.class).invoke(sCar, "property");
            if (sCpm != null) Log.i(TAG, "CarPropertyManager OK");
            return sCpm;
        } catch (Throwable t) {
            Log.w(TAG, "car connect failed", t);
            return null;
        }
    }
}
