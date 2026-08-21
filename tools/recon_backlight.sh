#!/usr/bin/env bash
# recon_backlight.sh — หา backlight node จริง + ทดสอบดับ + เช็กว่าทำไมแอพเขียนไม่ได้
# รันเมื่อจอออนไลน์:  bash tools/recon_backlight.sh [IP]
#   (จับตาดูจอจริงตอนสเต็ป TEST WRITE — จอควรมืดสนิทชั่วขณะแล้วกลับมา)
# เป้า: ตอบ 3 คำถาม — node ไหนดับจริง? · DAC perm แอพ(uid1000)เขียนได้ไหม? · SELinux block ไหม?
set -e
ADB="${ADB:-C:/Users/Pishe/Downloads/ADB_AppControl/adb/adb.exe}"
D="${1:-172.20.10.3}:5555"
export MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*'
S(){ "$ADB" -s "$D" shell "$@" 2>&1; }

"$ADB" connect "$D" >/dev/null 2>&1
S 'id' | grep -q 'uid=0' || { echo "!!! จอไม่ออนไลน์/ไม่ root — เปิดปุ่ม ADB ที่จอก่อน"; exit 1; }

echo "############ 1) enumerate ทุก brightness node ############"
S 'echo "--- /sys/class/backlight ---"; for d in /sys/class/backlight/*; do [ -e "$d" ] || continue; echo "$d: val=$(cat $d/brightness 2>/dev/null) max=$(cat $d/max_brightness 2>/dev/null) type=$(cat $d/type 2>/dev/null)"; done'
S 'echo "--- /sys/class/leds (backlight-ish) ---"; for d in /sys/class/leds/*; do case "$d" in *backlight*|*lcd*|*wled*|*panel*|*disp*) echo "$d: val=$(cat $d/brightness 2>/dev/null) max=$(cat $d/max_brightness 2>/dev/null)";; esac; done'
S 'echo "--- find อื่น ๆ ---"; find /sys -name brightness 2>/dev/null | grep -iE "backlight|lcd|wled|panel|disp"'

echo ""
echo "############ 2) DAC perm + SELinux label ของ node ที่ FloatingBox ใช้ ############"
S 'for n in /sys/class/leds/lcd-backlight/brightness /sys/class/leds/lcd_backlight/brightness /sys/class/backlight/panel0-backlight/brightness; do [ -e "$n" ] && { echo "== $n =="; ls -lZ "$n"; }; done'
echo ">>> ดู owner:group + mode — ถ้า root:system 0664 = แอพ(uid1000)เขียนได้ · root:root 0644 = เขียนไม่ได้"

echo ""
echo "############ 3) TEST WRITE 0 (as root) — จอควรมืดสนิท! ############"
S 'N=$(for n in /sys/class/leds/lcd-backlight/brightness /sys/class/leds/lcd_backlight/brightness $(find /sys/class/backlight/*/brightness 2>/dev/null); do [ -e "$n" ] && { echo "$n"; break; }; done)
echo "node = $N"
[ -z "$N" ] && { echo "!!! ไม่พบ node เลย"; exit 0; }
OLD=$(cat "$N"); echo "ค่าเดิม = $OLD"
echo 0 > "$N"; sleep 1; echo "หลังเขียน 0 -> อ่านได้ = $(cat $N)  (ถ้ายัง=OLD แปลว่า controller ดันคืน/เขียนไม่ติด)"
sleep 2
echo "$OLD" > "$N"; echo "คืนค่า -> $(cat $N)"'
echo ">>> ถ้าจอ 'มืดสนิทจริง' ตอน sleep = node นี้คุมฮาร์ดแวร์ได้ · ถ้าไม่มืด = ผิด node หรือ controller override"

echo ""
echo "############ 4) SELinux denial ของแอพ (uid1000 system_app) ############"
S 'dmesg 2>/dev/null | grep -iE "avc.*denied.*(sysfs_leds|backlight|lcd)" | tail -5; echo "--- logcat avc ---"; logcat -d -b events -t 300 2>/dev/null | grep -iE "avc.*denied" | grep -iE "leds|backlight|ex2|1000" | tail -5'
echo ">>> ถ้ามี avc denied = SELinux block (ต้อง chcon node เป็น type ที่แอพเขียนได้)"

echo ""
echo "############ 5) แอพเขียนได้ไหม (จำลอง uid ระบบ) ############"
S 'N=$(for n in /sys/class/leds/lcd-backlight/brightness $(find /sys/class/backlight/*/brightness 2>/dev/null); do [ -e "$n" ] && { echo "$n"; break; }; done)
echo "ลอง chmod 666 + chcon แล้วเช็ก (นี่คือสิ่งที่ init.rc hook จะทำตอนบูต):"
chmod 666 "$N" 2>&1 && echo "chmod 666 OK -> $(ls -l $N)"
chcon u:object_r:sysfs:s0 "$N" 2>&1 && echo "chcon OK" || echo "chcon: (ลอง label อื่นถ้า fail)"'
echo ""
echo ">>> สรุปทางแก้:"
echo "   - ถ้า node ดับจริง (step 3) + ต้อง chmod/chcon (step 2,4) → ทำ init.rc hook ใน /system chmod+chcon ตอนบูต ให้แอพเขียนได้ถาวร"
echo "   - ถ้า controller ดันไฟคืน → FloatingBox guard-tick มีอยู่แล้ว (เขียน 0 ซ้ำ 700ms) จะเอาชนะได้เมื่อเขียนติด"
