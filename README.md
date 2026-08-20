# Geely EX2 — บันทึกการปลดล็อกจอ + วิเคราะห์แอป

รวมทุกอย่างที่ค้นคว้าเกี่ยวกับการปลดล็อก ADB บนจอกลาง (head unit) ของ **Geely EX2**
และผลการแกะแอปคู่ (มือถือ ↔ รถ) ที่มากับชุมชน จัดเป็นหมวดไว้ทำต่อ

> ⚠️ ทุกอย่างในนี้เป็นบันทึกเพื่อการศึกษา/ทำเอง ทุกขั้นตอนกับตัวรถมีความเสี่ยง ทำแล้วรับผิดชอบเอง

## เริ่มที่ไหน

- 📄 **[MASTER.md](MASTER.md)** — ไฟล์ต้นฉบับเดียว รวมทุกอย่างจากงานจริง
- ⚡ **[quick/](quick/)** — ไฟล์หยิบใช้เร็ว (cheatsheet, รหัส, adb, troubleshoot, facts)
- 📚 **[docs/](docs/)** — เอกสารละเอียดแยกหัวข้อ (ด้านล่าง)

## สารบัญ

| # | เอกสาร | เนื้อหา |
|---|--------|---------|
| 01 | [docs/01-unlock-screen.md](docs/01-unlock-screen.md) | ขั้นตอนปลดล็อก ADB บนจอ (USB patch → ADB → ลง APK) |
| 02 | [docs/02-apps-architecture.md](docs/02-apps-architecture.md) | สถาปัตยกรรมแอปคู่ client↔server, transport, VHAL |
| 03 | [docs/03-app-features.md](docs/03-app-features.md) | ฟีเจอร์ทั้งสองฝั่งทำอะไรได้บ้าง |
| 04 | [docs/04-flash-usb-plan.md](docs/04-flash-usb-plan.md) | แผนเตรียม USB flash + ปัญหา read-only USB ที่เจอ |
| 05 | [docs/05-translation-plan.md](docs/05-translation-plan.md) | แผนแปลแอปเป็นไทย/อังกฤษ + ความเป็นไปได้ |
| 06 | [docs/06-install-app-system-signed.md](docs/06-install-app-system-signed.md) | **ติดตั้ง Car.apk จริง** — re-sign ด้วย AOSP platform key + ลงเป็น system app |
| — | [docs/translation-table.md](docs/translation-table.md) | ตารางแปล VN → ไทย → อังกฤษ (ครบทุก UI string) |
| — | [docs/artifacts.md](docs/artifacts.md) | ลิงก์หน้าเว็บ (artifact) ที่สร้างไว้ |

## เครื่องมือ

| ไฟล์ | ใช้ทำอะไร |
|------|-----------|
ดูรายละเอียดครบใน [tools/README.md](tools/README.md)

| ไฟล์ | ใช้ทำอะไร |
|------|-----------|
| [tools/prep-usb.ps1](tools/prep-usb.ps1) | เตรียม USB FAT32 + แตกแพตช์ + ตรวจโครงสร้าง |
| [tools/resign_apk.sh](tools/resign_apk.sh) | เซ็น Car.apk ใหม่ด้วย AOSP platform key (docs/06) |
| [tools/push_signed.sh](tools/push_signed.sh) | push signed APK เป็น system app → reboot (docs/06) |
| [tools/v2cert.py](tools/v2cert.py) / [fpcheck.py](tools/fpcheck.py) | แกะ/ตรวจ cert จาก APK v2/v3 (ไม่ต้องใช้ Java) |
| [tools/dex_strings.py](tools/dex_strings.py) / [ipa_strings.py](tools/ipa_strings.py) | สกัดข้อความจาก APK/IPA |

## สรุปสั้นสำหรับคนรีบ

- จอ Geely EX2 เป็น **Android ที่ root จากโรงงาน** แต่ปิด ADB ไว้ การ "ปลดล็อก" = รันแพตช์ผ่าน USB เพื่อ **เปิด ADB over Wi-Fi** เท่านั้น ไม่ได้เพิ่ม/ถอนสิทธิ์ระบบ
- ใช้ได้เฉพาะจอ **IHU629G / EEBADF1 / Flyme Auto E 1.8.0 build 1111 หรือ 1114** — ผิดเวอร์ชันเสี่ยงต้องแฟลชจอใหม่
- แอปเป็น **คู่ client↔server**: `Geely-Ex2-Car.apk` (รถ, เซิร์ฟเวอร์) + `Geely-Ex2-Phone.apk` / `GeelyEX2.ipa` (มือถือ, รีโมท) คุยกันตรงผ่าน **Wi-Fi TCP 47800** หรือ Bluetooth (Android=SPP, iOS=BLE-UART) — ไม่ผ่านคลาวด์
- แอปเป็น **ภาษาเวียดนามล้วน** งานชุมชนคนเวียดนามทำ (ติดต่อผ่าน Zalo) — มีเฉพาะ Car.apk ที่สลับอังกฤษได้
- **`adb install` ธรรมดาไม่ผ่าน** — Car.apk เป็น `sharedUserId=android.uid.system` ต้อง **re-sign ด้วย platform key** (จอใช้ AOSP test-key สาธารณะ) + ลงเป็น system app (ดู docs/06)
- สถานะงาน: **✅ ติดตั้งสำเร็จบนจอบิลด์ 1114 (2026-08-20)** — เป็น system app สิทธิ์เต็ม (userId=1000)

## สถานะ / TODO

- [x] เช็กบิลด์จอรถ → **1114**
- [x] หา USB ที่เขียนได้ (ตัว Kingston Windows เดิม read-only เฟิร์มแวร์ → ใช้ตัวอื่น)
- [x] โหลดแพตช์ 1114 + รัน `tools/prep-usb.ps1` เตรียม USB
- [x] ปลดล็อก ADB สำเร็จ
- [x] **ลง `Geely-Ex2-Car.apk` สำเร็จ** (re-sign + system app — ดู docs/06)
- [ ] ทดสอบแอปบนจอ (คุมแอร์/ไฟ ambient/กระจก จริง)
- [ ] (ทางเลือก) ลงแอปมือถือรีโมท — `Geely-Ex2-Phone.apk` / `GeelyEX2.ipa`
- [ ] (ทางเลือก) แปลแอปเป็นไทย/อังกฤษ — ดู docs/05 + `docs/translation-table.md`
- [ ] ปิด adb-over-wifi เมื่อไม่ใช้ (ความปลอดภัย)
