# MASTER — Geely EX2 ปลดล็อก + ติดตั้งแอป (ไฟล์ต้นฉบับรวมทุกอย่าง)

> ไฟล์เดียวรวมความรู้ทั้งหมดจากงานจริง (จอบิลด์ 1114, สำเร็จ 2026-08-20)
> ไฟล์ย่อยหยิบใช้เร็วอยู่ใน [`quick/`](quick/) — เอกสารละเอียดแยกหัวข้ออยู่ใน [`docs/`](docs/)

สารบัญ: [1.ข้อเท็จจริง](#1-ข้อเท็จจริงจอรถ) · [2.ปลดล็อก](#2-ปลดล็อก-adb) · [3.รหัส](#3-รหัสเมนูซ่อน) · [4.สถาปัตยกรรมแอป](#4-สถาปัตยกรรมแอป) · [5.ติดตั้ง Car.apk](#5-ติดตั้ง-carapk-วิธีจริง) · [6.ยืนยันผล](#6-ยืนยันผล) · [7.ภาษา/แปล](#7-ภาษาแปล) · [8.gotcha](#8-gotcha-ที่เจอจริง) · [9.ความปลอดภัย/ย้อนกลับ](#9-ความปลอดภัย--ย้อนกลับ) · [10.ค่าคงที่](#10-ค่าคงที่อ้างอิง)

---

## 1. ข้อเท็จจริงจอรถ

| รายการ | ค่า |
|--------|-----|
| หน่วยจอ | **IHU629G** (Beidou ICV Technology) |
| Model | **EEBADF1** |
| ซอฟต์แวร์ | **Flyme Auto E 1.8.0** — build **1114** (ตัวนี้), รองรับ 1111 ด้วย |
| Android | 9 (API 28) |
| Root | uid=0, `ro.secure=0`, `ro.build.tags=test-keys`, su ที่ /system/xbin/su |
| /system | mount ตรงบน `/dev/block/mmcblk0p41` — **ไม่ผ่าน dm-verity** (เขียน+รีบูตปลอดภัย) |
| Platform key | **AOSP test-key สาธารณะ** (ดูข้อ 10) |

ดูที่จอ: **ตั้งค่ารถ → รถของฉัน**

---

## 2. ปลดล็อก ADB

จอเป็น Android root จากโรงงาน แต่ปิด ADB ไว้ "ปลดล็อก" = รันแพตช์ผ่าน USB เพื่อเปิด ADB over Wi-Fi (ไม่เพิ่ม/ถอนสิทธิ์ระบบ ย้อนกลับได้ด้วย Factory Reset)

1. USB **FAT32** + แพตช์ตรงบิลด์ วางที่ราก โครงสร้าง: `USB/3C6025_..._user_995/OS/update.zip` (**ห้ามแตก update.zip**) — ใช้ `tools/prep-usb.ps1`
2. เสียบ USB → ปิด Bluetooth → เปิดแอปโทรศัพท์ → กด **Engineering Mode**
3. กดไอคอนติดตั้งอัปเดต (สีเขียว) → จอรีบูตเข้า Recovery รันเอง → **error ตอนจบ = ปกติ** → กดค้าง **Previous Track** บนพวงมาลัยจนจอรีสตาร์ต → **ADB เปิด**
4. ต่อ Wi-Fi ให้จอ + จด IP → **Dialer Mode** → กดปุ่ม **ADB**
5. `adb connect <IP>:5555` → ลงแอป (ดูข้อ 5)

รายละเอียด: [docs/01](docs/01-unlock-screen.md)

---

## 3. รหัสเมนูซ่อน

ผูกกับวันเวลาจอ เปลี่ยนทุกชั่วโมง — **ชั่วโมงใช้ระบบ 12 ชม. 2 หลัก** (19:00→07, 24:00→12), วันที่ 2 หลัก

| โหมด | สูตร | ใช้ |
|------|------|-----|
| Engineering | `#*(เดือน+10)(วันที่)(ชม.)` | เมนูวิศวกร รันแพตช์ + Wi-Fi |
| Dialer | `#*(เดือน+5)(วันที่)(ชม.)` | ปุ่ม ADB |

เช่น 20/08 บ่าย4(16:00): Eng `#*182004`, Dialer `#*132004`

---

## 4. สถาปัตยกรรมแอป

**คู่ client↔server คุยตรงไม่ผ่านคลาวด์:**

| ไฟล์ | package | อุปกรณ์ | บทบาท |
|------|---------|---------|-------|
| `Geely-Ex2-Car.apk` | `com.geely.ex2.tools` | จอรถ | server |
| `Geely-Ex2-Phone.apk` | `com.example.ex2_phone` | Android | client |
| `GeelyEX2.ipa` | `com.geely.ex2.phone` | iPhone (iOS 15+, debug build) | client |

Transport: **Wi-Fi TCP 47800** (ทั้งคู่) / Bluetooth: Android=SPP (`GeelyEX2-CarSync`, UUID …c0ffee000001), iOS=BLE-UART (…000002/3/4) — **รถไม่มี BLE server → iPhone ต้องใช้ Wi-Fi**

ฝั่งรถอ่าน/สั่งผ่าน VHAL: ประตู/กระจก (`DOOR_POS`/`WINDOW_POS`), แอร์ (HVAC), แบต, ลมยาง, โหมดขับ, regen, ambient, เกียร์, ความเร็ว, อุณหภูมินอก
ฝั่งรถทำเองได้ (ไม่ต้องมีมือถือ): ปิดกระจกอัตโนมัติ, คุม/ปิดเสียง AVAS, ตั้งเวลาไฟ ambient, ไอคอนแถบสถานะ
มี safety interlock: บล็อกสั่งประตู/กระจกตอนรถวิ่ง

รายละเอียด: [docs/02](docs/02-apps-architecture.md), [docs/03](docs/03-app-features.md)

---

## 5. ติดตั้ง Car.apk (วิธีจริง)

**`adb install` / Quick Install ไม่ผ่าน** → `INSTALL_FAILED_SHARED_USER_INCOMPATIBLE`
เพราะ manifest = `sharedUserId="android.uid.system"` → ต้อง **เซ็นด้วย platform key ของจอ** + ลงเป็น **system app**

จอใช้ AOSP test-key สาธารณะ → เซ็นใหม่ด้วยกุญแจ AOSP ได้:

1. **เซ็นใหม่** — `tools/resign_apk.sh` (โหลด uber-apk-signer + AOSP platform.pk8/x509.pem → เช็ก fp ตรงจอ → เซ็น zipalign+v2/v3 → verify)
2. **push เป็น system app** — `tools/push_signed.sh`:
   ```
   adb remount
   adb push <signed>.apk /system/app/GeelyEx2Tools/Geely-Ex2-Car.apk
   adb shell chmod 644 / chown 0:0 / chcon u:object_r:system_file:s0
   adb reboot
   ```
3. รีบูต → PackageManager สแกน /system/app เจอ → ติดตั้ง

รายละเอียด + สคริปต์: [docs/06](docs/06-install-app-system-signed.md)

---

## 6. ยืนยันผล

```
adb shell dumpsys package com.geely.ex2.tools | grep -E 'userId|codePath|flags'
```
สำเร็จ = `userId=1000` + `sharedUser=android.uid.system/1000` + `codePath=/system/app/GeelyEx2Tools` + `flags=[SYSTEM ...]` + logcat ไม่มี signature mismatch

---

## 7. ภาษา/แปล

**แก้ไข 2026-08-20:** **Car.apk แปลผ่าน string resource เต็มรูปแบบ** (`values/`=VN, **`values-en/`=EN ครบแล้ว**, `values-vi/`=VN) → ทำไทยง่าย **แก้ resource ล้วน ไม่ต้องแตะ smali**: เติม `values-th/strings.xml` + เพิ่ม `th` ใน locales_config แล้ว build (`tools/build_mod.sh`) → เซ็น platform key → push. ✅ พิสูจน์ลูปแล้ว
แอป**มือถือ** (`Geely-Ex2-Phone.apk`/`.ipa`) ต่างหากที่ **เวียดนามล้วน hardcode ในโค้ด** สลับ/แปลยาก
ตารางแปล VN→ไทย→อังกฤษ 125 รายการ: [`docs/translation-table.md`](docs/translation-table.md), แผน+วิธี: [docs/05](docs/05-translation-plan.md)

---

## 8. gotcha ที่เจอจริง

- **MSYS path mangling** (Git Bash): `/system/...` → `C:/Program Files/Git/system/...` → set `MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*'` แต่ใช้ Windows path (`C:/...`) สำหรับไฟล์ในเครื่อง
- **native libs + resources.arsc = STORED** → ต้อง zipalign ห้าม rebuild zip เอง (ใช้ apksigner/uber-apk-signer)
- **adb-over-wifi ไม่ค้างข้ามรีบูต** (`persist.adb.tcp.port` ไม่ติด) → หลังรีบูตกดปุ่ม ADB ใน Dialer ใหม่ + จอไม่ต่อ Wi-Fi อัตโนมัติเสมอไป
- **auto-mode classifier บล็อก** คำสั่งเขียน /system + โหลด key → รันเองผ่าน `! bash tools/xxx.sh`
- USB Kingston Windows retail = **read-only ระดับเฟิร์มแวร์** ฟอร์แมตไม่ได้ ใช้ USB อื่น
- **IPA เป็น debug build** (stub + .debug.dylib) sideload บางเครื่องเปิดไม่ติด; iOS ขั้นต่ำ 15.0 (ไม่ใช่ 12 ตามเอกสาร)

---

## 9. ความปลอดภัย / ย้อนกลับ

- จอ root + ro.secure=0 + test-keys สาธารณะ = ความปลอดภัยต่ำ (ปกติของ head unit จีน) ใครมีกุญแจ AOSP ก็เซ็นแอปเป็น system uid ได้
- **adb-over-wifi**: ใครในวงเดียวกันต่อได้ ไม่ใช้ควรปิด (อย่าเปิดปุ่ม ADB ค้างบน Wi-Fi สาธารณะ)
- ย้อนกลับทั้งหมด: **Factory Reset** (ตั้งค่ารถ → รถของฉัน) → ลบแอปที่เพิ่ม + ปิด ADB

---

## 10. ค่าคงที่อ้างอิง

```
Platform key (จอ, AOSP test-key) SHA-256:
  C8:A2:E9:BC:CF:59:7C:2F:B6:DC:66:BE:E2:93:FC:13:F2:FC:47:EC:77:BC:6B:2B:0D:52:C1:1F:51:19:2A:B8
  subject: C=US,ST=California,O=Android,CN=Android,emailAddress=android@android.com
APK เดิม เซ็นด้วยกุญแจ dev SHA-256:
  5f34f493274c0d51b9615bc20b9d1f8e10967bf50981cbbffe885ed7e7ed3f9e

แพตช์:
  1114 → โฟลเดอร์ 3C6025_SW0E22H0306H111400000_user_995
  1111 → โฟลเดอร์ 3C6025_SW0E22H0128H111100000_user_995
  OS/update.zip 831807 bytes (1114 sha256 F55AE666...4CC4)
  Google Drive: 1111=1rvTfF4WoTgXcZ98_y73fKRayZVBgxaOZ  1114=1RF5ut5r_zVGCxfe2O3LKc3SrecRVp3td

เครื่องมือ (ในเครื่อง):
  adb  = C:\Users\Pishe\Downloads\ADB_AppControl\adb\adb.exe
  JRE  = portable Temurin jdk-17.0.20+8-jre
  signer = uber-apk-signer 1.3.0
  AOSP key = raw.githubusercontent.com/aosp-mirror/platform_build/main/target/product/security/platform.{pk8,x509.pem}

จอ/เน็ต (รอบที่ทำ):
  car IP = 172.20.10.3:5555  (iPhone hotspot; computer 172.20.10.2)
  ติดตั้งที่ = /system/app/GeelyEx2Tools/Geely-Ex2-Car.apk
  ผล = com.geely.ex2.tools, versionName 1.0-system, userId=1000
```

แหล่งต้นทาง: repo `algodaohub/geely-ex2-hub`, กระทู้ xethongminh.net
