package com.oakhen.ex2dev;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * ฟังก์ชันรถผ่าน ECarX adaptapi (ICarFunction) — ช่องทางเดียวกับแอปของรถ (AutoSceneDirector) ใช้สั่งโหมดล้างรถ
 * AdapterAPI.jar / AdapterAPIImpl.jar อยู่ใน boot classpath ของจอ (/system/framework/boot-AdapterAPI*.vdex) → เรียกด้วย reflection ได้
 * รหัสฟังก์ชัน (ISceneMode): SCENE_FUNC_WASH_MODE 0x2f010200 · OFF 0x2f010200 · MOVING 0x2f010201 · WASHING 0x2f010202
 * เรียกบนเธรด Vhal.thread() เท่านั้น · เครื่องจำลอง (Vhal.sim) → ถามรถจำลอง SimCar แทน
 */
final class EcarxFn {
    static final String TAG = "EX2Ecarx";
    static final int WASH_MODE = 0x2f010200, WASH_OFF = 0x2f010200, WASH_MOVING = 0x2f010201;

    private static Object sFn;
    private static Class<?> sIf;

    private EcarxFn() {}

    /** ICarFunction หรือ null (ไม่มี adaptapi = ไม่ใช่จอ ECarX เช่นเครื่องจำลอง) */
    static synchronized Object fn(Context c) {
        if (sFn != null) return sFn;
        try {
            Object car = Class.forName("com.ecarx.xui.adaptapi.car.Car").getMethod("create", Context.class).invoke(null, c.getApplicationContext());
            sIf = Class.forName("com.ecarx.xui.adaptapi.car.base.ICarFunction");
            Method get = Class.forName("com.ecarx.xui.adaptapi.car.ICar").getMethod("getICarFunction");
            for (int i = 0; i < 20 && sFn == null; i++) {   // บางรุ่นเชื่อมต่อบริการแบบ async
                sFn = get.invoke(car);
                if (sFn == null) SystemClock.sleep(100);
            }
        } catch (ClassNotFoundException e) {
            Log.w(TAG, "ไม่มี ECarX adaptapi บนเครื่องนี้");
        } catch (Throwable t) {
            Log.w(TAG, "adaptapi: " + t);
        }
        return sFn;
    }

    /** สถานะรองรับ (ชื่อ enum FunctionStatus เช่น active / notavailable) หรือ null */
    static String status(Context c, int func) {
        if (Vhal.sim(c)) return SimCar.instance().fnStatus(func);
        Object f = fn(c);
        if (f == null) return null;
        try { return String.valueOf(sIf.getMethod("isFunctionSupported", int.class).invoke(f, func)); }
        catch (Throwable t) { Log.w(TAG, "isFunctionSupported " + hex(func) + ": " + t); return null; }
    }

    static Integer get(Context c, int func) {
        if (Vhal.sim(c)) return SimCar.instance().fnGet(func);
        Object f = fn(c);
        if (f == null) return null;
        try { return (Integer) sIf.getMethod("getFunctionValue", int.class).invoke(f, func); }
        catch (Throwable t) { Log.w(TAG, "getFunctionValue " + hex(func) + ": " + t); return null; }
    }

    static int[] supportedValues(Context c, int func) {
        if (Vhal.sim(c)) return SimCar.instance().fnSupported(func);
        Object f = fn(c);
        if (f == null) return null;
        try { return (int[]) sIf.getMethod("getSupportedFunctionValue", int.class).invoke(f, func); }
        catch (Throwable t) { return null; }
    }

    static boolean set(Context c, int func, int value) {
        if (Vhal.sim(c)) return SimCar.instance().fnSet(func, value);
        Object f = fn(c);
        if (f == null) return false;
        try {
            boolean ok = (Boolean) sIf.getMethod("setFunctionValue", int.class, int.class).invoke(f, func, value);
            Log.i(TAG, "setFunctionValue " + hex(func) + " = " + hex(value) + " → " + ok);
            return ok;
        } catch (Throwable t) { Log.w(TAG, "setFunctionValue " + hex(func), t); return false; }
    }

    static String hex(int v) { return "0x" + Integer.toHexString(v); }
}
