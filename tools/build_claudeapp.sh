#!/usr/bin/env bash
# build_claudeapp.sh — สร้างแอป ClaudeWeb (WebView ห่อ claude.ai + ไมค์ + ไอคอน/splash/หน้า offline) จากศูนย์ ไม่ใช้ gradle
# รันเอง:  ! bash tools/build_claudeapp.sh
# ใช้ toolchain: aapt2 (Google Maven) + javac(JDK17) + d8(r8.jar) + android28.jar + platform key
# ได้: _apps/ClaudeWeb.apk
set -e
REPO="/c/Users/Pishe/Desktop/geely-ex2-notes"
KIT="$REPO/_signing-kit"
RES="$REPO/claudeapp/res"
MANIFEST="$REPO/claudeapp/AndroidManifest.xml"

# --- toolchain (auto-detect จาก scratchpad devkit; ไม่เจอให้ set path เอง) ---
DEV="$(ls -d /c/Users/*/AppData/Local/Temp/claude/*/*/scratchpad/devkit 2>/dev/null | head -1)"
JBIN="$(dirname "$(ls -d "$DEV"/jdk/jdk-*/bin/javac.exe 2>/dev/null | head -1)")"
AJ="$DEV/android28.jar"; R8="$DEV/r8.jar"
[ -f "$JBIN/javac.exe" ] && [ -f "$AJ" ] && [ -f "$R8" ] || { echo "!!! ไม่พบ toolchain (javac/android28.jar/r8.jar) ใน devkit — set path เอง"; exit 1; }
JAVA="$JBIN/java.exe"

W="$REPO/_build/claudeapp"; rm -rf "$W"; mkdir -p "$W"; cd "$W"

echo ">>> [1/6] เอา aapt2 (Google Maven, windows)"
AV="$(curl -s --max-time 30 'https://dl.google.com/dl/android/maven2/com/android/tools/build/aapt2/maven-metadata.xml' | grep -oE '<release>[^<]+' | sed 's/<release>//')"
[ -n "$AV" ] || AV="8.5.0-11315950"
curl -sL "https://dl.google.com/dl/android/maven2/com/android/tools/build/aapt2/$AV/aapt2-$AV-windows.jar" -o aapt2.jar --max-time 180
unzip -o aapt2.jar aapt2.exe >/dev/null
AAPT2="$W/aapt2.exe"

echo ">>> [2/6] aapt2 compile res/ (ไอคอน/สี/ธีม/splash) -> res.zip"
"$AAPT2" compile --dir "$RES" -o res.zip

echo ">>> [3/6] aapt2 link -> base.apk + gen R.java"
mkdir -p gen
"$AAPT2" link -o base.apk -I "$AJ" \
  --manifest "$MANIFEST" \
  --java gen \
  --min-sdk-version 23 --target-sdk-version 28 \
  res.zip

echo ">>> [4/6] javac (MainActivity + R.java) -> d8 -> classes.dex"
rm -rf classes; mkdir classes
"$JBIN/javac.exe" -encoding UTF-8 -source 8 -target 8 -bootclasspath "$AJ" -d classes \
  "$REPO/claudeapp/src/com/oakhen/claudeweb/MainActivity.java" \
  gen/com/oakhen/claudeweb/R.java
"$JAVA" -cp "$R8" com.android.tools.r8.D8 --release --min-api 23 --lib "$AJ" \
  --output . $(find classes -name '*.class')

echo ">>> [5/6] ยัด classes.dex เข้า base.apk"
"$JBIN/jar.exe" uf base.apk classes.dex

echo ">>> [6/6] เซ็น (platform key) + verify"
rm -rf signed; mkdir signed
"$JAVA" -jar "$KIT/uas.jar" --apks base.apk --out signed \
  --ks "$KIT/platform.jks" --ksAlias platform --ksPass android --ksKeyPass android --allowResign \
  2>&1 | grep -iE 'signature verified|SHA256' || true
SIGNED="$(ls signed/*.apk | head -1)"
mkdir -p "$REPO/_apps"
cp "$SIGNED" "$REPO/_apps/ClaudeWeb.apk"
echo ">>> เสร็จ: _apps/ClaudeWeb.apk"
echo ">>> ลง:  adb -s <IP>:5555 install -r _apps/ClaudeWeb.apk"
echo ">>>      adb -s <IP>:5555 shell pm grant com.oakhen.claudeweb android.permission.RECORD_AUDIO"
echo ">>>      adb -s <IP>:5555 shell am start -n com.oakhen.claudeweb/.MainActivity"
echo ">>> เช็ค WebView เก่าไหมก่อนใช้ voice:  adb shell dumpsys webviewupdate | grep -iE 'Current WebView package|version'"
