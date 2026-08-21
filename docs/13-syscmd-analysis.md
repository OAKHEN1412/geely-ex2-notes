# 13 — แกะคำสั่ง system ทั้งหมด (Fable analysis) + จุดพลิกแพลง

_2026-08-21 · รวบรวมจากไฟล์จริงในรีโป (ไม่เดา) แล้วแกะว่าแต่ละคำสั่งสั่งงานจอ/รถยังไง · ⚑ = จุดพลิกแพลง/แฮ็ก/จุดเสี่ยง_

> ที่มา: MASTER.md, docs/02/06/07/08/11, quick/{codes,adb-recipes,install-car-apk,troubleshoot}.md,
> tools/{install_system_app,push_signed}.sh, devkit/{patch_autowindow.sh, src/.../FloatingBox.java, DevService.java}

---

## 0. ภาพรวม "ชั้น" ของการสั่งงาน
```
[รหัสหน้าปัด] → เปิด ADB   →  [adb over Wi-Fi]  →  shell (uid=0 root)
                                                    ├─ ระบบไฟล์/SELinux → ลง system app (สิทธิ์เต็ม)
                                                    ├─ VHAL (car_service) → สั่งรถ: ประตู/กระจก/แอร์
                                                    ├─ sysfs /sys/class/leds → คุม backlight ตรงฮาร์ดแวร์
                                                    └─ settings/props → คุม brightness/adb persist
```
หัวใจ: **ไม่ได้ root ด้วย exploit** — จอมาพร้อม `uid=0` + test-keys + `/system` ไม่มี dm-verity อยู่แล้ว งานทั้งหมดคือ "ใช้สิทธิ์ที่มีให้ถูกจุด"

---

## 1. ประตูเข้า: รหัสหน้าปัด (quick/codes.md)
| คำสั่ง | สั่งอะไร | ⚑ พลิกแพลง |
|--------|---------|-----------|
| `#*(เดือน+10)(วันที่)(ชม.12)` | เปิด **Engineering menu** (รันแพตช์/Wi-Fi) | ⚑ รหัส **หมุนตามนาฬิกาจอ** (ไม่ใช่รหัสตายตัว) — กันคนทั่วไป แต่คำนวณเองได้ด้วยสูตร เดือน+ออฟเซ็ต |
| `#*(เดือน+5)(วันที่)(ชม.12)` | เปิด **Dialer mode** → **ปุ่ม ADB** | ⚑ ต้องกด**ทุกครั้งหลังรีบูต** เพราะ adb-over-wifi ไม่ค้าง (ดูข้อ 2) |

ชม. = ระบบ 12 ชม. 2 หลัก (13:00→01, 24:00→12) · **ยึดเวลาบนจอ ไม่ใช่เวลาคอม**

---

## 2. ชั้น ADB (เข้าถึงเครื่อง)
| คำสั่ง | สั่งอะไร | ⚑ |
|--------|---------|---|
| `adb connect 172.20.10.3:5555` | ต่อ adb ผ่าน **Wi-Fi TCP** (ไม่ใช้สาย) | ⚑ refused=ADB ปิด (กดปุ่ม Dialer ใหม่) · timeout=จอหลุดเน็ต |
| `adb -s <IP>:5555 shell …` | ยิงคำสั่งเข้าจอ (สคริปต์ห่อเป็น `S(){ adb -s $D shell "$@"; }`) | — |
| `adb remount` | รีเมานต์ `/system` เป็น rw | ⚑ **ผ่านได้เพราะ `/system` เมานต์ตรงบน `mmcblk0p41` ไม่ผ่าน dm-verity** — บนเครื่อง verity คำสั่งนี้เฟล/อิฐ |
| `adb push <apk> /system/app/GeelyEx2Tools/…` | วางแอปเป็น system app | ต้องตามด้วย perms+SELinux (ข้อ 4) |
| `adb pull /system/framework/android.car.jar` | ดึง stub Car API มาคอมไพล์ overlay อุณหภูมิ | ⚑ ใช้ jar จริงของจอเป็น bootclasspath กัน API ไม่ตรง |
| `adb install -r <resigned>.apk` | อัปเดตทับ (ไม่ต้อง /system, ไม่ต้องรีบูต) | ⚑ **ได้เพราะ re-sign platform key แล้ว → signature ตรง `android.uid.system`** สิทธิ์มาจาก "ลายเซ็น" ไม่ใช่ตำแหน่ง /system |
| `setprop persist.adb.tcp.port 5555` | พยายามคง adb-over-wifi ข้ามรีบูต | ⚑ **ไม่ติดบนบิลด์ 1114** (best-effort, `\|\| true`) → ที่มาของ gotcha "กดปุ่ม ADB ใหม่ทุกครั้ง" |
| `getprop ro.product.model` | ยืนยันรุ่นจอ = IHU629G | — |
| `getprop \| grep acc\|ignition\|standby` | recon สถานะ ACC/กุญแจ (ออกแบบ remote-command ตอนดับรถ) | — |

---

## 3. ชั้น package / permission
| คำสั่ง | สั่งอะไร | ⚑ |
|--------|---------|---|
| `pm list packages \| grep geely.ex2` | หา package จอ | — |
| `pm grant com.oakhen.claudeweb android.permission.RECORD_AUDIO` | ให้สิทธิ์ไมค์แก่ ClaudeWeb | ⚑ runtime perm แอป**ขอเองไม่ได้เต็ม** ต้อง grant ผ่าน adb (คู่กับ voice claude.ai) |
| `dumpsys package com.geely.ex2.tools \| grep userId\|codePath\|flags` | **ยืนยันว่ากลายเป็น system app** | ⚑ เช็ค `userId=1000` + `codePath=/system/app/…` = ตัวชี้ขาดว่าลงสำเร็จจริง |
| `dumpsys media.drm \| grep widevine\|L1\|L3` | เช็กระดับ Widevine (Netflix) | ⚑ ผล = ไม่มี Widevine → **ดรอป Netflix** (ตัดสินใจจากคำสั่งจริง ไม่เดา) |
| `dumpsys webviewupdate` / `dumpsys audio \| grep mic` | recon WebView/ไมค์ ก่อนใช้ voice | — |

---

## 4. ⚑⚑ ชั้นระบบไฟล์ + SELinux (trick ตัวจริงของ "system app")
วางไฟล์เฉย ๆ ไม่พอ — ต้อง "ปลอมให้เนียนเป็นไฟล์ระบบ" ครบ 3 อย่าง (tools/install_system_app.sh, push_signed.sh):
```bash
chmod 755 <dir>; chmod 644 <apk>                 # สิทธิ์แบบ system app
chown 0:0 <dir> <apk>                             # เจ้าของ root:root
chcon u:object_r:system_file:s0 <dir> <apk>       # ⚑ SELinux label = system_file
```
- ⚑ **`chcon` คือหัวใจ** — ถ้า label ผิด SELinux เด้ง/ระบบไม่โหลดแอป ถึง perms จะถูก
- `mount | grep /system` → ต้องเห็น `mmcblkXpNN` (partition ตรง) **ไม่ใช่ `dm-X`** = ยืนยันไม่มี verity ก่อนแตะ
- เหตุผลที่ต้องทำ /system รอบแรก: APK เดิมเซ็น dev key → พอ re-sign platform key แล้วรอบต่อไป `install -r` ลง /data ได้เลย (ข้อ 2)

---

## 5. ⚑⚑⚑ ชั้นคุมจอ: backlight sysfs (FloatingBox.java — พลิกแพลงเยอะสุด)
**เป้าหมาย:** "ปิดจอให้มืดสนิททั้งแผง แต่เครื่องยัง Awake" → แตะปลุกได้ + Wi-Fi/adb/เสียงไม่ดับ (ต่างจากกดปุ่ม power ที่หลับลึก)

| กลไก | โค้ดจริง | ⚑ พลิกแพลง |
|------|---------|-----------|
| เขียน backlight = 0 ตรง sysfs | `FileWriter("/sys/class/leds/lcd-backlight/brightness") ← "0"` | ⚑ ดับ **ทั้งแผงรวมแถบสถานะ** (มืดกว่า brightness=0) โดยจอยัง on |
| เลี่ยง su ทุก tick | ครั้งแรก `su -c "chmod 666 <node>"` แล้วเขียนตรงด้วย FileWriter | ⚑ **spawn su แค่ครั้งเดียว** ไม่งั้น guard 700ms เรียก su รัว = หน่วง/เพี้ยน · fallback ท้ายสุด `su -c "echo v > node"` |
| guard tick | เขียน 0 ซ้ำทุก 700ms ระหว่างปิด | ⚑ **DisplayPowerController สู้กลับ** (ดันไฟคืน) ต้องย้ำ 0 เป็นช่วง ๆ |
| กัน controller สู้ | `Settings.System screen_brightness_mode = 0` (manual) | คืนค่าเดิมตอนปลุก |
| **serialize เขียนทั้งหมด** | ทุก write อยู่บน `newSingleThreadScheduledExecutor` | ⚑ **bug #1 critical**: ถ้า guard(เขียน0) กับ restore(เขียนคืน) แข่งกัน → จอค้างดำถาวรปลุกไม่ได้ (reboot only) · single-thread = restore เป็นคำสั่งสุดท้ายเสมอ |
| กู้ตอนบูต | `recoverBacklight()`: ถ้า process ตายตอนจอดำ → ซ่อม `screen_brightness` ที่ค้าง ≤0 + เขียน node คืน 120 | ⚑ safety net กัน "ดำข้ามรีบูต" |
| watchdog | คืนจอเองใน 10 นาทีถ้าไม่ถูกแตะ | กันลืมปิดค้าง |
| ปลุกจาก sleep ลึก | `KEYCODE_POWER = 26` ที่ปุ่มจริง | ⚑ sleep ลึก = Wi-Fi ดับ → ปลุกผ่าน adb ไม่ได้ ต้องปุ่มจริง |
| โหมดสำรอง (หรี่จอ) | overlay ดำ + `LayoutParams.screenBrightness=0` + `Settings.System screen_brightness=0` | ใช้เมื่อ **ไม่มี backlight node** |

---

## 6. ⚑⚑ ชั้นสั่งรถจริง: VHAL / car_service
| คำสั่ง | สั่งอะไร | ⚑ |
|--------|---------|---|
| `cmd car_service list-properties \| grep HVAC_TEMPERATURE` | หา propId ของแอร์ | — |
| `cmd car_service get-property-value 358614275` | อ่านค่าอุณหภูมิแอร์ | ⚑ `358614275` = **`0x15600503` = HVAC_TEMPERATURE_SET** — recon ผ่าน shell ก่อนเขียนโค้ด `CarPropertyManager` |
| (ในแอป) `CarProperty*Controller` อ่าน/สั่ง | `DOOR_POS`, `WINDOW_POS` (ประตู/กระจก), HVAC, `EV_BATTERY_LEVEL`, ลมยางแยกล้อ, โหมดขับ, regen, ambient | ⚑ **เขียน VHAL ได้เพราะ uid=system** — แอปทั่วไปสั่งไม่ได้ |

> ฟีเจอร์ "กระจกลดเร็ว 300ms" ไม่ได้สั่ง VHAL ตรง แต่ **ไปเร่ง service เดิมของจอ** (ข้อ 7)

---

## 7. ⚑⚑ ชั้นผ่าไบต์: smali surgery (patch_autowindow.sh)
แก้พฤติกรรม service เดิมของจอโดยไม่มี source — ผ่า dex:
```
baksmali classes9.dex → แก้ smali → smali reassemble → classes9-autowindow.dex
AutoWindowService:  const-wide/16 v2, 0x7d0  →  0x12c     (2000ms → 300ms, เร็ว ~10x)
```
- ⚑ **ค่าเป็น `static final` → ถูก inline ที่จุดใช้** (const-wide) ไม่ใช่ field ที่อ่านได้ → ต้องแก้ที่ **literal ตรง postDelayed** ไม่ใช่ที่นิยาม
- ⚑ **assert 0x7d0 มี "จุดเดียว"** ในไฟล์ก่อน sed — กันไป retime ค่าคงที่อื่นโดยไม่ตั้งใจ (ถ้าเจอ >1 หยุดทันที)
- ค่าอื่นใน `VhalConstants` (ปรับได้ถ้าอยาก): drop `0x14`=20%, close-max `0x1e`=30%, max speed `5.0f` km/h, UI poll `0xbb8`=3000ms
- ช่วงที่ปลอดภัย: `NEW_HEX ≤ 0x7fff` (ข้อจำกัด `const-wide/16`)

---

## 8. ⚑ ชั้นเก็บไฟล์: storage สำหรับ dashcam (docs/11)
| คำสั่ง | สั่งอะไร | ⚑ |
|--------|---------|---|
| `cat /proc/mounts \| grep vfat\|exfat\|fuse\|sdcardfs` (ใต้ `/storage/` ที่ไม่ใช่ emulated) | หา USB/ที่เก็บถอดได้ | ⚑ enumerate เอง + `StatFs` เช็กที่ว่าง |
| `su -c "mkdir -p <path>; chmod 777 <path>"` (ถ้า `!canWrite()`) | เปิดสิทธิ์เขียนโฟลเดอร์ | ⚑ **system app เขียน `/storage/<UUID>/` ตรง ๆ ไม่ต้อง SAF** (ข้ามกรอบ scoped storage ได้เพราะ uid=system) |
| `cmd media.camera list` / `/dev/video*` / `/sys/class/video4linux/*/name` | นับกล้อง | ⚑ อัดได้เฉพาะกล้องที่ผ่าน V4L2/Camera2 — **360 คาดไม่ได้** |
| exFAT >32GB ไม่ mount → ฟอร์แมต FAT32 | | ⚑ ไฟล์ ≤4GB → ตัด segment ≤3–5 นาที = loop record พอดี |

---

## 9. สรุป "จุดพลิกแพลง" ที่ต้องระวังสุด (top risks)
1. ⚑ **serialize backlight write** — พลาดจุดนี้ = จอค้างดำถาวร (แก้แล้ว bug #1) — อย่าแตะ threading model ของ FloatingBox
2. ⚑ **chcon system_file** — ลืม = SELinux เด้ง/แอปไม่โหลด แม้ perms ถูก
3. ⚑ **remount ได้เพราะไม่มี verity** — สมมติฐานนี้ผูกกับบิลด์ 1114/พาร์ทิชันตรง ถ้าอัปเดตจอโครงเปลี่ยน ต้องเช็ก `mount` ใหม่ก่อน
4. ⚑ **adb-over-wifi ไม่ค้าง** — ทุก flow เริ่มด้วยกดรหัส Dialer ใหม่ · sleep ลึก = ปลุกผ่าน adb ไม่ได้ ต้องปุ่มจริง (KEYCODE 26)
5. ⚑ **สิทธิ์มาจาก platform signature** — ทั้งระบบพึ่ง re-sign ด้วย AOSP test-key ที่จอเชื่อถือ ถ้าเซ็นผิด key = ไม่ได้ uid=system
6. ⚑ **smali inline constant + assert จุดเดียว** — ผ่า dex ต้องยึด literal ที่ใช้จริง ไม่ใช่ field
7. ⚑ **de-RCE แล้ว** — DevService เดิมเคยเปิด TCP 47811 + DexClassLoader (RCE ระดับ system ผ่าน Wi-Fi) — **ถอดออกแล้ว** (bug-hunt) ตัวปัจจุบันเป็น foreground service เปล่า ไม่มี socket

> ทุกอย่าง **reversible**: base 1.0-system ยังอยู่ + Factory Reset — ดู MASTER.md ข้อ 9
