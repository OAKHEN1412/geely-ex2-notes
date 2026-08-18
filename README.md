# Geely EX2 — บันทึกการปลดล็อกจอ + วิเคราะห์แอป

รวมทุกอย่างที่ค้นคว้าเกี่ยวกับการปลดล็อก ADB บนจอกลาง (head unit) ของ **Geely EX2**
และผลการแกะแอปคู่ (มือถือ ↔ รถ) ที่มากับชุมชน จัดเป็นหมวดไว้ทำต่อ

> ⚠️ ทุกอย่างในนี้เป็นบันทึกเพื่อการศึกษา/ทำเอง ทุกขั้นตอนกับตัวรถมีความเสี่ยง ทำแล้วรับผิดชอบเอง

## สารบัญ

| # | เอกสาร | เนื้อหา |
|---|--------|---------|
| 01 | [docs/01-unlock-screen.md](docs/01-unlock-screen.md) | ขั้นตอนปลดล็อก ADB บนจอ (USB patch → ADB → ลง APK) |
| 02 | [docs/02-apps-architecture.md](docs/02-apps-architecture.md) | สถาปัตยกรรมแอปคู่ client↔server, transport, VHAL |
| 03 | [docs/03-app-features.md](docs/03-app-features.md) | ฟีเจอร์ทั้งสองฝั่งทำอะไรได้บ้าง |
| 04 | [docs/04-flash-usb-plan.md](docs/04-flash-usb-plan.md) | แผนเตรียม USB flash + ปัญหา read-only USB ที่เจอ |
| 05 | [docs/05-translation-plan.md](docs/05-translation-plan.md) | แผนแปลแอปเป็นไทย/อังกฤษ + ความเป็นไปได้ |
| — | [docs/translation-table.md](docs/translation-table.md) | ตารางแปล VN → ไทย → อังกฤษ (ครบทุก UI string) |
| — | [docs/artifacts.md](docs/artifacts.md) | ลิงก์หน้าเว็บ (artifact) ที่สร้างไว้ |

## เครื่องมือ

| ไฟล์ | ใช้ทำอะไร |
|------|-----------|
| [tools/prep-usb.ps1](tools/prep-usb.ps1) | เตรียม USB FAT32 + แตกแพตช์ + ตรวจโครงสร้างให้ถูกต้อง |
| [tools/dex_strings.py](tools/dex_strings.py) | สกัด string table จาก APK (DEX) — ใช้แกะข้อความ/คลาส |
| [tools/ipa_strings.py](tools/ipa_strings.py) | สกัดข้อความจาก IPA (Mach-O debug dylib) |

## สรุปสั้นสำหรับคนรีบ

- จอ Geely EX2 เป็น **Android ที่ root จากโรงงาน** แต่ปิด ADB ไว้ การ "ปลดล็อก" = รันแพตช์ผ่าน USB เพื่อ **เปิด ADB over Wi-Fi** เท่านั้น ไม่ได้เพิ่ม/ถอนสิทธิ์ระบบ
- ใช้ได้เฉพาะจอ **IHU629G / EEBADF1 / Flyme Auto E 1.8.0 build 1111 หรือ 1114** — ผิดเวอร์ชันเสี่ยงต้องแฟลชจอใหม่
- แอปเป็น **คู่ client↔server**: `Geely-Ex2-Car.apk` (รถ, เซิร์ฟเวอร์) + `Geely-Ex2-Phone.apk` / `GeelyEX2.ipa` (มือถือ, รีโมท) คุยกันตรงผ่าน **Wi-Fi TCP 47800** หรือ Bluetooth (Android=SPP, iOS=BLE-UART) — ไม่ผ่านคลาวด์
- แอปเป็น **ภาษาเวียดนามล้วน** งานชุมชนคนเวียดนามทำ (ติดต่อผ่าน Zalo) — มีเฉพาะ Car.apk ที่สลับอังกฤษได้
- สถานะงาน: **ยังไม่ได้ลงมือ** — รอ (1) หา USB ที่เขียนได้ (2) เช็กบิลด์ที่จอรถ (3) โหลดแพตช์ให้ตรง

## สถานะ / TODO

- [ ] เช็กบิลด์จอรถ (ตั้งค่ารถ → รถของฉัน) ว่า 1111 หรือ 1114
- [ ] หา USB ที่ไม่ใช่ตัวติดตั้ง Windows (ตัวเดิม read-only ระดับเฟิร์มแวร์)
- [ ] โหลดแพตช์จาก Google Drive ให้ตรงบิลด์
- [ ] รัน `tools/prep-usb.ps1` เตรียม USB
- [ ] ปลดล็อก + ลง `Geely-Ex2-Car.apk`
- [ ] (ทางเลือก) แปลแอปเป็นไทย/อังกฤษ — ดู docs/05
