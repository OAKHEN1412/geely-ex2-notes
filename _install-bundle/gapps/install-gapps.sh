#!/usr/bin/env bash
# install-gapps.sh — ลง Google Play Services + Play Store + GSF เข้า /system/priv-app ของจอ Geely EX2
# ที่มา: OpenGApps pico arm64 9.0 (20220503) — แตกเฉพาะ core ไว้ใน ./system/ (โครงเดียวกับ /system ของจอ)
#
# รันเอง:  ! bash "C:/Users/Pishe/Desktop/geely-ex2-notes/_install-bundle/gapps/install-gapps.sh" [IP]
# ถอนออก:  ! bash ".../gapps/uninstall-gapps.sh" [IP]
#
# ไม่เขียนทับไฟล์เดิมของจอ (ถ้ามีไฟล์ชื่อซ้ำ = abort) → ถอนออกได้ด้วยการลบรายการไฟล์เดียวกัน
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
ADB="/c/Users/Pishe/Downloads/ADB_AppControl/adb/adb.exe"
D="${1:-172.20.10.3}:5555"
export MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*'
S(){ "$ADB" -s "$D" shell "$@" | tr -d '\r'; }
die(){ echo "!!! $*"; exit 1; }

cd "$HERE"
[ -d system/priv-app/PrebuiltGmsCorePi ] || die "ไม่พบ ./system — โฟลเดอร์ไม่ครบ"
FILES=$(find system -type f | sort)

echo ">>> [1/7] connect + root"
"$ADB" connect "$D" >/dev/null
"$ADB" -s "$D" root >/dev/null 2>&1 || true; sleep 2; "$ADB" connect "$D" >/dev/null 2>&1 || true
[ "$("$ADB" -s "$D" get-state 2>/dev/null)" = "device" ] || die "ต่อ $D ไม่ได้ — เปิดปุ่ม ADB ใน Dialer / เช็ก IP"
[ "$(S id -u)" = "0" ] || die "adb ไม่ใช่ root"

echo ">>> [2/7] ตรวจเครื่อง"
SDK=$(S getprop ro.build.version.sdk); ABI=$(S getprop ro.product.cpu.abi)
PRIV=$(S getprop ro.control_privapp_permissions)
echo "    SDK=$SDK ABI=$ABI privapp=${PRIV:-<ว่าง>}"
[ "$SDK" = "28" ] || die "ต้องเป็น Android 9 (SDK 28) — ได้ $SDK"
[ "$ABI" = "arm64-v8a" ] || die "ต้องเป็น arm64-v8a — ได้ $ABI"
# มีตัวใดตัวหนึ่งอยู่แล้ว (เช่น GSF stub ของ ROM) = package ซ้ำ path ต่างกัน -> PackageManager สับสน/บูตช้า -> หยุดก่อน
for p in com.google.android.gms com.google.android.gsf com.android.vending com.google.android.backuptransport; do
  [ -n "$(S pm path $p)" ] && die "มี $p อยู่แล้วในจอ ($(S pm path $p)) — หยุด ส่งผลนี้มาดูก่อน"
done
# privapp enforce + permission ไม่ครบ = system_server crash (bootloop) → xml ของ OpenGApps รองรับ แต่ให้ยืนยันอีกชั้น
if [ "$PRIV" = "enforce" ] && [ "$FORCE" != "1" ]; then
  die "จอตั้ง privapp=enforce (เสี่ยง bootloop ถ้า permission ไม่ครบ) — ถ้ายอมรับ รันซ้ำด้วย FORCE=1"
fi

echo ">>> [3/7] ตรวจว่าไม่ทับไฟล์เดิม"
for f in $FILES; do
  [ -z "$(S "ls /$f 2>/dev/null")" ] || die "/$f มีอยู่แล้วในจอ — ยกเลิก (ไม่เขียนทับของเดิม)"
done

echo ">>> [4/7] remount /system rw + เช็กที่ว่าง"
"$ADB" -s "$D" remount >/dev/null || die "remount ไม่ผ่าน"
S "mount | grep -E ' /system | / '" | head -2
FREE_KB=$(S "df -k /system | tail -1" | awk '{print $4}')
NEED_KB=$(du -sk system | awk '{print $1}')
echo "    ว่าง ${FREE_KB}KB / ต้องใช้ ${NEED_KB}KB"
[ "${FREE_KB:-0}" -gt $((NEED_KB + 20480)) ] || die "/system ที่ว่างไม่พอ"

echo ">>> [5/7] push"
for f in $FILES; do
  S "mkdir -p /$(dirname "$f")"
  "$ADB" -s "$D" push "$(cygpath -w "$HERE/$f")" "/$f" >/dev/null || die "push $f ไม่ผ่าน"
  echo "    + /$f"
done

echo ">>> [6/7] perms / owner / SELinux"
for dir in PrebuiltGmsCorePi GoogleServicesFramework Phonesky GoogleBackupTransport; do
  S "chmod 755 /system/priv-app/$dir; chown 0:0 /system/priv-app/$dir"
done
for f in $FILES; do S "chmod 644 /$f; chown 0:0 /$f"; done
S "chcon -R u:object_r:system_file:s0 /system/priv-app/PrebuiltGmsCorePi /system/priv-app/GoogleServicesFramework /system/priv-app/Phonesky /system/priv-app/GoogleBackupTransport /system/etc/default-permissions /system/etc/preferred-apps /system/etc/sysconfig/google*.xml /system/etc/permissions/*google* /system/framework/com.google.android.*.jar"
S "ls -lZ /system/priv-app | grep -E 'Gms|GoogleServices|Phonesky|GoogleBackup'"
S sync

echo ">>> [7/7] reboot"
S "setprop persist.adb.tcp.port 5555" || true
"$ADB" -s "$D" reboot
cat <<'EOF'
>>> DONE — รอจอบูต 1-3 นาที (บูตแรกช้า GMS optimize dex)
หลังบูต:
  1) ต่อเน็ต → เปิด Play Store → login Google
  2) ถ้าขึ้น "อุปกรณ์ไม่ได้รับการรับรอง": เอา GSF ID (ดู README) ไปลงที่ https://www.google.com/android/uncertified แล้วรอ ~30 นาที + ล้างข้อมูล Play Store/GMS
  3) Play Store จะอัปเดต Play Services เอง (รอสักพัก) → ค้นหา YouTube → ติดตั้ง
ถ้าจอวนโลโก้เกิน 5 นาที: ต่อ adb ได้ → รัน uninstall-gapps.sh
EOF
