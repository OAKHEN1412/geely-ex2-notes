package com.oakhen.ex2dev;

import android.content.Context;
import android.os.PowerManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * บันทึกการเดินทาง: อ่านค่าจากรถทุก 5 วิ (ตอนจอเปิด) → TripState ตัดสิน → เก็บลง files/trips.csv
 * รหัส/หน่วยตาม CentralEXAuto TelemetryReader บนจอนี้: ความเร็ว 0x11600207 (float, km/h อยู่แล้ว; สำรอง 0x11600208 int ÷18),
 * เกียร์ 0x11400401, ไมล์รวม 0x11600204 (float km), แบต 0x2140a6ed (float %, เกิน 100 = ทศนิยม 1 ตำแหน่ง → ÷10)
 * จอดับ/รถหลับ → จบทริปทันที (ไม่รอ 2 นาที)
 */
final class TripLog {
    static final String TAG = "EX2Trip";
    static final String K_STATE = "trip_state", K_PRICE = "trip_price_kwh", K_CAP = "trip_capacity_kwh";
    static final float DEF_PRICE = 5f, DEF_CAP = 39.4f;   // ฿/kWh (ค่าไฟบ้าน/ปั๊มปรับได้ในหน้าบันทึก) · แบต EX2 39.4 kWh
    static final int SPEED = 0x11600207, SPEED_DISP = 0x11600208, GEAR = 0x11400401, ODO = 0x11600204, SOC = 0x2140a6ed;
    static final int[] GLOBAL = {0};

    private static TripState sState;
    private static boolean sStarted;
    private static volatile String sLive = "";

    private TripLog() {}

    /** ข้อความสถานะสด (หน้าบันทึกแสดง) */
    static String live() { return sLive; }

    static void start(Context ctx) {
        if (sStarted) return;
        sStarted = true;
        final Context app = ctx.getApplicationContext();
        Vhal.thread().post(new Runnable() {
            public void run() {
                try { tick(app); } catch (Throwable t) { Log.w(TAG, "tick", t); }
                boolean on = true;
                try { on = ((PowerManager) app.getSystemService(Context.POWER_SERVICE)).isInteractive(); } catch (Throwable ignored) {}
                Vhal.thread().postDelayed(this, on ? 5000 : 30000);
            }
        });
    }

    private static void tick(Context app) {
        if (sState == null) sState = TripState.load(Prefs.of(app).getString(K_STATE, ""));
        boolean on = ((PowerManager) app.getSystemService(Context.POWER_SERVICE)).isInteractive();
        if (Vhal.cpm(app) == null) { sLive = "เชื่อมต่อระบบรถไม่ได้"; return; }
        List<Object> ev;
        if (!on) ev = sState.forceEnd();
        else {
            Double kmh = null;
            Float f = Vhal.getFloat(app, SPEED, GLOBAL);
            if (f != null) kmh = (double) Math.abs(f);
            else { Integer i = Vhal.getInt(app, SPEED_DISP, GLOBAL); if (i != null) kmh = Math.abs(i) / 18.0; }
            Integer gear = Vhal.getInt(app, GEAR, GLOBAL);
            Float odo = Vhal.getFloat(app, ODO, GLOBAL);
            Float soc = Vhal.getFloat(app, SOC, GLOBAL);
            Double socPct = soc == null ? null : (double) (soc > 100 ? soc / 10f : soc);
            ev = sState.onSample(System.currentTimeMillis(), kmh, gear, odo == null ? null : (double) odo, socPct);
            sLive = sState.active
                    ? String.format(java.util.Locale.US, "กำลังเดินทาง · %.1f km · %s km/h",
                        (odo != null && sState.startOdo >= 0) ? odo - sState.startOdo : sState.distInt, kmh == null ? "?" : String.valueOf(Math.round(kmh)))
                    : "จอดอยู่" + (socPct != null ? String.format(java.util.Locale.US, " · แบต %.0f%%", socPct) : "");
        }
        if (!ev.isEmpty()) {
            for (Object o : ev) {
                String line = o instanceof TripState.Trip ? ((TripState.Trip) o).csv() : ((TripState.Charge) o).csv();
                append(app, line);
                Log.i(TAG, "บันทึก " + line);
            }
        }
        Prefs.of(app).edit().putString(K_STATE, sState.save()).apply();
    }

    // ---------- ไฟล์ ----------
    static File file(Context c) { return new File(c.getFilesDir(), "trips.csv"); }

    private static synchronized void append(Context c, String line) {
        try {
            FileOutputStream o = new FileOutputStream(file(c), true);
            o.write((line + "\n").getBytes("UTF-8"));
            o.close();
        } catch (Throwable t) { Log.w(TAG, "append", t); }
    }

    /** ทุกบรรทัด (Trip/Charge) เรียงตามเวลาในไฟล์ */
    static synchronized List<Object> readAll(Context c) {
        List<Object> out = new ArrayList<Object>();
        File f = file(c);
        if (!f.exists()) return out;
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            String s;
            while ((s = r.readLine()) != null) {
                String[] p = s.split(",");
                try {
                    if (p[0].equals("T")) out.add(TripState.Trip.parse(p));
                    else if (p[0].equals("C")) out.add(TripState.Charge.parse(p));
                } catch (Throwable ignored) {}
            }
            r.close();
        } catch (Throwable t) { Log.w(TAG, "read", t); }
        return out;
    }

    /** ใส่/ลบค่าใช้จ่ายจริงของทริป (cost < 0 = กลับไปใช้ค่าคำนวณ) → เขียนไฟล์ใหม่ */
    static synchronized void setCost(Context c, long start, double cost) {
        List<Object> all = readAll(c);
        StringBuilder sb = new StringBuilder();
        for (Object o : all) {
            if (o instanceof TripState.Trip) {
                TripState.Trip t = (TripState.Trip) o;
                if (t.start == start) t.cost = cost;
                sb.append(t.csv());
            } else sb.append(((TripState.Charge) o).csv());
            sb.append('\n');
        }
        try {
            File tmp = new File(c.getFilesDir(), "trips.csv.tmp");
            FileOutputStream o = new FileOutputStream(tmp);
            o.write(sb.toString().getBytes("UTF-8"));
            o.close();
            if (!tmp.renameTo(file(c))) Log.w(TAG, "rename failed");
        } catch (Throwable t) { Log.w(TAG, "setCost", t); }
    }

    static float price(Context c) { return Prefs.of(c).getFloat(K_PRICE, DEF_PRICE); }
    static float capacity(Context c) { return Prefs.of(c).getFloat(K_CAP, DEF_CAP); }
}
