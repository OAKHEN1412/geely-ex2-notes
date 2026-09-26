# ชุดลงแอพจอ Geely EX2 (IHU629G / Flyme Auto E 1.8.0 / Android 9 arm64)

## A. รถคันใหม่ (ทำครั้งเดียวต่อคัน)

### A0. เช็กจอก่อน — ไม่ตรงข้อไหน หยุด
**ตั้งค่ารถ → ล่างสุด → รถของฉัน**: หน่วยจอ **IHU629G** · Model **EEBADF1** · **Flyme Auto E 1.8.0** · Build **1114**
(USB ที่เตรียมไว้ = แพตช์บิลด์ **1114** — ถ้าจอเป็น 1111 ต้องเตรียม USB ใหม่ด้วย `_patches/patch-1111.zip`)

### A1. ปลดล็อก ADB ด้วย USB
1. เสียบ USB (โฟลเดอร์ `3C6025_SW0E22H0306H111400000_user_995\OS\update.zip` ที่ราก) รอจอขึ้นว่าเจอ USB
2. **ปิด Bluetooth** (ไม่งั้นแอพโทรศัพท์โทรออกจริง)
3. เปิดแอพ **โทรศัพท์** → กดรหัส **Engineering Mode** (ดูสูตรล่าง)
4. กดไอคอนติดตั้งอัปเดต (**ตัวเครื่องหมายสีเขียว**) → จอรีบูตเข้า Recovery รันเอง
5. จบแล้ว **ขึ้น error = ปกติ** → **กดค้างปุ่ม Previous Track บนพวงมาลัย** จนจอรีสตาร์ต
6. ADB เปิดแล้ว → ถอด USB

### A2. ต่อ Wi-Fi + เปิด ADB
1. Engineering Mode → รายการ**สีน้ำเงิน** → ตั้งค่า Wi-Fi → ต่อ hotspot/Wi-Fi **วงเดียวกับคอม** → จด **IP**
2. แอพโทรศัพท์ → รหัส **Dialer Mode** → กดปุ่ม **ADB** 1 ครั้ง

### A3. ลงระบบ (คอม — รันผ่าน `!` · IP ไม่ใช่ 172.20.10.3 ให้ต่อท้ายคำสั่ง)
| # | ทำอะไร | คำสั่ง |
|---|--------|--------|
| 1 | EX2 BY OAKHEN เป็น system app + ตัวดับไฟจอ → **จอรีบูต** | `! bash "C:/Users/Pishe/Desktop/geely-ex2-notes/_install-bundle/new-car-system.sh" <IP>` |
| — | หลังบูต: ต่อ Wi-Fi + **กดปุ่ม ADB ใน Dialer Mode ใหม่** (ADB ไม่ค้างข้ามรีบูต) | |
| 2 | SmartTube + เปิด Wi-Fi | `! powershell -File "C:\Users\Pishe\Desktop\geely-ex2-notes\_install-bundle\install.ps1" -Only smarttube -Ip <IP>` |
| 3 | ทดสอบปิดจอ/เกียร์ R/ความสว่าง | `! bash "C:/Users/Pishe/Desktop/geely-ex2-notes/_install-bundle/test-screen-off.sh" <IP>` |
| 4 | Google Play → **จอรีบูต** (+ADB ใหม่) | `! bash "C:/Users/Pishe/Desktop/geely-ex2-notes/_install-bundle/gapps/install-gapps.sh" <IP>` |
| 5 | YouTube แท้ | เปิด Play Store → login → ติดตั้ง YouTube (ถ้า "ไม่ได้รับการรับรอง" ดูหัวข้อ C) |

`new-car-system.sh` เช็กก่อนแตะ /system: root, Android 9, **กุญแจ platform ของจอ = กุญแจที่เซ็นแอพ**, /system ไม่ติด dm-verity, เขียนได้จริง — ไม่ผ่านข้อไหนหยุดทันที

### รหัสเมนูซ่อน (กดในแอพโทรศัพท์)
| โหมด | สูตร |
|------|------|
| Engineering Mode | `#*` + (เดือน+10) + วันที่ 2 หลัก + ชั่วโมง 2 หลัก (**ระบบ 12 ชม.**) |
| Dialer Mode (ปุ่ม ADB) | `#*` + (เดือน+5) + วันที่ 2 หลัก + ชั่วโมง 2 หลัก (**ระบบ 12 ชม.**) |

ตัวอย่าง 27 ก.ย. เวลา 14:xx (= 02 ระบบ 12 ชม.) → Engineering `#*192702` · Dialer `#*142702`

## B. รถที่ลงแล้ว — อัปเดตแอพ
เปิดปุ่ม ADB ใน Dialer Mode → `! powershell -File "...\_install-bundle\install.ps1"` (`-Only oakhen` / `-Only smarttube`)

## C. Google Play "อุปกรณ์ไม่ได้รับการรับรอง"
1. เปิด Play Store + ต่อเน็ต 1–2 นาที
2. `! bash "C:/Users/Pishe/Desktop/geely-ex2-notes/_install-bundle/gapps/get-gsf-id.sh" <IP>` → ได้เลข GSF ID
3. https://www.google.com/android/uncertified → ล็อกอินบัญชีเดียวกับในรถ → ใส่เลข → Register → รอ ~30 นาที
4. ล้างข้อมูล Play Store + Play Services → login ใหม่

GApps ที่ลง: OpenGApps pico arm64 9.0 (20220503) เฉพาะ core (`PrebuiltGmsCorePi`, `GoogleServicesFramework`, `Phonesky`, `GoogleBackupTransport` + xml/framework) · ไม่ลง SetupWizard · ไม่เขียนทับไฟล์เดิม · หยุดถ้า `ro.control_privapp_permissions=enforce` (รันซ้ำด้วย `FORCE=1` ถ้ายอมรับความเสี่ยง)

## D. ถ้าพัง / คืนค่า
- GApps วนโลโก้ / crash ซ้ำ (ยังต่อ adb ได้): `gapps/uninstall-gapps.sh`
- ลบแอพที่ลงเพิ่ม + ปิด ADB: **ตั้งค่ารถ → รถของฉัน → คืนค่าจากโรงงาน** — ⚠️ ตามหลัก Android การคืนค่าโรงงานล้างแค่ /data **ไม่ลบไฟล์ใน /system** (EX2 BY OAKHEN, ex2_backlight.rc, GApps) → ลบด้วย adb ก่อน reset (docs/06 เดิมเขียนว่า reset ลบ /system/app ด้วย — ยังไม่เคยพิสูจน์บนจอนี้)
- ⚠️ บูตไม่ขึ้นจน adb ต่อไม่ได้: แพตช์ปลดล็อกใน USB **ไม่ใช่ OS เต็ม** กู้ไม่ได้ → ต้องใช้เฟิร์มแวร์เต็มจากศูนย์/ผู้ขาย — จึงห้ามข้ามขั้นเช็กของสคริปต์
