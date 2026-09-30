package com.oakhen.ex2dev;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * อัปเดตแอปจากจอรถ (ไม่ต้องต่อคอม) — เรียกบนเธรดเบื้องหลังเท่านั้น
 *  - แอปทั่วไป: ApkSource (APKPure) → เลือกเวอร์ชันใหม่สุดที่ minSdk ≤ เครื่องนี้ → โหลด → ตรวจลายเซ็นตรงกับตัวที่ลงอยู่ → ติดตั้ง
 *  - EX2 BY OAKHEN: GitHub Releases ของ repo (private) ด้วย token อ่านอย่างเดียวที่ build ฝังไว้ใน assets/oakhen_update.properties
 *  - ติดตั้งด้วย PackageInstaller session (เราเป็น uid system → ลงเงียบ ไม่ต้องกดยืนยัน) · XAPK = base + split ที่ตรงเครื่อง
 */
final class AppUpdater {
    static final String TAG = "EX2Update";

    /** แอปที่ดูแล (ไม่รวม Play Services — อัปเดตพลาดแล้วแอป Google พังทั้งชุด) */
    static final String[][] APPS = {
            {"com.google.android.youtube", "YouTube"},
            {"com.google.android.apps.youtube.music", "YouTube Music"},
            {"com.google.android.apps.maps", "Google Maps"},
            {"org.smarttube.stable", "SmartTube"},
            {"com.disney.disneyplus", "Disney+"},
            {"org.cromite.cromite", "Cromite"},
    };

    /** รายชื่อทั้งหมด = APPS + ที่เพิ่มเองใน Prefs "update_extra" (รูปแบบ pkg=ชื่อ,pkg=ชื่อ) */
    static List<String[]> apps(Context c) {
        List<String[]> out = new ArrayList<String[]>(Arrays.asList(APPS));
        for (String s : Prefs.of(c).getString("update_extra", "").split(",")) {
            String[] kv = s.trim().split("=", 2);
            if (kv[0].length() > 0) out.add(new String[]{kv[0], kv.length > 1 ? kv[1] : kv[0]});
        }
        return out;
    }

    interface Progress { void on(String msg); }

    static final class Candidate {
        String pkg, label, installedName, newName, url, type; long installedCode, newCode, size;
        boolean oakhen;
    }

    private AppUpdater() {}

    static PackageInfo installed(Context c, String pkg) {
        try { return c.getPackageManager().getPackageInfo(pkg, PackageManager.GET_SIGNATURES); } catch (Throwable t) { return null; }
    }

    /** เวอร์ชันใหม่สุดที่ลงเครื่องนี้ได้ · null = ล่าสุดแล้ว/ไม่มีข้อมูล · msg[0] = เหตุผล */
    static Candidate check(Context c, String pkg, String label, String[] msg) {
        PackageInfo pi = installed(c, pkg);
        if (pi == null) { msg[0] = "ไม่ได้ลงไว้"; return null; }
        try {
            List<ApkSource.Version> newer = ApkSource.newerThan(ApkSource.versions(pkg), pi.getLongVersionCode());
            if (newer.isEmpty()) { msg[0] = "ล่าสุดแล้ว (" + pi.versionName + ")"; return null; }
            int probes = 0;
            for (ApkSource.Version v : newer) {
                String key = "minsdk_" + pkg + "_" + v.code + "_" + v.type;
                int min = Prefs.of(c).getInt(key, -2);
                if (min == -2) {
                    if (++probes > 12) break;
                    min = ApkSource.minSdk(v);
                    if (min >= 0) Prefs.of(c).edit().putInt(key, min).apply();
                }
                if (min >= 0 && min <= Build.VERSION.SDK_INT) {
                    Candidate k = new Candidate();
                    k.pkg = pkg; k.label = label; k.installedName = pi.versionName; k.installedCode = pi.getLongVersionCode();
                    k.newName = v.name; k.newCode = v.code; k.url = v.url; k.type = v.type; k.size = v.size;
                    msg[0] = "มี " + v.name + (v.size > 0 ? String.format(java.util.Locale.US, " · %.0f MB", v.size / 1048576.0) : "");
                    return k;
                }
            }
            msg[0] = "เวอร์ชันใหม่ต้องใช้ Android ใหม่กว่านี้ · ใช้ " + pi.versionName + " ต่อ";
            return null;
        } catch (Throwable t) {
            msg[0] = "ตรวจไม่ได้: " + t.getClass().getSimpleName();
            Log.w(TAG, "check " + pkg, t);
            return null;
        }
    }

    // ---------- EX2 BY OAKHEN (GitHub Releases) ----------
    static Properties oakhenCfg(Context c) {
        Properties p = new Properties();
        try { InputStream in = c.getAssets().open("oakhen_update.properties"); p.load(in); in.close(); } catch (Throwable ignored) {}
        return p;
    }

    /** เลข build ของตัวที่ลงอยู่ (versionName "1.0-oakhen.<N>") */
    static int ownBuild(Context c) {
        try {
            String vn = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
            return Integer.parseInt(vn.substring(vn.lastIndexOf('.') + 1));
        } catch (Throwable t) { return 0; }
    }

    static Candidate checkOakhen(Context c, String[] msg) {
        Properties cfg = oakhenCfg(c);
        String repo = cfg.getProperty("repo", ""), token = cfg.getProperty("token", "");
        if (repo.isEmpty() || token.isEmpty()) { msg[0] = "ยังไม่ได้ใส่ token ตอน build (ดู tools/release_oakhen.sh)"; return null; }
        try {
            HttpURLConnection h = (HttpURLConnection) new URL("https://api.github.com/repos/" + repo + "/releases/latest").openConnection();
            h.setRequestProperty("Accept", "application/vnd.github+json");
            h.setRequestProperty("Authorization", "Bearer " + token);
            h.setConnectTimeout(15000); h.setReadTimeout(20000);
            int code = h.getResponseCode();
            if (code == 404) { msg[0] = "ยังไม่มี release"; return null; }
            if (code == 401 || code == 403) { msg[0] = "token ใช้ไม่ได้/หมดอายุ (HTTP " + code + ")"; return null; }
            JSONObject rel = new JSONObject(new String(ApkSource.readAll(h.getInputStream()), "UTF-8"));
            String tag = rel.optString("tag_name");
            int n;
            try { n = Integer.parseInt(tag.replaceAll("\\D+", "")); } catch (Throwable t) { msg[0] = "tag แปลก: " + tag; return null; }
            int mine = ownBuild(c);
            if (n <= mine) { msg[0] = "ล่าสุดแล้ว (build " + mine + ")"; return null; }
            JSONArray as = rel.optJSONArray("assets");
            for (int i = 0; as != null && i < as.length(); i++) {
                JSONObject a = as.getJSONObject(i);
                if (!a.optString("name").endsWith(".apk")) continue;
                Candidate k = new Candidate();
                k.oakhen = true; k.pkg = c.getPackageName(); k.label = "EX2 BY OAKHEN";
                k.installedName = "build " + mine; k.newName = "build " + n + " · " + rel.optString("name");
                k.url = a.optString("url"); k.type = "APK"; k.size = a.optLong("size");
                msg[0] = "มี build " + n + String.format(java.util.Locale.US, " · %.1f MB", k.size / 1048576.0);
                return k;
            }
            msg[0] = "release ไม่มีไฟล์ .apk";
            return null;
        } catch (Throwable t) {
            msg[0] = "ตรวจไม่ได้: " + t.getClass().getSimpleName();
            Log.w(TAG, "checkOakhen", t);
            return null;
        }
    }

    // ---------- โหลด + ตรวจ + ติดตั้ง ----------
    /** true = สั่งติดตั้งสำเร็จ · msg[0] = ผล */
    static boolean update(Context c, Candidate k, Progress p, String[] msg) {
        File dir = new File(c.getCacheDir(), "updates");
        dir.mkdirs();
        for (File old : dir.listFiles()) old.delete();
        File f = new File(dir, k.pkg + "-" + k.newCode + ("XAPK".equalsIgnoreCase(k.type) ? ".xapk" : ".apk"));
        try {
            download(c, k, f, p);
            List<File> apks = "XAPK".equalsIgnoreCase(k.type) ? extractXapk(f, dir) : Arrays.asList(f);
            // ลายเซ็นต้องตรงกับตัวที่ลงอยู่ — ไฟล์จากเว็บกระจกที่ถูกแก้จะหยุดตรงนี้ (Android ก็ปฏิเสธอยู่แล้ว แต่บอกผู้ใช้ให้ชัด)
            PackageInfo cur = installed(c, k.pkg);
            PackageInfo neu = c.getPackageManager().getPackageArchiveInfo(apks.get(0).getPath(), PackageManager.GET_SIGNATURES);
            if (neu == null || !k.pkg.equals(neu.packageName)) { msg[0] = "ไฟล์เสีย/ไม่ใช่แอปนี้"; return false; }
            if (cur == null || !sameSigs(cur.signatures, neu.signatures)) { msg[0] = "ลายเซ็นไม่ตรงกับแอปที่ลงอยู่ — ไม่ติดตั้ง"; return false; }
            p.on("กำลังติดตั้ง…");
            return install(c, k.pkg, apks, msg);
        } catch (Throwable t) {
            msg[0] = "ไม่สำเร็จ: " + t.getMessage();
            Log.w(TAG, "update " + k.pkg, t);
            return false;
        } finally {
            if (!k.oakhen) for (File old : dir.listFiles()) old.delete();   // OAKHEN: process จะถูกปิดตอนติดตั้ง ลบรอบหน้า
        }
    }

    private static void download(Context c, Candidate k, File out, Progress p) throws IOException {
        String url = k.url;
        HttpURLConnection h = null;
        for (int i = 0; i < 6; i++) {   // ตามลิงก์เอง: GitHub ต้องส่ง token เฉพาะ api.github.com ไม่ส่งต่อไปที่ CDN
            h = (HttpURLConnection) new URL(url).openConnection();
            h.setInstanceFollowRedirects(false);
            h.setConnectTimeout(20000); h.setReadTimeout(60000);
            if (k.oakhen && url.startsWith("https://api.github.com/")) {
                h.setRequestProperty("Accept", "application/octet-stream");
                h.setRequestProperty("Authorization", "Bearer " + oakhenCfg(c).getProperty("token", ""));
            }
            int code = h.getResponseCode();
            if (code / 100 == 3) { url = h.getHeaderField("Location"); h.disconnect(); continue; }
            if (code != 200) throw new IOException("HTTP " + code);
            break;
        }
        long total = h.getContentLengthLong() > 0 ? h.getContentLengthLong() : k.size;
        InputStream in = h.getInputStream();
        OutputStream o = new FileOutputStream(out);
        byte[] buf = new byte[65536];
        long got = 0, lastUi = 0;
        int n;
        while ((n = in.read(buf)) > 0) {
            o.write(buf, 0, n);
            got += n;
            long now = System.currentTimeMillis();
            if (now - lastUi > 500) {
                lastUi = now;
                p.on(String.format(java.util.Locale.US, "กำลังโหลด %.0f / %.0f MB", got / 1048576.0, total / 1048576.0));
            }
        }
        o.close(); in.close();
        if (total > 0 && got != total) throw new IOException("โหลดไม่ครบ " + got + "/" + total);
    }

    /** XAPK = zip ของ base.apk + split (ABI/ความละเอียดจอ/ภาษา) → เลือกเฉพาะที่ตรงเครื่อง */
    private static List<File> extractXapk(File xapk, File dir) throws Exception {
        ZipFile z = new ZipFile(xapk);
        JSONObject m = new JSONObject(new String(ApkSource.readAll(z.getInputStream(z.getEntry("manifest.json"))), "UTF-8"));
        JSONArray splits = m.optJSONArray("split_apks");
        List<String> want = new ArrayList<String>();
        String baseFile = null;   // ไฟล์ base ตาม id "base" (ชื่อไฟล์จริงเช่น org.wikipedia.apk ไม่ใช่ base.apk)
        if (splits == null) {   // XAPK แบบเก่า: ไฟล์ apk เดียว
            for (java.util.Enumeration<? extends ZipEntry> e = z.entries(); e.hasMoreElements(); ) {
                String n = e.nextElement().getName();
                if (n.endsWith(".apk") && !n.contains("/")) want.add(n);
            }
        } else {
            String abi = null;
            List<String> ids = new ArrayList<String>();
            for (int i = 0; i < splits.length(); i++) ids.add(splits.getJSONObject(i).optString("id"));
            for (String a : Build.SUPPORTED_ABIS) if (ids.contains("config." + a.replace('-', '_'))) { abi = "config." + a.replace('-', '_'); break; }
            String dens = densitySplit(ids);
            for (int i = 0; i < splits.length(); i++) {
                JSONObject s = splits.getJSONObject(i);
                String id = s.optString("id"), file = s.optString("file");
                boolean isAbi = id.matches("config\\.(arm64_v8a|armeabi_v7a|armeabi|x86|x86_64|mips|mips64)");
                boolean isDens = id.matches("config\\.(ldpi|mdpi|tvdpi|hdpi|xhdpi|xxhdpi|xxxhdpi|nodpi)");
                if (id.equals("base")) baseFile = file;
                if (id.equals("base") || (isAbi && id.equals(abi)) || (isDens && id.equals(dens)) || (!isAbi && !isDens)) want.add(file);
            }
        }
        List<File> out = new ArrayList<File>();
        for (String n : want) {   // base ก่อนเสมอ (ใช้ตรวจลายเซ็น)
            ZipEntry e = z.getEntry(n);
            if (e == null) continue;
            File f = new File(dir, n.replace('/', '_'));
            InputStream in = z.getInputStream(e);
            OutputStream o = new FileOutputStream(f);
            byte[] buf = new byte[65536];
            int r;
            while ((r = in.read(buf)) > 0) o.write(buf, 0, r);
            o.close(); in.close();
            if (n.equals(baseFile) || n.equals("base.apk")) out.add(0, f); else out.add(f);
        }
        z.close();
        return out;
    }

    private static String densitySplit(List<String> ids) {
        int dpi = android.content.res.Resources.getSystem().getDisplayMetrics().densityDpi;
        String[] names = {"ldpi", "mdpi", "tvdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
        int[] vals = {120, 160, 213, 240, 320, 480, 640};
        String best = null; int bestD = Integer.MAX_VALUE;
        for (int i = 0; i < names.length; i++) {
            if (!ids.contains("config." + names[i])) continue;
            int d = vals[i] >= dpi ? vals[i] - dpi : (dpi - vals[i]) * 2;   // ชอบใหญ่กว่า/เท่ากับจอมากกว่าเล็กกว่า
            if (d < bestD) { bestD = d; best = "config." + names[i]; }
        }
        return best;
    }

    private static boolean sameSigs(Signature[] a, Signature[] b) {
        if (a == null || b == null || a.length == 0) return false;
        java.util.Set<Signature> x = new java.util.HashSet<Signature>(Arrays.asList(a)), y = new java.util.HashSet<Signature>(Arrays.asList(b));
        return x.equals(y);
    }

    private static boolean install(Context c, String pkg, List<File> apks, String[] msg) throws Exception {
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        sp.setAppPackageName(pkg);
        int id = pi.createSession(sp);
        PackageInstaller.Session s = pi.openSession(id);
        try {
            int i = 0;
            for (File f : apks) {
                OutputStream o = s.openWrite((i++ == 0 ? "base" : "split" + i) + ".apk", 0, f.length());
                InputStream in = new FileInputStream(f);
                byte[] buf = new byte[65536];
                int r;
                while ((r = in.read(buf)) > 0) o.write(buf, 0, r);
                in.close();
                s.fsync(o);
                o.close();
            }
            final String action = "com.oakhen.ex2dev.INSTALL_" + id;
            final CountDownLatch done = new CountDownLatch(1);
            final int[] status = {PackageInstaller.STATUS_FAILURE};
            final String[] detail = {""};
            BroadcastReceiver r = new BroadcastReceiver() {
                public void onReceive(Context ctx, Intent it) {
                    status[0] = it.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
                    detail[0] = String.valueOf(it.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
                    if (status[0] == PackageInstaller.STATUS_PENDING_USER_ACTION) {   // ไม่ควรเกิดกับ uid system — เผื่อไว้
                        Intent confirm = it.getParcelableExtra(Intent.EXTRA_INTENT);
                        if (confirm != null) { confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(confirm); }
                        return;
                    }
                    done.countDown();
                }
            };
            c.getApplicationContext().registerReceiver(r, new IntentFilter(action));
            PendingIntent pend = PendingIntent.getBroadcast(c, id, new Intent(action).setPackage(c.getPackageName()), PendingIntent.FLAG_UPDATE_CURRENT);
            s.commit(pend.getIntentSender());
            done.await(180, TimeUnit.SECONDS);   // ติดตั้งตัวเอง (OAKHEN) = process ถูกปิดก่อนถึงบรรทัดถัดไป
            try { c.getApplicationContext().unregisterReceiver(r); } catch (Throwable ignored) {}
            if (status[0] == PackageInstaller.STATUS_SUCCESS) { msg[0] = "อัปเดตแล้ว"; return true; }
            msg[0] = "ติดตั้งไม่ผ่าน: " + detail[0];
            return false;
        } finally {
            s.close();
        }
    }
}
