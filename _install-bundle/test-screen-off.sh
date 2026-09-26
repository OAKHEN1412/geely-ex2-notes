#!/usr/bin/env bash
# test-screen-off.sh — พิสูจน์ที่รถว่า "ปิดจอ" = ดับไฟหลังจอจริงทั้งแผง (รวมแถบเมนูล่าง) ไม่ใช่แค่ทับดำส่วนแอพ
#   ! bash "C:/Users/Pishe/Desktop/geely-ex2-notes/_install-bundle/test-screen-off.sh" [IP]
# ต้องจอดรถ เกียร์ P
set -e
ADB="/c/Users/Pishe/Downloads/ADB_AppControl/adb/adb.exe"
D="${1:-172.20.10.3}:5555"
export MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*'
S(){ "$ADB" -s "$D" shell "$@" | tr -d '\r'; }
NODE=/sys/class/leds/lcd-backlight/brightness

"$ADB" connect "$D" >/dev/null; "$ADB" -s "$D" root >/dev/null 2>&1 || true; sleep 2; "$ADB" connect "$D" >/dev/null 2>&1 || true
[ "$("$ADB" -s "$D" get-state 2>/dev/null)" = "device" ] || { echo "!!! ต่อจอไม่ได้"; exit 1; }

echo "== 1) ไฟหลังจอทั้งหมดในเครื่อง (ถ้ามีมากกว่า 1 ตัว = อาจมีจอ/แถบแยกไฟ)"
S 'for f in /sys/class/backlight/*/brightness /sys/class/leds/*/brightness; do [ -f "$f" ] && echo "   $f = $(cat $f) / max $(cat ${f%brightness}max_brightness 2>/dev/null)"; done' | grep -iE "backlight|lcd|panel|disp|wled" || echo "   (ไม่เจอ node อื่น)"
S "dumpsys display | grep -iE 'mDisplayId|DisplayDeviceInfo' | head -6"

echo "== 2) ตัวดับไฟของ init ติดตั้งอยู่ไหม"
RC=$(S "ls -lZ /system/etc/init/ex2_backlight.rc 2>/dev/null"); echo "   $RC"; [ -n "$RC" ] || { echo "!!! ไม่มี ex2_backlight.rc — ปุ่มปิดจอจะแค่ทับดำ ต้องรัน tools/install_backlight_daemon.sh"; exit 1; }

BEFORE=$(S "cat $NODE")
echo "== 3) สั่งดับผ่าน property เดียวกับที่ปุ่มใช้ (ก่อน = $BEFORE)"
S "setprop sys.ex2bl 0"; sleep 1
AFTER=$(S "cat $NODE")
echo "   node หลังสั่ง = $AFTER  (ต้องเป็น 0)"
echo "   >>> ดูที่จอ 5 วินาที: ทั้งจอรวมแถบเมนูล่าง/แถบบน ต้องมืดสนิทเท่ากันหมด"
sleep 5
S "setprop sys.ex2bl ${BEFORE:-120}"; sleep 1
echo "   คืนไฟ -> node = $(S "cat $NODE")  (ต้องเท่ากับ $BEFORE)"
[ "$AFTER" = "0" ] && echo "   ✅ ดับไฟหลังจอจริง" || echo "   ❌ node ไม่เป็น 0 — ไฟไม่ได้ดับ"

echo "== 4) อ่านเกียร์ (สำหรับเปิดจอเองตอนเข้า R) — ค่า: P=4 R=2 N=1 D=8"
S "logcat -c"
echo "   >>> กด 🌚 ปิดจอ ที่กล่องลอยบนจอ แล้วเหยียบเบรก เข้าเกียร์ R (รถจอดนิ่ง) — จอต้องติดเองทันที"
echo "   รอ 25 วินาที..."
sleep 25
S "logcat -d -s EX2Reverse:* EX2Dev:*" | tail -15
echo "   เห็น 'เกียร์ R -> เปิดจอ' = ผ่าน · เห็น 'อ่านเกียร์ไม่ได้' = ไม่ผ่าน (ส่ง log นี้มา)"

echo "== 5) กรอบแผ่นดำตอนปิดจอ (กด 🌚 อีกครั้งตอนนี้ แล้วรอ 8 วินาที)"
sleep 8
S "dumpsys window windows | grep -B1 -A6 'com.geely.ex2.tools' | grep -E 'Window #|mAttrs|ty=|mFrame|fl='" | head -20
echo "   ty=SYSTEM_ERROR + mFrame เต็มจอ = แผ่นดำคลุมทั้งจอรวมแถบ · แตะจอเพื่อปลุก"

echo "== 6) ความสว่างหลังปลุกต้องเท่าก่อนปิด (แตะจอให้ติดก่อน แล้วรอ)"
sleep 3
B1=$(S "cat $NODE"); echo "   ก่อน: node = $B1 · screen_brightness = $(S settings get system screen_brightness) · mode = $(S settings get system screen_brightness_mode) (1=อัตโนมัติ)"
echo "   >>> กด 🌚 ปิดจอ แล้วแตะปลุก ภายใน 15 วินาที"
sleep 15
B2=$(S "cat $NODE"); echo "   หลังปลุก: node = $B2"
[ "$B1" = "$B2" ] && echo "   ✅ ความสว่างคืนเท่าเดิม" || echo "   ⚠️ ไม่เท่า ($B1 -> $B2) — ส่งผลนี้มา (ปุ่มคืนค่าจาก screen_brightness ถ้าโหมดอัตโนมัติจะไม่ตรง)"
