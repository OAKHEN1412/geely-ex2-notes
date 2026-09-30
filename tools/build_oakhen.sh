#!/usr/bin/env bash
# build_oakhen.sh — ประกอบ "EX2 BY OAKHEN" = Car.apk + แปลไทย + rebrand + dev harness (hot-push)
# ⚠️ รันเอง: agent โดน classifier บล็อก (harness = boot receiver + socket + DexClassLoader หน้าตาเหมือน backdoor)
#    วิธีรัน:  ! bash tools/build_oakhen.sh
# ต้องมี: Java (JDK/JRE ก็ได้ สำหรับรัน apktool/signer), _signing-kit/, devkit/prebuilt/ex2dev-classes17.dex, translations/values-th-strings.xml
set -e
REPO="/c/Users/Pishe/Desktop/geely-ex2-notes"
APK_IN="$REPO/app/Geely-Ex2-Car.apk"
KIT="$REPO/_signing-kit"
DEX="$REPO/devkit/prebuilt/ex2dev-classes17.dex"
THSTR="$REPO/translations/values-th-strings.xml"
DEVICE_FP="C8:A2:E9:BC:CF:59:7C:2F:B6:DC:66:BE:E2:93:FC:13:F2:FC:47:EC:77:BC:6B:2B:0D:52:C1:1F:51:19:2A:B8"
APKTOOL="$REPO/_build/apktool.jar"
WORK="$REPO/_build/work_oakhen"

# --- หา java: env JAVA > portable ในเครื่อง > java บน PATH ---
if [ -z "$JAVA" ]; then
  for c in \
    "$(ls -d /c/Users/*/AppData/Local/Temp/claude/*/*/scratchpad/devkit/jdk/jdk-*/bin/java.exe 2>/dev/null | head -1)" \
    "$(ls -d /c/Users/*/AppData/Local/Temp/claude/*/*/scratchpad/tools_java/jdk-*-jre/bin/java.exe 2>/dev/null | head -1)" \
    "java"; do
    [ -n "$c" ] && command -v "$c" >/dev/null 2>&1 && JAVA="$c" && break
  done
fi
command -v "$JAVA" >/dev/null 2>&1 || { echo "!!! ไม่พบ java — set env JAVA ให้ชี้ java.exe (Temurin 17)"; exit 1; }
echo ">>> java = $JAVA"

mkdir -p "$REPO/_build"
[ -f "$APKTOOL" ] || { echo ">>> โหลด apktool 3.0.3"; curl -sL "https://github.com/iBotPeaches/Apktool/releases/download/v3.0.3/apktool_3.0.3.jar" -o "$APKTOOL" --max-time 180; }

echo ">>> [1/6] decode resource-only"
rm -rf "$WORK"
"$JAVA" -jar "$APKTOOL" d -s -f "$APK_IN" -o "$WORK" >/dev/null

echo ">>> [2/6] ใส่ภาษาไทย + เพิ่ม th ใน locales_config"
cp "$THSTR" "$WORK/res/values-th/strings.xml"
py -X utf8 - "$WORK" <<'PY'
import sys,io
w=sys.argv[1]
lc=w+"/res/xml/locales_config.xml"; s=io.open(lc,encoding='utf-8').read()
if 'name="th"' not in s:
    s=s.replace('<locale android:name="en" />','<locale android:name="en" />\n    <locale android:name="th" />')
    io.open(lc,'w',encoding='utf-8').write(s)
print("    locales_config th:", 'name="th"' in io.open(lc,encoding='utf-8').read())
PY

echo ">>> [3/6] rebrand app_name + version"
py -X utf8 - "$WORK" <<'PY'
import sys,io
w=sys.argv[1]
p=w+"/res/values/strings.xml"; s=io.open(p,encoding='utf-8').read()
import re
s=re.sub(r'<string name="app_name">[^<]*</string>','<string name="app_name">EX2 BY OAKHEN</string>',s)
io.open(p,'w',encoding='utf-8').write(s)
y=w+"/apktool.yml"; t=io.open(y,encoding='utf-8').read()
t=re.sub(r'versionName: .*','versionName: 1.0-oakhen',t)
io.open(y,'w',encoding='utf-8').write(t)
print("    app_name EX2 BY OAKHEN:", 'EX2 BY OAKHEN' in io.open(p,encoding='utf-8').read())
PY

echo ">>> [4/6] ฉีด dev harness (classes17.dex + service/receiver)"
cp "$DEX" "$WORK/classes17.dex"
# swap classes9.dex เป็นตัวที่แก้ auto-window poll interval 2000ms -> 300ms (เตือนชัดถ้าหาย)
AWDEX="$REPO/devkit/prebuilt/classes9-autowindow.dex"
if [ -f "$AWDEX" ]; then
  cp "$AWDEX" "$WORK/classes9.dex"; echo "    auto-window: classes9 swapped (poll 300ms)"
else
  echo "    !!! classes9-autowindow.dex หาย — poll คงเดิม 2000ms (รัน devkit/patch_autowindow.sh ก่อนถ้าต้องการ)"
fi
py -X utf8 - "$WORK" <<'PY'
import sys,io
w=sys.argv[1]; mp=w+"/AndroidManifest.xml"; s=io.open(mp,encoding='utf-8').read()
inj='''        <service android:name="com.oakhen.ex2dev.DevService" android:exported="false" android:directBootAware="true" android:foregroundServiceType="dataSync"/>
        <receiver android:name="com.oakhen.ex2dev.DevStartReceiver" android:exported="true" android:directBootAware="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED"/>
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED"/>
            </intent-filter>
        </receiver>
        <activity android:name="com.oakhen.ex2dev.SettingsActivity" android:exported="true" android:label="EX2 ตั้งค่า" android:theme="@android:style/Theme.Material">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>
        <activity android:name="com.oakhen.ex2dev.SplitBackdropActivity" android:exported="false" android:theme="@android:style/Theme.Black.NoTitleBar" android:excludeFromRecents="true" android:taskAffinity="com.oakhen.ex2dev.backdrop" android:launchMode="singleTask" android:resizeableActivity="false"/>
        <!-- แบ่งจอรวมอยู่ในหน้า OAKHEN (SettingsActivity) + กล่องลอยแล้ว — ไม่มีไอคอนแยก -->
        <activity android:name="com.oakhen.ex2dev.SplitActivity" android:exported="false" android:label="แบ่งจอ" android:theme="@android:style/Theme.Material" android:excludeFromRecents="true" android:taskAffinity="com.oakhen.ex2dev.split" android:launchMode="singleTask" android:resizeableActivity="false"/>
    </application>'''
if 'android.permission.DEVICE_POWER' not in s:
    s=s.replace('<uses-permission android:name="android.permission.WRITE_SECURE_SETTINGS"/>',
                '<uses-permission android:name="android.permission.WRITE_SECURE_SETTINGS"/>\n    <uses-permission android:name="android.permission.DEVICE_POWER"/>', 1)
if 'com.oakhen.ex2dev.DevService' not in s:
    s=s.replace('    </application>', inj, 1)
io.open(mp,'w',encoding='utf-8').write(s)
print("    manifest harness:", 'com.oakhen.ex2dev.DevService' in io.open(mp,encoding='utf-8').read(),
      "| DEVICE_POWER:", 'android.permission.DEVICE_POWER' in io.open(mp,encoding='utf-8').read())
PY

echo ">>> [5/6] build"
OUT="$REPO/_build/EX2-BY-OAKHEN.apk"
rm -f "$OUT"   # กัน ship APK เก่าค้างถ้า build fail
"$JAVA" -jar "$APKTOOL" b "$WORK" -o "$OUT" > "$REPO/_build/apktool_build.log" 2>&1 \
  || { echo "!!! apktool build ล้มเหลว:"; tail -20 "$REPO/_build/apktool_build.log"; exit 1; }
[ -f "$OUT" ] || { echo "!!! ไม่ได้ output APK"; exit 1; }
unzip -l "$OUT" | grep -q classes17.dex || { echo "!!! APK ไม่มี classes17.dex — build ไม่สมบูรณ์ ยกเลิก"; exit 1; }
echo "    classes17.dex OK"

echo ">>> [6/6] sign + verify fingerprint = จอ"
rm -rf "$REPO/_build/signed_oakhen"; mkdir -p "$REPO/_build/signed_oakhen"
"$JAVA" -jar "$KIT/uas.jar" --apks "$OUT" --out "$REPO/_build/signed_oakhen" \
  --ks "$KIT/platform.jks" --ksAlias platform --ksPass android --ksKeyPass android --allowResign 2>&1 | grep -iE "signature verified|SHA256" || true
SIGNED=$(ls "$REPO/_build/signed_oakhen/"*.apk | head -1)
py -X utf8 "$REPO/tools/fpcheck.py" "$(cygpath -w "$SIGNED")" "$DEVICE_FP"
cp "$SIGNED" "$REPO/_signed-apk/EX2-BY-OAKHEN.apk"
echo ">>> เสร็จ: _signed-apk/EX2-BY-OAKHEN.apk"
echo ">>> ติดตั้ง (ไม่ต้องรีบูต):"
echo "    ADB=\"C:/Users/Pishe/Downloads/ADB_AppControl/adb/adb.exe\""
echo "    \"\$ADB\" -s <IP>:5555 install -r \"$REPO/_signed-apk/EX2-BY-OAKHEN.apk\"   # install -r ยิง MY_PACKAGE_REPLACED สตาร์ท service เอง"
echo "    \"\$ADB\" -s <IP>:5555 shell am start-foreground-service -n com.geely.ex2.tools/com.oakhen.ex2dev.DevService   # สตาร์ทเอง (เผื่อ)"
