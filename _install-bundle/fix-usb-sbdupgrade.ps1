# fix-usb-sbdupgrade.ps1 — เพิ่ม update.zip ใน <USB>\sbdUpgrade\OS\ ตามที่จอรถคันใหม่ขอ
# ที่มา: กดไอคอนเขียวใน Engineering Mode แล้วจอขึ้น
#   "ensure udisk plugged and update.zip exists under udisk \sbdUpgrade\OS directory"
# update.zip ตัวเดียวกัน (สคริปต์ข้างในเช็กแค่ ro.product.device == IHU629G) · ไม่ลบของเดิมบน USB
#   powershell -ExecutionPolicy Bypass -File fix-usb-sbdupgrade.ps1 [-Drive E]
param([string]$Drive)
$ErrorActionPreference = 'Stop'
$zip = 'C:\Users\Pishe\Desktop\geely-ex2-notes\_patches\patch-1114.zip'
$entry = '3C6025_SW0E22H0306H111400000_user_995/OS/update.zip'
$want = 'f55ae666a671aeb22665f5c149c6a3fe5b32b12b7a6c6fa850ea177a552f4cc4'

if (-not $Drive) {
    $v = @(Get-Volume | Where-Object { $_.DriveType -eq 'Removable' -and $_.DriveLetter })
    if ($v.Count -ne 1) { Write-Host "[ล้มเหลว] เจอ USB $($v.Count) ตัว — ระบุ -Drive <ตัวอักษร>" -ForegroundColor Red; exit 1 }
    $Drive = [string]$v[0].DriveLetter
}
$vol = Get-Volume -DriveLetter $Drive
if ($vol.DriveType -ne 'Removable') { Write-Host "[ล้มเหลว] ${Drive}: ไม่ใช่ USB" -ForegroundColor Red; exit 1 }
if ($vol.FileSystem -ne 'FAT32') { Write-Host "[ล้มเหลว] ${Drive}: เป็น $($vol.FileSystem) ต้องเป็น FAT32" -ForegroundColor Red; exit 1 }

Add-Type -AssemblyName System.IO.Compression.FileSystem
$z = [IO.Compression.ZipFile]::OpenRead($zip)
try {
    $e = $z.Entries | Where-Object FullName -eq $entry
    foreach ($d in 'sbdUpgrade', 'JsbdUpgrade') {   # รูปถ่ายอ่านได้ "JsbdUpgrade" — ใส่ทั้งสองชื่อ กันอ่านผิด
        $dir = "${Drive}:\$d\OS"
        New-Item -ItemType Directory -Force $dir | Out-Null
        [IO.Compression.ZipFileExtensions]::ExtractToFile($e, "$dir\update.zip", $true)
    }
} finally { $z.Dispose() }

$ok = $true
Get-ChildItem "${Drive}:\" -Recurse -Filter update.zip | ForEach-Object {
    $h = (Get-FileHash $_.FullName).Hash.ToLower()
    $m = $h -eq $want; if (-not $m) { $ok = $false }
    Write-Host ("  {0}  {1}" -f ($(if ($m) { 'OK ' } else { 'BAD' })), $_.FullName)
}
if ($ok) { Write-Host "[ผ่าน] USB พร้อม — ถอดแบบ Eject แล้วเสียบรถ" -ForegroundColor Green } else { Write-Host "[ล้มเหลว] ไฟล์ไม่ตรงต้นฉบับ" -ForegroundColor Red; exit 1 }
