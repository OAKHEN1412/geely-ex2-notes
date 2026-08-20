# 06 — ติดตั้ง Car.apk เป็น system app (re-sign ด้วย AOSP platform key)

> วิธีจริงที่ทำให้ `Geely-Ex2-Car.apk` ทำงานได้ — **คู่มือต้นทางไม่ได้บอก**
> "Quick Install" ของ ADB AppControl จะติด signature เหมือน `adb install` ธรรมดา

## ปัญหา

หลังปลดล็อก ADB แล้ว ลงแอปแบบปกติไม่ผ่าน:

```
adb install Geely-Ex2-Car.apk
Failure [INSTALL_FAILED_SHARED_USER_INCOMPATIBLE:
  Package com.geely.ex2.tools has no signatures that match
  those in shared user android.uid.system]
```

เพราะ manifest ประกาศ `sharedUserId="android.uid.system"` → แอปต้อง:
1. เซ็นด้วย **platform key ของจอ** (ไม่ใช่กุญแจ dev ที่มากับ APK)
2. ลงเป็น **system app ใน /system** (ลงที่ /data/app ไม่ได้)

ลง /system เฉย ๆ ก็ยังไม่พอ — logcat ตอนบูตขึ้น:
```
Failed to scan /system/app/GeelyEx2Tools:
  Signature mismatch for shared user: android.uid.system/1000
```

## กุญแจไข: จอใช้ AOSP test-key สาธารณะ

ดึง cert ของ `/system/framework/framework-res.apk` (เซ็นด้วย platform key) มาดู:

```
subject = C=US, ST=California, O=Android, CN=Android, emailAddress=android@android.com
SHA-256 = C8:A2:E9:BC:CF:59:7C:2F:B6:DC:66:BE:E2:93:FC:13:F2:FC:47:EC:77:BC:6B:2B:0D:52:C1:1F:51:19:2A:B8
```

**= AOSP platform test-key สาธารณะเป๊ะ** → private key ดาวน์โหลดได้จาก AOSP
(`build/target/product/security/platform.pk8` + `platform.x509.pem`)
→ เซ็น APK ใหม่ให้ตรง `android.uid.system` ได้

> เช็กบนเครื่องได้ด้วย `tools/v2cert.py` (แกะ cert จาก v2/v3 signing block โดยไม่ต้องใช้ Java)

## ขั้นตอน

### เตรียม (ครั้งเดียว)
- จอปลดล็อก ADB แล้ว + `adb connect <IP>:5555` ได้ (uid=0, root)
- ยืนยัน `/system` เขียนได้และ **ไม่ผ่าน dm-verity**:
  `adb shell "mount | grep /system"` → ต้องเห็น `/dev/block/mmcblkXpNN on /system` (ต่อ partition ตรง ไม่ใช่ /dev/block/dm-X)
- Java (portable JRE ก็ได้) + `uber-apk-signer.jar` + `openssl`

### 1. เซ็น APK ใหม่ — `tools/resign_apk.sh`
- โหลด `uber-apk-signer` + AOSP `platform.pk8`/`platform.x509.pem`
- **เช็ก fingerprint กุญแจ = ของจอ** (ไม่ตรง = abort)
- แปลงเป็น keystore → เซ็น (zipalign + v2/v3)
- verify cert ของ APK ที่เซ็นเสร็จ = `C8:A2…2A:B8`

### 2. push เป็น system app + reboot — `tools/push_signed.sh`
```
adb remount
adb push <signed>.apk /system/app/GeelyEx2Tools/Geely-Ex2-Car.apk
adb shell chmod 644 / chown 0:0 / chcon u:object_r:system_file:s0 ...
adb reboot
```

### 3. ยืนยันผล
```
adb shell dumpsys package com.geely.ex2.tools | grep -E 'userId|codePath|flags'
```
ต้องได้:
```
userId=1000
sharedUser=android.uid.system/1000
codePath=/system/app/GeelyEx2Tools
flags=[ SYSTEM DEBUGGABLE HAS_CODE ]
versionName=1.0-system
```
→ ติดตั้งเป็น system app สิทธิ์เต็ม (WRITE_SECURE_SETTINGS + VHAL) สำเร็จ

## ข้อควรระวัง (เจอจริงระหว่างทำ)

- **MSYS path mangling** (Git Bash): `/system/...` ถูกแปลงเป็น `C:/Program Files/Git/system/...`
  → set `MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*'` แต่ต้องใช้ **Windows path** สำหรับไฟล์ในเครื่อง (`C:/...`)
- **native libs เป็น STORED + resources.arsc STORED** → ต้อง zipalign ห้าม rebuild zip มั่ว (ใช้ apksigner/uber-apk-signer จัดให้)
- **Android 9 รับ v2/v3** ไม่ต้องมี v1
- **adb-over-wifi ไม่ค้างข้ามรีบูต** (`persist.adb.tcp.port` ไม่ติดบนบิลด์นี้) → หลังรีบูตต้องกดปุ่ม ADB ใน Dialer Mode ใหม่
- จอไม่ต่อ Wi-Fi อัตโนมัติหลังรีบูตเสมอไป → ต่อ hotspot ใหม่ + จด IP

## ความปลอดภัย

- จอ root + `ro.secure=0` + test-keys สาธารณะ = ความปลอดภัยต่ำอยู่แล้ว (ปกติของ head unit จีน)
  ใครมีกุญแจ AOSP (สาธารณะ) ก็เซ็นแอปเป็น system uid บนจอนี้ได้
- **adb-over-wifi**: ใครในวง Wi-Fi เดียวกันต่อ adb เข้าจอได้ ถ้าไม่ใช้ควรปิด (อย่าเปิดปุ่ม ADB ค้างบน Wi-Fi สาธารณะ)
- ทุกอย่างย้อนกลับได้ด้วย Factory Reset (ลบ /system/app ที่เพิ่ม + ปิด ADB)

## สถานะ

✅ ติดตั้งสำเร็จบนจอบิลด์ 1114 — 2026-08-20 (userId=1000, /system/app, ไม่มี signature mismatch)
