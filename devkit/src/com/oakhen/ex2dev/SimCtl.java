package com.oakhen.ex2dev;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * ตัวรับคำสั่งรถจำลองจากคอม: adb shell am broadcast -a com.oakhen.ex2dev.SIM --es cmd speed --es v 60
 * ลงทะเบียนตอนรัน (ไม่แตะ manifest) เฉพาะเครื่องที่ไม่มี android.car · ผู้ส่งต้องถือ android.permission.DUMP
 * (signature|privileged|development — shell/root/system มี, แอพทั่วไปไม่มี) · ผลตอบกลับ = result data (JSON บรรทัดเดียว)
 * คำสั่ง: speed gear key(long=1) plug soc chargekw set(prop,area,v,type) wash reset dump · off v=1/0 (เปิด-ปิดรถจำลองใน Prefs)
 * เริ่มจาก DevService (ดู docs/16-car-simulator.md "Pending wiring")
 */
final class SimCtl {
    static final String TAG = "EX2Sim";
    static final String ACTION = "com.oakhen.ex2dev.SIM";
    static final String PERM = "android.permission.DUMP";

    private static BroadcastReceiver sRx;

    private SimCtl() {}

    static synchronized void start(Context ctx) {
        if (sRx != null || !Vhal.noCar()) return;   // รถจริง: ไม่ลงทะเบียนอะไรเลย
        final Context app = ctx.getApplicationContext();
        sRx = new BroadcastReceiver() {
            public void onReceive(Context c, Intent i) {
                Map<String, String> a = new HashMap<String, String>();
                Bundle b = i.getExtras();
                if (b != null) for (String k : b.keySet()) { Object o = b.get(k); if (o != null) a.put(k, o.toString()); }
                String r = run(app, a);
                Log.i(TAG, a + " -> " + r);
                if (isOrderedBroadcast()) { setResultCode(1); setResultData(r); }
            }
        };
        app.registerReceiver(sRx, new IntentFilter(ACTION), PERM, null);
        if (Vhal.sim(app)) SimCar.instance().start();
        Log.i(TAG, "รถจำลองพร้อม — รอคำสั่ง " + ACTION);
    }

    private static String run(Context app, Map<String, String> a) {
        if ("off".equals(a.get("cmd"))) {
            boolean off = "1".equals(a.get("v")) || "true".equals(a.get("v"));
            Prefs.of(app).edit().putBoolean(Prefs.KEY_SIM_OFF, off).apply();
            return SimCar.reply(true, "sim " + (off ? "off" : "on"));
        }
        if (!Vhal.sim(app)) return SimCar.reply(false, "sim is off (cmd=off v=0 to enable)");
        return SimCar.instance().command(a);
    }
}
