#!/usr/bin/env bash
# diag-car.sh — เก็บข้อมูลวินิจฉัยจากจอรถ (อ่านอย่างเดียว ไม่แก้อะไรในรถ)
#   ใช้:  bash _install-bundle/diag-car.sh [IP] [time|net|youtube|sensors|all]
#   ผลลง  _diag/<วันเวลา>/*.txt
# หัวข้อ:
#   time     เวลาเครื่องเทียบคอม, auto_time, โซนเวลา, NTP — เวลาเพี้ยน = สาเหตุยอดฮิตของ YouTube "[400]"
#   net      เส้นทาง/DNS/Private DNS/proxy/อินเทอร์เฟซ (Wi-Fi vs TBox)
#   youtube  เวอร์ชัน, log ล่าสุดที่เกี่ยวกับ HTTP 4xx / DroidGuard / บัญชี
#   sensors  รายการเซ็นเซอร์, ใครเปิดใช้อยู่ (แพ็กเกจ), ค่าเข็มทิศดิบ ~5 วิ
set -u
IP="${1:-172.20.10.3}"; WHAT="${2:-all}"
ADB="${ADB:-C:/Users/Pishe/Downloads/ADB_AppControl/adb/adb.exe}"
case "$IP" in emulator-*|*:*) S="$IP";; *) S="$IP:5555";; esac
OUT="$(cd "$(dirname "$0")/.." && pwd)/_diag/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"
"$ADB" connect "$S" >/dev/null 2>&1
"$ADB" -s "$S" get-state >/dev/null 2>&1 || { echo "!!! ต่อ $S ไม่ได้"; exit 1; }
sh() { "$ADB" -s "$S" shell "$@" 2>&1 | tr -d '\r'; }

if [ "$WHAT" = all ] || [ "$WHAT" = time ]; then
  {
    echo "PC UTC     : $(date -u '+%F %T')"
    echo "car UTC    : $(sh "date -u '+%F %T'")"
    echo "car local  : $(sh date)"
    echo "auto_time=$(sh settings get global auto_time) auto_time_zone=$(sh settings get global auto_time_zone) ntp_server=$(sh settings get global ntp_server)"
    echo "persist.sys.timezone=$(sh getprop persist.sys.timezone)"
    echo "--- NetworkTimeUpdateService / NITZ"
    sh "dumpsys network_time_update_service 2>/dev/null | head -20"
    sh "logcat -d -b all 2>/dev/null | grep -iE 'NtpTrustedTime|NetworkTimeUpdate|setTime|nitz' | tail -20"
  } > "$OUT/time.txt"
  echo "== time"; head -6 "$OUT/time.txt"
fi

if [ "$WHAT" = all ] || [ "$WHAT" = net ]; then
  {
    sh "ip -4 addr | grep -E 'inet |^[0-9]'"
    echo "--- routes"; sh "ip route; ip rule 2>/dev/null | head -20"
    echo "--- dns"; sh "getprop | grep -iE 'dns|net\\.' | head -30"
    echo "private_dns_mode=$(sh settings get global private_dns_mode) spec=$(sh settings get global private_dns_specifier) http_proxy=$(sh settings get global http_proxy)"
    echo "--- connectivity"; sh "dumpsys connectivity | grep -E 'NetworkAgentInfo|Active default network|VALIDATED|CAPTIVE' | head -20"
    echo "--- youtube reachability"; sh "ping -c 2 -W 2 youtubei.googleapis.com 2>&1 | tail -3; ping -c 2 -W 2 www.youtube.com 2>&1 | tail -3"
  } > "$OUT/net.txt"
  echo "== net"; grep -E 'Active default|private_dns|packet loss' "$OUT/net.txt" | head -6
fi

if [ "$WHAT" = all ] || [ "$WHAT" = youtube ]; then
  {
    sh "dumpsys package com.google.android.youtube | grep -E 'versionName|lastUpdateTime|enabled='"
    sh "dumpsys package com.google.android.gms | grep -E 'versionName' | head -2"
    echo "--- log (YouTube/HTTP/DroidGuard/บัญชี)"
    sh "logcat -d -b main -b system -b crash 2>/dev/null | grep -iE 'youtube|droidguard|HttpStatus|status code|http.*4[0-9][0-9]|\\b400\\b|UNAUTHENTICATED|INVALID_ARGUMENT|FAILED_PRECONDITION|GoogleAuthUtil|AccountManager|BadAuthentication' | tail -150"
  } > "$OUT/youtube.txt"
  echo "== youtube"; grep -ciE '400|FAILED_PRECONDITION|droidguard' "$OUT/youtube.txt" | sed 's/^/บรรทัดที่เกี่ยว 400\/DroidGuard: /'
fi

if [ "$WHAT" = all ] || [ "$WHAT" = sensors ]; then
  {
    sh "dumpsys sensorservice"
  } > "$OUT/sensors.txt"
  echo "== sensors"; grep -E 'type: android.sensor.(magnetic|rotation|orientation|geomagnetic)' "$OUT/sensors.txt" | cut -c1-120
  grep -nE 'Active sensors|Connection Number|package|pid=' "$OUT/sensors.txt" | head -30
fi
echo ">>> เก็บไว้ที่ $OUT"
