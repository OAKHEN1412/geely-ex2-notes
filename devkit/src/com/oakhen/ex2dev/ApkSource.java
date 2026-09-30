package com.oakhen.ex2dev;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Inflater;

/**
 * แหล่ง APK ของแอปทั่วไป (Java ล้วน — ทดสอบบนคอมได้: devkit/test/run_update_test.sh)
 *  - รายการเวอร์ชัน: APKPure api.pureapk.com/m/v3/cms/app_version (protobuf) → ชื่อเวอร์ชัน, versionCode, ชนิด (APK/XAPK), ลิงก์โหลด
 *    รายการนี้ไม่บอก Android ขั้นต่ำ (ใส่ x-sv: 28 ก็ไม่กรอง — ทดสอบแล้ว ได้ YouTube 21.23 ที่ต้องใช้ Android 10)
 *  - Android ขั้นต่ำ: อ่านจากไฟล์ระยะไกลด้วย HTTP Range (เซิร์ฟเวอร์ตอบ 206) — ไม่ต้องโหลดทั้งไฟล์ 150+ MB
 *      APK  → AndroidManifest.xml (binary XML) → uses-sdk@minSdkVersion
 *      XAPK → manifest.json → "min_sdk_version"
 * ความปลอดภัย: ไฟล์จากเว็บกระจก ลงทับได้เฉพาะเมื่อลายเซ็นตรงกับตัวที่ลงอยู่ (Android บังคับ + AppUpdater เช็คก่อนเอง)
 */
final class ApkSource {
    static final class Version {
        String name, type, url; long code, size; int minSdk = -1;
        public String toString() { return name + " (" + code + ", " + type + (minSdk >= 0 ? ", sdk≥" + minSdk : "") + ")"; }
    }

    private ApkSource() {}

    /** รายการเวอร์ชันใหม่ → เก่า */
    static List<Version> versions(String pkg) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL("https://api.pureapk.com/m/v3/cms/app_version?hl=en-US&package_name=" + pkg).openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(30000);
        c.setRequestProperty("x-cv", "3172501");
        c.setRequestProperty("x-sv", "28");
        c.setRequestProperty("x-abis", "arm64-v8a,armeabi-v7a,armeabi");
        c.setRequestProperty("x-gp", "1");
        byte[] b = readAll(c.getInputStream());
        List<Version> out = new ArrayList<Version>();
        // root.1 → .7[] → .2[] → .3[] (item) → .2 (app) : 5=versionCode 6=versionName 24=asset{8=type, 9=ลิงก์}
        for (byte[] l1 : get(b, 1)) for (byte[] l7 : get(l1, 7)) for (byte[] l2 : get(l7, 2)) for (byte[] item : get(l2, 3)) {
            List<byte[]> apps = get(item, 2);
            if (apps.isEmpty()) continue;
            byte[] app = apps.get(0);
            List<byte[]> vc = get(app, 5), vn = get(app, 6), asset = get(app, 24);
            if (vc.isEmpty() || vn.isEmpty() || asset.isEmpty()) continue;
            Version v = new Version();
            try { v.code = Long.parseLong(str(vc.get(0))); } catch (NumberFormatException e) { continue; }
            v.name = str(vn.get(0));
            List<byte[]> t = get(asset.get(0), 8), u = get(asset.get(0), 9);
            v.type = t.isEmpty() ? "APK" : str(t.get(0));
            v.size = varintField(asset.get(0), 4);
            if (u.isEmpty()) continue;
            Matcher m = Pattern.compile("https://[\\x21-\\x7e]+").matcher(new String(u.get(0), "ISO-8859-1"));
            if (!m.find()) continue;
            v.url = m.group();
            out.add(v);
        }
        return out;
    }

    // ---------- Android ขั้นต่ำจากไฟล์ระยะไกล ----------

    /** minSdk ของเวอร์ชันนี้ (อ่านผ่าน Range) · -1 = อ่านไม่ได้ */
    static int minSdk(Version v) {
        try {
            if ("XAPK".equalsIgnoreCase(v.type)) {
                byte[] mj = remoteEntry(v.url, "manifest.json");
                if (mj == null) return -1;
                Matcher m = Pattern.compile("\"min_sdk_version\"\\s*:\\s*\"?(\\d+)").matcher(new String(mj, "UTF-8"));
                return m.find() ? Integer.parseInt(m.group(1)) : -1;
            }
            byte[] axml = remoteEntry(v.url, "AndroidManifest.xml");
            return axml == null ? -1 : axmlMinSdk(axml);
        } catch (Throwable t) { return -1; }
    }

    /** อ่านไฟล์หนึ่งไฟล์ใน zip ระยะไกล: ลองส่วนหัวไฟล์ก่อน (APK ส่วนใหญ่ manifest อยู่แรก) ไม่เจอค่อยอ่าน central directory ท้ายไฟล์ */
    static byte[] remoteEntry(String url, String name) throws IOException {
        String real = resolve(url);
        byte[] head = range(real, 0, 65535);
        byte[] e = localEntry(head, 0, name, real, -1, -1);
        if (e != null) return e;
        long size = totalSize(real);
        if (size <= 0) return null;
        long from = Math.max(0, size - 65557);
        byte[] tail = range(real, from, size - 1);
        int eocd = -1;
        for (int i = tail.length - 22; i >= 0; i--) if (u32(tail, i) == 0x06054b50L) { eocd = i; break; }
        if (eocd < 0) return null;
        long cdSize = u32(tail, eocd + 12), cdOff = u32(tail, eocd + 16);
        byte[] cd = cdOff >= from ? slice(tail, (int) (cdOff - from), (int) cdSize) : range(real, cdOff, cdOff + cdSize - 1);
        int p = 0;
        while (p + 46 <= cd.length && u32(cd, p) == 0x02014b50L) {
            int nl = u16(cd, p + 28), xl = u16(cd, p + 30), cl = u16(cd, p + 32);
            String n = new String(cd, p + 46, nl, "UTF-8");
            if (n.equals(name)) {   // ขนาดจาก central directory (ไฟล์ที่ใช้ data descriptor เขียน 0 ไว้ในหัวไฟล์ย่อย — XAPK)
                long lho = u32(cd, p + 42), csz = u32(cd, p + 20), usz = u32(cd, p + 24);
                byte[] lh = range(real, lho, lho + 30 + 1024 + csz);
                return localEntry(lh, 0, name, real, csz, usz);
            }
            p += 46 + nl + xl + cl;
        }
        return null;
    }

    private static byte[] localEntry(byte[] b, int p, String name, String url, long cdCsz, long cdUsz) throws IOException {
        if (b.length < p + 30 || u32(b, p) != 0x04034b50L) return null;
        int flags = u16(b, p + 6), method = u16(b, p + 8), nl = u16(b, p + 26), xl = u16(b, p + 28);
        long csz = u32(b, p + 18), usz = u32(b, p + 22);
        if (cdCsz >= 0) { csz = cdCsz; usz = cdUsz; }
        else if ((flags & 8) != 0 || csz == 0) return null;   // ขนาดไม่อยู่ในหัวไฟล์ย่อย → ให้ไปอ่านจาก central directory
        if (b.length < p + 30 + nl) return null;
        if (!new String(b, p + 30, nl, "UTF-8").equals(name)) return null;
        int data = p + 30 + nl + xl;
        byte[] comp = data + csz <= b.length ? slice(b, data, (int) csz) : range(url, data, data + csz - 1);
        if (method == 0) return comp;
        Inflater inf = new Inflater(true);
        inf.setInput(comp);
        byte[] out = new byte[(int) Math.max(usz, 1)];
        try { inf.inflate(out); } catch (java.util.zip.DataFormatException e) { return null; } finally { inf.end(); }
        return out;
    }

    /** binary XML: หา <uses-sdk> แล้วอ่าน attribute ที่ resource id = 0x0101020c (minSdkVersion) */
    static int axmlMinSdk(byte[] x) {
        int p = 8;   // ข้าม header ของไฟล์
        List<String> strings = new ArrayList<String>();
        int[] resIds = new int[0];
        while (p + 8 <= x.length) {
            int type = u16(x, p), hsz = u16(x, p + 2);
            int size = (int) u32(x, p + 4);
            if (size <= 0) break;
            if (type == 0x0001) strings = stringPool(x, p);
            else if (type == 0x0180) {
                int n = (size - hsz) / 4;
                resIds = new int[n];
                for (int i = 0; i < n; i++) resIds[i] = (int) u32(x, p + hsz + i * 4);
            } else if (type == 0x0102) {   // start element
                int nameIdx = (int) u32(x, p + 20);
                int attrStart = u16(x, p + 24), attrSize = u16(x, p + 26), attrCount = u16(x, p + 28);
                String el = nameIdx >= 0 && nameIdx < strings.size() ? strings.get(nameIdx) : "";
                if ("uses-sdk".equals(el)) {
                    int a0 = p + 16 + attrStart;
                    for (int i = 0; i < attrCount; i++) {
                        int a = a0 + i * attrSize;
                        int an = (int) u32(x, a + 4);
                        boolean isMin = (an >= 0 && an < resIds.length && resIds[an] == 0x0101020c)
                                || (an >= 0 && an < strings.size() && "minSdkVersion".equals(strings.get(an)));
                        if (isMin) {
                            int dataType = x[a + 15] & 0xff;
                            long data = u32(x, a + 16);
                            if (dataType == 0x10 || dataType == 0x11) return (int) data;
                            int raw = (int) u32(x, a + 8);   // ค่าเป็นสตริง (codename)
                            if (raw >= 0 && raw < strings.size()) try { return Integer.parseInt(strings.get(raw)); } catch (NumberFormatException ignored) {}
                            return -1;
                        }
                    }
                    return 1;   // มี uses-sdk แต่ไม่ระบุ = 1
                }
            }
            p += size;
        }
        return -1;
    }

    private static List<String> stringPool(byte[] x, int p) {
        List<String> out = new ArrayList<String>();
        int count = (int) u32(x, p + 8), flags = (int) u32(x, p + 16), strStart = (int) u32(x, p + 20);
        boolean utf8 = (flags & 0x100) != 0;
        int hsz = u16(x, p + 2);
        for (int i = 0; i < count; i++) {
            int off = p + strStart + (int) u32(x, p + hsz + i * 4);
            try {
                if (utf8) {
                    int q = off;
                    int n1 = x[q] & 0xff; q += (n1 & 0x80) != 0 ? 2 : 1;          // จำนวนตัวอักษร (utf16)
                    int n2 = x[q] & 0xff;                                             // จำนวนไบต์
                    if ((n2 & 0x80) != 0) { n2 = ((n2 & 0x7f) << 8) | (x[q + 1] & 0xff); q += 2; } else q += 1;
                    out.add(new String(x, q, n2, "UTF-8"));
                } else {
                    int n = u16(x, off), q = off + 2;
                    if ((n & 0x8000) != 0) { n = ((n & 0x7fff) << 16) | u16(x, off + 2); q += 2; }
                    out.add(new String(x, q, n * 2, "UTF-16LE"));
                }
            } catch (Throwable t) { out.add(""); }
        }
        return out;
    }

    // ---------- HTTP ----------
    /** ลิงก์ APKPure ตอบ 302 → ลิงก์ CDN จริง (ขอ Range ที่ปลายทางโดยตรง) */
    static String resolve(String url) throws IOException {
        for (int i = 0; i < 5; i++) {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setInstanceFollowRedirects(false);
            c.setRequestMethod("HEAD");
            c.setConnectTimeout(15000); c.setReadTimeout(15000);
            int code = c.getResponseCode();
            String loc = c.getHeaderField("Location");
            c.disconnect();
            if (code / 100 == 3 && loc != null) { url = loc; continue; }
            return url;
        }
        return url;
    }

    static long totalSize(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestProperty("Range", "bytes=0-0");
        c.setConnectTimeout(15000); c.setReadTimeout(15000);
        String cr = c.getHeaderField("Content-Range");
        c.disconnect();
        if (cr == null || !cr.contains("/")) return -1;
        return Long.parseLong(cr.substring(cr.lastIndexOf('/') + 1).trim());
    }

    static byte[] range(String url, long from, long to) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestProperty("Range", "bytes=" + from + "-" + to);
        c.setConnectTimeout(15000); c.setReadTimeout(30000);
        if (c.getResponseCode() != 206) throw new IOException("ไม่รองรับ Range: HTTP " + c.getResponseCode());
        return readAll(c.getInputStream());
    }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
        in.close();
        return o.toByteArray();
    }

    // ---------- protobuf (อ่านอย่างเดียว เฉพาะที่ใช้) ----------
    static List<byte[]> get(byte[] b, int field) {
        List<byte[]> out = new ArrayList<byte[]>();
        int[] i = {0};
        try {
            while (i[0] < b.length) {
                long key = varint(b, i);
                int f = (int) (key >>> 3), t = (int) (key & 7);
                if (t == 0) varint(b, i);
                else if (t == 1) i[0] += 8;
                else if (t == 5) i[0] += 4;
                else if (t == 2) {
                    int len = (int) varint(b, i);
                    if (len < 0 || i[0] + len > b.length) return out;
                    if (f == field) out.add(slice(b, i[0], len));
                    i[0] += len;
                } else return out;   // ไม่ใช่ message ที่อ่านได้
            }
        } catch (Throwable ignored) {}
        return out;
    }

    /** ค่า varint ของ field (ตัวแรก) · -1 = ไม่มี */
    static long varintField(byte[] b, int field) {
        int[] i = {0};
        try {
            while (i[0] < b.length) {
                long key = varint(b, i);
                int f = (int) (key >>> 3), t = (int) (key & 7);
                if (t == 0) { long v = varint(b, i); if (f == field) return v; }
                else if (t == 1) i[0] += 8;
                else if (t == 5) i[0] += 4;
                else if (t == 2) { int len = (int) varint(b, i); i[0] += len; }
                else return -1;
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    /** เวอร์ชันที่ใหม่กว่า versionCode ที่ลงอยู่ (ตัวละ 1 ไฟล์ — มีทั้ง APK/XAPK ใช้ APK ง่ายกว่า) ใหม่ → เก่า */
    static List<Version> newerThan(List<Version> all, long installed) {
        List<Version> out = new ArrayList<Version>();
        for (Version v : all) {
            if (v.code <= installed) continue;
            Version same = null;
            for (Version o : out) if (o.code == v.code) same = o;
            if (same == null) out.add(v);
            else if (!"APK".equalsIgnoreCase(same.type) && "APK".equalsIgnoreCase(v.type)) out.set(out.indexOf(same), v);
        }
        return out;
    }

    private static long varint(byte[] b, int[] i) {
        long r = 0; int s = 0;
        while (true) { int x = b[i[0]++] & 0xff; r |= (long) (x & 0x7f) << s; s += 7; if (x < 0x80) return r; }
    }

    private static String str(byte[] b) { try { return new String(b, "UTF-8"); } catch (Throwable t) { return ""; } }
    private static byte[] slice(byte[] b, int off, int len) { byte[] o = new byte[len]; System.arraycopy(b, off, o, 0, len); return o; }
    static int u16(byte[] b, int p) { return (b[p] & 0xff) | (b[p + 1] & 0xff) << 8; }
    static long u32(byte[] b, int p) { return (b[p] & 0xffL) | (b[p + 1] & 0xffL) << 8 | (b[p + 2] & 0xffL) << 16 | (b[p + 3] & 0xffL) << 24; }
}
