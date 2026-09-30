package com.oakhen.ex2dev;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ตัวตัดสินทริป (Java ล้วน ไม่พึ่ง Android — ทดสอบบนคอมได้: devkit/test/TripStateTest.java)
 * ป้อนค่าจากรถทุก ~5 วิ (ความเร็ว km/h, เกียร์, ไมล์รวม km, แบต %) → คืนทริปที่จบ / การชาร์จที่เจอ
 *  - เริ่มทริป: ออกจากเกียร์ P หรือวิ่ง > 3 km/h
 *  - จบทริป: จอด P ค้าง ≥ 2 นาที (เวลาจบ = ตอนเริ่มจอด) · รถดับ/ข้อมูลขาด > 10 นาที (จบที่ค่าล่าสุด) · forceEnd
 *  - ระยะ: ไมล์รวมท้าย−ต้น (ถ้าอ่านได้) ไม่งั้นรวมจากความเร็ว · ทริปสั้นกว่า 0.1 km ทิ้ง (ขยับรถในที่จอด)
 *  - ชาร์จ: แบตตอนเริ่มทริปมากกว่าตอนจบทริปก่อน ≥ 1% = มีการชาร์จระหว่างจอด
 */
final class TripState {
    static final int GEAR_P = 4;
    static final long PARK_END_MS = 120000, GAP_END_MS = 600000;
    static final double MIN_KM = 0.1;

    static final class Trip {
        long start, end, movingMs; double km, socStart = -1, socEnd = -1, maxKmh; double cost = -1;   // cost ≥ 0 = ผู้ใช้ใส่เอง
        double avgKmh() { return movingMs > 0 ? km / (movingMs / 3600000.0) : 0; }
        String csv() {
            return String.format(Locale.US, "T,%d,%d,%.3f,%.1f,%.1f,%.1f,%d,%.2f", start, end, km, socStart, socEnd, maxKmh, movingMs, cost);
        }
        static Trip parse(String[] f) {
            Trip t = new Trip();
            t.start = Long.parseLong(f[1]); t.end = Long.parseLong(f[2]); t.km = Double.parseDouble(f[3]);
            t.socStart = Double.parseDouble(f[4]); t.socEnd = Double.parseDouble(f[5]); t.maxKmh = Double.parseDouble(f[6]);
            t.movingMs = Long.parseLong(f[7]); t.cost = f.length > 8 ? Double.parseDouble(f[8]) : -1;
            return t;
        }
    }

    static final class Charge {
        long at; double from, to;
        String csv() { return String.format(Locale.US, "C,%d,%.1f,%.1f", at, from, to); }
        static Charge parse(String[] f) { Charge c = new Charge(); c.at = Long.parseLong(f[1]); c.from = Double.parseDouble(f[2]); c.to = Double.parseDouble(f[3]); return c; }
    }

    // สถานะ (เก็บลง Prefs ด้วย save()/load() กันแอพถูกปิดกลางทริป)
    boolean active;
    long startAt, lastAt, parkedSince, movingMs;
    double startOdo = -1, lastOdo = -1, startSoc = -1, lastSoc = -1, maxKmh, distInt, parkedOdo = -1, parkedSoc = -1, parkedDist;
    double lastEndSoc = -1;

    /** ป้อนค่าหนึ่งชุด (ค่าที่อ่านไม่ได้ = null) · คืน Trip/Charge ที่เกิดขึ้น */
    List<Object> onSample(long now, Double kmh, Integer gear, Double odo, Double soc) {
        List<Object> out = new ArrayList<Object>();
        if (active && lastAt > 0 && now - lastAt > GAP_END_MS) finish(out, lastAt, lastOdo, lastSoc, distInt);
        boolean moving = kmh != null && kmh > 3;
        boolean parked = gear != null ? gear == GEAR_P : (kmh != null && kmh < 1);
        if (!active) {
            if (!parked || moving) {
                active = true;
                startAt = now; startOdo = odo != null ? odo : -1; startSoc = soc != null ? soc : -1;
                lastOdo = startOdo; lastSoc = startSoc; maxKmh = 0; distInt = 0; movingMs = 0; parkedSince = 0;
                if (lastEndSoc >= 0 && soc != null && soc >= lastEndSoc + 1) {
                    Charge c = new Charge(); c.at = now; c.from = lastEndSoc; c.to = soc; out.add(c);
                }
            }
        } else {
            if (kmh != null && lastAt > 0) {
                long dt = Math.min(now - lastAt, 60000);   // กันช่องว่างยาวทำระยะเพี้ยน
                if (kmh > 1) movingMs += dt;
                distInt += kmh * dt / 3600000.0;
                if (kmh > maxKmh) maxKmh = kmh;
            }
            if (odo != null) lastOdo = odo;
            if (soc != null) lastSoc = soc;
            if (parked && !moving) {
                if (parkedSince == 0) { parkedSince = now; parkedOdo = lastOdo; parkedSoc = lastSoc; parkedDist = distInt; }
                else if (now - parkedSince >= PARK_END_MS) finish(out, parkedSince, parkedOdo, parkedSoc, parkedDist);
            } else parkedSince = 0;
        }
        lastAt = now;
        return out;
    }

    /** รถดับ/จอหลับ → จบทริปที่ค่าล่าสุด */
    List<Object> forceEnd() {
        List<Object> out = new ArrayList<Object>();
        if (active) {
            if (parkedSince > 0) finish(out, parkedSince, parkedOdo, parkedSoc, parkedDist);
            else finish(out, lastAt, lastOdo, lastSoc, distInt);
        }
        return out;
    }

    private void finish(List<Object> out, long endAt, double odoEnd, double socEnd, double dist) {
        Trip t = new Trip();
        t.start = startAt; t.end = endAt;
        t.km = (startOdo >= 0 && odoEnd >= startOdo) ? odoEnd - startOdo : dist;
        t.socStart = startSoc; t.socEnd = socEnd; t.maxKmh = maxKmh; t.movingMs = movingMs;
        if (t.km >= MIN_KM) out.add(t);
        if (socEnd >= 0) lastEndSoc = socEnd;
        active = false; parkedSince = 0;
    }

    String save() {
        return String.format(Locale.US, "%b,%d,%d,%d,%d,%.3f,%.3f,%.1f,%.1f,%.1f,%.4f,%.3f,%.1f,%.4f,%.1f",
                active, startAt, lastAt, parkedSince, movingMs, startOdo, lastOdo, startSoc, lastSoc, maxKmh, distInt,
                parkedOdo, parkedSoc, parkedDist, lastEndSoc);
    }

    static TripState load(String s) {
        TripState t = new TripState();
        if (s == null || s.isEmpty()) return t;
        try {
            String[] f = s.split(",");
            t.active = Boolean.parseBoolean(f[0]); t.startAt = Long.parseLong(f[1]); t.lastAt = Long.parseLong(f[2]);
            t.parkedSince = Long.parseLong(f[3]); t.movingMs = Long.parseLong(f[4]); t.startOdo = Double.parseDouble(f[5]);
            t.lastOdo = Double.parseDouble(f[6]); t.startSoc = Double.parseDouble(f[7]); t.lastSoc = Double.parseDouble(f[8]);
            t.maxKmh = Double.parseDouble(f[9]); t.distInt = Double.parseDouble(f[10]); t.parkedOdo = Double.parseDouble(f[11]);
            t.parkedSoc = Double.parseDouble(f[12]); t.parkedDist = Double.parseDouble(f[13]); t.lastEndSoc = Double.parseDouble(f[14]);
        } catch (Throwable e) { return new TripState(); }
        return t;
    }

    /** kWh ที่ใช้ = %แบตที่ลด × ความจุ (ติดลบ/อ่านไม่ได้ = 0) */
    static double kwh(Trip t, double capacityKwh) {
        if (t.socStart < 0 || t.socEnd < 0) return 0;
        return Math.max(0, (t.socStart - t.socEnd) / 100.0 * capacityKwh);
    }

    static double cost(Trip t, double capacityKwh, double pricePerKwh) {
        return t.cost >= 0 ? t.cost : kwh(t, capacityKwh) * pricePerKwh;
    }
}
