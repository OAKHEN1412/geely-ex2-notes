#!/usr/bin/env bash
# measure-app.sh — วัดอาการกระตุกของแอพบนจอรถ (เฟรม/CPU/GPU/อุณหภูมิ/หน่วยความจำ)
#   ! bash "C:/Users/Pishe/Desktop/geely-ex2-notes/_install-bundle/measure-app.sh" com.waze [วินาที=30] [IP]
# ระหว่างวัด ให้ใช้แอพตามปกติบนจอ (เช่น เลื่อน/ซูมแผนที่ หรือขับตามเส้นทาง) — ค่าที่ได้คือช่วงที่ใช้งานจริง
PKG="${1:-com.waze}"; SEC="${2:-30}"; D="${3:-172.20.10.3}:5555"
ADB="/c/Users/Pishe/Downloads/ADB_AppControl/adb/adb.exe"
export MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*'
S(){ "$ADB" -s "$D" shell "$@" | tr -d '\r'; }

"$ADB" connect "$D" >/dev/null 2>&1
[ "$("$ADB" -s "$D" get-state 2>/dev/null)" = "device" ] || { echo "!!! ต่อจอไม่ได้"; exit 1; }

S "monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1"; sleep 6
echo "== หน้าที่เปิดอยู่: $(S 'dumpsys activity activities | grep -m1 mResumedActivity')"
echo "== โหมดหน้าต่าง (fullscreen/freeform=แบ่งจอ): $(S "dumpsys activity activities" | awk -v p="$PKG/" '/^  Stack #/{m=$0} index($0,p){sub(/.*mode=/,"",m); sub(/ .*/,"",m); print m; exit}')"
echo ">>> ใช้แอพบนจอตามปกติ $SEC วินาที ..."
S "dumpsys gfxinfo $PKG reset >/dev/null"
P=$(S "pidof $PKG" | awk '{print $1}')
A1=$(S "cut -d' ' -f14,15 /proc/$P/stat"); T1=$(S "head -1 /proc/stat")
S "top -b -n 1 -m 8 -s 2 -o PID,%CPU,NAME" > /tmp/top1.txt
sleep "$SEC"
A2=$(S "cut -d' ' -f14,15 /proc/$P/stat"); T2=$(S "head -1 /proc/stat")

py -X utf8 - "$A1" "$A2" "$T1" "$T2" "$SEC" <<'PY'
import sys
a1=sum(map(int,sys.argv[1].split())); a2=sum(map(int,sys.argv[2].split()))
t1=list(map(int,sys.argv[3].split()[1:])); t2=list(map(int,sys.argv[4].split()[1:])); sec=float(sys.argv[5])
tot=sum(t2)-sum(t1); idle=(t2[3]+t2[4])-(t1[3]+t1[4])
print(f"== CPU แอพ: {(a2-a1)/sec:.0f}% ของ 1 core  |  ทั้งเครื่องใช้ {100*(tot-idle)/tot:.0f}% (8 core)")
PY
echo "== เฟรม (ช่วงที่ใช้งาน)"
S "dumpsys gfxinfo $PKG" | grep -E "Total frames|Janky|50th|90th|99th|Slow UI|Slow bitmap|Slow issue|Frame deadline|High input"
echo "== GPU"
S "cat /proc/gpufreq/gpufreq_var_dump 2>/dev/null | head -4; cat /sys/kernel/debug/ged/hal/gpu_utilization 2>/dev/null"
echo "== อุณหภูมิ (°C x1000)"
S 'for z in /sys/class/thermal/thermal_zone*; do t=$(cat $z/temp 2>/dev/null); [ "$t" -gt 0 ] 2>/dev/null && printf "%s=%s " $(cat $z/type) $t; done; echo'
echo "== CPU freq (ถ้าต่ำกว่า max มาก = ถูกลดความเร็วเพราะร้อน)"
S 'for c in 0 4 6; do f=/sys/devices/system/cpu/cpu$c/cpufreq; [ -d $f ] && echo "cpu$c: $(cat $f/scaling_cur_freq)/$(cat $f/cpuinfo_max_freq)"; done'
echo "== หน่วยความจำแอพ"
S "dumpsys meminfo $PKG" | grep -E "TOTAL|Graphics|GL mtrack|Native Heap" | head -5
echo "== แอพอื่นที่ใช้ CPU (ต้นช่วง)"
grep -vE "^\s*$|Tasks|Mem|Swap|cpu" /tmp/top1.txt | head -9
