# ลง Car.apk เป็น system app (สรุปสั่งเดียว)

> `adb install` ไม่ผ่าน! Car.apk = `sharedUserId=android.uid.system` → **ต้อง re-sign ด้วย platform key + ลง /system**

## เงื่อนไขก่อนเริ่ม
- ปลดล็อก ADB แล้ว, `adb connect <IP>:5555` ได้ (uid=0)
- `/system` ไม่ผ่าน verity: `adb shell "mount | grep /system"` เห็น `mmcblkXpNN` (ไม่ใช่ dm-X)
- Java (portable JRE ก็ได้) + openssl

## 2 คำสั่ง (รันผ่าน `!` — agent โดนบล็อก)
```bash
!  bash tools/resign_apk.sh      # เซ็นใหม่ด้วย AOSP platform key → ต้องเห็น "==> MATCH"
!  bash tools/push_signed.sh     # push /system/app + reboot
```

## resign_apk.sh ทำ
1. โหลด uber-apk-signer + AOSP `platform.pk8`/`platform.x509.pem`
2. เช็ก fingerprint กุญแจ = จอ (`C8:A2…2A:B8`) ไม่ตรง=abort
3. keystore → sign (zipalign + v2/v3) → verify cert = จอ

## push_signed.sh ทำ
re-verify fp → remount → push `/system/app/GeelyEx2Tools/` → perms/SELinux → reboot

## ต้องแก้ในสคริปต์ก่อนใช้เครื่อง/รอบอื่น
`D` (IP จอ), `ADB` path, `SCR`/scratchpad, JRE dir, `DEVICE_FP`

## ยืนยันสำเร็จ
`dumpsys package com.geely.ex2.tools` → `userId=1000`, `codePath=/system/app/GeelyEx2Tools`

เต็ม: [docs/06](../docs/06-install-app-system-signed.md)
