#!/usr/bin/env bash
# เซ็น Geely-Ex2-Car.apk ใหม่ด้วยกุญแจ AOSP platform สาธารณะ (ให้ตรง platform key ของจอ)
# ทำในเครื่องล้วน ไม่แตะรถ. มีตัวกันพลาด: ถ้ากุญแจ fingerprint ไม่ตรงจอ = abort
set -e
cd "/c/Users/Pishe/AppData/Local/Temp/claude/C--Users-Pishe/3bbe1848-8919-483c-b3c5-f1b0e6b3a560/scratchpad/tools_java"
JAVA="$PWD/jdk-17.0.20+8-jre/bin/java.exe"
KEYTOOL="$PWD/jdk-17.0.20+8-jre/bin/keytool.exe"
APK_IN="C:/Users/Pishe/Desktop/geely-ex2-notes/app/Geely-Ex2-Car.apk"
DEVICE_FP="C8:A2:E9:BC:CF:59:7C:2F:B6:DC:66:BE:E2:93:FC:13:F2:FC:47:EC:77:BC:6B:2B:0D:52:C1:1F:51:19:2A:B8"

echo ">>> [1/5] โหลด uber-apk-signer + AOSP platform key"
curl -sL "https://github.com/patrickfav/uber-apk-signer/releases/download/v1.3.0/uber-apk-signer-1.3.0.jar" -o uas.jar --max-time 180
curl -sL "https://raw.githubusercontent.com/aosp-mirror/platform_build/main/target/product/security/platform.pk8" -o platform.pk8 --max-time 120
curl -sL "https://raw.githubusercontent.com/aosp-mirror/platform_build/main/target/product/security/platform.x509.pem" -o platform.x509.pem --max-time 120
echo "    uas.jar=$(du -h uas.jar|cut -f1)  pk8=$(wc -c <platform.pk8)B  pem=$(wc -c <platform.x509.pem)B"

echo ">>> [2/5] ตรวจ fingerprint กุญแจที่โหลด ต้องตรงกับ platform key ของจอ"
FP=$(openssl x509 -in platform.x509.pem -noout -fingerprint -sha256 | sed 's/.*=//')
echo "    downloaded : $FP"
echo "    device     : $DEVICE_FP"
if [ "$FP" != "$DEVICE_FP" ]; then echo "!!! MISMATCH — กุญแจไม่ตรงจอ ยกเลิก"; exit 1; fi
echo "    [ok] กุญแจตรงจอ 100%"

echo ">>> [3/5] แปลงกุญแจ -> keystore"
openssl pkcs8 -inform DER -nocrypt -in platform.pk8 -out platform.key.pem 2>/dev/null
openssl pkcs12 -export -in platform.x509.pem -inkey platform.key.pem -out platform.p12 -name platform -passout pass:android 2>/dev/null
rm -f platform.jks
"$KEYTOOL" -importkeystore -srckeystore platform.p12 -srcstoretype PKCS12 -srcstorepass android \
  -destkeystore platform.jks -deststoretype JKS -deststorepass android -srcalias platform -destalias platform -noprompt 2>/dev/null
echo "    [ok] platform.jks"

echo ">>> [4/5] เซ็น APK ใหม่ (uber-apk-signer: zipalign + v1/v2/v3)"
rm -rf signed && mkdir -p signed
cp "$APK_IN" ./Geely-Ex2-Car.apk
"$JAVA" -jar uas.jar --apks ./Geely-Ex2-Car.apk --out signed \
  --ks platform.jks --ksAlias platform --ksPass android --ksKeyPass android \
  --allowResign 2>&1 | grep -iE "signed|verified|zipalign|cert|sha256|error|fail" | head -20 || true
SIGNED=$(ls signed/*.apk 2>/dev/null | head -1)
echo "    output: $SIGNED"

echo ">>> [5/5] ตรวจ cert ของ APK ที่เซ็นเสร็จ (ต้อง = platform key ของจอ)"
cd ..
py -X utf8 fpcheck.py "$(cygpath -w "$PWD/tools_java/$SIGNED" 2>/dev/null || echo "tools_java/$SIGNED")" "$DEVICE_FP"
echo ">>> DONE — ยังไม่ push อะไรขึ้นรถ รอยืนยัน"
