#!/usr/bin/env bash
# build_harness.sh — คอมไพล์ dev harness ทั้งหมด (devkit/src/com/oakhen/ex2dev/*.java) -> ex2dev-classes17.dex
#   รันเมื่อ "แก้โค้ด harness" (SettingsActivity / FloatingBox / DevService / Prefs ฯลฯ) แล้วต้องประกอบ APK ใหม่
#   ใช้:  JAVAC=/path/to/javac.exe  bash devkit/build_harness.sh
#         (ไม่ set JAVAC จะลอง auto-detect JDK ใน scratchpad devkit)
# ได้:  devkit/prebuilt/ex2dev-classes17.dex   ->  จากนั้น  ! bash tools/build_oakhen.sh  เพื่อฉีดเข้า APK
# ต้องมี: JDK17 (javac), r8.jar (D8), android28.jar
set -e
REPO="${REPO:-$(cd "$(dirname "$0")/.." && pwd)}"   # หาจากที่อยู่สคริปต์ (ย้ายเครื่องได้) · override ด้วย env REPO
D="$REPO/devkit"
SRC="$D/src/com/oakhen/ex2dev"
OUTDEX="$D/prebuilt/ex2dev-classes17.dex"
R8="${R8:-$D/prebuilt/r8.jar}"
AJ="${ANDROID_JAR:-$D/prebuilt/android28.jar}"

# --- หา javac / java ---
if [ -z "$JAVAC" ]; then
  JAVAC="$(ls -d /c/Users/*/AppData/Local/Temp/claude/*/*/scratchpad/devkit/jdk/jdk-*/bin/javac.exe 2>/dev/null | head -1)"
fi
[ -n "$JAVAC" ] || JAVAC="$(command -v javac 2>/dev/null)"   # สำรอง: javac ใน PATH
[ -n "$JAVAC" ] && command -v "$JAVAC" >/dev/null 2>&1 || { echo "!!! ต้อง set env JAVAC ให้ชี้ javac.exe (Temurin JDK 17)"; exit 1; }
JAVA="$(dirname "$JAVAC")/java.exe"
[ -f "$R8" ] || { echo "!!! ไม่พบ $R8 — โหลด: curl -L https://maven.google.com/com/android/tools/r8/8.3.37/r8-8.3.37.jar -o $R8"; exit 1; }
[ -f "$AJ" ] || { echo "!!! ไม่พบ $AJ (android28.jar) — โหลด: curl -L https://raw.githubusercontent.com/Sable/android-platforms/master/android-28/android.jar -o $AJ"; exit 1; }

W="$REPO/_build/harness"; rm -rf "$W"; mkdir -p "$W/build"

echo ">>> [1/3] javac (ทุกไฟล์ใน com/oakhen/ex2dev/)"
"$JAVAC" -encoding UTF-8 -source 8 -target 8 -bootclasspath "$AJ" -d "$W/build" \
  "$SRC"/*.java 2> "$W/compile.log" || { echo "!!! javac ล้มเหลว:"; cat "$W/compile.log"; exit 1; }
cat "$W/compile.log" 2>/dev/null || true

echo ">>> [2/3] D8 -> classes.dex (min-api 26)"
"$JAVA" -cp "$R8" com.android.tools.r8.D8 --release --min-api 26 --lib "$AJ" \
  --output "$W" $(find "$W/build" -name '*.class') 2> "$W/d8.log" || { echo "!!! D8 ล้มเหลว:"; cat "$W/d8.log"; exit 1; }

echo ">>> [3/3] ติดตั้งเป็น prebuilt"
mkdir -p "$D/prebuilt"
cp "$W/classes.dex" "$OUTDEX"
echo ">>> เสร็จ: $OUTDEX ($(stat -c%s "$OUTDEX" 2>/dev/null || wc -c < "$OUTDEX") bytes)"
echo ">>> ถัดไป:  ! bash tools/build_oakhen.sh   # ฉีด dex ใหม่เข้า EX2 BY OAKHEN แล้วเซ็น"
