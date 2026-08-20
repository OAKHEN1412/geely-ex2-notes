# CHEATSHEET — ทำทั้งหมดในหน้าเดียว

> จอ Geely EX2 บิลด์ **1114** · เต็ม → [MASTER.md](../MASTER.md)

## ปลดล็อก (ครั้งเดียว)
1. USB FAT32 + แพตช์ 1114 (`prep-usb.ps1`) → เสียบรถ
2. ปิด BT → แอปโทรศัพท์ → **Engineering** `#*(ด+10)(วว)(ชม12)`
3. ติดตั้งอัปเดต(สีเขียว) → error ตอนจบ=ปกติ → กดค้าง **Previous Track** จนรีสตาร์ต
4. Wi-Fi + จด IP → **Dialer** `#*(ด+5)(วว)(ชม12)` → กดปุ่ม **ADB**

## ต่อ + ลง Car.apk
```bash
ADB="C:/Users/Pishe/Downloads/ADB_AppControl/adb/adb.exe"
"$ADB" connect 172.20.10.3:5555
# เซ็นใหม่ (จำเป็น! ไม่งั้น signature mismatch):
!  bash tools/resign_apk.sh     # → ==> MATCH
!  bash tools/push_signed.sh    # → reboot
# หลังรีบูต: ยืนยัน
"$ADB" shell dumpsys package com.geely.ex2.tools | grep -E 'userId|codePath'
# ควรได้ userId=1000, codePath=/system/app/GeelyEx2Tools
```

## กฎเหล็ก
- บิลด์ต้องตรง (1114) · ระหว่าง Recovery **ห้ามตัดไฟ/ถอด USB**
- Car.apk ต้อง **re-sign ด้วย AOSP platform key** เสมอ
- หลังรีบูต adb-over-wifi ดับ → กดปุ่ม ADB ใน Dialer ใหม่
- คำสั่งแตะ /system รันเองผ่าน `!` (agent โดน classifier บล็อก)

## พัง? → [troubleshoot.md](troubleshoot.md) · ย้อนกลับ: Factory Reset (ตั้งค่ารถ→รถของฉัน)
