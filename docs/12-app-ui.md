# 12 — หน้าตาแอป (ชื่อ/ไอคอน/UI) ของแอปที่สร้างเอง

_2026-08-21 · ครอบคลุมแอปที่ "เขียน source เอง" 2 ตัว: ClaudeWeb (ใหม่ทั้งตัว) + ศูนย์ตั้งค่า EX2 BY OAKHEN_

## A) ClaudeWeb — แอปใหม่ (WebView ห่อ claude.ai)

**ตัวตนแบรนด์**
- ชื่อแอป: **Claude** (`res/values/strings.xml` → `app_name`)
- ไอคอน: **adaptive icon** (API26+) พื้นสีดินเผา `#D97757` + sunburst ครีม `#FAF9F5`
  - vector: `res/drawable/ic_launcher_foreground.xml` + `ic_launcher_background.xml`
  - `res/mipmap-anydpi-v26/ic_launcher.xml` (+ `_round`)
  - PNG fallback ครบ 5 density: `res/mipmap-{mdpi..xxxhdpi}/ic_launcher.png` (+ round)
  - สร้างซ้ำได้ด้วย `py -X utf8 tools/gen_claude_icon.py`
- โลโก้ในแอป (splash/overlay): `res/drawable/ic_logo.xml` (sunburst clay บนพื้นเข้ม)

**UI/UX flow** (`MainActivity.java`)
1. **Splash** — `AppTheme.Splash` วาด `res/drawable/splash_window.xml` (พื้นเข้ม + โลโก้กลางจอ) ตั้งแต่ cold start ก่อน `onCreate` → ไม่เห็นจอขาว
2. **Loading overlay** — โลโก้ + สปินเนอร์สี clay + "กำลังเปิด Claude…" ค้างจนกว่า `onPageFinished`/progress 100
3. **หน้าเว็บ** — claude.ai เต็มจอ (immersive), ไมค์/voice ใช้ได้
4. **Offline** — เน็ตหลุด/โหลด main frame พลาด → หน้า "เชื่อมต่ออินเทอร์เน็ตไม่ได้" + ปุ่ม **ลองใหม่** (reload) — ไม่ค้างจอเปล่า

**ธีมสี** (`res/values/colors.xml`): `claude_bg #1F1E1C` · `claude_clay #D97757` · `claude_cream #FAF9F5` · `claude_muted #8A8781`

**Build**: `! bash tools/build_claudeapp.sh` → `aapt2 compile res` → `link` (gen `R.java`) → `javac` → `d8` → เซ็น platform key → `_apps/ClaudeWeb.apk`
> pipeline resource (aapt2 compile/link + R.java) ถูกเพิ่มในสคริปต์แล้ว — จำเป็นเพราะตอนนี้มี res/ (ไอคอน/ธีม/สตริง)

## B) EX2 BY OAKHEN — ศูนย์ตั้งค่า (SettingsActivity)

จากเดิมสวิตช์เดียว → เป็น **ศูนย์ควบคุมฟีเจอร์แบบการ์ด** สวิตช์ต่อฟีเจอร์ (จำค่าใน SharedPreferences `ex2dev`, คีย์รวมที่ `Prefs.java`):

| สวิตช์ | คีย์ | ผลจริง |
|--------|------|--------|
| **กล่องลอย (master)** | `floating_enabled` | โชว์/ซ่อนกล่องทันที + เป็นตัว gate ฟีเจอร์ลูก (ลูกจางลงเมื่อปิด) |
| เริ่มอัตโนมัติหลังบูต | `auto_start` | `DevService` เช็คตอนบูต — เปิดคู่กับ master ถึงจะโผล่กล่องเอง |
| ปุ่ม 🌚 ปิดจอ | `btn_screenoff` | `FloatingBox` โชว์ปุ่มนี้ในเมนูกล่องหรือไม่ |
| ปุ่ม 🌙 หรี่จอ | `btn_dim` | เช่นเดียวกัน |
| กระจกไว 300ms *(แถวข้อมูล)* | — | ฝังในแอป (smali patch `classes9`) — ไม่ใช่สวิตช์ runtime จึงเป็นแถวสถานะ ไม่ทำสวิตช์หลอก |

โค้ดที่อ่าน pref จริง: `SettingsActivity` (เขียน), `DevService.onStartCommand` (`floating_enabled && auto_start`), `FloatingBox.togglePanel` (`btn_screenoff`/`btn_dim`)

**Build harness dex** (เมื่อแก้โค้ด harness): `JAVAC=... bash devkit/build_harness.sh` → `devkit/prebuilt/ex2dev-classes17.dex` → แล้วค่อย `! bash tools/build_oakhen.sh`
