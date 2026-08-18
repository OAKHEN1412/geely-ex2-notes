# 02 — สถาปัตยกรรมแอป (จากการแกะไฟล์จริง)

ทั้งหมดนี้แกะจาก DEX string table ของ APK และ Mach-O ของ IPA โดยตรง (ดู `tools/`)

## แอปเป็น "คู่" client ↔ server

| ไฟล์ | package | ลงที่ | บทบาท |
|------|---------|-------|-------|
| `Geely-Ex2-Car.apk` | `com.geely.ex2.tools` | จอรถ | **Server** — รับคำสั่ง ทำงานตลอด |
| `Geely-Ex2-Phone.apk` | `com.example.ex2_phone` | Android | **Client** — รีโมท |
| `GeelyEX2.ipa` | `com.geely.ex2.phone` | iPhone | **Client** — รีโมท (คนละ OS) |

> `com.example.ex2_phone` = ชื่อ default ของ Android Studio ที่คนทำไม่ได้เปลี่ยน → ยืนยันว่าเป็นงานทำมือ/ชุมชน

## Transport — คนละแบบระหว่าง Android กับ iOS

| ฝั่ง | Bluetooth | Wi-Fi |
|------|-----------|-------|
| **Android** (`BtSppCarLink`) | **SPP / RFCOMM** (คลาสสิก) — `createRfcommSocketToServiceRecord` | TCP 47800 (`TcpCarLink`) |
| **iPhone** (`BleCarLink.swift`) | **BLE-UART** (Nordic-style) — CBCentralManager, UUID `6f1e2a00-…-c0ffee000002/3/4` | TCP 47800 (`TcpSocketLink`, Apple Network framework) |
| **รถ** (server) | **SPP เท่านั้น** — `listenUsingRfcommWithServiceRecord`, ชื่อ `GeelyEX2-CarSync`, UUID `…c0ffee000001` | `CarTcpServer` พอร์ต 47800 |

### ข้อสรุปสำคัญ

- **Android ↔ รถ**: ต่อได้ทั้ง Bluetooth (SPP) และ Wi-Fi ✓
- **iPhone ↔ รถ**: รถ **ไม่มี BLE server** (มีแต่ SPP + TCP) ส่วน iPhone พูด BLE-UART → ช่องทาง Bluetooth ของ iPhone **ไม่เจออะไรให้ต่อในเวอร์ชันนี้** ต้องใช้ **Wi-Fi/TCP** เท่านั้น
- แอป iOS เตือนเรื่องนี้เอง: *"Thiết bị không có dịch vụ BLE-UART — chỉ hỗ trợ xe có Bluetooth mới"*
- โค้ด BLE-UART ใน iOS น่าจะเล็งไปที่ดองเกิลฮาร์ดแวร์ หรือ car app เวอร์ชันอื่น

### บทเรียนจากการแกะ (กันพลาดซ้ำ)

- อย่าเชื่อ `Info.plist` อย่างเดียว — มันเขียน "BLE-UART หรือ TCP 47800" แต่ต้องแกะ binary จริงถึงรู้ว่า Android=SPP ไม่ใช่ BLE
- การ grep หาเลขพอร์ต "47800" ใน iOS binary **ไม่เจอ** เพราะเก็บเป็น Int32 ไม่ใช่สตริง — ต้องดูชื่อคลาส (`TcpSocketLink`, `NWEndpoint.Port`) แทน จึงยืนยันว่า iOS ต่อ TCP ได้จริง

## โปรโตคอล

- คุยกันด้วย **JSON** 3 ชนิด: `CarCommandMessage` → `CarCommandResponse` (คำสั่ง/ตอบ) และ `CarStatusMessage` (รถ push สถานะขึ้นมาเรื่อย ๆ)
- มี `NsdManager` (Android) / Bonjour-style discovery → มือถือหารถเจอเองในวง Wi-Fi ไม่ต้องพิมพ์ IP (มีทั้งโหมด auto-detect และกรอก IP เอง)
- ทุกอย่าง **peer-to-peer ไม่ผ่านคลาวด์** มือถือกับรถต้องอยู่ใกล้กัน/วงเน็ตเดียวกัน

## ฝั่งรถอ่าน/เขียนผ่าน VHAL (Vehicle HAL)

คลาสใน `com.geely.ex2.tools.data.vhal.*`:

| คลาส | ทำอะไร | อ่าน/เขียน |
|------|--------|-----------|
| `CarPropertyDoorWindowController` | ประตู + กระจก (`DOOR_POS`, `WINDOW_POS`) | อ่าน + สั่ง |
| `CarPropertyHvacController` | แอร์ | อ่าน + สั่ง |
| `CarPropertyBatteryVhalReader` | แบต (`EV_BATTERY_LEVEL`, `EV_CURRENT_BATTERY_CAPACITY`) | อ่าน |
| `CarPropertyTirePressureReader` | ลมยาง แยกรายล้อ | อ่าน |
| `CarPropertyDrivingModeReader` | โหมดขับ (ECO/COMFORT/DYNAMIC/SNOW/PURE/HYBRID/ADAPTIVE/XC) | อ่าน |
| `CarPropertyEnergyRegenerationReader` | ระดับ regen brake | อ่าน |
| `CarPropertyAmbientLightReader` | ไฟ ambient | อ่าน |
| `CarPropertyVehicleInfoReader` | ข้อมูลรถ | อ่าน |

บวก `GEAR_SELECTION` (P/R/N/D), `PERF_VEHICLE_SPEED`, `ENV_OUTSIDE_TEMPERATURE`

> ⚠️ บาง property อาจต้องเป็น **system app** ถึงเขียนได้ — โค้ดมีข้อความ `bị từ chối (cần build system?)` และ `DOOR_POS không đọc được (AOSP 0x16400B00 / vendor 0x264020A9) — cần build system?` ฟีเจอร์ควบคุมประตูเสี่ยงใช้ไม่ได้มากที่สุด

## Permission ที่ฝั่งรถขอ (ระดับระบบ)

`WRITE_SECURE_SETTINGS`, `WRITE_SETTINGS`, `SYSTEM_ALERT_WINDOW`, `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `USE_EXACT_ALARM`, `SCHEDULE_EXACT_ALARM`

`WRITE_SECURE_SETTINGS` = แอปธรรมดาขอเองไม่ได้ ต้องให้ผ่าน adb → นี่คือเหตุผลจริงที่ต้องเปิด ADB ก่อน
และยังอ้างถึง `com.flyme.auto.scenedirector` = คอมโพเนนต์ระบบของจอ Flyme Auto โดยตรง

## หมายเหตุ IPA

- **เป็น debug build** — main binary เป็นแค่ stub 90KB เรียก `GeelyEX2.debug.dylib` (2.5MB) + มี `__preview.dylib` ของ Xcode
- iOS ขั้นต่ำ **15.0** (ไม่ใช่ 12 อย่างที่ INSTALL-IOS.md เขียน — เชื่อ `MinimumOSVersion` ใน Info.plist)
- debug build sideload แล้วบางเครื่องเปิดไม่ติด (สร้างมาสำหรับรันในเครื่องนักพัฒนา)
