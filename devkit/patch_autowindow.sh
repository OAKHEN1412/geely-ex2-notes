#!/usr/bin/env bash
# patch_autowindow.sh — แก้ค่า auto-window poll interval ใน classes9.dex (smali surgery)
# ค่าเดิม: AUTO_WINDOW_DOOR_POLL_INTERVAL_MS = 2000ms (0x7d0) inline ที่ AutoWindowService.postDelayed
#          -> ทำให้เปิดประตูแล้วรอถึง 2 วิกว่ากระจกจะเริ่มลด
# ค่าใหม่ (ปรับได้ที่ NEW_MS ล่าง): 200ms -> เร็วขึ้น ~10x
# ผลลัพธ์: devkit/prebuilt/classes9-autowindow.dex (build_oakhen.sh จะ swap เข้า APK อัตโนมัติ)
#
# ค่าคงที่อื่นใน VhalConstants (อยากปรับก็แก้ที่ smali8/.../VhalConstants.smali แล้ว reassemble classes8 ด้วย):
#   AUTO_WINDOW_DROP_PERCENT = 0x14 (20%) · AUTO_WINDOW_CLOSE_MAX_PERCENT = 0x1e (30%)
#   AUTO_WINDOW_MAX_SPEED_KMH = 5.0f · AUTO_WINDOW_UI_POLL_INTERVAL_MS = 0xbb8 (3000ms)
# NOTE: ค่าพวกนี้เป็น static final -> ถูก inline ที่จุดใช้ ต้องแก้ที่ const-* ใน service ไม่ใช่ที่ field
set -e
REPO="/c/Users/Pishe/Desktop/geely-ex2-notes"
NEW_HEX="0x12c"  # 300ms — แก้ได้ (0xfa=250, 0x96=150, 0x64=100). ต้องอยู่ในช่วง const-wide/16 (<=0x7fff)
JAVA="${JAVA:-$(ls -d "$REPO"/_build/toolchain/jdk-*/bin/java.exe /c/Users/*/AppData/Local/Temp/claude/*/*/scratchpad/devkit/jdk/jdk-*/bin/java.exe 2>/dev/null | head -1)}"
[ -n "$JAVA" ] && command -v "$JAVA" >/dev/null 2>&1 || { echo "!!! set env JAVA ให้ชี้ java.exe (JDK 17)"; exit 1; }

W="$REPO/_build/autowindow"; mkdir -p "$W"; cd "$W"
echo ">>> โหลด smali/baksmali 2.5.2 + deps (ครั้งแรก)"
declare -A J=(
 [baksmali]="https://repo1.maven.org/maven2/org/smali/baksmali/2.5.2/baksmali-2.5.2.jar"
 [smali]="https://repo1.maven.org/maven2/org/smali/smali/2.5.2/smali-2.5.2.jar"
 [dexlib2]="https://repo1.maven.org/maven2/org/smali/dexlib2/2.5.2/dexlib2-2.5.2.jar"
 [util]="https://repo1.maven.org/maven2/org/smali/util/2.5.2/util-2.5.2.jar"
 [guava]="https://repo1.maven.org/maven2/com/google/guava/guava/32.1.3-jre/guava-32.1.3-jre.jar"
 [jcommander]="https://repo1.maven.org/maven2/com/beust/jcommander/1.82/jcommander-1.82.jar"
 [antlr]="https://repo1.maven.org/maven2/org/antlr/antlr-runtime/3.5.2/antlr-runtime-3.5.2.jar"
)
for k in "${!J[@]}"; do [ -f "$k.jar" ] || curl -sL "${J[$k]}" -o "$k.jar" --max-time 180; done
CP="baksmali.jar;smali.jar;dexlib2.jar;util.jar;guava.jar;jcommander.jar;antlr.jar"

echo ">>> ดึง classes9.dex สดจาก APK เดิม"
unzip -o -j "$REPO/app/Geely-Ex2-Car.apk" classes9.dex >/dev/null

echo ">>> baksmali -> smali9"
rm -rf smali9; "$JAVA" -cp "$CP" org.jf.baksmali.Main d -a 28 classes9.dex -o smali9

echo ">>> แก้ poll interval 0x7d0 -> $NEW_HEX ที่ AutoWindowService"
F="smali9/com/geely/ex2/tools/data/window/AutoWindowService.smali"
# assert: 0x7d0 (=2000ms) ต้องมี "จุดเดียว" ในไฟล์นี้ กัน sed ไป retime constant อื่นโดยไม่ตั้งใจ
N=$(grep -c "const-wide/16 v2, 0x7d0" "$F")
[ "$N" = "1" ] || { echo "!!! คาด 0x7d0 จุดเดียว แต่เจอ $N จุด — หยุด (โครง smali เปลี่ยน/ค่าซ้ำ)"; exit 1; }
sed -i "s/const-wide\/16 v2, 0x7d0/const-wide\/16 v2, $NEW_HEX/" "$F"
grep -q "const-wide/16 v2, $NEW_HEX" "$F" || { echo "!!! ไม่พบจุดแก้หลัง sed"; exit 1; }

echo ">>> ลดกระจกทีละสเต็ป: hook AutoWindowController -> com.oakhen.ex2dev.WindowStepper (อยู่ใน classes17)"
C="smali9/com/geely/ex2/tools/data/window/AutoWindowController.smali"
py -X utf8 - "$(cygpath -w "$W/$C")" <<'PY'
import sys, io
p = sys.argv[1]; s = io.open(p, encoding='utf-8').read()
WS = 'Lcom/geely/ex2/tools/data/window/AutoWindowController;'

# 1) dropWindowOnDoorOpen: ก่อน writeWindow(corner, target) ให้ WindowStepper รับงาน (v4 = % ปัจจุบัน, v3 = target)
old = f'''    :cond_70
    new-instance v1, Ljava/lang/StringBuilder;'''
new = f'''    :cond_70
    iget-object v1, p0, {WS}->doorWindow:Lcom/geely/ex2/tools/data/vhal/CarPropertyDoorWindowController;

    invoke-static {{v1, p1, v4, v3}}, Lcom/oakhen/ex2dev/WindowStepper;->drop(Ljava/lang/Object;Ljava/lang/Object;II)Z

    move-result v1

    if-eqz v1, :cond_ex2_single

    return-void

    :cond_ex2_single
    new-instance v1, Ljava/lang/StringBuilder;'''
i = s.index('.method private final dropWindowOnDoorOpen(')
j = s.index('.end method', i)
body = s[i:j]
assert body.count(old) == 1, 'dropWindowOnDoorOpen: ไม่เจอจุด :cond_70 แบบคาด'
assert 'const/16 v3, 0x14' in body and 'invoke-virtual {v0}, Ljava/lang/Integer;->intValue()I' in body
s = s[:i] + body.replace(old, new) + s[j:]

# 2) closeWindowOnDoorClose: ยกเลิกสเต็ปที่ค้างก่อนเริ่มปิด
i = s.index('.method private final closeWindowOnDoorClose(')
k = s.index('    .line 149', i)
s = s[:k] + '    invoke-static {p1}, Lcom/oakhen/ex2dev/WindowStepper;->cancel(Ljava/lang/Object;)V\n\n' + s[k:]

io.open(p, 'w', encoding='utf-8').write(s)
print('    hook drop:', 'WindowStepper;->drop' in s, '| hook cancel:', 'WindowStepper;->cancel' in s)
PY

echo ">>> smali reassemble -> classes9-autowindow.dex"
"$JAVA" -cp "$CP" org.jf.smali.Main a -a 28 smali9 -o "$REPO/devkit/prebuilt/classes9-autowindow.dex"
echo ">>> เสร็จ: devkit/prebuilt/classes9-autowindow.dex ($(wc -c < "$REPO/devkit/prebuilt/classes9-autowindow.dex") bytes)"
echo ">>> ต่อไป: bash tools/build_oakhen.sh (จะ swap classes9 ให้อัตโนมัติ)"
