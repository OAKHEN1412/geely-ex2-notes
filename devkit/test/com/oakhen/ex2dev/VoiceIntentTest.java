package com.oakhen.ex2dev;

import java.util.Arrays;
import java.util.List;

/** ทดสอบ VoiceIntent (ตัวจับคำสั่งเสียง) บนคอม: bash devkit/test/run_voice_test.sh */
public class VoiceIntentTest {
    static int fail = 0;
    static void check(boolean ok, String what) { System.out.println((ok ? "  ok   " : "  FAIL ") + what); if (!ok) fail++; }

    /** พูดว่า → ต้องได้ kind นี้ (+ text/num ถ้าระบุ) */
    static void say(String phrase, VoiceIntent.Kind kind, String text, Double num) {
        VoiceIntent v = VoiceIntent.parse(phrase);
        boolean ok = v.kind == kind
                && (text == null || text.equals(v.text))
                && (num == null || (v.num != null && Math.abs(v.num - num) < 1e-9));
        check(ok, "\"" + phrase + "\" → " + v + (ok ? "" : "   (ต้องการ " + kind + (text != null ? "[" + text + "]" : "") + (num != null ? "=" + num : "") + ")"));
    }
    static void say(String phrase, VoiceIntent.Kind kind) { say(phrase, kind, null, null); }

    static void splitOf(String phrase, String... apps) {
        VoiceIntent v = VoiceIntent.parse(phrase);
        boolean ok = v.kind == VoiceIntent.Kind.SPLIT && v.apps.equals(Arrays.asList(apps));
        check(ok, "\"" + phrase + "\" → " + v + (ok ? "" : "   (ต้องการ " + Arrays.asList(apps) + ")"));
    }

    static final List<String> LABELS = Arrays.asList("YouTube", "YouTube Music", "Maps", "Spotify", "CarPlay", "Chrome", "การตั้งค่า", "LINE", "Gallery");
    static final List<String> PKGS = Arrays.asList("com.google.android.youtube", "com.google.android.apps.youtube.music",
            "com.google.android.apps.maps", "com.spotify.music", "com.njda.carplay", "com.android.chrome", "com.android.settings", "jp.naver.line.android", "com.android.gallery3d");
    static void app(String spoken, String expectLabel) {
        int i = VoiceIntent.matchApp(spoken, LABELS, PKGS);
        String got = i < 0 ? null : LABELS.get(i);
        check(expectLabel == null ? got == null : expectLabel.equals(got), "แอพ \"" + spoken + "\" → " + got + (expectLabel == null ? " (ต้องไม่พบ)" : " (ต้อง " + expectLabel + ")"));
    }

    public static void main(String[] a) {
        System.out.println("1) เปิดแอพ");
        say("เปิดยูทูป", VoiceIntent.Kind.OPEN_APP, "ยูทูป", null);
        say("เปิด ยูทูป ให้หน่อย", VoiceIntent.Kind.OPEN_APP, "ยูทูป", null);
        say("ช่วยเปิดแผนที่ครับ", VoiceIntent.Kind.OPEN_APP, "แผนที่", null);
        say("เปิดแอพ Spotify", VoiceIntent.Kind.OPEN_APP, "Spotify", null);
        say("open YouTube Music", VoiceIntent.Kind.OPEN_APP, "YouTube Music", null);

        System.out.println("2) แบ่งจอ");
        splitOf("แบ่งจอ ยูทูป กับ แมพ", "ยูทูป", "แมพ");
        splitOf("แบ่งจอยูทูปกับคาร์เพลย์และแผนที่", "ยูทูป", "คาร์เพลย์", "แผนที่");
        splitOf("แบ่งจอระหว่าง Spotify กับ Google Maps", "Spotify", "Google Maps");
        say("แบ่งจอ", VoiceIntent.Kind.SPLIT);
        check(VoiceIntent.parse("แบ่งจอ").apps.isEmpty(), "\"แบ่งจอ\" เฉย ๆ → apps ว่าง (VoiceAssistant ถามกลับ)");

        System.out.println("3) นำทาง");
        say("ไป ตลาดไท", VoiceIntent.Kind.NAVIGATE, "ตลาดไท", null);
        say("นำทางไปสยามพารากอน", VoiceIntent.Kind.NAVIGATE, "สยามพารากอน", null);
        say("นำทางไป เซ็นทรัล ลาดพร้าว ครับ", VoiceIntent.Kind.NAVIGATE, "เซ็นทรัล ลาดพร้าว", null);

        System.out.println("4) เพลง");
        say("เพลงถัดไป", VoiceIntent.Kind.MEDIA_NEXT);
        say("ข้ามเพลง", VoiceIntent.Kind.MEDIA_NEXT);
        say("เพลงก่อนหน้า", VoiceIntent.Kind.MEDIA_PREV);
        say("เล่น", VoiceIntent.Kind.MEDIA_PLAY);
        say("เปิดเพลง", VoiceIntent.Kind.MEDIA_PLAY);
        say("หยุด", VoiceIntent.Kind.MEDIA_PAUSE);
        say("หยุดเพลงหน่อย", VoiceIntent.Kind.MEDIA_PAUSE);
        say("เปิดเพลง ใจสั่น", VoiceIntent.Kind.MEDIA_SEARCH, "ใจสั่น", null);
        say("เปิดเพลงของ Taylor Swift", VoiceIntent.Kind.MEDIA_SEARCH, "Taylor Swift", null);

        System.out.println("5) รถ");
        say("เปิดแอร์", VoiceIntent.Kind.AC_ON);
        say("ปิดแอร์", VoiceIntent.Kind.AC_OFF);
        say("เปิด A/C", VoiceIntent.Kind.AC_ON);
        say("อุณหภูมิ 24 องศา", VoiceIntent.Kind.TEMP, null, 24.0);
        say("ตั้งอุณหภูมิยี่สิบสี่องศา", VoiceIntent.Kind.TEMP, null, 24.0);
        say("แอร์ ๒๒ องศา", VoiceIntent.Kind.TEMP, null, 22.0);
        say("อุณหภูมิ 23.5 องศา", VoiceIntent.Kind.TEMP, null, 23.5);
        say("พัดลม 3", VoiceIntent.Kind.FAN, null, 3.0);
        say("พัดลมห้า", VoiceIntent.Kind.FAN, null, 5.0);
        say("สลับโหมดขับ", VoiceIntent.Kind.DRIVE_MODE);
        say("รีเจน", VoiceIntent.Kind.REGEN);
        say("สลับรีเจน", VoiceIntent.Kind.REGEN);

        System.out.println("6) ยกเลิก/ปิด");
        say("ยกเลิก", VoiceIntent.Kind.CANCEL);
        say("ปิด", VoiceIntent.Kind.CANCEL);
        say("ไม่เอาแล้ว", VoiceIntent.Kind.CANCEL);

        System.out.println("7) ไม่ตรงอะไร → UNKNOWN (ให้ LLM ต่อ)");
        say("วันนี้อากาศเป็นยังไง", VoiceIntent.Kind.UNKNOWN);
        say("ร้านกาแฟแถวนี้มีไหม", VoiceIntent.Kind.UNKNOWN);
        say("พัดลม", VoiceIntent.Kind.UNKNOWN);   // ไม่มีเลข
        say("", VoiceIntent.Kind.UNKNOWN);
        say("   ", VoiceIntent.Kind.UNKNOWN);

        System.out.println("8) เลขไทย");
        check(eq(VoiceIntent.parseNumber("สิบหกองศา"), 16), "สิบหก = 16");
        check(eq(VoiceIntent.parseNumber("ยี่สิบเอ็ด"), 21), "ยี่สิบเอ็ด = 21");
        check(eq(VoiceIntent.parseNumber("สามสิบสอง"), 32), "สามสิบสอง = 32");
        check(eq(VoiceIntent.parseNumber("สอง สี่"), 24), "สอง สี่ = 24");
        check(eq(VoiceIntent.parseNumber("เจ็ด"), 7), "เจ็ด = 7");
        check(VoiceIntent.parseNumber("ไม่มีตัวเลข") == null, "ไม่มีเลข = null");

        System.out.println("9) เทียบชื่อแอพ (ชื่อบนจอ + คำเรียกเล่น)");
        app("ยูทูป", "YouTube");
        app("youtube", "YouTube");
        app("ยูทูป มิวสิค", "YouTube Music");
        app("แมพ", "Maps");
        app("แผนที่", "Maps");
        app("Google Maps", "Maps");
        app("สปอติฟาย", "Spotify");
        app("คาร์เพลย์", "CarPlay");
        app("ตั้งค่า", "การตั้งค่า");
        app("ไลน์", "LINE");
        app("ซูเปอร์มาริโอ", null);

        System.out.println("10) ผลจาก LLM");
        check(VoiceIntent.fromLlm("open_app", "ยูทูป", null, null).kind == VoiceIntent.Kind.OPEN_APP, "open_app");
        check(VoiceIntent.fromLlm("open_app", " ", null, null).kind == VoiceIntent.Kind.UNKNOWN, "open_app ไม่มีชื่อ = UNKNOWN");
        check(VoiceIntent.fromLlm("split", null, Arrays.asList("a", "b"), null).kind == VoiceIntent.Kind.SPLIT, "split 2 แอพ");
        check(VoiceIntent.fromLlm("split", null, Arrays.asList("a"), null).kind == VoiceIntent.Kind.UNKNOWN, "split 1 แอพ = UNKNOWN");
        check(VoiceIntent.fromLlm("temp", null, null, 25.0).num == 25.0, "temp 25");
        check(VoiceIntent.fromLlm("temp", null, null, null).kind == VoiceIntent.Kind.UNKNOWN, "temp ไม่มีค่า = UNKNOWN");
        check(VoiceIntent.fromLlm("music", "x", null, null).kind == VoiceIntent.Kind.MEDIA_SEARCH, "music + คำค้น");
        check(VoiceIntent.fromLlm("music", null, null, null).kind == VoiceIntent.Kind.MEDIA_PLAY, "music ไม่มีคำค้น = เล่น");
        check(VoiceIntent.fromLlm("DRIVE", null, null, null).kind == VoiceIntent.Kind.DRIVE_MODE, "drive (ตัวใหญ่ก็ได้)");
        check(VoiceIntent.fromLlm("fly_to_moon", null, null, null).kind == VoiceIntent.Kind.UNKNOWN, "intent แปลก = UNKNOWN");
        check("{\"intent\":\"next\"}".equals(VoiceIntent.extractJson("```json\n{\"intent\":\"next\"}\n```")), "ตัด JSON จาก ``` fence");
        check("{\"a\":{\"b\":1}}".equals(VoiceIntent.extractJson("ได้เลย {\"a\":{\"b\":1}} ครับ")), "ตัด JSON ซ้อน");
        check(VoiceIntent.extractJson("ไม่มี json") == null, "ไม่มี JSON = null");

        System.out.println(fail == 0 ? "ALL PASS" : fail + " FAIL");
        System.exit(fail == 0 ? 0 : 1);
    }

    static boolean eq(Double d, int v) { return d != null && Math.abs(d - v) < 1e-9; }
}
