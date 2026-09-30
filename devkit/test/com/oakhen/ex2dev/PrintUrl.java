package com.oakhen.ex2dev;
public class PrintUrl {   // พิมพ์ลิงก์โหลดของเวอร์ชันที่ต้องการ (ใช้เตรียมทดสอบ) · args: pkg versionName [APK|XAPK]
    public static void main(String[] a) throws Exception {
        String type = a.length > 2 ? a[2] : "APK";
        for (ApkSource.Version v : ApkSource.versions(a[0])) if (v.name.equals(a[1]) && type.equals(v.type)) { System.out.println(v.url); return; }
    }
}
