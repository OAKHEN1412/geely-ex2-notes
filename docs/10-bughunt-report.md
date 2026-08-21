# 10 — Bug-hunt report (fable /scrutinize 5 รอบ) + สถานะแก้

_2026-08-21 · รีวิว adversarial 5 รอบ + verify/dedupe ไล่โค้ดจริงทุกไฟล์ที่จะติดตั้ง_

ขอบเขต: EX2 BY OAKHEN harness (DevService/FloatingBox/SettingsActivity/DevStartReceiver) · แอป claude.ai WebView (`claudeapp/`) · build scripts (`build_oakhen.sh`, `patch_autowindow.sh`) · auto-window smali patch
ผล: **16 บั๊ก verified** (1 critical, 1 high, 9 medium, 5 low) — **แก้แล้วทั้งหมด** (harness recompile + claude app source + scripts)

> หมายเหตุ: ยังไม่ได้เทสต์บนจอจริง (adb offline) — fix ผ่าน compile ครบ แต่ต้องยืนยันพฤติกรรมตอน adb กลับ

## 🔴 Critical
| # | ไฟล์ | บั๊ก | แก้ | สถานะ |
|---|------|------|-----|-------|
| 1 | FloatingBox.java (backlight) | **guard thread เขียน backlight=0 หลังปลุก → จอค้างดำถาวร ปลุกไม่ได้ (reboot only)** + leak หลาย thread ตอน toggle เร็ว | เขียน backlight ทุกครั้ง **serialize บน single-thread executor** (guard+restore ไม่แข่งกัน, restore เป็นคำสั่งสุดท้ายเสมอ), guard tick เช็ค `sBlOff` ก่อนเขียน+reschedule (มีตัวเดียว) | ✅ fixed |

## 🟠 High
| # | ไฟล์ | บั๊ก | แก้ | สถานะ |
|---|------|------|-----|-------|
| 2 | claudeapp/MainActivity.java | **WebView grant สิทธิ์ทุกอย่างให้ทุก origin + นำทางออกนอกโดเมนได้ → ไมค์ถูกยึดเงียบ ๆ** โดยหน้าเว็บอื่น | onPermissionRequest grant เฉพาะ origin claude.ai/anthropic + เฉพาะ `RESOURCE_AUDIO_CAPTURE` + ต่อเมื่อแอปมีสิทธิ์จริง; shouldOverrideUrlLoading **บล็อกออกนอกโดเมน** | ✅ fixed |

## 🟡 Medium
| # | ไฟล์ | บั๊ก | แก้ | สถานะ |
|---|------|------|-----|-------|
| 3 | build_oakhen.sh | apktool build fail ถูกกลบด้วย `\| tail -2` + ไม่ลบ OUT เก่า → **เซ็น+ลง APK เก่าแทนโดยไม่รู้** | `rm -f OUT` ก่อน build, รัน apktool เก็บ log เช็ก exit, classes17 check เป็น **fatal** | ✅ fixed |
| 4 | FloatingBox.java | ปิดจอ backlight ตั้ง `screen_brightness_mode=0` แต่ **ไม่คืน → auto-brightness ปิดถาวร** | เซฟ mode เดิมก่อนตั้ง 0 + คืนตอนปลุก | ✅ fixed |
| 5 | FloatingBox.java | watchdog capture `black` view เก่า + ไม่ยกเลิกตัวก่อน → **overlay ดำค้าง + leak message** | watchdog เดี่ยว (ไม่ผูก view), `removeCallbacks` ทุกครั้ง, ปลุกลบ `sBlack` ตัวปัจจุบัน | ✅ fixed |
| 6 | FloatingBox.java | `su waitFor()` รันบน main thread (onClick/recover) → **ANR** | ทุกการเขียน backlight ย้ายไป bl executor thread | ✅ fixed |
| 7 | FloatingBox.java | recoverBacklight คืนแค่ sysfs ไม่คืน Settings.System → **จอมืดอีกหลัง die-during-dim+reboot** | recover ซ่อม Settings.System (brightness/mode) ถ้าค้างต่ำด้วย | ✅ fixed |
| 8 | DevService.java | startForeground โยน exception ถูกกลืน → **crash + START_STICKY วนลูป** | catch → fallback notification ขั้นต่ำ / ไม่ได้ก็ `stopSelf()` | ✅ fixed |
| 9 | claudeapp/MainActivity.java | ไม่ pause WebView ตอน background → **ไมค์ค้าง hot + JS/timer รันต่อ** เบื้องหลัง | onPause `web.onPause()+pauseTimers()`, onResume คู่กัน | ✅ fixed |
| 10 | claudeapp/MainActivity.java | `restoreState()` ไม่มี fallback → **จอว่างถาวร**หลัง recreate (สลับภาษาไทย/low-mem) | `if(b==null \|\| restoreState(b)==null) loadUrl(HOME)` | ✅ fixed |
| 11 | claudeapp (manifest+MainActivity) | `usesCleartextTraffic=true` + MIXED_CONTENT_COMPATIBILITY + fileAccess → **MITM inject** เข้า session | cleartext=false, MIXED_CONTENT_NEVER_ALLOW, ปิด file/content access | ✅ fixed |

## 🟢 Low
| # | ไฟล์ | บั๊ก | แก้ | สถานะ |
|---|------|------|-----|-------|
| 12 | build_oakhen.sh | classes9 swap ข้ามเงียบถ้า dex หาย → **ship poll 2000ms โดยไม่เตือน** | เปลี่ยนเป็น `if/else` เตือนชัดถ้าหาย | ✅ fixed |
| 13 | patch_autowindow.sh | sed ไม่ anchor → อาจ retime `0x7d0` จุดอื่นในคลาสเดียวกัน | assert `0x7d0` มีจุดเดียวก่อน sed (ไม่ตรง=exit) | ✅ fixed |
| 14 | build_oakhen.sh | echo แนะนำ `am broadcast ...START` แต่ receiver ไม่มี action นั้นแล้ว → คำสั่งตาย | เปลี่ยนเป็น `am start-foreground-service` + หมายเหตุ MY_PACKAGE_REPLACED | ✅ fixed |
| 15 | claudeapp/AndroidManifest.xml | `allowBackup=true` → session claude.ai หลุดผ่าน `adb backup` | `allowBackup=false` | ✅ fixed |
| 16 | claudeapp/MainActivity.java | แตะ voice ก่อนตอบ dialog สิทธิ์ → grant เว็บแต่ native เงียบ (voice พังเงียบ) | onPermissionRequest `deny()` ถ้าแอปยังไม่มี RECORD_AUDIO | ✅ fixed |

## สรุป
- โค้ดทั้งหมดที่จะติดตั้ง **แก้บั๊ก verified ครบ 16 ข้อ** + recompile ผ่าน
- **ต้องเทสต์บนจอจริง** (พรุ่งนี้): โดยเฉพาะ #1 (ปิดจอ→แตะปลุก หลาย ๆ รอบเร็ว ๆ ไม่ค้างดำ), #6 (ไม่ ANR), claude.ai voice (#2/#16)
- reviewers รันบน Opus (workflow default) ไม่ใช่ fable — คุณภาพรีวิวสูง เนื้อหาตรวจแล้ว
