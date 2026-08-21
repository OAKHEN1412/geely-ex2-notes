#!/usr/bin/env bash
# build_mod.sh — แกะ Car.apk → แก้ (resource) → build → เซ็น platform key → verify
# ทำในเครื่องล้วน ไม่แตะรถ. 2 เฟส:
#   ./build_mod.sh decode   → แกะ resource ออกมาที่ work/  (แล้วไปแก้ไฟล์ใน work/res/, apktool.yml)
#   ./build_mod.sh package  → build จาก work/ + เซ็น + verify fingerprint = จอ
# ต้องมี: Java 17 (portable ก็ได้), apktool (โหลดอัตโนมัติ), _signing-kit/ (uas.jar + platform.jks)
set -e
REPO="/c/Users/Pishe/Desktop/geely-ex2-notes"
APK_IN="$REPO/app/Geely-Ex2-Car.apk"
KIT="$REPO/_signing-kit"
DEVICE_FP="C8:A2:E9:BC:CF:59:7C:2F:B6:DC:66:BE:E2:93:FC:13:F2:FC:47:EC:77:BC:6B:2B:0D:52:C1:1F:51:19:2A:B8"

# ---- ต้องแก้ให้ตรงเครื่อง: path java 17 (portable Temurin หรือ system) ----
JAVA="${JAVA:-java}"   # ตั้ง env JAVA=... ชี้ java.exe ได้ ถ้าไม่มีใน PATH
command -v "$JAVA" >/dev/null 2>&1 || { echo "!!! ไม่พบ java — ตั้ง env JAVA ให้ชี้ java.exe (Temurin JRE 17)"; exit 1; }

WORK="${WORK:-$REPO/_build/work}"        # ที่แกะ resource (gitignore)
APKTOOL="${APKTOOL:-$REPO/_build/apktool.jar}"
mkdir -p "$(dirname "$WORK")"

get_apktool() {
  [ -f "$APKTOOL" ] && return
  echo ">>> โหลด apktool 3.0.3"
  curl -sL "https://github.com/iBotPeaches/Apktool/releases/download/v3.0.3/apktool_3.0.3.jar" -o "$APKTOOL" --max-time 180
}

case "${1:-}" in
  decode)
    get_apktool
    echo ">>> แกะ resource-only (ข้าม smali = เร็ว) -> $WORK"
    "$JAVA" -jar "$APKTOOL" d -s -f "$APK_IN" -o "$WORK"
    echo ">>> เสร็จ. แก้ไฟล์ได้เลย:"
    echo "    - เปลี่ยนชื่อแอป : $WORK/res/values*/strings.xml  (app_name)"
    echo "    - เพิ่มภาษาไทย   : สร้าง/เติม $WORK/res/values-th/strings.xml (ก๊อป key จาก values-en แล้วแปล)"
    echo "    - เปลี่ยนไอคอน   : $WORK/res/mipmap-*/ic_launcher*.png"
    echo "    - เลข version    : $WORK/apktool.yml (versionName)"
    echo ">>> แก้เสร็จรัน: $0 package"
    ;;
  package)
    get_apktool
    [ -d "$WORK" ] || { echo "!!! ยังไม่ได้แกะ — รัน $0 decode ก่อน"; exit 1; }
    OUT="$REPO/_build/car_mod.apk"
    echo ">>> build จาก $WORK"
    "$JAVA" -jar "$APKTOOL" b "$WORK" -o "$OUT"
    echo ">>> เซ็นด้วย platform key"
    rm -rf "$REPO/_build/signed"; mkdir -p "$REPO/_build/signed"
    "$JAVA" -jar "$KIT/uas.jar" --apks "$OUT" --out "$REPO/_build/signed" \
      --ks "$KIT/platform.jks" --ksAlias platform --ksPass android --ksKeyPass android --allowResign \
      2>&1 | grep -iE "signature verified|SHA256|error|fail" || true
    SIGNED=$(ls "$REPO/_build/signed/"*.apk | head -1)
    echo ">>> verify fingerprint = จอ"
    py -X utf8 "$REPO/tools/fpcheck.py" "$(cygpath -w "$SIGNED")" "$DEVICE_FP"
    echo ">>> พร้อม push: $SIGNED"
    echo "    ต่อไป: push เข้า /system เหมือน push_signed.sh (แก้ SIGNED path ให้ชี้ตัวนี้)"
    ;;
  *)
    echo "ใช้: $0 decode   (แกะ+ให้แก้)   |   $0 package   (build+เซ็น+verify)"; exit 1 ;;
esac
