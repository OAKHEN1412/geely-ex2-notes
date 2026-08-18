# prep-usb.ps1 — เตรียมแฟลชไดรฟ์สำหรับแพตช์ปลดล็อกจอ Geely EX2
#
# ใช้เมื่อ: โหลดไฟล์ ZIP แพตช์จาก Google Drive มาแล้ว และมี USB ที่ยอมให้เขียนได้
#
#   .\prep-usb.ps1 -Drive E -Zip "C:\Users\Pishe\Downloads\<ไฟล์แพตช์>.zip"
#   .\prep-usb.ps1 -Drive E -Zip "...\patch.zip" -Format     # ฟอร์แมต FAT32 ก่อน (ลบข้อมูลเดิมทั้งหมด)

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][ValidatePattern('^[A-Za-z]$')][string]$Drive,
    [Parameter(Mandatory = $true)][string]$Zip,
    [switch]$Format
)

$ErrorActionPreference = 'Stop'
$Drive = $Drive.ToUpper()
$root  = "${Drive}:\"

function Fail($msg) { Write-Host "[ล้มเหลว] $msg" -ForegroundColor Red; exit 1 }
function Ok($msg)   { Write-Host "[ผ่าน] $msg"   -ForegroundColor Green }
function Info($msg) { Write-Host "  $msg" -ForegroundColor DarkGray }

# --- 1. ตรวจไฟล์ ZIP ---
if (-not (Test-Path -LiteralPath $Zip)) { Fail "ไม่พบไฟล์ ZIP: $Zip" }
Ok "พบไฟล์ ZIP ($([math]::Round((Get-Item -LiteralPath $Zip).Length / 1MB, 1)) MB)"

# --- 2. ตรวจไดรฟ์ปลายทาง ---
$vol = Get-Volume -DriveLetter $Drive -ErrorAction SilentlyContinue
if (-not $vol) { Fail "ไม่พบไดรฟ์ ${Drive}:" }
if ($vol.DriveType -ne 'Removable') {
    Fail "${Drive}: ไม่ใช่ removable drive (เป็น $($vol.DriveType)) — ยกเลิกเพื่อความปลอดภัย"
}

$part = Get-Partition -DriveLetter $Drive
$disk = Get-Disk -Number $part.DiskNumber
if ($disk.IsReadOnly) {
    Fail "ดิสก์ '$($disk.FriendlyName)' ตั้ง read-only ไว้ เขียนไม่ได้ — ต้องใช้ USB ตัวอื่น"
}
Ok "ไดรฟ์ ${Drive}: = $($disk.FriendlyName) / removable / $([math]::Round($vol.Size / 1GB, 2)) GB"

# --- 3. ฟอร์แมต (ถ้าสั่ง) ---
if ($Format) {
    if ($vol.Size -gt 32GB) {
        Fail "ไดรฟ์ใหญ่กว่า 32GB — Windows ฟอร์แมต FAT32 ให้ไม่ได้ ใช้ fat32format GUI แทน (https://ridgecrop.co.uk/index.htm?guiformat.htm)"
    }
    Write-Host "กำลังจะลบข้อมูลทั้งหมดใน ${Drive}: แล้วฟอร์แมตเป็น FAT32" -ForegroundColor Yellow
    if ((Read-Host "พิมพ์ YES เพื่อยืนยัน") -ne 'YES') { Fail "ยกเลิกโดยผู้ใช้" }
    Format-Volume -DriveLetter $Drive -FileSystem FAT32 -NewFileSystemLabel 'GEELY' -Force -Confirm:$false | Out-Null
    Ok "ฟอร์แมตเป็น FAT32 เรียบร้อย"
    $vol = Get-Volume -DriveLetter $Drive
}

if ($vol.FileSystem -ne 'FAT32') {
    Fail "${Drive}: เป็น $($vol.FileSystem) — ต้องเป็น FAT32 (รันซ้ำพร้อม -Format)"
}
Ok "ระบบไฟล์เป็น FAT32"

# --- 4. แตก ZIP ลงราก USB ---
Info "กำลังแตกไฟล์ลง $root ..."
Expand-Archive -LiteralPath $Zip -DestinationPath $root -Force
Ok "แตกไฟล์เสร็จ"

# --- 5. ตรวจโครงสร้างที่ได้ ---
$patchDir = Get-ChildItem -LiteralPath $root -Directory | Where-Object { $_.Name -like '3C6025_*_user_995' }

if (-not $patchDir) {
    Fail "ไม่พบโฟลเดอร์ 3C6025_*_user_995 ที่รากของ USB — ZIP อาจห่อโฟลเดอร์ซ้อนอีกชั้น ให้ย้ายขึ้นมาไว้ที่รากเอง"
}
if ($patchDir.Count -gt 1) {
    Fail "พบโฟลเดอร์แพตช์มากกว่าหนึ่งชุด ($($patchDir.Name -join ', ')) — เก็บไว้ชุดเดียวให้ตรงบิลด์ของรถ"
}
Ok "พบโฟลเดอร์แพตช์: $($patchDir.Name)"

$updateZip = Join-Path $patchDir.FullName 'OS\update.zip'
if (-not (Test-Path -LiteralPath $updateZip -PathType Leaf)) {
    Fail "ไม่พบ $($patchDir.Name)\OS\update.zip — โครงสร้างไม่ถูกต้อง"
}
Ok "พบ OS\update.zip ($([math]::Round((Get-Item -LiteralPath $updateZip).Length / 1MB, 1)) MB) และยังไม่ถูกแตก"

$stray = Get-ChildItem -LiteralPath (Join-Path $patchDir.FullName 'OS') -Directory -ErrorAction SilentlyContinue
if ($stray) {
    Write-Host "[เตือน] มีโฟลเดอร์แปลกปลอมใน OS\ : $($stray.Name -join ', ') — ถ้าเป็นผลจากการแตก update.zip ให้ลบทิ้ง" -ForegroundColor Yellow
}

# --- 6. สรุป ---
Write-Host ''
Write-Host 'โครงสร้างบน USB:' -ForegroundColor Cyan
Write-Host "  $root"
Write-Host "  └── $($patchDir.Name)"
Write-Host "      └── OS"
Write-Host "          └── update.zip"
Write-Host ''

$build = if ($patchDir.Name -match 'H(1111|1114)00000') { $Matches[1] } else { 'ไม่ทราบ' }
Write-Host "USB พร้อมใช้งาน — แพตช์บิลด์ $build" -ForegroundColor Green
Write-Host "ยืนยันอีกครั้งว่าจอรถเป็นบิลด์ $build (ตั้งค่ารถ -> รถของฉัน) ก่อนเสียบเข้ารถ" -ForegroundColor Yellow
