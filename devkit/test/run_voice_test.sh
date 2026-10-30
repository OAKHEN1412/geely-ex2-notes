#!/usr/bin/env bash
# ทดสอบ VoiceIntent (Java ล้วน) บนคอม — ไม่ต้องมีรถ/เครื่องจำลอง/android.jar
#   ใช้:  bash devkit/test/run_voice_test.sh      (ตั้ง JDK_BIN=/path/to/jdk/bin ได้ถ้าไม่ได้ใช้ JDK ใน _build/toolchain)
set -e
R="${REPO:-$(cd "$(dirname "$0")/../.." && pwd)}"
J="${JDK_BIN:-$R/_build/toolchain/jdk-17.0.20.1+1/bin}"
if [ ! -x "$J/javac.exe" ] && [ ! -x "$J/javac" ]; then J="$(dirname "$(command -v javac)")"; fi
JAVAC="$J/javac"; JAVA="$J/java"
[ -x "$J/javac.exe" ] && JAVAC="$J/javac.exe" && JAVA="$J/java.exe"
W="$R/_build/test_voice"; rm -rf "$W"; mkdir -p "$W"
"$JAVAC" -encoding UTF-8 -d "$W" "$R/devkit/src/com/oakhen/ex2dev/VoiceIntent.java" "$R/devkit/test/com/oakhen/ex2dev/VoiceIntentTest.java"
"$JAVA" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$W" com.oakhen.ex2dev.VoiceIntentTest
