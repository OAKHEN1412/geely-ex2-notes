#!/usr/bin/env bash
# build_plugin.sh — คอมไพล์ plugin (Java) -> .dex พร้อม push เข้า EX2 BY OAKHEN
#   JAVAC=/path/to/javac.exe  bash devkit/build_plugin.sh com/oakhen/plugins/HelloPlugin.java com.oakhen.plugins.HelloPlugin
# ได้ไฟล์ devkit/plugins/out/plugin.dex  ->  push ด้วย:
#   py tools/push_plugin.py <IP> push com.oakhen.plugins.HelloPlugin devkit/plugins/out/plugin.dex "arg"
# ต้องมี: JDK (javac), r8.jar (D8), android28.jar, + Plugin interface (devkit/src)
set -e
REPO="/c/Users/Pishe/Desktop/geely-ex2-notes"
D="$REPO/devkit"
R8="${R8:-$D/prebuilt/r8.jar}"
AJ="${ANDROID_JAR:-$D/prebuilt/android28.jar}"
SRCREL="${1:-com/oakhen/plugins/HelloPlugin.java}"
CLASS="${2:-com.oakhen.plugins.HelloPlugin}"

# หา javac / java
if [ -z "$JAVAC" ]; then JAVAC="$(ls -d /c/Users/*/AppData/Local/Temp/claude/*/scratchpad/devkit/jdk/jdk-*/bin/javac.exe 2>/dev/null | head -1)"; fi
[ -n "$JAVAC" ] && command -v "$JAVAC" >/dev/null 2>&1 || { echo "!!! ต้อง set env JAVAC ให้ชี้ javac.exe (Temurin JDK 17)"; exit 1; }
JAVA="$(dirname "$JAVAC")/java.exe"
[ -f "$R8" ] || { echo "!!! ไม่พบ $R8 — โหลด: curl -L https://maven.google.com/com/android/tools/r8/8.3.37/r8-8.3.37.jar -o $R8"; exit 1; }
[ -f "$AJ" ] || { echo "!!! ไม่พบ $AJ (android28.jar)"; exit 1; }

cd "$D/plugins"
echo ">>> [1/2] javac (plugin + Plugin interface)"
rm -rf build out; mkdir -p build out
"$JAVAC" -encoding UTF-8 -source 8 -target 8 -bootclasspath "$AJ" -cp "$D/src" -d build \
  "$D/src/com/oakhen/ex2dev/Plugin.java" "src/$SRCREL" 2> compile.log || { cat compile.log; exit 1; }
cat compile.log 2>/dev/null || true

echo ">>> [2/2] D8 -> dex (เฉพาะคลาส plugin ไม่รวม interface)"
PKGPATH=$(echo "$CLASS" | tr '.' '/')
"$JAVA" -cp "$R8" com.android.tools.r8.D8 --release --min-api 28 --lib "$AJ" --output out build/$PKGPATH*.class 2> d8.log || { cat d8.log; exit 1; }
mv out/classes.dex out/plugin.dex
echo ">>> เสร็จ: devkit/plugins/out/plugin.dex"
echo "    push:  py $REPO/tools/push_plugin.py <IP> push $CLASS $D/plugins/out/plugin.dex \"hello\""
