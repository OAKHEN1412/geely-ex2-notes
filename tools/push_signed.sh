#!/usr/bin/env bash
# push APK ที่เซ็นด้วย platform key แล้ว เข้า /system/app -> รีบูต
# มี re-verify fingerprint ก่อน push: ไม่ตรงจอ = abort
set -e
SCR="/c/Users/Pishe/AppData/Local/Temp/claude/C--Users-Pishe/3bbe1848-8919-483c-b3c5-f1b0e6b3a560/scratchpad"
cd "$SCR"
DEVICE_FP="C8:A2:E9:BC:CF:59:7C:2F:B6:DC:66:BE:E2:93:FC:13:F2:FC:47:EC:77:BC:6B:2B:0D:52:C1:1F:51:19:2A:B8"

SIGNED_UNIX=$(ls tools_java/signed/*.apk 2>/dev/null | head -1)
if [ -z "$SIGNED_UNIX" ]; then echo "!!! ไม่พบ signed APK — รัน resign_apk.sh ก่อน"; exit 1; fi
SIGNED_WIN=$(cygpath -w "$SCR/$SIGNED_UNIX")
echo ">>> signed APK: $SIGNED_UNIX"

echo ">>> [0/6] re-verify fingerprint ก่อน push"
py -X utf8 fpcheck.py "$SIGNED_WIN" "$DEVICE_FP" || { echo "!!! fingerprint ไม่ตรง — ยกเลิก ไม่ push"; exit 1; }

ADB="/c/Users/Pishe/Downloads/ADB_AppControl/adb/adb.exe"
D="172.20.10.3:5555"
DST="/system/app/GeelyEx2Tools"
export MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*'

echo ">>> [1/6] connect + root"
"$ADB" connect "$D"
"$ADB" -s "$D" root >/dev/null 2>&1 || true
sleep 2
"$ADB" connect "$D" >/dev/null 2>&1 || true

echo ">>> [2/6] remount /system rw"
"$ADB" -s "$D" remount

echo ">>> [3/6] push signed APK -> $DST (แทนที่ตัวเดิม)"
"$ADB" -s "$D" shell "mkdir -p $DST"
"$ADB" -s "$D" push "$SIGNED_WIN" "$DST/Geely-Ex2-Car.apk"

echo ">>> [4/6] perms / owner / SELinux"
"$ADB" -s "$D" shell "chmod 755 $DST; chmod 644 $DST/Geely-Ex2-Car.apk; chown 0:0 $DST $DST/Geely-Ex2-Car.apk; chcon u:object_r:system_file:s0 $DST $DST/Geely-Ex2-Car.apk"
"$ADB" -s "$D" shell "ls -lZ $DST/"

echo ">>> [5/6] setprop keep adb-over-wifi (best effort)"
"$ADB" -s "$D" shell "setprop persist.adb.tcp.port 5555" || true

echo ">>> [6/6] reboot head unit"
"$ADB" -s "$D" reboot
echo ">>> DONE. รอจอบูต ~1 นาที แล้วบอก 'บูตแล้ว' (ถ้าจอไม่ต่อ wifi กลับ ให้ต่อ wifi + เปิด ADB ใน Dialer เหมือนเดิม)"
