# Troubleshoot (อาการ → แก้)

| อาการ | สาเหตุ | แก้ |
|-------|--------|-----|
| จอดับตอนรันแพตช์ นานไม่ติด | รีบูตเข้า Recovery (ปกติ) | **ห้ามตัดไฟ/ถอด USB** · กดค้าง Previous Track · รอ 5-10 นาที · force reboot จอ (กดปุ่ม power/วอลุ่มค้าง 10-15 วิ) |
| error ตอนจบแพตช์ | ตั้งใจให้เป็น | ไม่ใช่ปัญหา กดค้าง Previous Track ต่อ |
| กดรหัสเมนูไม่เข้า | ชั่วโมงไม่ได้แปลง 12 ชม. / ยึดเวลาคอมแทนจอ | คำนวณใหม่จากเวลาจอ (quick/codes.md) |
| `adb connect` → **refused (10061)** | จอถึงได้ แต่ ADB ปิด | Dialer Mode → กดปุ่ม ADB |
| `adb connect` → **timeout (10060)** | จอไม่อยู่บนเน็ต | ต่อ Wi-Fi จอใหม่ + จด IP (สแกน: adb-recipes.md) |
| หลังรีบูต adb ต่อไม่ได้ | adb-over-wifi ไม่ค้าง | กดปุ่ม ADB ใน Dialer ใหม่ (ทุกครั้งหลังรีบูต) |
| `INSTALL_FAILED_SHARED_USER_INCOMPATIBLE` | ต้องเป็น system app + platform sig | ลง /system + **re-sign** (install-car-apk.md) |
| logcat `Signature mismatch ... android.uid.system` | APK เซ็นผิดกุญแจ | re-sign ด้วย AOSP platform key (resign_apk.sh) |
| แอปไม่โผล่หลังลง /system | signature ไม่ตรง / รีบูตยังไม่สแกน | เช็ก logcat · ต้อง re-sign · รอรีบูตจบ |
| `adb push` ไป `C:/Program Files/Git/system/...` | MSYS path mangling | `export MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*'` + Windows path สำหรับไฟล์ local |
| คำสั่ง agent โดน "blocked by classifier" | เขียน /system หรือโหลด key | รันเองผ่าน `! bash tools/xxx.sh` |
| USB ฟอร์แมตไม่ได้ "read only" | USB retail Windows = write-protect เฟิร์มแวร์ | ใช้ USB อื่น |
| iPhone: แอปเปิดไม่ติด | IPA เป็น debug build | ลองเครื่องอื่น / iOS ต้อง 15.0+ · หรือใช้ Android |
| iPhone ต่อรถไม่ได้ | รถไม่มี BLE server | ใช้ **Wi-Fi/TCP** (กรอก IP จอ) ไม่ใช่ Bluetooth |
