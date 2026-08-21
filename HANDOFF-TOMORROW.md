# HANDOFF — งานต่อวันพรุ่งนี้ (Geely EX2 / EX2 BY OAKHEN)

_เขียน 2026-08-21 ปลายเซสชัน · ผู้ใช้ GitHub OAKHEN1412 · ภาษาไทย_

## สถานะรวม
วันนี้สร้าง/แก้เสร็จ **โค้ด+APK พร้อมลงหมด** แต่ **ยังไม่ได้เทสต์บนจอจริง** (adb offline ทั้งวันช่วงหลัง) — พรุ่งนี้ = ต่อ adb → build/install/recon/test

### อัปเดต 2026-08-21 (รอบ 3 ที่รถ) — ✅ ปิดจอดับสนิทถาวร สำเร็จ!
**backlight true-off แก้จบแล้ว** (เทสผ่านหลังรีบูตครบวงจร):
- สถาปัตยกรรมสุดท้าย (ไม่มี daemon/sh/su): **แอพ set `sys.ex2bl` (system_prop) → init `write` เขียน node**
  - แอพ system_app เขียน /sys/class/leds เองไม่ได้ (SELinux search denied) แต่ **set system_prop ได้** (`allow system_app system_prop set`)
  - init (u:r:init:s0) **เขียน sysfs_leds ได้** (มี `write /sys/class/leds/vibrator/trigger` ใน rc เดิม = หลักฐานสิทธิ์)
  - `on property:sys.ex2bl=*  write /sys/class/leds/lcd-backlight/brightness ${sys.ex2bl}`
  - เขียน 0 ครั้งเดียว **คงอยู่เอง** (ถ้าไม่แตะ Settings.System) ไม่ต้อง re-write loop
- ไฟล์: `devkit/backlight/ex2_backlight.rc` (init trigger) · `tools/install_backlight_daemon.sh` (ลง /system ผ่าน `!`) · FloatingBox ใช้ `SystemProperties.set` (reflection)
- **ผลเทสหลังรีบูต**: `setprop sys.ex2bl 0` → node 166→0 คงที่ · แตะ 🌚 ผ่านแอพ → node=0 ดับสนิท · แตะปลุก → node=166 · DevService+กล่องลอยเด้งเองหลังบูต ✅
- ทางที่ลองแล้วไม่เวิร์ก (บันทึกกันซ้ำ): daemon sh via init service — **init transition→su:s0 ถูก deny**, shell domain เขียน node ไม่ได้, มีแต่ su/init เขียนได้ → เลยใช้ init write ตรง

### อัปเดต 2026-08-21 (รอบ 2 ที่รถ) — media ครบ, hub ใช้ได้, backlight ติด SELinux
- ✅ **ลง media/เกมครบ 8 แอพ**: ClaudeWeb, Jellyfin, Moonlight(com.limelight), NewPipe, RetroArch(com.retroarch.aarch64), SimpleAppLauncher(com.simplemobiletools.applauncher), SmartTube(org.smarttube.stable), VLC(org.videolan.vlc)
- ✅ **EX2 hub ใช้งานได้**: Section A สวิตช์เรา + Section B การ์ดเปิดหน้าเดิม → เปิด `MainActivity` เห็น FlymeVerticalNav ครบทุกแท็บ **เป็นภาษาไทย** (แปลติด) · ⚠️ deep-link เข้าตรงแท็บไม่ติด (route extra ไม่ตรง contract → เปิด EmptyStartScreen, แท็บบาร์อยู่ครบให้แตะเอง) — ถ้าจะให้เข้าตรงแท็บต้อง baksmali MainActivity หา key จริง
- 🔴 **backlight true-off = ติด SELinux (ต้นตอสุดท้าย)**:
  ```
  avc: denied { search } comm="geely.ex2.tools" tcontext=u:object_r:sysfs_leds:s0 tclass=dir
      scontext=u:r:system_app:s0  permissive=0
  ```
  แอพ `system_app` **search /sys/class/leds ไม่ได้** → `File.exists()`=false → `detectBL()`=null → fallback ไป dim (brightness=0 → floor 10 = ดับไม่สนิท) · แอพ exec sh/su ไม่ได้ด้วย (entrypoint denied shell_exec/su_exec) → shell out ไม่ได้
  - จอ: `ro.secure=0`, `/system/xbin/su` (AOSP toybox `su <uid> <cmd>` ไม่ใช่ `su -c`), ไม่มี Magisk, sepolicy=/vendor/etc/selinux/precompiled_sepolicy
  - **พิสูจน์แล้วว่า node ถูกตัว**: `echo 0 > /sys/class/leds/lcd-backlight/brightness` (as root) → **จอดับสนิทจริง** (user ยืนยัน) · แต่ **ห้ามแตะ Settings.System** (ตั้ง screen_brightness → DisplayPowerController ไล่ node ไป floor 10)
  - แก้แล้วในโค้ด: backlight path เลิกแตะ Settings.System (commit) — แต่ยังไม่พอเพราะ SELinux block detectBL
  - **ทางแก้ (รอ user เลือก, ต้องรีบูต)**: (A) init.rc ใน /system เขียน node ตาม property + แอพสั่งผ่าน property · (B) magiskpolicy inject `allow system_app sysfs_leds` + re-inject ตอนบูต · **user เลือก "พักไว้ก่อน"**
- 🐛 backlight code fix: `FloatingBox.screenOffBacklight/screenOnBacklight` ถอด `Settings.System.screen_brightness_mode` ออก (มันทริก controller ไล่ floor)

### อัปเดต 2026-08-21 (รอบเทสจริงบนจอ) — ต่อ adb ลง+เทสแล้ว
สถานะ: จอ 172.20.10.3 (IP เปลี่ยนได้ · ARP mac e8-8f-c4-08-dd-72) · **build+ลงทั้ง 2 แอปสำเร็จ, fp ตรงจอ**
- ✅ **EX2 harness ทำงาน**: DevService foreground รัน, กล่องลอย ☰ เด้งบนจอ, ศูนย์ตั้งค่า 4 สวิตช์เรนเดอร์สวยครบ
- 🐛 **แก้บั๊กจริง**: `FloatingBox.onTouch` ACTION_DOWN เดิม `return false` → แตะกล่องทะลุไปโดน UI รถ (เปิด 360) + ไม่ได้ ACTION_UP → togglePanel ไม่ทำงาน · แก้เป็น `return true` → **เมนู 🌚/🌙/▽ เปิดได้แล้ว** (rebuild dex + install ยืนยันผ่าน)
- ⚠️ **ClaudeWeb: claude.ai เรนเดอร์ไม่ขึ้น (จอขาว)** — WebView = `com.android.webview 83.0.4103.120` (Chrome 83 ปี 2020 เก่าเกิน) DOM มี `#root` แต่สูง 0px = SPA บูตไม่ขึ้น · **แผน: re-sign WebView ใหม่ด้วย platform key → install -r → ตั้ง provider** (ยังไม่ทำ, user เลือกเทส EX2 ก่อน)
- 🔴 **ปิดจอ (backlight) ยังดับไม่สนิท** — แอพ (uid=1000 system ไม่ใช่ root) เขียน `/sys/class/leds/.../brightness` ไม่ผ่าน (perm/SELinux) → เหลือแค่ black overlay ฝั่งแอพ, panel ยังติด
  - **ต้อง recon**: `bash tools/recon_backlight.sh <IP>` (หา node จริง + เขียน 0 as root ดับไหม + DAC perm + SELinux avc)
  - **แผนแก้**: ถ้า root เขียน 0 แล้วดับจริง → ทำ **init.rc hook ใน /system: chmod 0666 + chcon node ตอนบูต** ให้แอพ(group system)เขียนได้ถาวร → guard-tick 700ms เดิมจะเอาชนะ controller ได้
- build fix: `build_claudeapp.sh` ยัด classes.dex ด้วย python zipfile (JDK ในเครื่องไม่มี jar.exe) · toolchain รอดใน scratchpad `432971d6/.../devkit` (jdk+android28.jar+r8.jar) + `_build/toolchain/`
- recon โบนัส: **กล้อง 360°/AVM ทำงาน** (กล้องหน้า+bird's-eye) · launcher = Flyme Auto (CarPlay/Android Auto/แอปแนะนำ)

### อัปเดต 2026-08-21 (รอบบ่าย) — จัดชื่อ/ไอคอน/UI ของแอปที่สร้างเอง ✅ (verify compile ผ่านแล้ว)
- **ClaudeWeb**: ได้ **ชื่อ "Claude" + ไอคอนแบรนด์ (adaptive clay+sunburst ครบทุก density)** + splash + loading overlay + หน้า offline+ปุ่มลองใหม่ · เพิ่ม `claudeapp/res/` (สร้างไอคอนซ้ำ: `py tools/gen_claude_icon.py`) · `build_claudeapp.sh` เพิ่ม pipeline resource (aapt2 compile/link+R.java) แล้ว
- **EX2 BY OAKHEN**: `SettingsActivity` เป็น **ศูนย์ควบคุม สวิตช์เปิด/ปิดต่อฟีเจอร์** (กล่องลอย/เริ่มอัตโนมัติ/ปุ่มปิดจอ/ปุ่มหรี่จอ) เดินสายให้ `DevService`+`FloatingBox` อ่าน pref จริง (`Prefs.java`) · รายละเอียด `docs/12-app-ui.md`
- **prebuilt `ex2dev-classes17.dex` rebuild แล้ว** (18.5KB→23KB รวม toggle) ด้วยสคริปต์ใหม่ `devkit/build_harness.sh` · **verify แล้วว่า javac+d8 ผ่านทั้ง 2 แอป** (โหลด android28.jar/r8.jar ไว้ใน `_build/toolchain/`)
- ยังเหลือ: **ลงจอจริง + เทสต์ UI** (ยังไม่ได้ install — adb offline)

---

## ⚠️ 0. สิ่งแรกที่ต้องทำพรุ่งนี้: กู้ toolchain (สำคัญสุด)
toolchain (JDK17, android28.jar, r8.jar, apktool, baksmali/smali) อยู่ใน **scratchpad ของเซสชันวันนี้ = หายข้ามวัน** build scripts auto-detect ไม่เจอ → ต้องโหลดใหม่ก่อน build อะไร รันชุดนี้:
```bash
D="/c/Users/Pishe/Desktop/geely-ex2-notes/_build/toolchain"; mkdir -p "$D"; cd "$D"
# JDK17 (มี javac) — Adoptium
curl -sL "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk" -o jdk17.zip; unzip -q -o jdk17.zip
JDK=$(find "$D" -name javac.exe | head -1 | xargs dirname); echo "JDK bin = $JDK"
# stubs + dexer + apktool
curl -sL "https://raw.githubusercontent.com/Sable/android-platforms/master/android-28/android.jar" -o android28.jar
curl -sL "https://maven.google.com/com/android/tools/r8/8.3.37/r8-8.3.37.jar" -o r8.jar
curl -sL "https://github.com/iBotPeaches/Apktool/releases/download/v3.0.3/apktool_3.0.3.jar" -o apktool.jar
# baksmali/smali (สำหรับ patch_autowindow) — org.smali 2.5.2 + deps
for u in baksmali/2.5.2/baksmali-2.5.2 smali/2.5.2/smali-2.5.2 dexlib2/2.5.2/dexlib2-2.5.2 util/2.5.2/util-2.5.2; do curl -sL "https://repo1.maven.org/maven2/org/smali/$u.jar" -o "$(basename $u).jar"; done
curl -sL "https://repo1.maven.org/maven2/com/google/guava/guava/32.1.3-jre/guava-32.1.3-jre.jar" -o guava.jar
curl -sL "https://repo1.maven.org/maven2/com/beust/jcommander/1.82/jcommander-1.82.jar" -o jcommander.jar
curl -sL "https://repo1.maven.org/maven2/org/antlr/antlr-runtime/3.5.2/antlr-runtime-3.5.2.jar" -o antlr.jar
```
> จากนั้น set env ก่อนรัน build script: `export JAVA="$JDK/java.exe"` และแก้ path `DEV`/`AJ`/`R8` ใน `build_oakhen.sh`/`build_claudeapp.sh` ให้ชี้ `_build/toolchain` (หรือ symlink) — apktool `_build/apktool.jar` build_oakhen โหลดเองอยู่แล้ว
> **classes9-autowindow.dex + ex2dev-classes17.dex ที่ build แล้ว อยู่ใน `devkit/prebuilt/` (ในรีโป durable)** — ถ้าไม่แก้โค้ด harness/smali เพิ่ม **ไม่ต้อง recompile** แค่ build_oakhen ก็ประกอบ APK ได้

---

## 1. พร้อม build+install (พอต่อ adb: เปิดปุ่ม ADB ใน Dialer ที่จอ → `adb connect 172.20.10.3:5555`)
| แอป | คำสั่ง | ได้อะไร |
|-----|--------|--------|
| **EX2 BY OAKHEN** | `! bash tools/build_oakhen.sh` → install -r + `am start-foreground-service ...DevService` | ไทย + rebrand + กล่องลอยปิดจอ(backlight) + กระจก 300ms + สวิตช์ตั้งค่า · **RCE ถอดแล้ว + บั๊ก 16 ตัวแก้แล้ว** |
| **ClaudeWeb** | `! bash tools/build_claudeapp.sh` → install + `pm grant com.oakhen.claudeweb android.permission.RECORD_AUDIO` + `am start` | claude.ai เต็มจอ + ไมค์ (login อีเมล) |
| **media/เกม** | `adb install -r _apps/{Jellyfin,RetroArch,Moonlight}*.apk` | ดูหนัง(server ส่วนตัว) + เกม |

ลงบนจอแล้ว (ไม่ต้องลงซ้ำ): NewPipe, SmartTube, VLC, Simple App Launcher

## 2. Recon queue (รันตอน adb กลับ — ปลดล็อกฟีเจอร์ที่ค้าง)
```bash
ADB="C:/Users/Pishe/Downloads/ADB_AppControl/adb/adb.exe"; D="172.20.10.3:5555"; S(){ "$ADB" -s $D shell "$@"; }
S 'find /sys -name brightness 2>/dev/null | grep -iE "backlight|lcd|panel|wled"'   # backlight path (กล่องลอย)
S 'dumpsys webviewupdate | grep -iE "Current WebView package|version"'             # WebView เก่าไหม (voice claude.ai)
S 'dumpsys audio | grep -iE "input|mic|source" | head'                            # ไมค์ (claude.ai voice)
S 'cmd media.camera list; ls -l /dev/video*; for v in /sys/class/video4linux/*/name; do echo "$v=$(cat $v)"; done' # กล้อง (dashcam)
S 'cat /proc/mounts | grep -iE "media_rw|/storage/|vfat|exfat"; df -h | grep -iE "storage|emulated"' # storage USB/ในเครื่อง (dashcam) — เสียบ USB ก่อน
S 'cmd car_service list-properties | grep -i HVAC_TEMPERATURE; cmd car_service get-property-value 358614275'        # HVAC temp (overlay อุณหภูมิ)
"$ADB" -s $D pull /system/framework/android.car.jar                                # compile overlay อุณหภูมิ
S 'getprop | grep -iE "acc|ignition|standby"; settings get system screen_off_timeout' # ไฟจอตอนดับรถ (remote-command)
```

## 3. เทสต์ที่ต้องทำ
- **กล่องลอย**: 🌚 ปิดจอ → มืดหมดทั้งจอ + แตะปลุก · **กดปิด→แตะ→ปิด เร็ว ๆ หลายรอบ ต้องไม่ค้างดำ** (บั๊ก #1 ที่แก้) · 🌙 หรี่จอ · สวิตช์ในหน้า "EX2 กล่องลอย" · รีบูตแล้วเด้งเอง
- **กระจก**: เปิดประตูรถจอด → ลดเร็วขึ้น (~0.3s แทน 2s)
- **ClaudeWeb**: login อีเมล, ปุ่มไมค์/voice, แนบไฟล์ (ถ้า voice ไม่ทำงาน เช็ก WebView version)

## 4. Design พร้อมทำ (ยังไม่ build — รอ recon)
- **Overlay ตัวเลขอุณหภูมิ** (แทนแถบสี): อ่าน `HVAC_TEMPERATURE_SET` (0x15600503) ผ่าน CarPropertyManager วาด badge °C — รอ recon propId/area + `android.car.jar` · โครงโค้ด fable มีแล้ว
- **Dashcam (กล้องรอบคัน)** → **`docs/11-dashcam-design.md`** (design ครบ): อัดได้เฉพาะกล้องถอย/หน้า (V4L2/Camera2) — 360 คาดไม่ได้ · แท็บใหม่ "กล้อง/บันทึก" · **เลือกที่เก็บ USB/ในเครื่อง** (system app เขียน /storage/<UUID>/ ตรง ๆ ไม่ต้อง SAF — Storage.java พร้อม) · รอ recon camera engine + storage mount
- **Remote web-command** (สั่งรถจากเว็บ ผ่าน VHAL, outbound poll ปลอดภัย): ทำได้ตอนจอมีไฟ+เน็ต · ติดเรื่องไฟจอ/เน็ตตอนดับรถ
- ❌ **Netflix/Widevine**: ดรอปแล้ว (ไม่มี Widevine + cert 50/50) · CarPlay AI Box คือทางฮาร์ดแวร์ถ้าอยากได้จริง

## 5. ค่าสำคัญ
```
จอ: IHU629G / Flyme Auto E 1114 / Android 9 / arm64 / root / com.geely.ex2.tools (uid 1000)
adb: C:\Users\Pishe\Downloads\ADB_AppControl\adb\adb.exe  · car IP 172.20.10.3:5555 (เปลี่ยนได้)
platform key fp (DEVICE_FP): C8:A2:E9:BC:CF:59:7C:2F:B6:DC:66:BE:E2:93:FC:13:F2:FC:47:EC:77:BC:6B:2B:0D:52:C1:1F:51:19:2A:B8
signing kit: _signing-kit/ (platform.jks pass=android, uas.jar) · fpcheck.py
```

## 6. Gotcha (จำไว้)
- **adb-over-wifi ไม่ค้างข้ามรีบูต/หลับ** → เปิดปุ่ม ADB ใน Dialer ใหม่ทุกครั้ง · จอ sleep ลึก = Wi-Fi ดับด้วย (ปลุกด้วย KEYCODE_POWER=26 ที่ปุ่มจริง)
- **classifier บล็อก agent ตอน build/sign/แตะ backdoor** → รันเองผ่าน `!` (ไม่ใช่ให้ agent ลอบเลี่ยง)
- **py ต้องการ path `C:/...` ไม่ใช่ `/c/...`** สำหรับ argument ที่ส่งเข้า Python
- reversible ทั้งหมด: base 1.0-system ยังอยู่ + Factory Reset

## 7. ไฟล์ใหม่วันนี้
`docs/07-dev-harness.md` `docs/08-media-apps.md` `docs/10-bughunt-report.md` · `tools/build_oakhen.sh` `tools/build_claudeapp.sh` `tools/push_plugin.py`(dev, deprecated) · `devkit/src/com/oakhen/ex2dev/*` `devkit/patch_autowindow.sh` `devkit/prebuilt/{ex2dev-classes17.dex,classes9-autowindow.dex}` · `claudeapp/{AndroidManifest.xml,src/.../MainActivity.java}` · `translations/values-th-strings.xml` · `_apps/*` `_signed-apk/EX2-BY-OAKHEN.apk`
