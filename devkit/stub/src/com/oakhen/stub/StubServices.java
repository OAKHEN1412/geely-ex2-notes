package com.oakhen.stub;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;

import java.lang.reflect.Method;

/**
 * บริการระบบจำลอง สำหรับจอรถที่ ROM ตัดทิ้ง (รันด้วย app_process ในสิทธิ์ root)
 *   crossprofileapps  — GMS PhoneHub เรียก CrossProfileApps.getTargetUserProfiles() → ได้ลิสต์ว่าง (ไม่ crash)
 *   batteryproperties — BatteryManager ไม่เป็น null (Google Maps) · CAPACITY = เปอร์เซ็นต์ที่ตั้งผ่าน prop
 * รัน:  CLASSPATH=/data/local/tmp/ex2stub.dex app_process /system/bin com.oakhen.stub.StubServices
 * ลงทะเบียนเฉพาะชื่อที่ยังไม่มี (ไม่ทับบริการจริง)
 */
public final class StubServices {
    static final int FIRST = IBinder.FIRST_CALL_TRANSACTION;

    public static void main(String[] args) throws Exception {
        Class<?> sm = Class.forName("android.os.ServiceManager");
        Method check = sm.getMethod("checkService", String.class);
        Method add = sm.getMethod("addService", String.class, IBinder.class);
        String[][] svcs = {
                {"crossprofileapps", "android.content.pm.ICrossProfileApps"},
                {"batteryproperties", "android.os.IBatteryPropertiesRegistrar"},
        };
        for (String[] s : svcs) {
            if (check.invoke(null, s[0]) != null) { System.out.println(s[0] + ": มีอยู่แล้ว ข้าม"); continue; }
            try {
                add.invoke(null, s[0], new Stub(s[1]));
                System.out.println(s[0] + ": ลงทะเบียนแล้ว");
            } catch (Throwable t) {
                Throwable c = t.getCause() != null ? t.getCause() : t;
                System.out.println(s[0] + ": ลงทะเบียนไม่ได้ - " + c);
            }
        }
        System.out.flush();
        Object lock = new Object();
        synchronized (lock) { lock.wait(); }   // อยู่ต่อเพื่อรับ binder call (thread pool ของ app_process)
    }

    static final class Stub extends Binder {
        final String desc;
        Stub(String desc) { this.desc = desc; attachInterface(null, desc); }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            if (code == INTERFACE_TRANSACTION) { reply.writeString(desc); return true; }
            if (reply == null) return true;   // oneway
            reply.writeNoException();
            if (desc.endsWith("ICrossProfileApps")) {
                // getTargetUserProfiles → List<UserHandle> ว่าง (N=0) · startActivityAsUser = void
                if (code == FIRST + 1) reply.writeInt(0);
            } else if (desc.endsWith("IBatteryPropertiesRegistrar")) {
                // registerListener/unregisterListener/getProperty/scheduleUpdate
                if (code == FIRST + 2) {
                    int id = data.dataAvail() >= 4 ? readId(data) : 0;
                    reply.writeInt(0);        // status OK
                    reply.writeInt(1);        // out BatteryProperty != null
                    reply.writeLong(value(id));
                }
            }
            return true;
        }

        private static int readId(Parcel data) {
            try { data.enforceInterface("android.os.IBatteryPropertiesRegistrar"); } catch (Throwable ignored) {}
            return data.readInt();
        }

        private static long value(int id) {
            // BatteryManager: CHARGE_COUNTER=1 CURRENT_NOW=2 CURRENT_AVERAGE=3 CAPACITY=4 ENERGY_COUNTER=5
            if (id == 4) {
                try {
                    Class<?> sp = Class.forName("android.os.SystemProperties");
                    return (Integer) sp.getMethod("getInt", String.class, int.class).invoke(null, "sys.ex2.soc", 100);
                } catch (Throwable t) { return 100; }
            }
            return 0;
        }
    }
}
