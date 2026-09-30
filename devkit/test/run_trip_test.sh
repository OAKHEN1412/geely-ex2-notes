#!/usr/bin/env bash
# ทดสอบ TripState (Java ล้วน) บนคอม — ไม่ต้องมีรถ/เครื่องจำลอง
set -e
R="/c/Users/Pishe/Desktop/geely-ex2-notes"
J="$R/_build/toolchain/jdk-17.0.20.1+1/bin"
W="$R/_build/test"; rm -rf "$W"; mkdir -p "$W"
"$J/javac.exe" -encoding UTF-8 -d "$W" "$R/devkit/src/com/oakhen/ex2dev/TripState.java" "$R/devkit/test/com/oakhen/ex2dev/TripStateTest.java"
"$J/java.exe" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$W" com.oakhen.ex2dev.TripStateTest
