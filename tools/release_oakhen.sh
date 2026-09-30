#!/usr/bin/env bash
# release_oakhen.sh — build EX2 BY OAKHEN แล้วปล่อยเป็น GitHub Release (รถกด "อัปเดตแอป" แล้วโหลดตัวนี้)
#   ต้องมี: _signing-kit/update-token.txt (fine-grained token ของ repo นี้ · Contents: Read-only) และ gh ที่ล็อกอินแล้ว
#   ต้อง commit ให้เรียบร้อยก่อน (เลข build = จำนวน commit — ห้ามปล่อยจาก working tree ที่ยังไม่ commit)
#   ใช้:  bash tools/release_oakhen.sh "สรุปสั้น ๆ ว่ามีอะไรใหม่"
set -e
REPO="/c/Users/Pishe/Desktop/geely-ex2-notes"
cd "$REPO"
[ -z "$(git status --porcelain -- devkit/src tools)" ] || { echo "!!! มีไฟล์ใน devkit/src หรือ tools ยังไม่ commit"; exit 1; }
[ -s _signing-kit/update-token.txt ] || { echo "!!! ไม่มี _signing-kit/update-token.txt — รถจะอัปเดตเองไม่ได้"; exit 1; }
TC="$REPO/_build/toolchain"
export JAVAC="$TC/jdk-17.0.20.1+1/bin/javac.exe" JAVA="$TC/jdk-17.0.20.1+1/bin/java.exe" R8="$TC/r8.jar" ANDROID_JAR="$TC/android28.jar"
bash devkit/build_harness.sh | tail -1
bash tools/build_oakhen.sh | grep -E "!!!|versionName|เสร็จ"
N=$(git rev-list --count HEAD)
NOTES="${1:-$(git log -1 --pretty=%s)}"
git push -q origin HEAD
gh release create "oakhen-$N" "_signed-apk/EX2-BY-OAKHEN.apk" --title "EX2 BY OAKHEN build $N" --notes "$NOTES" --target "$(git rev-parse HEAD)"
echo ">>> ปล่อย oakhen-$N แล้ว — ในรถ: หน้า OAKHEN → อัปเดตแอป → ตรวจอัปเดต"
