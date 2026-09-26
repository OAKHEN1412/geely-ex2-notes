#!/usr/bin/env bash
# new-car-system.sh — รถคันใหม่ (ปลดล็อก ADB แล้ว): ลง EX2 BY OAKHEN เป็น system app + ตัวดับไฟจอ แล้วรีบูต
#   ! bash "C:/Users/Pishe/Desktop/geely-ex2-notes/_install-bundle/new-car-system.sh" [IP]
# ทำครั้งเดียวต่อคัน · หลังจากนั้นอัปเดตแอพด้วย install.ps1 (install -r) ได้เลย
# ทุกเช็กดูจาก "ข้อความ" ที่ได้ (ไม่ใช้ exit code ผ่าน pipe) · ไม่ตรงข้อไหน = หยุดก่อนแตะ /system
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"
ADB="/c/Users/Pishe/Downloads/ADB_AppControl/adb/adb.exe"
D="${1:-172.20.10.3}:5555"
DEVICE_FP="C8:A2:E9:BC:CF:59:7C:2F:B6:DC:66:BE:E2:93:FC:13:F2:FC:47:EC:77:BC:6B:2B:0D:52:C1:1F:51:19:2A:B8"
APK="$HERE/apks/EX2-BY-OAKHEN.apk"
RC="$REPO/devkit/backlight/ex2_backlight.rc"
DST=/system/app/GeelyEx2Tools
export MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*'
S(){ "$ADB" -s "$D" shell "$@" 2>&1 | tr -d '\r'; }
W(){ cygpath -w "$1"; }
die(){ echo "!!! $*"; exit 1; }

[ -f "$APK" ] || die "ไม่พบ $APK"
[ -f "$RC" ] || die "ไม่พบ $RC"

echo ">>> [1/8] connect + root"
"$ADB" connect "$D" >/dev/null 2>&1 || true
"$ADB" -s "$D" root >/dev/null 2>&1 || true; sleep 2; "$ADB" connect "$D" >/dev/null 2>&1 || true
[ "$("$ADB" -s "$D" get-state 2>/dev/null)" = "device" ] || die "ต่อ $D ไม่ได้ — เปิดปุ่ม ADB ใน Dialer Mode / เช็ก IP"
[ "$(S id -u)" = "0" ] || die "adb ไม่ใช่ root"

echo ">>> [2/8] ข้อมูลจอ (ต้องเป็น IHU629G / Android 9 / บิลด์ 1114)"
S "getprop ro.build.version.sdk; getprop ro.product.model; getprop ro.build.display.id; getprop ro.build.version.incremental" | sed 's/^/    /'
[ "$(S getprop ro.build.version.sdk)" = "28" ] || die "ไม่ใช่ Android 9 — หยุด"

echo ">>> [3/8] กุญแจ platform ของจอ = กุญแจที่เซ็นแอพ"
TMP="$(W "${TMPDIR:-/tmp}")\\framework-res.apk"
"$ADB" -s "$D" pull /system/framework/framework-res.apk "$TMP" >/dev/null || die "ดึง framework-res.apk ไม่ได้"
py -X utf8 "$(W "$REPO/tools/fpcheck.py")" "$TMP" "$DEVICE_FP" || die "กุญแจจอไม่ตรง — แอพที่เซ็นไว้ใช้กับจอนี้ไม่ได้ (อย่าฝืน push)"
py -X utf8 "$(W "$REPO/tools/fpcheck.py")" "$(W "$APK")" "$DEVICE_FP" || die "APK ไม่ได้เซ็นด้วยกุญแจที่ตรง"

echo ">>> [4/8] /system ต้องไม่ผ่าน dm-verity"
MNT=$(S "mount | grep -E ' /system | / ' | head -2"); echo "    $MNT"
echo "$MNT" | grep -q "/dev/block/dm-" && die "/system อยู่บน dm-verity — remount แล้วจะบูตไม่ขึ้น หยุด"
[ -n "$MNT" ] || die "อ่าน mount ไม่ได้"

echo ">>> [5/8] remount /system rw"
R=$("$ADB" -s "$D" remount 2>&1 | tr -d '\r'); echo "    $R"
# ไม่เชื่อข้อความของ adb — ลองเขียนจริง
[ "$(S "touch /system/.ex2rw && rm /system/.ex2rw && echo RW")" = "RW" ] || die "/system ยังเขียนไม่ได้ — remount ไม่ผ่าน"

echo ">>> [6/8] push EX2 BY OAKHEN -> $DST"
S "mkdir -p $DST"
"$ADB" -s "$D" push "$(W "$APK")" "$DST/Geely-Ex2-Car.apk" >/dev/null || die "push APK ไม่ผ่าน"
S "chmod 755 $DST; chmod 644 $DST/Geely-Ex2-Car.apk; chown 0:0 $DST $DST/Geely-Ex2-Car.apk; chcon u:object_r:system_file:s0 $DST $DST/Geely-Ex2-Car.apk"
S "ls -lZ $DST/" | sed 's/^/    /'

echo ">>> [7/8] ตัวดับไฟจอ (init trigger) -> /system/etc/init/ex2_backlight.rc"
"$ADB" -s "$D" push "$(W "$RC")" /system/etc/init/ex2_backlight.rc >/dev/null || die "push rc ไม่ผ่าน"
RCLABEL=$(S "ls -Z /system/etc/init/AAutoService.rc 2>/dev/null" | awk '{print $1}')
case "$RCLABEL" in u:object_r:*) ;; *) RCLABEL="u:object_r:system_file:s0";; esac
S "chmod 644 /system/etc/init/ex2_backlight.rc; chown 0:0 /system/etc/init/ex2_backlight.rc; chcon $RCLABEL /system/etc/init/ex2_backlight.rc"
S "ls -lZ /system/etc/init/ex2_backlight.rc" | sed 's/^/    /'
S sync

echo ">>> [8/8] reboot"
"$ADB" -s "$D" reboot
cat <<'EOF'
>>> เสร็จ — รอจอบูต ~1 นาที
หลังบูต: ต่อ Wi-Fi + เปิดปุ่ม ADB ใน Dialer Mode ใหม่ (adb-over-wifi ไม่ค้างข้ามรีบูตบนบิลด์นี้) แล้วเช็ก:
  adb shell "dumpsys package com.geely.ex2.tools | grep -E 'userId=|codePath='"   # ต้อง userId=1000, codePath=/system/app/GeelyEx2Tools
ต่อด้วย install.ps1 -Only smarttube · test-screen-off.sh · gapps/install-gapps.sh
EOF
