#!/usr/bin/env bash
# ลง Geely-Ex2-Car.apk เป็น system app บนจอ Geely EX2 (root, ADB over WiFi)
# ปลอดภัย: /system ไม่ผ่าน dm-verity, เพิ่มไฟล์เดียว, ย้อนกลับได้ด้วย rm
# ปิด MSYS path conversion กันไม่ให้ /system/... ถูกแปลงเป็น C:/Program Files/Git/system/...
export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL='*'
set -e
ADB="/c/Users/Pishe/Downloads/ADB_AppControl/adb/adb.exe"
D="172.20.10.3:5555"
# Windows-style path (เพราะปิด MSYS path conversion แล้ว adb=โปรแกรม Windows ต้องการ C:/...)
APK="C:/Users/Pishe/Desktop/geely-ex2-notes/app/Geely-Ex2-Car.apk"
DST="/system/app/GeelyEx2Tools"

echo ">>> [1/7] connect + root"
"$ADB" connect "$D"
"$ADB" -s "$D" root >/dev/null 2>&1 || true
sleep 2
"$ADB" connect "$D" >/dev/null 2>&1 || true

echo ">>> [2/7] remount /system rw"
"$ADB" -s "$D" remount

echo ">>> [3/7] cleanup stray mangled path (ถ้ามีจากรอบก่อน)"
"$ADB" -s "$D" shell 'rm -rf "/C:" 2>/dev/null; rm -rf /system/app/GeelyEx2Tools 2>/dev/null; echo cleaned'

echo ">>> [4/7] mkdir + push APK -> $DST"
"$ADB" -s "$D" shell "mkdir -p $DST"
"$ADB" -s "$D" push "$APK" "$DST/Geely-Ex2-Car.apk"

echo ">>> [5/7] set perms / owner / SELinux context"
"$ADB" -s "$D" shell "chmod 755 $DST; chmod 644 $DST/Geely-Ex2-Car.apk; chown 0:0 $DST $DST/Geely-Ex2-Car.apk; chcon u:object_r:system_file:s0 $DST $DST/Geely-Ex2-Car.apk"
"$ADB" -s "$D" shell "ls -lZ $DST/"

echo ">>> [6/7] keep adb-over-wifi alive across reboot"
"$ADB" -s "$D" shell "setprop persist.adb.tcp.port 5555" || true

echo ">>> [7/7] reboot head unit (ดับ ~30-60 วิ แล้วขึ้นเอง)"
"$ADB" -s "$D" reboot
echo ">>> DONE pushed + rebooting. รอ ~1 นาที แล้วพิมพ์ในแชทว่า 'บูตแล้ว'"
