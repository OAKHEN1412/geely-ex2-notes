#!/usr/bin/env bash
# ทดสอบ ApkSource (รายการเวอร์ชัน APKPure + อ่าน Android ขั้นต่ำผ่าน Range) บนคอม — ต้องมีเน็ต
set -e
R="/c/Users/Pishe/Desktop/geely-ex2-notes"
J="$R/_build/toolchain/jdk-17.0.20.1+1/bin"
W="$R/_build/test-update"; rm -rf "$W"; mkdir -p "$W"
"$J/javac.exe" -encoding UTF-8 -d "$W" "$R/devkit/src/com/oakhen/ex2dev/ApkSource.java" "$R/devkit/test/com/oakhen/ex2dev/ApkSourceTest.java"
"$J/java.exe" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$W" com.oakhen.ex2dev.ApkSourceTest "$@"
