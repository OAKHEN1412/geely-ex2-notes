#!/usr/bin/env bash
# get-gsf-id.sh — ดึง GSF ID (android_id) ของจอ ไปลงทะเบียนที่ https://www.google.com/android/uncertified
#   ! bash "C:/Users/Pishe/Desktop/geely-ex2-notes/_install-bundle/gapps/get-gsf-id.sh" [IP]
# ใช้ได้หลังลง GApps + รีบูต + เปิด Play Store / ต่อเน็ตแล้วครั้งหนึ่ง (gservices.db ถึงจะถูกสร้าง)
ADB="/c/Users/Pishe/Downloads/ADB_AppControl/adb/adb.exe"
D="${1:-172.20.10.3}:5555"
export MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*'
S(){ "$ADB" -s "$D" shell "$@" | tr -d '\r'; }

"$ADB" connect "$D" >/dev/null; "$ADB" -s "$D" root >/dev/null 2>&1 || true; sleep 2; "$ADB" connect "$D" >/dev/null 2>&1 || true
[ "$("$ADB" -s "$D" get-state 2>/dev/null)" = "device" ] || { echo "!!! ต่อจอไม่ได้ — เปิดปุ่ม ADB ใน Dialer"; exit 1; }

DB=/data/data/com.google.android.gsf/databases/gservices.db
[ -n "$(S "ls $DB 2>/dev/null")" ] || { echo "!!! ยังไม่มี $DB — เปิด Play Store + ต่อเน็ตก่อน แล้วรันใหม่"; exit 1; }

ID=""
# ทาง 1: sqlite3 บนจอ (ถ้ามี)
# (เช็กจาก "ข้อความ" ไม่ใช่ exit code — S ผ่าน pipe | tr ทำให้ exit code เป็นของ tr เสมอ)
[ -n "$(S "command -v sqlite3")" ] && ID=$(S "sqlite3 $DB \"select value from main where name='android_id';\"")
# ทาง 2: ไม่มี sqlite3 บนจอ -> ดึงไฟล์มาอ่านด้วย Python บนคอม
if [ -z "$ID" ]; then
  TMP="$(cygpath -w "${TMPDIR:-/tmp}")\\gservices.db"
  S "cp $DB /data/local/tmp/gservices.db; chmod 644 /data/local/tmp/gservices.db"
  "$ADB" -s "$D" pull /data/local/tmp/gservices.db "$TMP" >/dev/null && S "rm /data/local/tmp/gservices.db"
  ID=$(py -X utf8 -c "import sqlite3,sys;r=sqlite3.connect(sys.argv[1]).execute(\"select value from main where name='android_id'\").fetchone();print(r[0] if r else '')" "$TMP")
fi

[ -n "$ID" ] || { echo "!!! ยังไม่มี android_id — เปิด Play Store รอ 1-2 นาทีแล้วรันใหม่"; exit 1; }
echo "GSF ID (เลขฐาน 10 — ใส่อันนี้ในเว็บ): $ID"
echo "เปิด https://www.google.com/android/uncertified -> ล็อกอินบัญชีเดียวกับในรถ -> วางเลขนี้ -> Register"
