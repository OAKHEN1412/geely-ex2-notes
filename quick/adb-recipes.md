# ADB recipes (จอ Geely EX2)

```bash
ADB="C:/Users/Pishe/Downloads/ADB_AppControl/adb/adb.exe"   # adb ที่มากับ ADB AppControl
D="172.20.10.3:5555"                                        # IP:พอร์ต จอ (แก้ตามจริง)
export MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*'           # กัน Git Bash แปลง /system/...
```

## เชื่อมต่อ / ตรวจ
```bash
"$ADB" connect $D
"$ADB" devices                         # ต้องเห็น <D>  device
"$ADB" -s $D shell id                  # uid=0(root) ?
"$ADB" -s $D shell getprop ro.product.model   # IHU629G ?
```

## ตรวจแอปติดตั้ง
```bash
"$ADB" -s $D shell "pm list packages | grep geely.ex2"
"$ADB" -s $D shell "dumpsys package com.geely.ex2.tools | grep -E 'userId|codePath|flags'"
"$ADB" -s $D shell "logcat -d | grep -i 'signature mismatch'"   # ควรว่าง
```

## เขียน /system (system app)
```bash
"$ADB" -s $D remount
"$ADB" -s $D push "C:/path/signed.apk" /system/app/GeelyEx2Tools/Geely-Ex2-Car.apk
"$ADB" -s $D shell "chmod 644 /system/app/GeelyEx2Tools/Geely-Ex2-Car.apk; chown 0:0 ...; chcon u:object_r:system_file:s0 ..."
"$ADB" -s $D reboot
```

## ย้อนกลับ (ถอนแอป)
```bash
"$ADB" -s $D remount
"$ADB" -s $D shell "rm -rf /system/app/GeelyEx2Tools"
"$ADB" -s $D reboot
# หรือ Factory Reset ที่จอ (ลบทุกอย่าง + ปิด ADB)
```

## หา IP จอ (ถ้าลืม/เปลี่ยน)
```bash
for i in $(seq 1 14); do ping -n 1 -w 400 172.20.10.$i >/dev/null 2>&1 && echo UP 172.20.10.$i; done
```
> พอร์ต 5555 **refused** = จอถึงได้แต่ ADB ปิด → กดปุ่ม ADB ใน Dialer ใหม่
> **timeout** = จอไม่อยู่บนเน็ต → ต่อ Wi-Fi จอใหม่
