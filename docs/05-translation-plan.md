# 05 — แผนแปลแอปเป็นไทย / อังกฤษ

## ข้อจำกัดหลัก: ข้อความ hardcode ในโค้ด

แอปนี้ **ไม่มีไฟล์ภาษาแยก** (`strings.xml` / `Localizable.strings`) — ทุกคำฝังในโค้ด Compose/SwiftUI คอมไพล์ติดไปกับ binary จึงแปลยากกว่าแอปทั่วไป ต้องแก้ที่ binary โดยตรง

## ความเป็นไปได้ต่อแพลตฟอร์ม

| แพลตฟอร์ม | ความเป็นไปได้ | วิธี |
|-----------|--------------|------|
| **Android** (Phone/Car apk) | ทำได้ (ปานกลาง) | apktool decode → แก้ `const-string` ในโค้ด → build → เซ็นใหม่ |
| **iOS** (ipa) | แทบไม่คุ้ม | ข้อความอยู่ใน Mach-O + เป็น debug build; patch แทนที่ได้แค่ "ยาวเท่าเดิมหรือสั้นกว่า" ข้อความไทย/อังกฤษยาวไม่เท่า → พังง่าย |

## ทางเลือก เรียงจากคุ้มสุด

1. **ถามผู้พัฒนา** — ในแอปมีติดต่อ Zalo เป็นงาน hobby ขอ build อังกฤษ/ไทย หรือ source อาจได้ง่าย
2. **แปลหน้าจอสด ๆ** — Android มี Google แปลหน้าจอเรียลไทม์ทับเป็นไทยได้ ไม่ต้อง rebuild; Car.apk มีปุ่มสลับอังกฤษในตัวอยู่แล้ว
3. **Patch APK เอง** — มี[ตารางแปลครบแล้ว](translation-table.md) พร้อม mapping VN→ไทย/อังกฤษ

## ขั้นตอน patch (เมื่อพร้อม)

ต้องติดตั้งก่อน (เครื่องนี้ยังไม่มี): **JDK** + **apktool** + **apksigner/zipalign** (Android SDK build-tools)

```
1. apktool d Geely-Ex2-Phone.apk -o work
2. แก้ const-string ในไฟล์ smali ตาม docs/translation-table.md
   (หรือทำ dict VN→TH แล้ว sed แทนที่ทั้งโปรเจกต์ — ระวังคำที่ปนกับ VHAL property)
3. apktool b work -o Geely-Ex2-Phone-th.apk
4. zipalign -p 4 → apksigner sign ด้วย keystore ของตัวเอง
5. ติดตั้งทับ (ต้อง uninstall ตัวเดิมก่อนถ้า signature ต่างกัน)
```

### ⚠️ ห้ามแปลคำพวกนี้ (เป็นชื่อ VHAL / protocol)

`WINDOW_POS`, `DOOR_POS`, `GEAR_SELECTION`, `EV_BATTERY_LEVEL`, `PERF_VEHICLE_SPEED`,
`AVAS`, `AUTO_WINDOW_*`, `CarCommandMessage`, `47800`, UUID ต่าง ๆ

แปลเฉพาะข้อความที่ผู้ใช้เห็นบนจอ (ดูตารางแปล) ส่วน log ภายในแปลหรือไม่ก็ได้

## หมายเหตุ

- ถึงแปลได้ ก็ยังต้องปลดล็อกจอ + ลง Car.apk อยู่ดี ไม่งั้นแอปแค่หน้าตาสวยแต่ต่อรถไม่ติด
- การแก้แอปคนอื่นเพื่อใช้เองทั่วไปโอเค แต่ไม่ควรเอาไป redistribute
