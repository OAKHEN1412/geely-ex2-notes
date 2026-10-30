package com.oakhen.ex2dev;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * คำสั่งเสียง → intent (Java ล้วน ไม่มี Android — ทดสอบบนคอมได้: bash devkit/test/run_voice_test.sh)
 *  - parse(): ตัวจับคีย์เวิร์ดไทย (+อังกฤษไม่กี่คำ) · ไม่ตรง = UNKNOWN → VoiceAssistant ค่อยถาม LLM
 *  - fromLlm(): แปลงผลจาก LLM (ชุด intent เดียวกัน) เป็นวัตถุเดียวกัน
 *  - matchApp(): เทียบชื่อที่พูดกับชื่อแอพในเครื่อง (ไทย/อังกฤษ + คำเรียกเล่น เช่น ยูทูป)
 * ลำดับตรวจสำคัญ: "เปิด" มี "ปิด" ซ่อนอยู่ (เ-ป-ิ-ด) → ตรวจ "เปิด" ก่อนเสมอ · คำสั่งรถ/เพลงต้องมาก่อน "เปิด <แอพ>"
 */
final class VoiceIntent {
    enum Kind {
        OPEN_APP, SPLIT, NAVIGATE,
        MEDIA_NEXT, MEDIA_PREV, MEDIA_PLAY, MEDIA_PAUSE, MEDIA_SEARCH,
        AC_ON, AC_OFF, TEMP, FAN, DRIVE_MODE, REGEN,
        CANCEL, UNKNOWN
    }

    final Kind kind;
    /** OPEN_APP: ชื่อแอพ · NAVIGATE: สถานที่ · MEDIA_SEARCH: คำค้น */
    final String text;
    /** SPLIT: ชื่อแอพ 2–3 ตัว */
    final List<String> apps;
    /** TEMP: °C · FAN: ระดับ */
    final Double num;

    private VoiceIntent(Kind kind, String text, List<String> apps, Double num) {
        this.kind = kind; this.text = text; this.apps = apps; this.num = num;
    }

    static VoiceIntent of(Kind k) { return new VoiceIntent(k, null, null, null); }
    static VoiceIntent of(Kind k, String text) { return new VoiceIntent(k, text, null, null); }
    static VoiceIntent of(Kind k, Double num) { return new VoiceIntent(k, null, null, num); }
    static VoiceIntent split(List<String> apps) { return new VoiceIntent(Kind.SPLIT, null, apps, null); }

    public String toString() {
        return kind + (text != null ? "[" + text + "]" : "") + (apps != null ? apps.toString() : "") + (num != null ? "=" + num : "");
    }

    // ---------- ตัวจับคีย์เวิร์ด ----------
    private static final String[] CANCEL_WORDS = {"ยกเลิก", "ปิด", "ไม่เอา", "ไม่เอาแล้ว", "พอแล้ว", "ปิดผู้ช่วย", "ออก", "cancel"};
    private static final String[] NEXT_WORDS = {"เพลงถัดไป", "เพลงต่อไป", "ข้ามเพลง", "เพลงหน้า", "ถัดไป", "next"};
    private static final String[] PREV_WORDS = {"เพลงก่อนหน้า", "ย้อนเพลง", "เพลงที่แล้ว", "เพลงก่อน", "ก่อนหน้า", "previous", "prev"};
    private static final String[] PLAY_WORDS = {"เล่น", "เล่นต่อ", "เล่นเพลง", "เล่นเพลงต่อ", "เปิดเพลง", "play"};
    private static final String[] PAUSE_WORDS = {"หยุด", "หยุดเพลง", "หยุดเล่น", "หยุดก่อน", "พัก", "พักเพลง", "ปิดเพลง", "pause", "stop"};

    private static final Pattern P_SPLIT = Pattern.compile("(แบ่งจอ|แยกจอ)\\s*(?:ระหว่าง)?\\s*(.*)");
    private static final Pattern P_MUSIC = Pattern.compile("^(เปิดเพลง|เล่นเพลง|ฟังเพลง|play)\\s*(?:ของ|ชื่อ)?\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_NAV = Pattern.compile("^(นำทางไปที่|นำทางไปยัง|นำทางไป|นำทาง|พาไปที่|พาไป|ไปที่|ไป|navigate to|go to)\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_OPEN = Pattern.compile("^(เปิดแอปพลิเคชั่น|เปิดแอปพลิเคชัน|เปิดแอพ|เปิดแอป|เปิด|open|launch)\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_LEAD = Pattern.compile("^(ช่วย|กรุณา|รบกวน|ขอ(?=เปิด|ให้|แบ่ง|นำทาง|ไป))\\s*");
    private static final Pattern P_TAIL = Pattern.compile("\\s*(ให้หน่อย|หน่อย|ด้วย|ครับ|ค่ะ|คะ|นะครับ|นะคะ|นะ|จ้ะ|จ้า)$");
    private static final Pattern P_SEP = Pattern.compile("\\s*(?:กับ|และ|แล้วก็|กะ|\\+|&|,|\\band\\b)\\s*");
    private static final Pattern P_DEC = Pattern.compile("\\d+(?:\\.\\d+)?");

    /** ข้อความที่ได้ยิน → intent (ไม่ตรงอะไรเลย = UNKNOWN) */
    static VoiceIntent parse(String raw) {
        String t = clean(raw);
        if (t.isEmpty()) return of(Kind.UNKNOWN);
        String c = compact(t);

        // 1) ยกเลิก
        if (c.startsWith("ยกเลิก") || oneOf(c, CANCEL_WORDS)) return of(Kind.CANCEL);

        // 2) แบ่งจอ
        Matcher m = P_SPLIT.matcher(t);
        if (m.find()) {
            List<String> apps = new ArrayList<String>();
            for (String s : P_SEP.split(m.group(2))) {
                s = s.trim();
                if (!s.isEmpty() && apps.size() < 3) apps.add(s);
            }
            return split(apps);
        }

        // 3) รถ (ก่อน "เปิด <แอพ>" — "เปิดแอร์" ต้องไม่ถูกมองเป็นชื่อแอพ)
        Double n = parseNumber(t);
        if (has(c, "พัดลม") || has(c, "แรงลม")) {
            return n != null ? of(Kind.FAN, n) : of(Kind.UNKNOWN);
        }
        if (has(c, "อุณหภูมิ") || has(c, "องศา") || (has(c, "แอร์") && n != null)) {
            return n != null ? of(Kind.TEMP, n) : of(Kind.UNKNOWN);
        }
        if (has(c, "แอร์")) {
            if (has(c, "เปิด")) return of(Kind.AC_ON);   // ตรวจ "เปิด" ก่อน "ปิด" (เปิด มี ปิด อยู่ข้างใน)
            if (has(c, "ปิด")) return of(Kind.AC_OFF);
            return of(Kind.UNKNOWN);
        }
        if (has(c, "โหมดขับ") || has(c, "โหมดการขับ") || has(c, "สลับโหมด") || has(c, "เปลี่ยนโหมด")) return of(Kind.DRIVE_MODE);
        if (has(c, "รีเจน") || has(c, "regen")) return of(Kind.REGEN);

        // 4) เพลง
        if (hasAny(c, NEXT_WORDS)) return of(Kind.MEDIA_NEXT);
        if (hasAny(c, PREV_WORDS)) return of(Kind.MEDIA_PREV);
        if (oneOf(c, PLAY_WORDS)) return of(Kind.MEDIA_PLAY);
        if (oneOf(c, PAUSE_WORDS)) return of(Kind.MEDIA_PAUSE);
        m = P_MUSIC.matcher(t);
        if (m.matches() && !m.group(2).trim().isEmpty()) return of(Kind.MEDIA_SEARCH, m.group(2).trim());

        // 5) นำทาง
        m = P_NAV.matcher(t);
        if (m.matches() && !m.group(2).trim().isEmpty()) return of(Kind.NAVIGATE, m.group(2).trim());

        // 6) เปิดแอพ
        m = P_OPEN.matcher(t);
        if (m.matches() && !m.group(2).trim().isEmpty()) return of(Kind.OPEN_APP, m.group(2).trim());

        return of(Kind.UNKNOWN);
    }

    // ---------- ผลจาก LLM ----------
    /**
     * intent ที่ LLM คืนมา: open_app(arg=แอพ) · split(apps) · navigate(arg=สถานที่) · next · prev · play · pause ·
     * music(arg=คำค้น) · ac_on · ac_off · temp(num) · fan(num) · drive · regen · cancel · unknown
     * ขาดอาร์กิวเมนต์ที่ต้องใช้ = UNKNOWN
     */
    static VoiceIntent fromLlm(String intent, String arg, List<String> apps, Double num) {
        if (intent == null) return of(Kind.UNKNOWN);
        String i = intent.trim().toLowerCase();
        boolean a = arg != null && !arg.trim().isEmpty();
        if (i.equals("open_app")) return a ? of(Kind.OPEN_APP, arg.trim()) : of(Kind.UNKNOWN);
        if (i.equals("navigate")) return a ? of(Kind.NAVIGATE, arg.trim()) : of(Kind.UNKNOWN);
        if (i.equals("music")) return a ? of(Kind.MEDIA_SEARCH, arg.trim()) : of(Kind.MEDIA_PLAY);
        if (i.equals("split")) {
            List<String> out = new ArrayList<String>();
            if (apps != null) for (String s : apps) if (s != null && !s.trim().isEmpty() && out.size() < 3) out.add(s.trim());
            return out.size() >= 2 ? split(out) : of(Kind.UNKNOWN);
        }
        if (i.equals("temp")) return num != null ? of(Kind.TEMP, num) : of(Kind.UNKNOWN);
        if (i.equals("fan")) return num != null ? of(Kind.FAN, num) : of(Kind.UNKNOWN);
        if (i.equals("next")) return of(Kind.MEDIA_NEXT);
        if (i.equals("prev")) return of(Kind.MEDIA_PREV);
        if (i.equals("play")) return of(Kind.MEDIA_PLAY);
        if (i.equals("pause")) return of(Kind.MEDIA_PAUSE);
        if (i.equals("ac_on")) return of(Kind.AC_ON);
        if (i.equals("ac_off")) return of(Kind.AC_OFF);
        if (i.equals("drive")) return of(Kind.DRIVE_MODE);
        if (i.equals("regen")) return of(Kind.REGEN);
        if (i.equals("cancel")) return of(Kind.CANCEL);
        return of(Kind.UNKNOWN);
    }

    /** ตัดเอาก้อน JSON ออกจากคำตอบ LLM (อาจมี ```json ครอบ หรือข้อความนำหน้า) · ไม่มี = null */
    static String extractJson(String s) {
        if (s == null) return null;
        int a = s.indexOf('{'), b = s.lastIndexOf('}');
        return a >= 0 && b > a ? s.substring(a, b + 1) : null;
    }

    // ---------- เทียบชื่อแอพ ----------
    /** คำเรียกเล่น → ชื่อในเครื่อง (ตัวเล็ก ไม่เว้นวรรค) · ยาวก่อนสั้น */
    private static final String[][] ALIAS = {
            {"กูเกิลแมพ", "maps"}, {"กูเกิ้ลแมพ", "maps"}, {"กูเกิลแมป", "maps"}, {"กูเกิลแผนที่", "maps"},
            {"คาร์เพลย์", "carplay"}, {"คาร์เพลย", "carplay"}, {"คาเพลย์", "carplay"},
            {"สปอติฟาย", "spotify"}, {"สปอทิฟาย", "spotify"}, {"สปอตติฟาย", "spotify"}, {"สโปติฟาย", "spotify"},
            {"เน็ตฟลิกซ์", "netflix"}, {"ดิสนีย์", "disney"},
            {"ยูทูปส์", "youtube"}, {"ยูทูป", "youtube"}, {"ยูทูบ", "youtube"},
            {"มิวสิค", "music"}, {"มิวสิก", "music"},
            {"แผนที่", "maps"}, {"แมพ", "maps"}, {"แมป", "maps"},
            {"โครม", "chrome"}, {"ไลน์", "line"}, {"ตั้งค่า", "settings"}, {"กล้อง", "camera"},
    };

    /** ดัชนีของชื่อที่ตรงที่สุด (-1 = ไม่พบ) · pkgs ใช้ช่วยแอพที่ชื่อบนจอไม่ตรงคำเรียก (เช่น CarPlay) */
    static int matchApp(String spoken, List<String> labels, List<String> pkgs) {
        String q = compact(spoken);
        for (String[] a : ALIAS) q = q.replace(a[0], a[1]);
        if (q.isEmpty()) return -1;
        int best = -1, bestScore = 49;   // ต้องได้ ≥ 50
        for (int i = 0; i < labels.size(); i++) {
            String l = compact(labels.get(i));
            int s = 0;
            if (l.isEmpty()) continue;
            if (l.equals(q)) s = 100;
            else if (l.contains(q)) s = 80 - Math.min(30, l.length() - q.length());
            else if (q.contains(l) && l.length() >= 3) s = 70 - Math.min(30, q.length() - l.length());
            else {
                double sim = 1.0 - (double) lev(l, q) / Math.max(l.length(), q.length());
                if (sim >= 0.75) s = (int) (40 + sim * 30);
            }
            if (pkgs != null && q.length() >= 5 && pkgs.get(i) != null && pkgs.get(i).toLowerCase().contains(q)) s = Math.max(s, 55);
            if (s > bestScore) { bestScore = s; best = i; }
        }
        return best;
    }

    // ---------- เลขไทย/อารบิก ----------
    private static final String[] NUM_WORDS = {"ยี่สิบ", "สิบ", "ศูนย์", "หนึ่ง", "เอ็ด", "สอง", "สาม", "สี่", "ห้า", "หก", "เจ็ด", "แปด", "เก้า"};
    private static final int[] NUM_VALS = {20, 10, 0, 1, 1, 2, 3, 4, 5, 6, 7, 8, 9};   // 20 = ยี่สิบ · 10 = สิบ (ตัวคูณหลักสิบ)

    /** ตัวเลขแรกในประโยค: 24 · ๒๔ · 24.5 · "ยี่สิบสี่" · "สิบหก" · "สาม" (ไม่เจอ = null) */
    static Double parseNumber(String s) {
        if (s == null) return null;
        StringBuilder sb = new StringBuilder();
        for (char ch : s.toCharArray()) sb.append(ch >= '๐' && ch <= '๙' ? (char) ('0' + (ch - '๐')) : ch);
        String x = sb.toString();
        Matcher m = P_DEC.matcher(x);
        if (m.find()) return Double.valueOf(m.group());
        for (int start = 0; start < x.length(); start++) {
            List<Integer> idx = new ArrayList<Integer>();   // ลำดับ token ที่เรียงติดกัน (ข้ามช่องว่าง)
            int p = start;
            while (p < x.length()) {
                if (x.charAt(p) == ' ' && !idx.isEmpty()) { p++; continue; }
                int hit = -1;
                for (int k = 0; k < NUM_WORDS.length && hit < 0; k++) if (x.startsWith(NUM_WORDS[k], p)) hit = k;
                if (hit < 0) break;
                idx.add(hit);
                p += NUM_WORDS[hit].length();
            }
            if (!idx.isEmpty()) return (double) wordsValue(idx);
        }
        return null;
    }

    private static int wordsValue(List<Integer> idx) {
        int n = idx.size();
        int f = NUM_VALS[idx.get(0)];
        boolean f20 = idx.get(0) == 0, f10 = idx.get(0) == 1;
        if (f20 || f10) {   // ยี่สิบ / สิบ (+ หลักหน่วย)
            int v = f20 ? 20 : 10;
            if (n > 1 && idx.get(1) > 1) v += NUM_VALS[idx.get(1)];
            return v;
        }
        if (n > 1 && idx.get(1) == 1) {   // <หลัก> สิบ (+ หน่วย)
            int v = f * 10;
            if (n > 2 && idx.get(2) > 1) v += NUM_VALS[idx.get(2)];
            return v;
        }
        if (n > 1 && idx.get(1) > 1) return f * 10 + NUM_VALS[idx.get(1)];   // "สองสี่" = 24
        return f;
    }

    // ---------- ตัวช่วย ----------
    /** ตัดช่องว่างซ้ำ + คำสุภาพหัว/ท้าย (ครับ ค่ะ หน่อย ช่วย…) */
    static String clean(String raw) {
        if (raw == null) return "";
        String t = raw.trim().replaceAll("\\s+", " ");
        for (int i = 0; i < 3; i++) {
            String u = P_TAIL.matcher(t).replaceFirst("").trim();
            u = P_LEAD.matcher(u).replaceFirst("").trim();
            if (u.equals(t)) break;
            t = u;
        }
        return t;
    }

    /** ตัวเล็ก ไม่มีช่องว่าง/เครื่องหมาย — STT ไทยเว้นวรรคไม่แน่นอน ("เปิด ยูทูป" = "เปิดยูทูป") */
    static String compact(String s) {
        if (s == null) return "";
        String x = s.toLowerCase().replace("a/c", "แอร์").replace("เอซี", "แอร์");
        return x.replaceAll("[\\s.,!?'\"_\\-/]+", "");
    }

    private static boolean has(String c, String w) { return c.contains(w); }
    private static boolean hasAny(String c, String[] ws) { for (String w : ws) if (c.contains(w)) return true; return false; }
    private static boolean oneOf(String c, String[] ws) { return Arrays.asList(ws).contains(c); }

    private static int lev(String a, String b) {
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++)
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            int[] tmp = prev; prev = cur; cur = tmp;
        }
        return prev[b.length()];
    }
}
