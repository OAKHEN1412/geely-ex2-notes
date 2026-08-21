# 08 — แอปดูวิดีโอบนจอ (YouTube / Netflix)

> จอ Android 9 (API 28) arm64, root, น่าจะ **ไม่มี Google Play Services** (Flyme จีน)
> ⚠️ **ความปลอดภัย:** จอไม่มี interlock กันเล่นวิดีโอตอนรถวิ่ง — อย่าดูวิดีโอขณะขับ

## YouTube — ✅ ได้ (โหลดมาแล้วใน `_apps/`)
| แอป | ไฟล์ | UI | จุดเด่น |
|-----|------|----|--------|
| **NewPipe** ⭐ | `_apps/NewPipe_v0.29.1.apk` | มือถือ (touch) | เบา, ไม่ต้อง GMS, ไม่ต้องล็อกอิน, ไม่มีโฆษณา, เล่นเสียงพื้นหลัง — **เหมาะจอสัมผัสสุด** |
| **SmartTube** | `_apps/SmartTube_32.10_arm64.apk` | ทีวี (leanback) | เต็มจอสวย เหมาะดูยาว ๆ แต่คุมด้วยลูกศร/สัมผัสอาจงงกว่า |

ทั้งคู่ **ไม่ต้องมี Google Play** — ลงแล้วใช้ได้เลย

### ลง (เปิด ADB ที่จอก่อน — ไม่ต้องรีบูต)
```bash
ADB="C:/Users/Pishe/Downloads/ADB_AppControl/adb/adb.exe"
D="<IP>:5555"; "$ADB" connect $D
"$ADB" -s $D install -r "C:/Users/Pishe/Desktop/geely-ex2-notes/_apps/NewPipe_v0.29.1.apk"
"$ADB" -s $D install -r "C:/Users/Pishe/Desktop/geely-ex2-notes/_apps/SmartTube_32.10_arm64.apk"
```
> หรือลากไฟล์เข้า ADB AppControl → Install

## Netflix — ⚠️ น่าจะไม่รอด (บอกตรง)
Netflix บนจอ **root + ไม่มี GMS + Widevine ระดับต่ำ** มักเจอ:
- **จอดำ/เล่นไม่ได้** เพราะ DRM (Widevine) — จอ head unit มักได้แค่ **L3** → SD หรือเล่นไม่ขึ้นเลย
- Netflix **ตรวจ root เจอ** → ปฏิเสธ (จอนี้ root จากโรงงาน su ที่ /system/xbin ซ่อนยาก ไม่มี Magisk)
- ไม่มีใน Play (ไม่ certified) → ต้อง sideload APK ซึ่งก็มักจอดำ

### เช็กก่อนว่าพอมีหวังไหม — ดูระดับ Widevine
```bash
"$ADB" -s $D shell "dumpsys media.drm 2>/dev/null | grep -iE 'widevine|security level|L1|L3'"
# หรือดูว่ามี lib L1: "$ADB" -s $D shell "ls /vendor/lib*/mediadrm /system/vendor/lib*/mediadrm 2>/dev/null"
```
- ถ้าเห็น **L1** → มีหวัง sideload Netflix APK (APKMirror) อาจเล่น SD/HD ได้ (แต่ root ยังเสี่ยงโดนบล็อก)
- ถ้า **L3 อย่างเดียว** → Netflix แทบไม่รอด → ทางเลือกจริง:
  - ดู Netflix จาก **มือถือ** แล้ว mirror/หล่อภาพ (แต่ตอนขับก็ไม่ควร)
  - ใช้ **ไฟล์วิดีโอโหลดไว้** + VLC/Plex แทน
  - YouTube (NewPipe/SmartTube) ใช้แทนความบันเทิงหลัก

### ถ้าจะลอง Netflix อยู่ดี
โหลด Netflix APK จาก APKMirror (เวอร์ชันรองรับ Android 9) → `adb install -r` → เปิดดูว่าจอดำไหม
> ผมไม่ได้โหลด Netflix มาให้ (เป็นแอป DRM + โอกาสใช้ไม่ได้สูง) — ถ้าเช็ก Widevine แล้วเป็น L1 บอกได้ ผมช่วยหาเวอร์ชันที่ลงได้
