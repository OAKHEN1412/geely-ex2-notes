# 16 — รถจำลอง (SimCar) สำหรับเทสบนเครื่องจำลอง · 30 ต.ค. 2026

เครื่องจำลอง `EX2_A9` (docs/14 หัวข้อ 9) ไม่มี android.car / VHAL / ECarX adaptapi → QuickPanel, บันทึกทริป, ปุ่มพวงมาลัย, ดักเกียร์ R ทดสอบไม่ได้
SimCar เป็นรถปลอมใน process ของ OAKHEN: Vhal / EcarxFn ตอบจาก SimCar แทน แล้วสั่งจากคอมผ่าน adb

## อะไรถูกจำลอง
| ส่วน | รายละเอียด |
|---|---|
| ที่เก็บค่า | `(prop, area)` → Integer / Float / Boolean · `set` ได้เฉพาะคู่ที่มี (area ผิด/ชนิดผิด = สั่งไม่ผ่าน เหมือนรถ) · `put` สร้างใหม่ได้ (`cmd=set`) |
| ค่าเริ่มต้น | เกียร์ P (`0x11400400`/`0x11400401` = 4) · ความเร็ว 0 (`0x11600207` float, `0x11600208` int = km/h × 18) · แบต 80% (`0x2140a6ed`) · ไมล์รวม 12345.6 km (`0x11600204`) · ระยะวิ่งได้ (`0x11400308`) · โหมดขับ ECO · รีเจนกลาง · A/C ปิด (area 1) · 24 °C (area 1, 4) · พัดลม 2 (area 1) · ปุ่มพวงมาลัย 5 รหัส `0x41020100`..`0x41020500` = 2 (ปล่อย) |
| ECarX adaptapi | `0x2f010200` โหมดล้างรถ: status `active` (รถจริงตอบ `notavailable` — รถจำลองตั้งให้รองรับเพื่อเห็นแถวนี้ใน QuickPanel; สลับด้วย `wash notavailable`) · ค่า OFF · รองรับ OFF/MOVING · func อื่น = `notavailable` |
| ขับ | tick 5 Hz (ก้าวย่อย 100 ms): ความเร็วไล่ตามเป้า (`speed`) แบบนุ่ม — เร่งไม่เกิน 8 km/h/s, เบรกไม่เกิน 12 km/h/s, ใกล้เป้าชะลอแบบเอ็กซ์โปเนนเชียล (0→60 ≈ 12 วิ) · ขยับเฉพาะเกียร์ D (R จำกัด 15 km/h) · ไมล์ = ∫ความเร็ว · แบตลด = กม. × 13 kWh/100 km ÷ 39.4 kWh (≈ 0.33 %/km) · แบต 0 = ไม่ขยับ |
| เกียร์ | รับ N=1 R=2 P=4 D=8 · วิ่งเกิน 3 km/h แล้วเข้า P หรือสลับ R↔D = ไม่ยอม (`ok:false`) |
| ชาร์จ | `plug 1` + เกียร์ P + จอดนิ่ง → แบตขึ้น `chargeKw` (ค่าเริ่ม 7 kW AC; `chargekw 50` = DC) จนเต็ม 100% |
| ปุ่มพวงมาลัย | `key` = ลง (1) → ปล่อย (2) ที่ 100 ms · `long=1` ค้าง 1 วิ (SwcWatch ถือ ≥ 0.7 วิ = กดค้าง) · listener ได้ค่าปัจจุบันก่อนหนึ่งครั้งตอนลงทะเบียน (SwcWatch ข้ามค่าแรกของแต่ละรหัส — ถ้าไม่ส่ง การกดครั้งแรกจะถูกกลืน) |

## สมมติฐาน (ไม่ใช่ค่าวัดจากรถ)
- **แบต 39.4 kWh** = `TripLog.DEF_CAP` (ค่าที่แอพใช้คิด kWh ของทริป) — ใช้เป็นความจุใช้งานได้
- **สิ้นเปลือง 13 kWh/100 km** คงที่ทุกความเร็ว (ของจริงขึ้นกับความเร็ว/แอร์/ลม)
- ชาร์จ AC 7 kW คงที่ (ไม่มีโค้งชาร์จ) · ไม่มีรีเจน (เบรกไม่คืนพลังงาน) · แอร์/โหลดอื่นไม่กินแบต · ไม่มีเอฟเฟกต์ความชัน
- หน่วย `0x11600208` ตามคอมเมนต์ใน `TripLog` (int ÷ 18 = km/h) — ยังไม่ยืนยันบนรถ
- `Vhal.sim` ใช้ `Class.forName("android.car.Car")` ไม่เจอเท่านั้น → รถจริง (มี class) **ไม่เคยเปิดรถจำลอง** ต่อให้ต่อ Car service ไม่ติด

## ไฟล์
| ไฟล์ | หน้าที่ |
|---|---|
| `devkit/src/com/oakhen/ex2dev/SimCar.java` | แกน Java ล้วน (ที่เก็บ + โมเดลขับ + listener + `command()`/`dump()`) ทดสอบบนคอมได้ |
| `devkit/src/com/oakhen/ex2dev/SimCtl.java` | BroadcastReceiver `com.oakhen.ex2dev.SIM` (ลงทะเบียนตอนรัน ไม่แตะ manifest) |
| `Vhal.java` | `noCar()` · `sim(ctx)` · `listen(ctx, prop, listener)` · getter/setter/`supported` ตก SimCar เมื่อ `sim()` · `cpm()` คืน SimCar เป็นตัวแทน (ไม่ใช่ null) เพื่อให้ QuickPanel/TripLog ถือว่า "ต่อรถได้" โดยไม่แก้ |
| `EcarxFn.java` | `status/get/set/supportedValues` ตก SimCar เมื่อ `sim()` |
| `ReverseWatch.java` | `readGear` อ่านเกียร์จาก SimCar เมื่อ `sim()` (ของจริงเป็น polling จาก FloatingBox ไม่ใช่ callback) |
| `Prefs.java` | `KEY_SIM_OFF = "sim_off"` |
| `tools/carsim/carsim.py` | หน้าควบคุมเว็บ (127.0.0.1) + CLI · stdlib ล้วน |
| `devkit/test/run_sim_test.sh` + `devkit/test/com/oakhen/ex2dev/SimCarTest.java` | ทดสอบ 87 ข้อ · `build_harness.sh` หา repo จากที่อยู่สคริปต์แล้ว (เดิม hardcode path เครื่อง Pishe) |

## วิธีรัน
1. เปิดเครื่องจำลองด้วย `Desktop\EX2 Emulator.bat` · build + ลงแอพ (ขั้นตอนเดิม `build_harness.sh` → `build_oakhen.sh` → `adb install -r`) · **ต้องรวม Pending wiring ด้านล่างก่อน** ไม่งั้นไม่มีตัวรับคำสั่ง
2. หน้าเว็บ: `python -I tools/carsim/carsim.py` แล้วเปิด http://127.0.0.1:8765 (`serve --port N` เปลี่ยนพอร์ต) · มี slider ความเร็ว, P/R/N/D, แบต, เสียบสายชาร์จ, ปุ่มพวงมาลัย 5 ปุ่ม (กดสั้น/ค้าง), ค่าโหมดขับ/รีเจน/แอร์/ล้างรถจาก `dump`, ปุ่ม "ขับทริป" (D → 60 → ล่อง 2 นาที → หยุด → P; ต้องจอด P ต่ออีก 2 นาทีให้ TripLog ปิดทริป)
3. CLI: `python -I tools/carsim/carsim.py speed 60` · `gear D` · `key voice --long` (หรือ `key 0x41020500`) · `plug 1` · `soc 20` · `chargekw 50` · `set 0x22010100 0 0x22010102` · `wash notavailable` · `reset` · `dump` · `trip --cruise 30` · `off 1` (ปิดรถจำลองใน Prefs, `off 0` เปิดกลับ)
4. ไม่มี adb ใน PATH → หา `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe` เอง (`--adb` / `-s serial` override)
5. ดิบ ๆ: `adb shell am broadcast -a com.oakhen.ex2dev.SIM --es cmd gear --es v D` → `result data` เป็น JSON · log tag `EX2Sim`
6. ทดสอบบนคอมไม่ต้องมีเครื่องจำลอง: `bash devkit/test/run_sim_test.sh` (และ `run_trip_test.sh` เดิม)

ความปลอดภัย: ผู้ส่ง broadcast ต้องถือ `android.permission.DUMP` (signature|privileged|development → shell/root/system มี, แอพทั่วไปไม่มี) ตั้งตอน `registerReceiver` · ตัวรับไม่ถูกลงทะเบียนเลยบนเครื่องที่มี android.car · หน้าเว็บ bind 127.0.0.1 เท่านั้น, ตรวจ Host/Origin, รับ JSON เท่านั้น, ค่า argument จำกัด `[0-9A-Za-z_.+-]` (adb ต่อ argv เป็น shell string บนเครื่อง)

## Pending wiring (รอรวม — ไฟล์ที่ agent อื่นแก้อยู่: SwcWatch, DevService)
ยังไม่ได้ apply เพราะ `SwcWatch.java` / `DevService.java` ถูกแก้ขนานอยู่ · diff นี้สร้างจากไฟล์ ณ 30 ต.ค. แล้วทดสอบ `patch -p1 -l` + คอมไพล์ทั้ง `ex2dev/*.java` ผ่านบนสำเนา (ถ้า context ขยับ ให้ใส่ด้วยมือตามเจตนา)
- `DevService`: เรียก `SimCtl.start(app)` ก่อน `SwcWatch.start` (บนรถจริง `SimCtl.start` คืนทันที ไม่ลงทะเบียนอะไร) · ไฟล์นี้เป็น CRLF → ใช้ `patch -p1 -l` หรือใส่ด้วยมือ
- `SwcWatch`: `register()` ถ้า `Vhal.sim(app)` ให้ฟังปุ่มผ่าน `Vhal.listen` (ทุกรหัสใน `KNOWN`) · แยกตัวประมวลผลค่าปุ่มออกเป็น `onKey(app, prop, val)` ใช้ร่วมกับ callback ของ Car
- ไม่ต่อสายก็ใช้ได้บางส่วน: QuickPanel / TripLog / ReverseWatch ทำงานกับรถจำลองอยู่แล้ว (ผ่าน `Vhal`) แต่ไม่มีตัวรับ broadcast (สั่งจาก carsim ไม่ได้) และ SwcWatch จะรายงาน "ลงทะเบียนปุ่มไม่ได้"

```diff
--- a/devkit/src/com/oakhen/ex2dev/SwcWatch.java
+++ b/devkit/src/com/oakhen/ex2dev/SwcWatch.java
@@ -84,7 +84,20 @@
         });
     }
 
+    /** เครื่องจำลอง: ฟังปุ่มจากรถจำลอง SimCar (Vhal.listen ส่งค่าปัจจุบันก่อน 1 ครั้ง → ถูกข้ามเหมือนบนรถ) */
+    private static boolean registerSim(final Context app) {
+        SimCar.Listener l = new SimCar.Listener() {
+            public void onChange(int prop, int area, Object v) { if (v instanceof Integer) onKey(app, prop, (Integer) v); }
+        };
+        for (int k : KNOWN) Vhal.listen(app, k, l);
+        sStarted = true;
+        sStatus = "ฟังปุ่มอยู่ " + KNOWN.length + " รหัส (รถจำลอง)";
+        Log.i(TAG, sStatus);
+        return true;
+    }
+
     private static boolean register(final Context app) {
+        if (Vhal.sim(app)) return registerSim(app);
         Object cpm = Vhal.cpm(app);
         if (cpm == null) { sStatus = "เชื่อมต่อระบบรถไม่ได้"; return false; }
         try {
@@ -133,6 +146,11 @@
             if (!(o instanceof Integer)) return;
             val = (Integer) o;
         } catch (Throwable t) { return; }
+        onKey(app, prop, val);
+    }
+
+    /** เธรด main — ค่าปุ่มหนึ่งค่า (จาก Car หรือรถจำลอง) */
+    private static void onKey(final Context app, int prop, int val) {
         boolean first;
         synchronized (SwcWatch.class) {
             first = !sFirstDone.contains(prop);
--- a/devkit/src/com/oakhen/ex2dev/DevService.java
+++ b/devkit/src/com/oakhen/ex2dev/DevService.java
@@ -35,6 +35,8 @@
             try { GmsGuard.start(app); } catch (Throwable t) { Log.e(TAG, "GmsGuard", t); }
             // เข็มทิศ Maps ใช้ทิศ GPS (ค่า override ของ sensorservice หายเมื่อรีบูต → สั่งใหม่ทุกครั้ง)
             try { SensorGuard.apply(app); } catch (Throwable t) { Log.e(TAG, "SensorGuard", t); }
+            // รถจำลอง (เฉพาะเครื่องที่ไม่มี android.car): ตัวรับคำสั่ง com.oakhen.ex2dev.SIM จากคอม (tools/carsim) — ต้องมาก่อน SwcWatch
+            try { SimCtl.start(app); } catch (Throwable t) { Log.e(TAG, "SimCtl", t); }
             // ปุ่มพวงมาลัย (ดักรหัส + ทำตามที่ตั้งไว้ในหน้า OAKHEN)
             try { SwcWatch.start(app); } catch (Throwable t) { Log.e(TAG, "SwcWatch", t); }
             // บันทึกการเดินทาง (อ่านค่ารถทุก 5 วิตอนจอเปิด)
```

## ยังไม่ได้ยืนยัน
- ยังไม่ได้รันบนเครื่องจำลองจริง (ตอนเขียนไม่มี adb/SDK/AVD บนเครื่องนี้): การลงทะเบียน receiver ด้วยสิทธิ์ `DUMP`, รูปแบบผลลัพธ์ `am broadcast` บน Android 9, SwcWatch ผ่าน `Vhal.listen`
- QuickPanel จะขึ้นข้อความ "ค่าที่แสดง = ค่าจริงจากรถ" ทั้งที่เป็นรถจำลอง (QuickPanel ห้ามแก้ตอนนี้)
