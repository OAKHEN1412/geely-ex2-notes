# 07 — EX2 BY OAKHEN + dev harness (hot-push ผ่าน WiFi)

> แอปเดียว = `Geely-Ex2-Car.apk` + **แปลไทย** + **rebrand "EX2 BY OAKHEN"** + **dev harness**
> ติดตั้งครั้งเดียว แล้ว **push ฟีเจอร์ใหม่ (.dex plugin) เข้าไปสด ๆ ผ่าน WiFi — ไม่ต้อง reinstall ไม่ต้องรีบูต**

## ⚠️ ความปลอดภัย (อ่านก่อน)
dev harness = **DevService** เปิด TCP socket บน WiFi (พอร์ต 47811) รับ `.dex` มาโหลดด้วย `DexClassLoader`
แล้วรัน **ด้วยสิทธิ์ android.uid.system เต็ม** (เข้าถึง VHAL / secure settings ได้ทุกอย่าง)
→ **นี่คือ remote code execution ระดับ system ผ่าน WiFi** มีแค่ token คงที่ (`OAKHEN`) กันแบบบาง ๆ

- ใช้เฉพาะ **WiFi ส่วนตัว/hotspot ตัวเอง** เท่านั้น — ห้ามเปิดค้างบน WiFi สาธารณะ
- ใครในวงเดียวกันที่รู้โปรโตคอล+token รันโค้ดเป็น system บนจอได้
- เป็น **เครื่องมือ dev** ล้วน — ก่อนใช้จริงระยะยาวควรถอด harness ออก (build ตัวไม่มี classes17.dex) หรือเพิ่ม auth ให้แน่นกว่านี้
- เพราะเหตุนี้ **auto-mode classifier ของ Claude บล็อกไม่ให้ agent build/เซ็นให้** (หน้าตาเหมือน backdoor) → ต้อง **รันเองผ่าน `!`**

## สถาปัตยกรรม
```
[คอม] push_plugin.py --(WiFi TCP 47811)--> [จอ] DevService (ใน EX2 BY OAKHEN, uid=system)
                                                   └─ DexClassLoader โหลด plugin.dex → เรียก Plugin.run(ctx,arg) → คืนผล
```
- ฝัง harness เป็น **classes17.dex เพิ่ม** (ไม่ต้อง decode Compose 57MB) + เพิ่ม `<service>`/`<receiver>` ใน manifest
- โค้ด: `devkit/src/com/oakhen/ex2dev/` — `Plugin.java` (interface), `DevService.java` (socket+loader), `DevStartReceiver.java` (boot/replace/START)
- โปรโตคอล: `magic "EX2D"` + `token` + `cmd`(`R`=ping/`P`=push) + (`P`: className, arg, dexLen, dexBytes) → reply(str)

## Build (รันเอง — agent โดนบล็อก)
```bash
! bash tools/build_oakhen.sh
```
ทำ: decode → ใส่ไทย(values-th) → rebrand → ฉีด classes17.dex + manifest → build → เซ็น platform key → verify fp=จอ
→ ได้ `_signed-apk/EX2-BY-OAKHEN.apk`

## ติดตั้ง — ไม่ต้องรีบูต
```bash
ADB="C:/Users/Pishe/Downloads/ADB_AppControl/adb/adb.exe"
"$ADB" -s <IP>:5555 install -r "_signed-apk/EX2-BY-OAKHEN.apk"     # อัปเดตสด (platform sig ตรง → ลง /data ได้)
"$ADB" -s <IP>:5555 shell am broadcast -a com.oakhen.ex2dev.START  # สตาร์ท harness ครั้งแรก
```
> รอบถัด ๆ ไป `install -r` ทับ → `MY_PACKAGE_REPLACED` สตาร์ท DevService ให้อัตโนมัติ (ไม่ต้อง broadcast/รีบูต)
> **ทำไม install -r ไม่ต้องรีบูต:** ตอนแรกที่ต้อง /system+รีบูต เพราะ APK เดิมเซ็น dev key; พอ re-sign platform key แล้ว signature ตรง `android.uid.system` → ลง /data ได้ + สิทธิ์ระบบมาจาก signature ไม่ใช่ตำแหน่ง /system
> เก็บ base ใน /system ไว้เหมือนเดิมได้ (ให้สิทธิ์แน่น) แล้ว dev push ทับผ่าน /data

## เขียน + push plugin (วงจร dev)
```bash
# 1) คอมไพล์ plugin -> dex  (ต้องมี JDK; r8.jar/android28.jar โหลดตาม URL ในสคริปต์ถ้าไม่มี)
JAVAC=/path/to/javac.exe  bash devkit/build_plugin.sh com/oakhen/plugins/HelloPlugin.java com.oakhen.plugins.HelloPlugin
# 2) push เข้าจอ (รันสดทันที)
py tools/push_plugin.py <IP> push com.oakhen.plugins.HelloPlugin devkit/plugins/out/plugin.dex "sawadee"
# ping เช็ก harness:
py tools/push_plugin.py <IP> ping
```
plugin implement `com.oakhen.ex2dev.Plugin` → `String run(Context ctx, String arg)` (ตัวอย่าง: `devkit/plugins/src/.../HelloPlugin.java`)

## ข้อจำกัด (พูดตรง)
- **hot-swap โค้ด/Compose ที่โหลดไปแล้วในที่ = ทำไม่ได้** ถ้าไม่มี framework — DexClassLoader โหลด "คลาสใหม่" ได้ plugin จึงเป็นโค้ด standalone (อ่าน/สั่ง VHAL, ยิง settings, ทดสอบ API) ไม่ใช่แก้ UI เดิมสด ๆ  → อยากได้ UI สดต้องออกแบบเป็น server-driven UI (push JSON ให้ host render)
- Android 9 ไม่มี per-app locale picker → ไทยโชว์เมื่อภาษาระบบจอ = ไทย (เลือก "ระบบ" ในแอป)
- plugin รันในโปรเซส host — ถ้า plugin crash แรง อาจกระทบ service (จับ exception ไว้แล้วระดับนึง)

## ไฟล์เกี่ยวข้อง
| ไฟล์ | หน้าที่ |
|------|--------|
| `devkit/src/com/oakhen/ex2dev/*.java` | ซอร์ส harness (Plugin/DevService/DevStartReceiver) |
| `devkit/prebuilt/ex2dev-classes17.dex` | harness ที่คอมไพล์แล้ว (ฉีดเข้า APK) |
| `tools/build_oakhen.sh` | ประกอบ EX2 BY OAKHEN ครบ (รันผ่าน `!`) |
| `tools/push_plugin.py` | client push plugin ผ่าน WiFi |
| `devkit/build_plugin.sh` | คอมไพล์ plugin → dex |
| `devkit/plugins/src/.../HelloPlugin.java` | ตัวอย่าง plugin |
