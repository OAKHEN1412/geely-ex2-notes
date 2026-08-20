# ไฟล์ทำงาน (local เท่านั้น — ไม่ push ขึ้น git)

โฟลเดอร์ `_*` ด้านล่างเก็บไฟล์ไบนารี/กุญแจ/ผลงานจริง อยู่ในเครื่องนี้ที่เดียว
(gitignore ไว้ ไม่ขึ้น GitHub เพราะเป็นไบนารี/ของเฉพาะรอบ)

| โฟลเดอร์ | มีอะไร | ใช้ตอน |
|----------|--------|--------|
| `_signed-apk/` | `Geely-Ex2-Car-aligned-signed.apk` — APK ที่เซ็นด้วย AOSP platform key แล้ว (พร้อมลง) | push เข้า /system/app ได้เลย (ไม่ต้องเซ็นใหม่) |
| `_signing-kit/` | `platform.pk8` `platform.x509.pem` `platform.jks` `uas.jar` | เซ็น APK ตัวอื่นด้วย platform key (เช่นตอนแปลภาษา) |
| `_patches/` | `patch-1111.zip` `patch-1114.zip` | แฟลชปลดล็อกใหม่ (เผื่อ Factory Reset) |
| `_artifacts/` | หน้าเว็บ 3 ตัว (unlock guide, iOS preview, ตารางแปล) + ไอคอนแอป | เปิดดู offline / แชร์ |

## หมายเหตุ
- **APK ที่เซ็นแล้ว** ใน `_signed-apk/` = ตัวที่ลงจริงบนจอสำเร็จ (cert `C8:A2…2A:B8`) เก็บไว้ลงซ้ำได้เลยไม่ต้องรัน resign
- **กุญแจ platform** เป็น AOSP test-key สาธารณะ (ไม่ลับ) แต่ gitignore ไว้กันรก
- ไม่ได้เก็บ portable JRE (jdk-17…-jre, ~130MB) — โหลดใหม่ได้จาก Adoptium ถ้าต้องเซ็นอีก (ดู tools/resign_apk.sh)
- เอกสาร/สคริปต์ที่ track ใน git อยู่ที่ `MASTER.md`, `quick/`, `docs/`, `tools/`, `app/`
