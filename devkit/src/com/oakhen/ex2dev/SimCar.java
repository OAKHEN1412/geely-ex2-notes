package com.oakhen.ex2dev;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * รถจำลอง (SimCar) — ตัวแทน VHAL / ECarX adaptapi บนเครื่องจำลองที่ไม่มี android.car (Java ล้วน ไม่พึ่ง Android — ทดสอบบนคอม: devkit/test/run_sim_test.sh)
 * ใช้เมื่อ Vhal.sim() = true เท่านั้น (ไม่มี android.car + ไม่ได้ปิดใน Prefs) · บนรถจริงไม่ถูกเรียกเลย
 * ที่เก็บค่า = (prop, area) → Integer/Float/Boolean · set() ได้เฉพาะคู่ที่มีอยู่ (เหมือนรถ: area ผิดสั่งไม่ผ่าน) · put() สร้างใหม่ได้
 * โมเดลขับ (tick ทุก 200 ms): ความเร็วไล่ตามเป้าแบบนุ่ม · ไมล์ = ∫ความเร็ว · แบตลดตามพลังงาน · เสียบสาย+เกียร์ P = ชาร์จ
 * สมมติฐาน (ไม่ใช่ค่าวัดจากรถ): แบต 39.4 kWh (ตาม TripLog.DEF_CAP) · สิ้นเปลือง 13 kWh/100 km · ชาร์จ AC 7 kW · ไม่มีรีเจน/แอร์กินไฟ
 * callback: ยิงบนเธรดที่เปลี่ยนค่า (นอก lock) · listen() ส่งค่าปัจจุบันให้ทันทีหนึ่งครั้ง (เหมือน registerCallback ของรถ — SwcWatch ข้ามค่าแรก)
 */
final class SimCar {
    // รหัสตรงกับ Vhal / TripLog / ReverseWatch (run_sim_test.sh ตรวจว่ายังตรงกัน) — Vhal ดึงเข้ามาไม่ได้เพราะพึ่ง Android
    static final int GEAR_SEL = 0x11400400, GEAR_CUR = 0x11400401;
    static final int SPEED = 0x11600207, SPEED_DISP = 0x11600208, ODO = 0x11600204, SOC = 0x2140a6ed, RANGE = 0x11400308;
    static final int DRIVE_MODE = 0x22010100, DRIVE_ECO = 0x22010101;
    static final int REGEN = 0x20020500, REGEN_MID = 0x20020502;
    static final int HVAC_AC_ON = 0x15600099, HVAC_FAN = 0x15400500, HVAC_TEMP = 0x25401036;
    static final int WASH_MODE = 0x2f010200, WASH_OFF = 0x2f010200, WASH_MOVING = 0x2f010201;
    static final int[] KEYS = {0x41020100, 0x41020200, 0x41020300, 0x41020400, 0x41020500};   // 1 = กดลง, 2 = ปล่อย
    static final int G_N = 1, G_R = 2, G_P = 4, G_D = 8;

    static final double CAP_KWH = 39.4, KWH_100KM = 13.0, DEF_CHARGE_KW = 7.0;
    static final double ACCEL = 8, DECEL = 12, K = 0.6, SNAP = 0.3, MAX_REV = 15, MOVING_KMH = 3;   // km/h ต่อวินาที · 1/วิ · km/h
    static final long STEP_MS = 100, TICK_MS = 200, TAP_MS = 100, LONG_MS = 1000;

    interface Listener { void onChange(int prop, int area, Object value); }

    private static final class Ev { int prop, area; Object val; }
    private static final class Sub { int prop; Listener l; }

    private static SimCar sInst;

    private final TreeMap<Long, Object> mVals = new TreeMap<Long, Object>();
    private final CopyOnWriteArrayList<Sub> mSubs = new CopyOnWriteArrayList<Sub>();
    private final Map<Integer, Integer> mFnVal = new TreeMap<Integer, Integer>();
    private final Map<Integer, String> mFnStatus = new TreeMap<Integer, String>();
    private ScheduledThreadPoolExecutor mExec;
    private long mLast;

    private int gear = G_P;
    private double target, speed, odo, soc, chargeKw = DEF_CHARGE_KW;
    private boolean plugged, charging;

    SimCar() { reset(); }

    static synchronized SimCar instance() {
        if (sInst == null) sInst = new SimCar();
        return sInst;
    }

    // ---------- เริ่ม/หยุดตัวเดิน ----------
    /** เริ่ม tick พื้นหลัง 5 Hz (เรียกซ้ำได้) */
    synchronized void start() {
        if (mExec != null) return;
        mExec = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
            public Thread newThread(Runnable r) { Thread t = new Thread(r, "ex2-sim"); t.setDaemon(true); return t; }
        });
        mLast = System.nanoTime();
        mExec.scheduleAtFixedRate(new Runnable() {
            public void run() {
                long n = System.nanoTime(), dt = (n - mLast) / 1000000;
                mLast = n;
                try { tick(dt); } catch (Throwable ignored) {}
            }
        }, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS);
    }

    synchronized void stop() {
        if (mExec != null) { mExec.shutdownNow(); mExec = null; }
    }

    private synchronized ScheduledThreadPoolExecutor exec() {
        start();
        return mExec;
    }

    // ---------- ค่าเริ่มต้น ----------
    /** กลับค่าเริ่มต้น: จอด P · ความเร็ว 0 · แบต 80% · ECO · รีเจนกลาง · แอร์ปิด 24 °C พัดลม 2 · โหมดล้างรถ OFF (รองรับ) */
    void reset() {
        List<Ev> ev = new ArrayList<Ev>();
        synchronized (this) {
            gear = G_P; target = 0; speed = 0; odo = 12345.6; soc = 80; plugged = false; charging = false; chargeKw = DEF_CHARGE_KW;
            put0(GEAR_SEL, 0, G_P, ev); put0(GEAR_CUR, 0, G_P, ev);
            put0(DRIVE_MODE, 0, DRIVE_ECO, ev);
            put0(REGEN, 0, REGEN_MID, ev);
            put0(HVAC_AC_ON, 1, Boolean.FALSE, ev);
            put0(HVAC_FAN, 1, 2, ev);
            put0(HVAC_TEMP, 1, 24f, ev); put0(HVAC_TEMP, 4, 24f, ev);
            for (int k : KEYS) put0(k, 0, 2, ev);
            mFnVal.put(WASH_MODE, WASH_OFF); mFnStatus.put(WASH_MODE, "active");
            refresh(ev);
        }
        fire(ev);
    }

    // ---------- อ่าน ----------
    synchronized Object get(int prop, int area) { return mVals.get(key(prop, area)); }

    /** ลองทีละ area (แบบ Vhal.getInt) · ไม่ระบุ area = ทุก area ที่มี */
    synchronized Integer getInt(int prop, int... areas) { Object o = first(prop, areas, Integer.class); return (Integer) o; }
    synchronized Float getFloat(int prop, int... areas) { return (Float) first(prop, areas, Float.class); }
    synchronized Boolean getBool(int prop, int... areas) { return (Boolean) first(prop, areas, Boolean.class); }

    synchronized boolean has(int prop) {
        for (Long k : mVals.keySet()) if ((int) (k >> 32) == prop) return true;
        return false;
    }

    private Object first(int prop, int[] areas, Class<?> t) {
        if (areas == null || areas.length == 0) {
            for (Map.Entry<Long, Object> e : mVals.entrySet())
                if ((int) (e.getKey() >> 32) == prop && t.isInstance(e.getValue())) return e.getValue();
            return null;
        }
        for (int a : areas) {
            Object o = mVals.get(key(prop, a));
            if (t.isInstance(o)) return o;
        }
        return null;
    }

    // ---------- เขียน (เหมือน Vhal.setX: ไม่มีคู่ prop/area หรือชนิดไม่ตรง = ไม่ผ่าน) ----------
    boolean set(int prop, int area, Object v) {
        List<Ev> ev = new ArrayList<Ev>();
        boolean ok;
        synchronized (this) {
            Object cur = mVals.get(key(prop, area));
            ok = cur != null && cur.getClass() == v.getClass() && write(prop, area, v, ev);
            if (ok) refresh(ev);
        }
        fire(ev);
        return ok;
    }

    /** ตั้งที่ area แรกที่มี (แบบ Vhal.setInt) */
    boolean setInt(int prop, int value, int... areas) {
        for (int a : areas) if (get(prop, a) instanceof Integer) return set(prop, a, value);
        return false;
    }

    /** ตั้งทุก area ที่มี (แบบ Vhal.setFloat — คนขับ+ผู้โดยสาร) */
    boolean setFloat(int prop, float value, int... areas) {
        boolean any = false;
        for (int a : areas) if (get(prop, a) instanceof Float && set(prop, a, value)) any = true;
        return any;
    }

    boolean setBool(int prop, boolean value, int... areas) {
        for (int a : areas) if (get(prop, a) instanceof Boolean) return set(prop, a, value);
        return false;
    }

    /** สร้าง/ทับค่าได้เลย (ใช้จากหน้าควบคุม cmd=set) */
    void put(int prop, int area, Object v) {
        List<Ev> ev = new ArrayList<Ev>();
        synchronized (this) { write(prop, area, v, ev); refresh(ev); }
        fire(ev);
    }

    private boolean write(int prop, int area, Object v, List<Ev> ev) {
        if (prop == GEAR_SEL || prop == GEAR_CUR) return v instanceof Integer && setGear((Integer) v, ev);
        if (prop == ODO && v instanceof Number) odo = Math.max(0, ((Number) v).doubleValue());
        if (prop == SOC && v instanceof Number) soc = Math.max(0, Math.min(100, ((Number) v).doubleValue()));
        put0(prop, area, v, ev);
        return true;
    }

    // ---------- ฟังค่า ----------
    /** prop = 0 ฟังทุกตัว · ส่งค่าปัจจุบันให้ทันที (เหมือน registerCallback ของรถ) */
    void listen(int prop, Listener l) {
        Sub s = new Sub();
        s.prop = prop; s.l = l;
        List<Ev> now = new ArrayList<Ev>();
        synchronized (this) {
            mSubs.add(s);
            for (Map.Entry<Long, Object> e : mVals.entrySet()) {
                int p = (int) (e.getKey() >> 32);
                if (prop != 0 && p != prop) continue;
                Ev x = new Ev(); x.prop = p; x.area = (int) (long) e.getKey(); x.val = e.getValue(); now.add(x);
            }
        }
        for (Ev x : now) call(l, x);
    }

    void unlisten(Listener l) {
        for (Sub s : mSubs) if (s.l == l) mSubs.remove(s);
    }

    private void fire(List<Ev> ev) {
        for (Ev x : ev) for (Sub s : mSubs) if (s.prop == 0 || s.prop == x.prop) call(s.l, x);
    }

    private static void call(Listener l, Ev x) {
        try { l.onChange(x.prop, x.area, x.val); } catch (Throwable ignored) {}
    }

    // ---------- ECarX adaptapi (EcarxFn) ----------
    synchronized String fnStatus(int func) { String s = mFnStatus.get(func); return s == null ? "notavailable" : s; }
    synchronized Integer fnGet(int func) { return mFnVal.get(func); }
    synchronized int[] fnSupported(int func) { return func == WASH_MODE ? new int[]{WASH_OFF, WASH_MOVING} : null; }
    synchronized boolean fnSet(int func, int value) {
        if (!"active".equals(fnStatus(func)) || !mFnVal.containsKey(func)) return false;
        mFnVal.put(func, value);
        return true;
    }
    synchronized void setFnStatus(int func, String st) { mFnStatus.put(func, st); }

    // ---------- สั่งขับ ----------
    /** เป้าความเร็ว km/h ("คันเร่ง") — ขยับจริงเมื่อเกียร์ D/R เท่านั้น */
    synchronized void setTarget(double kmh) { target = Math.max(0, Math.min(160, kmh)); }

    synchronized void setPlugged(boolean on) { plugged = on; if (!on) charging = false; }

    synchronized void setChargeKw(double kw) { chargeKw = Math.max(0, kw); }

    void setSoc(double pct) { put(SOC, 0, (float) pct); }

    /** เปลี่ยนเกียร์ (VehicleGear N=1 R=2 P=4 D=8) · วิ่งเกิน 3 km/h แล้วเข้า P หรือสลับ R↔D = ไม่ยอม */
    boolean setGear(int g) {
        List<Ev> ev = new ArrayList<Ev>();
        boolean ok;
        synchronized (this) { ok = setGear(g, ev); refresh(ev); }
        fire(ev);
        return ok;
    }

    private boolean setGear(int g, List<Ev> ev) {
        if (g != G_N && g != G_R && g != G_P && g != G_D) return false;
        if (g == gear) return true;
        boolean moving = speed > MOVING_KMH;
        if (moving && (g == G_P || (g == G_R && gear == G_D) || (g == G_D && gear == G_R))) return false;
        gear = g;
        put0(GEAR_SEL, 0, g, ev); put0(GEAR_CUR, 0, g, ev);
        return true;
    }

    /** กดปุ่มพวงมาลัย: ลง (1) → รอ holdMs → ปล่อย (2) */
    void key(final int prop, long holdMs) {
        keyDown(prop);
        exec().schedule(new Runnable() { public void run() { keyUp(prop); } }, holdMs, TimeUnit.MILLISECONDS);
    }

    void keyDown(int prop) { put(prop, 0, 1); }
    void keyUp(int prop) { put(prop, 0, 2); }

    // ---------- โมเดลขับ ----------
    /** เดินเวลา dtMs (แบ่งก้าวละ ≤ 100 ms ให้นุ่ม) · เรียกจาก tick พื้นหลัง หรือทดสอบบนคอม */
    void tick(long dtMs) {
        List<Ev> ev = new ArrayList<Ev>();
        synchronized (this) {
            for (long rem = dtMs; rem > 0; ) {
                long s = Math.min(rem, STEP_MS);
                step(s / 1000.0);
                rem -= s;
            }
            refresh(ev);
        }
        fire(ev);
    }

    private double effTarget() {
        if (soc <= 0) return 0;
        if (gear == G_D) return target;
        if (gear == G_R) return Math.min(target, MAX_REV);
        return 0;   // P / N
    }

    private void step(double dt) {
        double v0 = speed, tgt = effTarget(), d = tgt - v0;
        double v = v0 + Math.max(-DECEL, Math.min(ACCEL, K * d)) * dt;
        if ((d > 0 && v > tgt) || (d < 0 && v < tgt) || Math.abs(tgt - v) < SNAP) v = tgt;
        speed = v;
        double km = (v0 + v) / 2 * dt / 3600.0;
        odo += km;
        soc = Math.max(0, soc - km * KWH_100KM / 100 / CAP_KWH * 100);
        charging = plugged && gear == G_P && v == 0 && soc < 100;
        if (charging) soc = Math.min(100, soc + chargeKw * dt / 3600.0 / CAP_KWH * 100);
    }

    /** ค่าที่คำนวณได้ → ที่เก็บ (เฉพาะที่เปลี่ยนจึงมี event) */
    private void refresh(List<Ev> ev) {
        put0(SPEED, 0, (float) speed, ev);
        put0(SPEED_DISP, 0, (int) Math.round(speed * 18), ev);   // TripLog: int ÷ 18 = km/h
        put0(ODO, 0, (float) odo, ev);
        put0(SOC, 0, Math.round(soc * 100) / 100f, ev);
        put0(RANGE, 0, (float) (soc / 100 * CAP_KWH / (KWH_100KM / 100)), ev);
    }

    private boolean put0(int prop, int area, Object v, List<Ev> ev) {
        Object old = mVals.put(key(prop, area), v);
        if (v.equals(old)) return false;
        Ev x = new Ev();
        x.prop = prop; x.area = area; x.val = v;
        ev.add(x);
        return true;
    }

    private static long key(int prop, int area) { return ((long) prop << 32) | (area & 0xffffffffL); }

    // ---------- สถานะ ----------
    synchronized double speed() { return speed; }
    synchronized int gear() { return gear; }
    synchronized boolean charging() { return charging; }
    synchronized boolean plugged() { return plugged; }

    static String gearName(int g) { return g == G_P ? "P" : g == G_R ? "R" : g == G_N ? "N" : g == G_D ? "D" : String.valueOf(g); }

    /** JSON บรรทัดเดียว (ASCII ล้วน — ผ่าน adb/Binder ได้ปลอดภัย) */
    synchronized String dump() {
        StringBuilder b = new StringBuilder("{\"ok\":true");
        b.append(String.format(Locale.US, ",\"speed\":%.1f,\"target\":%.1f,\"gear\":\"%s\",\"soc\":%.2f,\"odo\":%.3f,\"range\":%.1f",
                speed, target, gearName(gear), soc, odo, soc / 100 * CAP_KWH / (KWH_100KM / 100)));
        b.append(String.format(Locale.US, ",\"plugged\":%d,\"charging\":%d,\"chargeKw\":%.1f", plugged ? 1 : 0, charging ? 1 : 0, chargeKw));
        b.append(",\"wash\":{\"status\":\"").append(fnStatus(WASH_MODE)).append("\",\"value\":").append(mFnVal.get(WASH_MODE)).append('}');
        b.append(",\"props\":[");
        boolean first = true;
        for (Map.Entry<Long, Object> e : mVals.entrySet()) {
            Object v = e.getValue();
            b.append(first ? "" : ",").append(String.format(Locale.US, "{\"p\":\"0x%08x\",\"a\":%d,\"v\":%s}",
                    (int) (e.getKey() >> 32), (int) (long) e.getKey(), v instanceof Float ? String.format(Locale.US, "%.3f", v) : String.valueOf(v)));
            first = false;
        }
        return b.append("]}").toString();
    }

    // ---------- คำสั่งจากหน้าควบคุม (SimCtl / carsim.py) ----------
    static String reply(boolean ok, String msg) {
        return "{\"ok\":" + ok + ",\"msg\":\"" + msg.replace("\\", "/").replace("\"", "'") + "\"}";
    }

    static int parseInt(String s) { return (int) Long.decode(s.trim()).longValue(); }

    private static boolean truthy(String s) { return s != null && (s.equals("1") || s.equalsIgnoreCase("true") || s.equalsIgnoreCase("on")); }

    /** a: cmd + v/prop/area/long/type (ค่าทั้งหมดเป็นข้อความ) → JSON ตอบกลับ */
    String command(Map<String, String> a) {
        String cmd = a.get("cmd"), v = a.get("v");
        if (cmd == null) return reply(false, "no cmd");
        try {
            if (cmd.equals("dump")) return dump();
            if (cmd.equals("speed")) { setTarget(Double.parseDouble(v)); return reply(true, "target " + v + " km/h" + (gear() == G_D || gear() == G_R ? "" : " (gear " + gearName(gear()) + ": car will not move)")); }
            if (cmd.equals("gear")) {
                int g = v.length() == 1 && Character.isLetter(v.charAt(0)) ? "NRPD".indexOf(v.toUpperCase().charAt(0)) : -1;
                int code = g >= 0 ? new int[]{G_N, G_R, G_P, G_D}[g] : parseInt(v);
                boolean ok = setGear(code);
                return reply(ok, (ok ? "gear " : "gear rejected (moving?), now ") + gearName(gear()));
            }
            if (cmd.equals("key")) { key(parseInt(v), truthy(a.get("long")) ? LONG_MS : TAP_MS); return reply(true, "key " + v); }
            if (cmd.equals("plug")) { setPlugged(truthy(v)); return reply(true, "plugged " + truthy(v)); }
            if (cmd.equals("soc")) { setSoc(Double.parseDouble(v)); return reply(true, "soc " + v); }
            if (cmd.equals("chargekw")) { setChargeKw(Double.parseDouble(v)); return reply(true, "chargeKw " + v); }
            if (cmd.equals("wash")) { setFnStatus(WASH_MODE, v); return reply(true, "wash status " + v); }
            if (cmd.equals("reset")) { reset(); return reply(true, "reset"); }
            if (cmd.equals("set")) {
                int prop = parseInt(a.get("prop")), area = a.get("area") == null ? 0 : parseInt(a.get("area"));
                Object cur = get(prop, area);
                String t = a.get("type");
                if (t == null) t = cur instanceof Float ? "f" : cur instanceof Boolean ? "b" : cur instanceof Integer ? "i"
                        : v.equalsIgnoreCase("true") || v.equalsIgnoreCase("false") ? "b" : v.contains(".") ? "f" : "i";
                put(prop, area, t.equals("f") ? (Object) Float.valueOf(v) : t.equals("b") ? (Object) Boolean.valueOf(truthy(v)) : (Object) Integer.valueOf(parseInt(v)));
                return reply(true, String.format("set 0x%08x/%d = %s", prop, area, v));
            }
        } catch (Throwable t) {
            return reply(false, cmd + ": " + t.getClass().getSimpleName() + " " + t.getMessage());
        }
        return reply(false, "unknown cmd " + cmd);
    }
}
