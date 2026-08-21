# 11 — Dashcam (บันทึกกล้องรอบคัน) — design + storage เลือก USB/ในเครื่อง

_design โดย fable · ยังไม่ build — รอ recon กล้องบนจอก่อน_ · ฟีเจอร์ของ EX2 BY OAKHEN (แท็บใหม่ "กล้อง/บันทึก")

## สถานะ: design พร้อม, ติด recon 1 อย่าง
**Camera engine เข้าถึงได้ไหม** ต้อง recon ก่อน (ชี้เป็นชี้ตาย) — storage layer/UI/code ออกแบบครบแล้ว ใช้ได้กับทุก engine

## 1. Feasibility กล้อง (จาก recon)
| ผล recon | อัดได้ไหม | engine |
|----------|----------|--------|
| Camera2 โชว์หลายกล้อง (`cmd media.camera list`) | ✅ ดีสุด | Camera2 + MediaRecorder (Java) |
| มีแค่ `/dev/videoN` (V4L2) | ✅ (กล้องถอย/หน้า) | static ffmpeg arm64 (ต้อง push) หรือ screenrecord |
| AVM เป็น overlay MCU ล้วน | ❌ กล้องรอบคันอัดไม่ได้ | เหลือ `screenrecord` (อัดหน้าจอตอนโชว์กล้อง) |

> คาด: อัด **กล้องถอย/หน้า** ได้ · **360 รอบคันทีละตัวน่าจะไม่ได้** (MCU stitch แยก) · กล้องถอยอาจ live เฉพาะตอนเข้าเกียร์ถอย

**recon กล้อง:**
```bash
S(){ "$ADB" -s $D shell "$@"; }
S "cmd media.camera list; dumpsys media.camera | grep -iE 'Camera [0-9]|facing|Number of camera'"
S "ls -l /dev/video*; for v in /sys/class/video4linux/*/name; do echo \"$v=$(cat $v)\"; done"
S "which screenrecord; pm list packages | grep -iE 'avm|oneos|360|camera|dvr'"
```

## 2. Storage เลือก USB/ในเครื่อง — verdict
- **EX2 BY OAKHEN = platform-signed system app** → มี `WRITE_MEDIA_STORAGE`+`WRITE_EXTERNAL_STORAGE` → **เขียน `/storage/<UUID>/` (USB) ตรง ๆ ด้วย `java.io.File` ไม่ต้อง SAF/scoped**
- enumerate จาก **`/proc/mounts`** (หา `vfat|exfat|fuse|sdcardfs` ใต้ `/storage/` ที่ไม่ใช่ emulated) + `StatFs` free space
- fallback: ถ้า `/storage/<UUID>` เขียนไม่ได้ → raw `/mnt/media_rw/<UUID>/` ผ่าน su
- internal = `/storage/emulated/0/EX2DASH`

**recon storage (เสียบ USB flash ก่อนรัน):**
```bash
S 'sm list-volumes all'
S 'cat /proc/mounts | grep -iE "media_rw|/storage/|vfat|exfat|fuse|sdcardfs"'
S 'df -h | grep -iE "storage|media_rw|emulated"'
S 'echo test > /storage/*/EX2W 2>&1; ls -l /storage/*/EX2W; rm -f /storage/*/EX2W'   # เขียนตรงได้ไหม
```

## 3. ฟีเจอร์/ตั้งค่า (แท็บ "กล้อง/บันทึก")
**MUST:** สวิตช์เปิด/ปิดอัด · เลือกกล้อง (ถอย/หน้า/จอ) · ความละเอียด/บิตเรต/fps · **loop** + ความยาวคลิป (1/3/5 นาที) · **ที่เก็บ (USB/ในเครื่อง)** + max GB + ลบเก่าอัตโนมัติ · อัดเสียง on/off
**NICE:** auto-start ตอนขับ (VHAL เกียร์ D/speed>0) · overlay เวลา+ความเร็ว(VHAL)+GPS · G-sensor lock (คลิปชนกันวนทับ) · ปุ่มเซฟฉุกเฉิน/snapshot · parking mode (จำกัดไฟจอตอนดับรถ) · ตัวจัดการคลิป (เล่น/ลบ)

prefs keys: `cam_enabled cam_source cam_res cam_seg_min cam_dir cam_max_gb cam_audio cam_autostart cam_overlay cam_gsensor`

## 4. โครงโค้ด storage (เพิ่มใน com.oakhen.ex2dev)

**Storage.java — enumerate + free + resolve + ensureDir**
```java
package com.oakhen.ex2dev;
import android.content.Context; import android.os.StatFs; import java.io.*; import java.util.*;
public class Storage {
    public static class Dest { public String label,path,uuid; public long freeBytes; public boolean removable; }
    static final String SUB = "EX2DASH";
    public static List<Dest> list(Context ctx){
        ArrayList<Dest> out=new ArrayList<>();
        Dest in=new Dest(); in.uuid="internal"; in.removable=false;
        in.path="/storage/emulated/0/"+SUB; in.freeBytes=free("/storage/emulated/0");
        in.label="ในเครื่อง ("+gb(in.freeBytes)+" ว่าง)"; out.add(in);
        try{
            BufferedReader r=new BufferedReader(new FileReader("/proc/mounts")); String ln; HashSet<String> seen=new HashSet<>();
            while((ln=r.readLine())!=null){
                String[] p=ln.split("\\s+"); if(p.length<3) continue;
                String mp=p[1], fs=p[2];
                if(!fs.matches("(?i)vfat|exfat|fuse|sdcardfs|texfat")) continue;
                if(!mp.startsWith("/storage/")||mp.startsWith("/storage/emulated")) continue;
                if(!seen.add(mp)) continue;
                Dest d=new Dest(); d.removable=true; d.uuid=new File(mp).getName();
                d.path=mp+"/"+SUB; d.freeBytes=free(mp);
                d.label="USB "+d.uuid+" ("+gb(d.freeBytes)+" ว่าง)"; out.add(d);
            } r.close();
        }catch(Throwable ignored){}
        return out;
    }
    public static Dest resolve(Context ctx,String prefUuid){
        List<Dest> l=list(ctx); for(Dest d:l) if(d.uuid.equals(prefUuid)) return d; return l.get(0); // internal
    }
    public static File ensureDir(Dest d){ File f=new File(d.path); f.mkdirs();
        if(!f.canWrite()){ try{Runtime.getRuntime().exec(new String[]{"su","-c","mkdir -p "+d.path+"; chmod 777 "+d.path}).waitFor();}catch(Throwable t){} } return f; }
    public static long free(String p){ try{StatFs s=new StatFs(p); return s.getAvailableBytes();}catch(Throwable t){return 0;} }
    static String gb(long b){ return String.format(Locale.US,"%.1fGB",b/1073741824f); }
}
```

**plug/unplug realtime (register runtime ใน SettingsActivity + DashcamService)**
```java
IntentFilter f=new IntentFilter();
for(String a:new String[]{Intent.ACTION_MEDIA_MOUNTED,Intent.ACTION_MEDIA_UNMOUNTED,
     Intent.ACTION_MEDIA_EJECT,Intent.ACTION_MEDIA_BAD_REMOVAL}) f.addAction(a);
f.addDataScheme("file");
ctx.registerReceiver(receiver, f);   // onReceive -> refresh Spinner / เช็ค dest+fallback ; unregister ใน onDestroy
```

**DashcamService integration + fallback + loop**
```java
String pref = prefs.getString("cam_dir","internal");
Storage.Dest dest = Storage.resolve(this, pref);
if(!"internal".equals(pref) && dest.uuid.equals("internal")) notifyUser("USB ไม่พบ — บันทึกลงในเครื่องแทน");
File dir = Storage.ensureDir(dest);
if(Storage.free(dir.getParent()) < 300L*1024*1024) pruneOldest(dir);   // <300MB -> ลบเก่า
File seg = new File(dir, "EX2_"+ts()+".mp4");    // recorder.setOutputFile(seg)
// USB ถอดกลางอัด (ACTION_MEDIA_UNMOUNTED/BAD_REMOVAL) -> stopSegment() ปิดไฟล์ -> resolve ใหม่ (internal) -> เริ่มต่อ
void pruneOldest(File dir){
    File[] fs=dir.listFiles((d,n)->n.endsWith(".mp4"));   // ยกเว้น locked/ (G-sensor)
    if(fs==null) return; Arrays.sort(fs,(a,b)->Long.compare(a.lastModified(),b.lastModified()));
    long maxB=prefs.getInt("cam_max_gb",8)*1073741824L, tot=0; for(File x:fs) tot+=x.length();
    for(File x:fs){ if(tot<=maxB && Storage.free(dir.getParent())>300L*1024*1024) break; tot-=x.length(); x.delete(); }
}
```

**Spinner ที่เก็บ (แท็บ, plain Android)** — adapter จาก `Storage.list()`, เลือกแล้ว `prefs.putString("cam_dir", uuid)`, refresh ตอน plug/unplug

## 5. Caveats
- **exFAT** (USB >32GB): Flyme รองรับ mount ไหมดู `/proc/mounts` (เห็น exfat/texfat=OK); ถ้าไม่ → ฟอร์แมต **FAT32** (ไฟล์ ≤4GB → segment ≤3–5 นาที = loop ตั้งไว้พอดี)
- **USB ถอดกลางอัด**: MediaRecorder error → try/catch, ปิดไฟล์, resolve→internal, เสีย 1 คลิป (ยอมรับได้)
- **ไฟดับตอนดับรถ**: อัดตอนจอดได้แค่ช่วง standby; `recorder.stop()` ใน onDestroy/ACTION_SHUTDOWN กันไฟล์ท้ายเสีย
- **Flyme path**: บาง build USB โผล่แค่ `/mnt/media_rw/<UUID>` (canWrite false) → fallback su (ในโค้ด ensureDir)
- **ความร้อน/CPU/USB speed**: encode ลง USB ช้ากว่า → จำกัด bitrate, USB ดี ๆ (3.0) กัน frame drop
- ควรมีปุ่ม "🗂 เปิดโฟลเดอร์คลิป" เล่น/ลบในแอป (งานต่อยอด)
