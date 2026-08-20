# Facts / ค่าคงที่ (หยิบเร็ว)

## จอ
```
รุ่น IHU629G · model EEBADF1 · Flyme Auto E 1.8.0 · build 1114 · Android 9 (API 28)
root uid=0 · ro.secure=0 · ro.build.tags=test-keys · su=/system/xbin/su
/system = /dev/block/mmcblk0p41 (ต่อตรง ไม่มี dm-verity)
```

## Fingerprints (SHA-256)
```
platform key จอ (AOSP test-key สาธารณะ):
  C8:A2:E9:BC:CF:59:7C:2F:B6:DC:66:BE:E2:93:FC:13:F2:FC:47:EC:77:BC:6B:2B:0D:52:C1:1F:51:19:2A:B8
  subject: C=US,ST=California,O=Android,CN=Android,emailAddress=android@android.com
APK เดิม (กุญแจ dev, ใช้ไม่ได้):
  5f34f493274c0d51b9615bc20b9d1f8e10967bf50981cbbffe885ed7e7ed3f9e
```

## แพตช์
```
1114 → 3C6025_SW0E22H0306H111400000_user_995   (update.zip 831807B, sha256 F55AE666...4CC4)
1111 → 3C6025_SW0E22H0128H111100000_user_995
GDrive: 1111=1rvTfF4WoTgXcZ98_y73fKRayZVBgxaOZ · 1114=1RF5ut5r_zVGCxfe2O3LKc3SrecRVp3td
```

## แอป
```
Car   com.geely.ex2.tools    → จอ (server)   versionName 1.0-system, uid 1000
Phone com.example.ex2_phone  → Android (client)
iOS   com.geely.ex2.phone    → iPhone iOS15+ (client, debug build)
Transport: TCP 47800 · Android SPP UUID …c0ffee000001 (GeelyEX2-CarSync) · iOS BLE-UART …000002/3/4
ภาษา: เวียดนามล้วน (Car.apk มี VI/EN)
```

## เครื่องมือ + เน็ต (รอบที่ทำ)
```
adb    C:\Users\Pishe\Downloads\ADB_AppControl\adb\adb.exe
JRE    portable Temurin jdk-17.0.20+8-jre
signer uber-apk-signer 1.3.0
AOSP key  aosp-mirror/platform_build → target/product/security/platform.{pk8,x509.pem}
car IP 172.20.10.3:5555 (hotspot; คอม 172.20.10.2)
ติดตั้งที่ /system/app/GeelyEx2Tools/Geely-Ex2-Car.apk
```

## ต้นทาง
repo `algodaohub/geely-ex2-hub` · กระทู้ xethongminh.net
