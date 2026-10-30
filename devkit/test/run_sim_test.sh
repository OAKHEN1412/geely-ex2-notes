#!/usr/bin/env bash
# ทดสอบ SimCar (Java ล้วน) + TripState ที่ป้อนจากรถจำลอง บนคอม — ไม่ต้องมีรถ/เครื่องจำลอง
set -e
R="${REPO:-$(cd "$(dirname "$0")/../.." && pwd)}"
J="$R/_build/toolchain/jdk-17.0.20.1+1/bin"
[ -e "$J/javac.exe" ] || J="$(dirname "$(command -v javac)")"   # สำรอง: JDK ใน PATH
JC="$J/javac"; [ -e "$J/javac.exe" ] && JC="$J/javac.exe"
JV="$J/java"; [ -e "$J/java.exe" ] && JV="$J/java.exe"
W="$R/_build/test_sim"; rm -rf "$W"; mkdir -p "$W"
S="$R/devkit/src/com/oakhen/ex2dev"
"$JC" -encoding UTF-8 --release 8 -d "$W" "$S/SimCar.java" "$S/TripState.java" "$R/devkit/test/com/oakhen/ex2dev/SimCarTest.java"
"$JV" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dsrc="$S" -cp "$W" com.oakhen.ex2dev.SimCarTest
