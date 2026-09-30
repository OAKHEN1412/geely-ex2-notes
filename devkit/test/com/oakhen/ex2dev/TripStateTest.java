package com.oakhen.ex2dev;

import java.util.ArrayList;
import java.util.List;

/** ทดสอบ TripState บนคอม: bash devkit/test/run_trip_test.sh */
public class TripStateTest {
    static int fail = 0;
    static void check(boolean ok, String what) { System.out.println((ok ? "  ok   " : "  FAIL ") + what); if (!ok) fail++; }
    static final long S = 1000, M = 60000;

    static List<Object> all = new ArrayList<Object>();
    static TripState st;
    static long t;
    static double odo, soc;
    static void feed(Double kmh, Integer gear, boolean odoOk, boolean socOk) {
        all.addAll(st.onSample(t, kmh, gear, odoOk ? odo : null, socOk ? soc : null));
    }
    static void drive(long ms, double kmh, int gear) {   // ทุก 5 วิ
        for (long e = 0; e < ms; e += 5 * S) { t += 5 * S; odo += kmh * 5.0 / 3600.0; soc -= kmh * 5.0 / 3600.0 * 0.15; feed(kmh, gear, true, true); }
    }
    static List<TripState.Trip> trips() { List<TripState.Trip> o = new ArrayList<TripState.Trip>(); for (Object x : all) if (x instanceof TripState.Trip) o.add((TripState.Trip) x); return o; }
    static List<TripState.Charge> charges() { List<TripState.Charge> o = new ArrayList<TripState.Charge>(); for (Object x : all) if (x instanceof TripState.Charge) o.add((TripState.Charge) x); return o; }
    static void reset() { st = new TripState(); all.clear(); t = 1000000000000L; odo = 12000; soc = 80; }

    public static void main(String[] a) {
        System.out.println("1) ขับปกติ 10 นาที 40 km/h แล้วจอด P 3 นาที");
        reset();
        drive(30 * S, 0, 4);
        long startExpect = t + 5 * S;
        drive(10 * M, 40, 8);
        long parkAt = t + 5 * S;
        drive(3 * M, 0, 4);
        List<TripState.Trip> tr = trips();
        check(tr.size() == 1, "ได้ 1 ทริป (ได้ " + tr.size() + ")");
        if (tr.size() == 1) {
            TripState.Trip x = tr.get(0);
            check(Math.abs(x.km - 6.67) < 0.1, "ระยะ ~6.67 km (ได้ " + x.km + ")");
            check(x.start == startExpect, "เวลาเริ่ม = ตอนออกจาก P");
            check(x.end == parkAt, "เวลาจบ = ตอนเริ่มจอด ไม่ใช่ 2 นาทีหลัง");
            check(Math.abs(x.socStart - 80) < 0.01 && x.socEnd < 80, "แบต 80 → " + x.socEnd);
            check(Math.abs(x.avgKmh() - 40) < 1, "ความเร็วเฉลี่ย ~40 (ได้ " + x.avgKmh() + ")");
            check(Math.abs(TripState.kwh(x, 39.4) - (x.socStart - x.socEnd) / 100 * 39.4) < 1e-9, "kWh = %แบต × ความจุ");
        }

        System.out.println("2) ขยับรถในที่จอด (D 20 วิ 2 km/h) → ไม่นับ");
        reset();
        drive(20 * S, 2, 8);
        drive(3 * M, 0, 4);
        check(trips().isEmpty(), "ไม่มีทริป (ได้ " + trips().size() + ")");

        System.out.println("3) รถดับกลางทาง (ข้อมูลขาด 15 นาที) → ทริปจบที่ค่าล่าสุด");
        reset();
        drive(5 * M, 50, 8);
        long lastSample = t;
        t += 15 * M;
        feed(0.0, 4, true, true);
        tr = trips();
        check(tr.size() == 1 && tr.get(0).end == lastSample, "จบที่เวลาค่าล่าสุดก่อนดับ");

        System.out.println("4) ชาร์จระหว่างจอด 60% → 85% แล้วออกรถ → เจอการชาร์จ");
        reset();
        drive(5 * M, 50, 8);
        drive(3 * M, 0, 4);
        double endSoc = trips().get(0).socEnd;
        soc = endSoc + 25;
        drive(10 * S, 20, 8);
        List<TripState.Charge> ch = charges();
        check(ch.size() == 1 && Math.abs(ch.get(0).from - endSoc) < 0.01 && Math.abs(ch.get(0).to - (endSoc + 25)) < 0.1, "การชาร์จ " + endSoc + " → " + (endSoc + 25));

        System.out.println("5) จอด 30 วิแล้วดับรถ → forceEnd จบที่ตอนเริ่มจอด");
        reset();
        drive(5 * M, 50, 8);
        long parkAt2 = t + 5 * S;
        drive(30 * S, 0, 4);
        all.addAll(st.forceEnd());
        tr = trips();
        check(tr.size() == 1 && tr.get(0).end == parkAt2, "จบที่ตอนเริ่มจอด");

        System.out.println("6) บันทึก/โหลดสถานะกลางทริป (แอพถูกปิด) แล้วขับต่อ");
        reset();
        drive(3 * M, 60, 8);
        String saved = st.save();
        st = TripState.load(saved);
        drive(3 * M, 60, 8);
        drive(3 * M, 0, 4);
        tr = trips();
        check(tr.size() == 1 && Math.abs(tr.get(0).km - 6.0) < 0.1, "ทริปเดียวต่อเนื่อง ~6 km (ได้ " + (tr.isEmpty() ? "-" : tr.get(0).km) + ")");

        System.out.println("7) อ่านไมล์ไม่ได้ → ใช้ระยะจากความเร็ว");
        reset();
        for (long e = 0; e < 6 * M; e += 5 * S) { t += 5 * S; feed(60.0, 8, false, true); }
        for (long e = 0; e < 3 * M; e += 5 * S) { t += 5 * S; feed(0.0, 4, false, true); }
        tr = trips();
        check(tr.size() == 1 && Math.abs(tr.get(0).km - 6.0) < 0.15, "~6 km จากความเร็ว (ได้ " + (tr.isEmpty() ? "-" : tr.get(0).km) + ")");

        System.out.println("8) CSV ไป-กลับ");
        TripState.Trip x = tr.get(0); x.cost = 42.5;
        TripState.Trip y = TripState.Trip.parse(x.csv().split(","));
        check(y.start == x.start && y.end == x.end && Math.abs(y.km - x.km) < 0.001 && y.cost == 42.5, "ค่าเท่าเดิม + ค่าใช้จ่ายที่ใส่เอง");
        check(TripState.cost(y, 39.4, 5) == 42.5, "ค่าใช้จ่ายที่ใส่เองมาก่อนค่าคำนวณ");

        System.out.println(fail == 0 ? "ALL PASS" : fail + " FAIL");
        System.exit(fail == 0 ? 0 : 1);
    }
}
