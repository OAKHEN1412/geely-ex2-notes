# install.ps1 — ลงแอพเข้าจอ Geely EX2 (IHU629G / Android 9) ผ่าน ADB Wi-Fi
#
# ก่อนรัน: ที่จอรถ เปิดปุ่ม ADB ใน Dialer + ต่อ Wi-Fi วงเดียวกับคอม
#
#   .\install.ps1                        # ลงครบ (EX2 BY OAKHEN + SmartTube)
#   .\install.ps1 -Ip 172.20.10.5        # IP จอเปลี่ยน
#   .\install.ps1 -Only oakhen           # ลงเฉพาะตัวเดียว (oakhen | smarttube)

[CmdletBinding()]
param(
    [string]$Ip = '172.20.10.3',
    [ValidateSet('all', 'oakhen', 'smarttube')][string]$Only = 'all',
    [string]$Adb = 'C:\Users\Pishe\Downloads\ADB_AppControl\adb\adb.exe'
)

$ErrorActionPreference = 'Stop'
$here  = Split-Path -Parent $MyInvocation.MyCommand.Path
$apks  = Join-Path $here 'apks'
$dev   = "${Ip}:5555"

function Fail($msg) { Write-Host "[ล้มเหลว] $msg" -ForegroundColor Red; exit 1 }
function Ok($msg)   { Write-Host "[ผ่าน] $msg"   -ForegroundColor Green }
function Info($msg) { Write-Host "  $msg" -ForegroundColor DarkGray }
# PS 5.1 + ErrorActionPreference=Stop: stderr ของ adb ที่ redirect 2>&1 = โยน exception ทันที (ทดสอบแล้ว)
# -> ใน A ใช้ Continue แล้วแปลงทุกบรรทัด (stdout/stderr) เป็นข้อความ ให้สคริปต์ตัดสินเองจากเนื้อหา
function A { $ErrorActionPreference = 'Continue'; ((& $Adb -s $dev @args 2>&1) | ForEach-Object { "$_" }) -join "`n" }

$apps = @(@(
    @{ key = 'oakhen';  file = 'EX2-BY-OAKHEN.apk';                     pkg = 'com.geely.ex2.tools';     flags = @('-r') },
    @{ key = 'smarttube'; file = 'SmartTube_stable_32.56_arm64-v8a.apk'; pkg = 'org.smarttube.stable';    flags = @('-r', '-g') }
) | Where-Object { $Only -eq 'all' -or $_.key -eq $Only })   # @() กัน .Count ของ hashtable เดี่ยว

# --- 1. ตรวจไฟล์ ---
if (-not (Test-Path -LiteralPath $Adb)) { Fail "ไม่พบ adb: $Adb" }
foreach ($a in $apps) {
    $p = Join-Path $apks $a.file
    if (-not (Test-Path -LiteralPath $p)) { Fail "ไม่พบ $($a.file) ใน apks\" }
}
Ok "ไฟล์ครบ $($apps.Count) แอพ"

# --- 2. ต่อจอ ---
A connect $dev | Out-Null   # (A ใส่ -s ให้ ไม่เป็นไร: adb connect ไม่สน -s)
$state = (A get-state).Trim()
if ($state -ne 'device') {
    Fail "ต่อ $dev ไม่ได้ ($state) — เปิดปุ่ม ADB ใน Dialer ที่จอใหม่ / เช็ก IP (ตั้งค่า > Wi-Fi) แล้วรันซ้ำพร้อม -Ip"
}
$model = (A shell getprop ro.product.model).Trim()
$sdk   = (A shell getprop ro.build.version.sdk).Trim()
Ok "ต่อจอแล้ว: $dev  ($model / SDK $sdk)"

# --- 3. ลงแอพ ---
foreach ($a in $apps) {
    $p = Join-Path $apks $a.file
    Info "กำลังลง $($a.file) ..."
    $out = A install @($a.flags) $p
    if ($out -notmatch 'Success') {
        if ($a.key -eq 'oakhen' -and $out -match 'INSTALL_FAILED_(UPDATE_INCOMPATIBLE|SHARED_USER_INCOMPATIBLE)') {
            Fail "EX2 BY OAKHEN ลายเซ็นไม่ตรงตัวในจอ — ต้องลงแบบ system (docs/06-install-app-system-signed.md)`n$out"
        }
        Fail "ลง $($a.file) ไม่ผ่าน:`n$out"
    }
    Ok "ลง $($a.pkg) สำเร็จ"
}

# --- 4. หลังลง ---
# Wi-Fi เปิดไว้ + จำข้ามรีบูต (แอพ EX2 BY OAKHEN จะคอยเปิดคืน + สแกนหาเครือข่ายเดิมทุกครั้งที่สตาร์ทรถ)
A shell svc wifi enable | Out-Null
A shell settings put global wifi_scan_always_enabled 1 | Out-Null
$wifi = (A shell settings get global wifi_on).Trim()
if ($wifi -ne '0') { Ok "Wi-Fi เปิดอยู่ (wifi_on=$wifi)" } else { Write-Host '[เตือน] Wi-Fi ยังปิด — เปิดเองที่หน้าตั้งค่า' -ForegroundColor Yellow }
if ($apps.key -contains 'oakhen') {
    A shell am start-foreground-service -n com.geely.ex2.tools/com.oakhen.ex2dev.DevService | Out-Null
    $svc = A shell "dumpsys activity services com.oakhen.ex2dev.DevService | grep -c ServiceRecord"
    if ($svc.Trim() -ne '0') { Ok 'DevService (กล่องลอย) ทำงานแล้ว' } else { Write-Host '[เตือน] DevService ยังไม่ขึ้น — เปิดแอพ "EX2 ตั้งค่า" บนจอแล้วเปิดสวิตช์กล่องลอย' -ForegroundColor Yellow }
}
if ($apps.key -contains 'smarttube') {
    A shell monkey -p org.smarttube.stable -c android.intent.category.LAUNCHER 1 | Out-Null
    Ok 'เปิด SmartTube บนจอแล้ว'
}

# --- 5. สรุปเวอร์ชันที่อยู่ในจอ ---
Write-Host ''
foreach ($a in $apps) {
    $v = (A shell "dumpsys package $($a.pkg) | grep -m1 versionName").Trim()
    Write-Host "  $($a.pkg)  $v" -ForegroundColor Cyan
}
Write-Host ''
Write-Host 'เสร็จ' -ForegroundColor Green
