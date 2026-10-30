package com.oakhen.ex2dev;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** ทดสอบ SimCar บนคอม: bash devkit/test/run_sim_test.sh */
public class SimCarTest {
    static int fail = 0;
    static void check(boolean ok, String what) { System.out.println((ok ? "  ok   " : "  FAIL ") + what); if (!ok) fail++; }
    static boolean near(double a, double b, double tol) { return Math.abs(a - b) <= tol; }
    static final int[] G0 = {0};

    static Map<String, String> cmd(String... kv) {
        Map<String, String> m = new HashMap<String, String>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("0) รหัสใน SimCar ตรงกับ Vhal / EcarxFn / TripLog / ReverseWatch");
        String src = System.getProperty("src");
        String vhal = read(src, "Vhal.java"), trip = read(src, "TripLog.java"), rev = read(src, "ReverseWatch.java"), ecx = read(src, "EcarxFn.java");
        checkConst(vhal, "DRIVE_MODE", SimCar.DRIVE_MODE); checkConst(vhal, "DRIVE_ECO", SimCar.DRIVE_ECO);
        checkConst(vhal, "REGEN", SimCar.REGEN); checkConst(vhal, "REGEN_MID", SimCar.REGEN_MID);
        checkConst(vhal, "HVAC_AC_ON", SimCar.HVAC_AC_ON); checkConst(vhal, "HVAC_FAN", SimCar.HVAC_FAN); checkConst(vhal, "HVAC_TEMP", SimCar.HVAC_TEMP);
        checkConst(vhal, "WASH_MODE", SimCar.WASH_MODE); checkConst(vhal, "WASH_MOVING", SimCar.WASH_MOVING);
        checkConst(ecx, "WASH_MODE", SimCar.WASH_MODE); checkConst(ecx, "WASH_OFF", SimCar.WASH_OFF); checkConst(ecx, "WASH_MOVING", SimCar.WASH_MOVING);
        checkConst(trip, "SPEED", SimCar.SPEED); checkConst(trip, "SPEED_DISP", SimCar.SPEED_DISP); checkConst(trip, "GEAR", SimCar.GEAR_CUR);
        checkConst(trip, "ODO", SimCar.ODO); checkConst(trip, "SOC", SimCar.SOC);
        checkConst(rev, "GEAR_SELECTION", SimCar.GEAR_SEL); checkConst(rev, "CURRENT_GEAR", SimCar.GEAR_CUR);
        check(SimCar.G_P == TripState.GEAR_P, "เกียร์ P ตรง TripState");

        System.out.println("1) ค่าเริ่มต้น");
        SimCar c = new SimCar();
        check(c.getInt(SimCar.GEAR_CUR, G0) == SimCar.G_P && c.getInt(SimCar.GEAR_SEL, G0) == SimCar.G_P, "เกียร์ P");
        check(c.getFloat(SimCar.SPEED, G0) == 0f, "ความเร็ว 0");
        check(c.getFloat(SimCar.SOC, G0) == 80f, "แบต 80%");
        check(c.getFloat(SimCar.ODO, G0) > 0, "ไมล์รวม > 0");
        check(c.getInt(SimCar.DRIVE_MODE, 0, 1, 0x1000000) == SimCar.DRIVE_ECO, "โหมดขับ ECO (ลองหลาย area)");
        check(c.getInt(SimCar.REGEN, 0, 1) == SimCar.REGEN_MID, "รีเจนกลาง");
        check(Boolean.FALSE.equals(c.getBool(SimCar.HVAC_AC_ON, 1, 5, 0x31, 0)), "แอร์ปิด");
        check(c.getFloat(SimCar.HVAC_TEMP, 1, 4) == 24f && c.getInt(SimCar.HVAC_FAN, 1, 5, 0x31, 0) == 2, "24 °C · พัดลม 2");
        check("active".equals(c.fnStatus(SimCar.WASH_MODE)) && c.fnGet(SimCar.WASH_MODE) == SimCar.WASH_OFF, "โหมดล้างรถ OFF · รองรับ");
        check(c.fnSupported(SimCar.WASH_MODE).length == 2 && "notavailable".equals(c.fnStatus(0x1234)), "ค่าที่รองรับ · func อื่น notavailable");
        check(c.get(SimCar.KEYS[4], 0).equals(2), "ปุ่มพวงมาลัยอยู่สถานะปล่อย (2)");
        check(c.getInt(SimCar.SPEED, G0) == null, "อ่านชนิดไม่ตรง → null");

        System.out.println("2) set/get ตามกติการถ");
        check(c.setInt(SimCar.DRIVE_MODE, 0x22010103, 0, 1) && c.getInt(SimCar.DRIVE_MODE, 0) == 0x22010103, "setInt โหมดขับ");
        check(!c.setInt(SimCar.DRIVE_MODE, 5, 0x77) && !c.setBool(SimCar.DRIVE_MODE, true, 0), "area ผิด/ชนิดผิด = ไม่ผ่าน");
        check(c.setFloat(SimCar.HVAC_TEMP, 21.5f, 1, 4) && c.getFloat(SimCar.HVAC_TEMP, 4) == 21.5f && c.getFloat(SimCar.HVAC_TEMP, 1) == 21.5f, "setFloat ทุก area");
        check(c.setBool(SimCar.HVAC_AC_ON, true, 1, 5, 0x31, 0) && c.getBool(SimCar.HVAC_AC_ON, 1), "setBool แอร์เปิด");
        check(c.fnSet(SimCar.WASH_MODE, SimCar.WASH_MOVING) && c.fnGet(SimCar.WASH_MODE) == SimCar.WASH_MOVING, "fnSet โหมดล้างรถ");
        c.setFnStatus(SimCar.WASH_MODE, "notavailable");
        check(!c.fnSet(SimCar.WASH_MODE, SimCar.WASH_OFF), "notavailable → สั่งไม่ได้");
        c.put(0x7f000001, 3, 42);
        check(c.getInt(0x7f000001) == 42 && c.has(0x7f000001) && c.getInt(0x7f000001, 3) == 42, "put สร้าง prop ใหม่ · อ่านโดยไม่ระบุ area");
        c.reset();
        check(c.getInt(SimCar.DRIVE_MODE, 0) == SimCar.DRIVE_ECO && "active".equals(c.fnStatus(SimCar.WASH_MODE)), "reset กลับค่าเริ่ม");

        System.out.println("3) listener");
        final List<String> got = new ArrayList<String>();
        SimCar.Listener l = new SimCar.Listener() {
            public void onChange(int p, int a, Object v) { got.add(String.format("%08x/%d=%s", p, a, v)); }
        };
        c.listen(SimCar.GEAR_CUR, l);
        check(got.size() == 1 && got.get(0).equals("11400401/0=4"), "listen ส่งค่าปัจจุบันให้ก่อน 1 ครั้ง: " + got);
        got.clear();
        check(c.setGear(SimCar.G_D), "เข้า D");
        check(got.size() == 1 && got.get(0).equals("11400401/0=8"), "เกียร์เปลี่ยน → listener ยิง 1 ครั้ง: " + got);
        c.setTarget(40); c.tick(1000);
        check(got.size() == 1, "ความเร็วเปลี่ยนไม่ยิง listener ของเกียร์");
        c.unlisten(l); c.setGear(SimCar.G_N);
        check(got.size() == 1, "unlisten แล้วเงียบ");
        final List<String> keys = new ArrayList<String>();
        c.reset();
        c.listen(0, new SimCar.Listener() { public void onChange(int p, int a, Object v) { if ((p & 0xFFFF0000) == 0x41020000) keys.add(Integer.toHexString(p) + "=" + v); } });
        keys.clear();
        c.keyDown(0x41020500); c.keyUp(0x41020500);
        check(keys.size() == 2 && keys.get(0).equals("41020500=1") && keys.get(1).equals("41020500=2"), "ปุ่มสั่งเสียง ลง(1) → ปล่อย(2): " + keys);
        keys.clear();
        c.key(0x41020100, 300);
        check(keys.size() == 1 && keys.get(0).equals("41020100=1"), "key(): ลงทันที");
        Thread.sleep(700);
        check(keys.size() == 2 && keys.get(1).equals("41020100=2"), "key(): ปล่อยหลัง hold: " + keys);
        c.stop();

        System.out.println("4) เร่ง/เบรก + ไมล์ + แบตลด");
        c.reset();
        c.setTarget(60);
        c.tick(5000);
        check(c.speed() == 0, "เกียร์ P: คันเร่งไม่ทำให้ขยับ");
        c.setGear(SimCar.G_D);
        c.tick(1000);
        double v1 = c.speed();
        check(v1 > 5 && v1 <= 8.5, "1 วิแรกเร่งนุ่ม ≤ 8 km/h/s (ได้ " + v1 + ")");
        c.tick(4000);
        check(c.speed() > v1 && c.speed() < 60, "5 วิ ยังไม่ถึง 60 (ได้ " + c.speed() + ")");
        c.tick(20000);
        check(near(c.speed(), 60, 0.01), "ถึง 60 ที่ 25 วิ (ได้ " + c.speed() + ")");
        check(c.getFloat(SimCar.SPEED, G0) == 60f && c.getInt(SimCar.SPEED_DISP, G0) == 1080, "SPEED / SPEED_DISP(×18) อัปเดต");
        check(!c.setGear(SimCar.G_P) && c.gear() == SimCar.G_D, "วิ่งอยู่ เข้า P ไม่ได้");
        check(!c.setGear(SimCar.G_R) && c.gear() == SimCar.G_D, "วิ่งอยู่ D→R ไม่ได้");
        double odo0 = c.getFloat(SimCar.ODO, G0), soc0 = c.getFloat(SimCar.SOC, G0);
        c.tick(60000);   // ล่องที่ 60 km/h 1 นาที = 1 km
        double dOdo = c.getFloat(SimCar.ODO, G0) - odo0, dSoc = soc0 - c.getFloat(SimCar.SOC, G0);
        check(near(dOdo, 1.0, 0.01), "ไมล์เพิ่ม 1 km ใน 1 นาทีที่ 60 (ได้ " + dOdo + ")");
        double expect = 1.0 * 13 / 100 / 39.4 * 100;   // 0.33 %
        check(near(dSoc, expect, 0.02), "แบตลด ~" + String.format("%.2f", expect) + "% ต่อ km (ได้ " + dSoc + ")");
        check(near(c.getFloat(SimCar.RANGE, G0), c.getFloat(SimCar.SOC, G0) / 100 * 39.4 / 0.13, 0.5), "ระยะวิ่งได้ = SOC × ความจุ ÷ 13 kWh/100km");
        c.setTarget(0);
        c.tick(2000);
        check(c.speed() < 60 && c.speed() > 0, "เบรกนุ่ม ไม่หยุดทันที (ได้ " + c.speed() + ")");
        c.tick(15000);
        check(c.speed() == 0, "หยุดสนิท");
        check(c.setGear(SimCar.G_P) && c.getInt(SimCar.GEAR_CUR, G0) == SimCar.G_P, "หยุดแล้วเข้า P ได้");
        c.reset(); c.setGear(SimCar.G_R); c.setTarget(40); c.tick(30000);
        check(near(c.speed(), 15, 0.01), "ถอยหลังจำกัด 15 km/h (ได้ " + c.speed() + ")");
        c.reset(); c.setGear(SimCar.G_D); c.setSoc(0); c.setTarget(50); c.tick(5000);
        check(c.speed() == 0, "แบตหมด = ไม่ขยับ");

        System.out.println("5) ชาร์จ");
        c.reset();
        c.setSoc(50);
        c.tick(3600 * 1000L);
        check(c.getFloat(SimCar.SOC, G0) == 50f && !c.charging(), "ไม่เสียบสาย = ไม่ชาร์จ");
        c.setPlugged(true);
        c.tick(3600 * 1000L);
        double gain = c.getFloat(SimCar.SOC, G0) - 50;
        check(c.charging() && near(gain, 7.0 / 39.4 * 100, 0.1), "เสียบ + P 1 ชม. ที่ 7 kW → +" + String.format("%.1f", 7.0 / 39.4 * 100) + "% (ได้ +" + gain + ")");
        c.setChargeKw(50); c.tick(3600 * 1000L);
        check(c.getFloat(SimCar.SOC, G0) == 100f && !c.charging(), "เต็ม 100% แล้วหยุดชาร์จ");
        c.setGear(SimCar.G_N); c.setSoc(50); c.tick(10000);
        check(c.getFloat(SimCar.SOC, G0) == 50f && !c.charging(), "เสียบสายแต่ไม่ใช่เกียร์ P = ไม่ชาร์จ");

        System.out.println("6) คำสั่งจากหน้าควบคุม");
        c.reset();
        check(c.command(cmd("cmd", "gear", "v", "D")).contains("\"ok\":true") && c.gear() == SimCar.G_D, "gear D");
        check(c.command(cmd("cmd", "speed", "v", "60")).contains("\"ok\":true"), "speed 60");
        c.tick(30000);
        check(near(c.speed(), 60, 0.01), "วิ่งตามคำสั่ง");
        check(c.command(cmd("cmd", "gear", "v", "P")).contains("\"ok\":false") && c.gear() == SimCar.G_D, "gear P ขณะวิ่ง → ok:false");
        check(c.command(cmd("cmd", "soc", "v", "20")).contains("\"ok\":true") && c.getFloat(SimCar.SOC, G0) == 20f, "soc 20");
        check(c.command(cmd("cmd", "plug", "v", "1")).contains("plugged true") && c.plugged(), "plug 1");
        check(c.command(cmd("cmd", "set", "prop", "0x22010100", "area", "0", "v", "0x22010102")).contains("\"ok\":true") && c.getInt(SimCar.DRIVE_MODE, 0) == 0x22010102, "set prop hex");
        check(c.command(cmd("cmd", "set", "prop", "0x15600099", "area", "1", "v", "true")).contains("\"ok\":true") && c.getBool(SimCar.HVAC_AC_ON, 1), "set bool ตามชนิดเดิม");
        check(c.command(cmd("cmd", "set", "prop", "0x25401036", "area", "1", "v", "18.5")).contains("\"ok\":true") && c.getFloat(SimCar.HVAC_TEMP, 1) == 18.5f, "set float");
        String d = c.command(cmd("cmd", "dump"));
        check(d.startsWith("{\"ok\":true") && d.contains("\"gear\":\"D\"") && d.contains("\"p\":\"0x22010100\",\"a\":0,\"v\":570491138") && d.indexOf('\n') < 0, "dump JSON บรรทัดเดียว: " + d.substring(0, Math.min(120, d.length())) + "...");
        check(c.command(cmd("cmd", "nope")).contains("\"ok\":false") && c.command(cmd("cmd", "soc", "v", "abc")).contains("\"ok\":false"), "คำสั่งผิด → ok:false ไม่ throw");
        c.stop();

        System.out.println("7) TripState ป้อนจากรถจำลอง → ได้ 1 ทริปถูกต้อง");
        SimCar s = new SimCar();
        TripState st = new TripState();
        List<Object> all = new ArrayList<Object>();
        long[] now = {1000000000000L};
        double soc0b = s.getFloat(SimCar.SOC, G0), odo0b = s.getFloat(SimCar.ODO, G0);
        feed(s, st, all, now, 30000);                                   // จอด P 30 วิ
        s.command(cmd("cmd", "gear", "v", "D")); s.command(cmd("cmd", "speed", "v", "50"));
        long startExpect = now[0] + 5000;
        feed(s, st, all, now, 10 * 60000);                             // ขับ 10 นาที
        s.command(cmd("cmd", "speed", "v", "0"));
        feed(s, st, all, now, 30000);                                   // เบรกจนหยุด
        check(s.speed() == 0, "หยุดสนิท");
        s.command(cmd("cmd", "gear", "v", "P"));
        feed(s, st, all, now, 3 * 60000);                               // จอด P 3 นาที
        List<TripState.Trip> tr = new ArrayList<TripState.Trip>();
        for (Object o : all) if (o instanceof TripState.Trip) tr.add((TripState.Trip) o);
        check(tr.size() == 1, "ได้ 1 ทริป (ได้ " + tr.size() + ")");
        if (tr.size() == 1) {
            TripState.Trip t = tr.get(0);
            double simKm = s.getFloat(SimCar.ODO, G0) - odo0b;
            check(near(t.km, simKm, 0.05) && t.km > 7 && t.km < 8.4, "ระยะ = ไมล์ของรถจำลอง (ต่างไม่เกิน 50 ม. = 5 วิแรกก่อนตัวอย่างแรก) ~8.3 km (ได้ " + t.km + ", sim " + simKm + ")");
            check(t.start == startExpect, "เริ่มตอนออกจาก P");
            check(t.end > t.start && t.end <= now[0] - 2 * 60000, "จบตอนเริ่มจอด (ก่อนครบ 2 นาที)");
            check(near(t.socStart, soc0b, 0.05) && t.socEnd < t.socStart, "แบต " + t.socStart + " → " + t.socEnd);
            check(near(t.maxKmh, 50, 0.1), "ความเร็วสูงสุด 50 (ได้ " + t.maxKmh + ")");
            check(near(t.socStart - t.socEnd, t.km * 13 / 100 / 39.4 * 100, 0.1), "แบตที่ลด = กม. × 13 kWh/100km ÷ 39.4");
            check(near(TripState.kwh(t, 39.4), t.km * 0.13, 0.05), "kWh ≈ กม. × 0.13 (ได้ " + TripState.kwh(t, 39.4) + ")");
        }

        System.out.println(fail == 0 ? "\nผ่านทั้งหมด" : "\nล้มเหลว " + fail + " ข้อ");
        System.exit(fail == 0 ? 0 : 1);
    }

    /** ป้อน TripState ทุก 5 วิ (เวลาเสมือน) จากค่าที่อ่านผ่าน API เดียวกับที่ TripLog ใช้ */
    static void feed(SimCar s, TripState st, List<Object> out, long[] now, long ms) {
        for (long e = 0; e < ms; e += 5000) {
            s.tick(5000); now[0] += 5000;
            Float spd = s.getFloat(SimCar.SPEED, G0), odo = s.getFloat(SimCar.ODO, G0), soc = s.getFloat(SimCar.SOC, G0);
            out.addAll(st.onSample(now[0], (double) Math.abs(spd), s.getInt(SimCar.GEAR_CUR, G0), (double) odo, (double) soc));
        }
    }

    static String read(String dir, String f) throws Exception {
        return new String(Files.readAllBytes(new File(dir, f).toPath()), "UTF-8");
    }

    /** หา "NAME = 0x..." / "NAME = 123" ในซอร์สแล้วเทียบกับค่าใน SimCar */
    static void checkConst(String src, String name, int want) {
        Matcher m = Pattern.compile("\\b" + name + "\\s*=\\s*(0x[0-9a-fA-F]+|\\d+)").matcher(src);
        if (!m.find()) { check(false, name + " ไม่พบในซอร์ส"); return; }
        int got = (int) Long.decode(m.group(1)).longValue();
        check(got == want, name + " = 0x" + Integer.toHexString(want));
    }
}
