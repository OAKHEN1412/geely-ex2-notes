#!/usr/bin/env bash
# uninstall-gapps.sh — ถอด GApps ที่ install-gapps.sh ใส่ไว้ (ลบเฉพาะไฟล์ในรายการ ./system/) แล้วรีบูต
#   ! bash "C:/Users/Pishe/Desktop/geely-ex2-notes/_install-bundle/gapps/uninstall-gapps.sh" [IP]
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
ADB="/c/Users/Pishe/Downloads/ADB_AppControl/adb/adb.exe"
D="${1:-172.20.10.3}:5555"
export MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*'
S(){ "$ADB" -s "$D" shell "$@" | tr -d '\r'; }

cd "$HERE"
"$ADB" connect "$D" >/dev/null
"$ADB" -s "$D" root >/dev/null 2>&1 || true; sleep 2; "$ADB" connect "$D" >/dev/null 2>&1 || true
"$ADB" -s "$D" remount >/dev/null
for f in $(find system -type f | sort); do S "rm -f /$f" && echo "    - /$f"; done
for dir in PrebuiltGmsCorePi GoogleServicesFramework Phonesky GoogleBackupTransport; do S "rm -rf /system/priv-app/$dir"; done
S "rmdir /system/etc/default-permissions /system/etc/preferred-apps 2>/dev/null" || true
# ข้อมูล/อัปเดตของ GMS ที่อยู่ใน /data
for p in com.google.android.gms com.google.android.gsf com.android.vending com.google.android.backuptransport; do
  S "pm uninstall $p" >/dev/null 2>&1 || true
  S "rm -rf /data/data/$p /data/user_de/0/$p"
done
S sync
"$ADB" -s "$D" reboot
echo ">>> ถอด GApps แล้ว — รีบูตจอ"
