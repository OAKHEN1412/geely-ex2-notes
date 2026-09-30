package com.oakhen.ex2dev;

import java.util.List;

/** ทดสอบ ApkSource กับเซิร์ฟเวอร์จริง (ต้องมีเน็ต): bash devkit/test/run_update_test.sh */
public class ApkSourceTest {
    public static void main(String[] a) throws Exception {
        String[] pkgs = a.length > 0 ? a : new String[]{"com.google.android.youtube", "org.smarttube.stable", "com.disney.disneyplus"};
        for (String pkg : pkgs) {
            long t0 = System.currentTimeMillis();
            List<ApkSource.Version> vs = ApkSource.versions(pkg);
            System.out.println(pkg + ": " + vs.size() + " เวอร์ชัน (" + (System.currentTimeMillis() - t0) + " ms)");
            if (vs.isEmpty()) continue;
            for (int i = 0; i < Math.min(3, vs.size()); i++) System.out.println("   " + vs.get(i));
            // ไล่หาเวอร์ชันใหม่สุดที่ลง Android 9 (sdk 28) ได้ — ทีละตัว (ในแอปใช้ binary search)
            int probes = 0;
            for (ApkSource.Version v : vs) {
                long t1 = System.currentTimeMillis();
                v.minSdk = ApkSource.minSdk(v);
                probes++;
                System.out.println("   probe " + v + " ใช้ " + (System.currentTimeMillis() - t1) + " ms");
                if (v.minSdk >= 0 && v.minSdk <= 28) { System.out.println(" → ใหม่สุดที่ลง Android 9 ได้: " + v.name); break; }
                if (probes >= 12) { System.out.println(" → หยุดที่ 12 probe"); break; }
            }
        }
    }
}
