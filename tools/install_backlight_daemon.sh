#!/usr/bin/env bash
# install_backlight_daemon.sh — ติดตั้ง root daemon คุม backlight ถาวรใน /system + init.rc
# ทำให้ "ปิดจอ (backlight) ดับสนิท" ทำงานหลังรีบูต (แอพ system_app เขียน sysfs_leds เองไม่ได้เพราะ SELinux)
#   รัน:  bash tools/install_backlight_daemon.sh [IP]      (ต้อง adb root)
# หลังรัน: reboot -> init สตาร์ท service ex2_bl อัตโนมัติ -> daemon อ่าน flag ที่แอพเขียนแล้วคุม node
set -e
ADB="${ADB:-C:/Users/Pishe/Downloads/ADB_AppControl/adb/adb.exe}"
D="${1:-172.20.10.3}:5555"
REPO="/c/Users/Pishe/Desktop/geely-ex2-notes"
# adb.exe = โปรแกรม Windows ต้องการ path แบบ C:/... (ไม่ใช่ /c/...) สำหรับไฟล์ local ที่ push
REPO_WIN="$(cygpath -w "$REPO" 2>/dev/null | tr '\\' '/' || echo 'C:/Users/Pishe/Desktop/geely-ex2-notes')"
export MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*'
S(){ "$ADB" -s "$D" shell "$@" 2>&1; }

"$ADB" connect "$D" >/dev/null 2>&1
S 'id' | grep -q 'uid=0' || { echo "!!! จอไม่ root/ไม่ออนไลน์"; exit 1; }

echo ">>> [1/5] remount /system rw"
S 'mount -o rw,remount /system' ; S 'mount -o rw,remount /' 2>/dev/null || true

echo ">>> [2/5] push script + rc เข้า /system"
"$ADB" -s "$D" push "$REPO_WIN/devkit/backlight/ex2_backlight.sh" /system/etc/ex2_backlight.sh 2>&1 | tail -1
"$ADB" -s "$D" push "$REPO_WIN/devkit/backlight/ex2_backlight.rc" /system/etc/init/ex2_backlight.rc 2>&1 | tail -1

echo ">>> [3/5] perms + SELinux label (ให้ตรงกับไฟล์ระบบอื่น)"
S 'chmod 755 /system/etc/ex2_backlight.sh; chown 0:0 /system/etc/ex2_backlight.sh; chcon u:object_r:system_file:s0 /system/etc/ex2_backlight.sh'
# .rc ให้ label ตรงกับ .rc อื่นในโฟลเดอร์ (มัก system_file)
RCLABEL=$(S "ls -Z /system/etc/init/AAutoService.rc 2>/dev/null | awk '{print \$1}'")
[ -z "$RCLABEL" ] && RCLABEL="u:object_r:system_file:s0"
S "chmod 644 /system/etc/init/ex2_backlight.rc; chown 0:0 /system/etc/init/ex2_backlight.rc; chcon $RCLABEL /system/etc/init/ex2_backlight.rc"
echo "    rc label = $RCLABEL"

echo ">>> [4/5] ยืนยันไฟล์"
S 'ls -lZ /system/etc/ex2_backlight.sh /system/etc/init/ex2_backlight.rc'

echo ">>> [5/5] เสร็จ — ต้อง reboot ให้ init โหลด service"
echo "    รีบูต:  $ADB -s $D reboot"
echo "    หลังรีบูต: เปิดปุ่ม ADB ที่ Dialer ใหม่ แล้วเช็ก:  adb shell 'ps -A | grep ex2_backlight'  (ต้องเห็น)"
echo "    ทดสอบ: แตะกล่องลอย -> 🌚 ปิดจอ -> ต้องดับสนิท"
