# tools/

| ไฟล์ | ใช้ทำอะไร |
|------|-----------|
| `prep-usb.ps1` | เตรียม USB FAT32 + แตกแพตช์ + ตรวจโครงสร้าง (ดู docs/01, 04) — UTF-8 BOM สำหรับ PowerShell 5.1 |
| `dex_strings.py` | สกัด string table จาก APK (DEX) — `py -X utf8 dex_strings.py app.apk [--vietnamese] [--classes com.x]` |
| `ipa_strings.py` | สกัดข้อความจาก IPA (Mach-O debug dylib) — `py -X utf8 ipa_strings.py app.ipa [--vietnamese] [--grep a,b]` |
| `v2cert.py` | แกะ cert (SHA-256 + subject) จาก APK v2/v3 signing block ไม่ต้องใช้ Java — `py v2cert.py a.apk b.apk` |
| `fpcheck.py` | ตรวจ cert fingerprint ของ APK เทียบค่าที่คาดหวัง — `py fpcheck.py app.apk <SHA256_FP>` |
| `resign_apk.sh` | เซ็น Car.apk ใหม่ด้วย AOSP platform key + verify (ดู docs/06) |
| `push_signed.sh` | push signed APK เป็น system app → reboot (ดู docs/06) |
| `install_system_app.sh` | push APK ธรรมดาเป็น system app (เวอร์ชันก่อน re-sign — เก็บไว้อ้างอิง; ตัวนี้จะติด signature) |

## ⚠️ ต้องแก้ก่อนใช้ (ค่าเฉพาะเครื่อง/รอบ)

สคริปต์ที่แตะจอ (`resign_apk.sh`, `push_signed.sh`, `install_system_app.sh`) hardcode ค่าพวกนี้ไว้ตามรอบที่ทำจริง — แก้ให้ตรงเครื่อง/รอบของคุณ:

- **`D` / IP จอ** — เช่น `172.20.10.3:5555` (ดูจากเมนู Wi-Fi ของจอ, เปลี่ยนทุกครั้งที่ต่อใหม่)
- **`ADB`** — path ของ adb.exe (ที่นี่ใช้ตัวที่มากับ ADB AppControl)
- **`SCR` / scratchpad path** — path ที่เก็บ JRE + uber-apk-signer + กุญแจ
- **JRE dir** — `jdk-17.0.20+8-jre` (ตามเวอร์ชันที่โหลด)
- **`DEVICE_FP`** — fingerprint platform key ของจอ (ยืนยันด้วย v2cert.py กับ framework-res.apk ของจอเองก่อน)

## รันผ่าน `!` ในเซสชัน Claude Code

สคริปต์ที่เขียน /system หรือโหลดกุญแจ จะโดน auto-mode classifier บล็อกถ้าให้ agent รัน —
ให้ผู้ใช้รันเองด้วย `! bash tools/xxx.sh` (รันในเซสชัน ผลกลับเข้าแชท)
