package com.oakhen.ex2dev;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.widget.Toast;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ปุ่มพวงมาลัย (SWC) บนจอนี้มาเป็นค่า VHAL กลุ่ม 0x4102xxxx (CentralEXAuto: 0x41020400/0x41020200 ค่า 2 = กด 1 ครั้ง,
 * 0x41020500 = ปุ่มไมค์) — ปุ่มไหนเป็นรหัสไหนต้องดูบนรถ → มีโหมดดักปุ่ม (บันทึกทุกค่าที่เข้ามา ให้หน้า OAKHEN แสดง)
 * แต่ละรหัสตั้งการกระทำได้ (Prefs "swc_<hex>"): ไม่ทำอะไร (ค่าเริ่ม — ไม่ทับการทำงานเดิมของรถ) / สลับโหมดขับ / สลับรีเจน /
 * เพลงถัดไป / เพลงก่อนหน้า / เล่น-หยุด (ส่งปุ่มมีเดียด้วย injectInputEvent — ถึงแอพเพลงที่เล่นอยู่ เช่น YT Music)
 * callback ของ CarPropertyManager เป็น interface ใน android.car (ไม่มีตอนคอมไพล์) → สร้างด้วย java.lang.reflect.Proxy
 */
final class SwcWatch {
    static final String TAG = "EX2Swc";
    static final int GROUP = 0x41020000, GROUP_MASK = 0xFFFF0000;
    static final int CLICK = 2;

    /** ปุ่มที่รู้จัก (จับจาก log ของ KeyServer ในรถ 30 ก.ย.) · ค่า 1 = กดลง, 2 = ปล่อย */
    static final int[] KNOWN = {0x41020400, 0x41020200, 0x41020100, 0x41020300, 0x41020500};
    static final String[] KNOWN_NAMES = {"ซ้าย", "ขวา", "บน (เสียง +)", "ล่าง (เสียง −)", "สั่งเสียง"};
    static final long LONG_MS = 700;
    static String name(int prop) {
        for (int i = 0; i < KNOWN.length; i++) if (KNOWN[i] == prop) return KNOWN_NAMES[i];
        return String.format("0x%08X", prop);
    }

    static final String[] ACTIONS = {"none", "drive", "regen", "next", "prev", "play", "voice"};
    static final String[] ACTION_LABELS = {"ไม่ทำอะไร", "สลับโหมดขับ", "สลับรีเจน", "เพลงถัดไป", "เพลงก่อนหน้า", "เล่น/หยุด", "ผู้ช่วยสั่งเสียง"};
    /** ปุ่มสั่งเสียง — กดสั้นเปิดผู้ช่วยสั่งเสียงเป็นค่าเริ่ม (ปุ่มอื่นค่าเริ่ม = ไม่ทำอะไร) */
    static final int VOICE_KEY = 0x41020500;

    /** รหัสที่เห็นแล้ว → ค่าล่าสุด/จำนวนครั้ง (ให้หน้า OAKHEN แสดงตอนดักปุ่ม) */
    static final class Seen { int prop, last, count; long at; boolean lastLong; }
    private static final Map<Integer, Seen> sSeen = new LinkedHashMap<Integer, Seen>();
    private static final java.util.Set<Integer> sFirstDone = new java.util.HashSet<Integer>();
    private static volatile String sStatus = "ยังไม่เริ่ม";
    private static boolean sStarted;
    private static Handler sMain;

    private SwcWatch() {}

    static String status() { return sStatus; }

    static synchronized List<Seen> seen() {
        List<Seen> out = new ArrayList<Seen>();
        for (Seen s : sSeen.values()) { Seen c = new Seen(); c.prop = s.prop; c.last = s.last; c.count = s.count; c.at = s.at; c.lastLong = s.lastLong; out.add(c); }
        return out;
    }

    /** longPress=false: กดสั้น · true: กดค้าง ≥ 0.7 วิ */
    static String action(Context c, int prop, boolean longPress) {
        return Prefs.of(c).getString(key(prop, longPress), prop == VOICE_KEY && !longPress ? "voice" : "none");
    }
    static void setAction(Context c, int prop, boolean longPress, String a) { Prefs.of(c).edit().putString(key(prop, longPress), a).apply(); }
    private static String key(int prop, boolean longPress) { return (longPress ? "swcL_" : "swc_") + Integer.toHexString(prop); }
    private static final java.util.Map<Integer, Long> sDownAt = new java.util.HashMap<Integer, Long>();

    /** เริ่มฟังปุ่ม (DevService) · ต่อ VHAL ไม่ได้ → ลองใหม่ทุก 10 วิ สูงสุด ~5 นาที */
    static void start(Context ctx) {
        final Context app = ctx.getApplicationContext();
        if (sMain == null) sMain = new Handler(Looper.getMainLooper());
        Vhal.thread().post(new Runnable() {
            int tries;
            public void run() {
                if (sStarted) return;
                if (register(app)) return;
                if (++tries < 30) Vhal.thread().postDelayed(this, 10000);
            }
        });
    }

    private static boolean register(final Context app) {
        Object cpm = Vhal.cpm(app);
        if (cpm == null) { sStatus = "เชื่อมต่อระบบรถไม่ได้"; return false; }
        try {
            List<Integer> props = new ArrayList<Integer>();
            List<?> cfgs = (List<?>) cpm.getClass().getMethod("getPropertyList").invoke(cpm);
            for (Object cfg : cfgs) {
                int id = (Integer) cfg.getClass().getMethod("getPropertyId").invoke(cfg);
                if ((id & GROUP_MASK) == GROUP) props.add(id);
            }
            // รายการของรถไม่มีปุ่มบน/ล่าง (ได้แค่ 3 รหัส) แต่ KeyServer ของรถฟังได้ → ลงทะเบียนชุดที่รู้จักเพิ่มเสมอ
            for (int k : KNOWN) if (!props.contains(k)) props.add(k);
            final Class<?> cbCls = Class.forName("android.car.hardware.property.CarPropertyManager$CarPropertyEventCallback");
            Object cb = Proxy.newProxyInstance(cbCls.getClassLoader(), new Class<?>[]{cbCls}, new InvocationHandler() {
                public Object invoke(Object self, Method m, Object[] a) {
                    String n = m.getName();
                    if ("onChangeEvent".equals(n)) { onEvent(app, a[0]); return null; }
                    if ("onErrorEvent".equals(n)) return null;
                    if ("hashCode".equals(n)) return System.identityHashCode(self);
                    if ("equals".equals(n)) return self == a[0];
                    if ("toString".equals(n)) return "SwcWatch.callback";
                    return null;
                }
            });
            Method reg = cpm.getClass().getMethod("registerCallback", cbCls, int.class, float.class);
            int ok = 0;
            for (int p : props) {
                try { if ((Boolean) reg.invoke(cpm, cb, p, 0f)) ok++; } catch (Throwable t) { Log.w(TAG, "register 0x" + Integer.toHexString(p) + ": " + t); }
            }
            sStarted = ok > 0;
            sStatus = sStarted ? "ฟังปุ่มอยู่ " + ok + " รหัส" : "ลงทะเบียนปุ่มไม่ได้";
            Log.i(TAG, sStatus + " " + props);
            return sStarted;
        } catch (Throwable t) {
            sStatus = "ลงทะเบียนปุ่มไม่ได้: " + t.getClass().getSimpleName();
            Log.w(TAG, "register", t);
            return false;
        }
    }

    /** เธรด main (callback ของ Car) */
    private static void onEvent(final Context app, Object v) {
        int prop, val;
        try {
            prop = (Integer) v.getClass().getMethod("getPropertyId").invoke(v);
            Object o = v.getClass().getMethod("getValue").invoke(v);
            if (!(o instanceof Integer)) return;
            val = (Integer) o;
        } catch (Throwable t) { return; }
        boolean first;
        synchronized (SwcWatch.class) {
            first = !sFirstDone.contains(prop);
            sFirstDone.add(prop);
            if (!first) {
                Seen s = sSeen.get(prop);
                if (s == null) { s = new Seen(); s.prop = prop; sSeen.put(prop, s); }
                s.last = val; s.count++; s.at = System.currentTimeMillis();
            }
        }
        Log.i(TAG, String.format("ปุ่ม 0x%08x = %d%s", prop, val, first ? " (ค่าตั้งต้นตอนลงทะเบียน — ข้าม)" : ""));
        // ค่าแรกหลัง registerCallback = ค่าปัจจุบัน ไม่ใช่การกด (บนรถ: 0x41020200 = 2 ทันที) → ถ้าไม่ข้าม สตาร์ทรถทีไรสลับโหมดเอง
        if (first) return;
        long now = SystemClock.uptimeMillis();
        if (val == 1) { sDownAt.put(prop, now); return; }
        if (val != CLICK) return;
        Long down = sDownAt.remove(prop);
        boolean longPress = down != null && now - down >= LONG_MS;
        synchronized (SwcWatch.class) { Seen s = sSeen.get(prop); if (s != null) s.lastLong = longPress; }
        String a = action(app, prop, longPress);
        if ("drive".equals(a)) cycle(app, Vhal.DRIVE_MODE, Vhal.DRIVE_AREAS,
                new int[]{Vhal.DRIVE_ECO, Vhal.DRIVE_COMFORT, Vhal.DRIVE_DYNAMIC}, new String[]{"ECO", "COMFORT", "SPORT"}, "โหมดขับ");
        else if ("regen".equals(a)) cycle(app, Vhal.REGEN, Vhal.REGEN_AREAS,
                new int[]{Vhal.REGEN_LOW, Vhal.REGEN_MID, Vhal.REGEN_HIGH}, new String[]{"ต่ำ", "กลาง", "สูง"}, "รีเจน");
        else if ("next".equals(a)) media(KeyEvent.KEYCODE_MEDIA_NEXT);
        else if ("prev".equals(a)) media(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        else if ("play".equals(a)) media(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        else if ("voice".equals(a)) VoiceAssistant.toggle(app);
    }

    /** ผลของ cycle สำหรับผู้เรียกที่ไม่อยากได้ Toast (ผู้ช่วยสั่งเสียงเอาไปพูดเอง) · เรียกบน main thread */
    interface Done { void on(boolean ok, String msg); }

    private static void cycle(Context app, int prop, int[] areas, int[] vals, String[] names, String what) {
        cycle(app, prop, areas, vals, names, what, null);
    }

    /** อ่านค่าปัจจุบันจากรถ → ตั้งค่าถัดไปในรอบ → แจ้งบนจอ (done == null = Toast) */
    static void cycle(final Context app, final int prop, final int[] areas, final int[] vals, final String[] names, final String what, final Done done) {
        Vhal.thread().post(new Runnable() {
            public void run() {
                Integer cur = Vhal.getInt(app, prop, areas);
                int i = 0;
                if (cur != null) for (int k = 0; k < vals.length; k++) if (vals[k] == cur) i = (k + 1) % vals.length;
                final boolean ok = Vhal.setInt(app, prop, vals[i], areas);
                final String msg = what + ": " + (ok ? names[i] : "สั่งไม่ได้");
                sMain.post(new Runnable() {
                    public void run() {
                        if (done != null) done.on(ok, msg); else Toast.makeText(app, msg, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    static void media(int code) {
        try {
            Class<?> im = Class.forName("android.hardware.input.InputManager");
            Object inst = im.getMethod("getInstance").invoke(null);
            Method inj = im.getMethod("injectInputEvent", android.view.InputEvent.class, int.class);
            long now = SystemClock.uptimeMillis();
            for (int action : new int[]{KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP}) {
                KeyEvent e = new KeyEvent(now, now, action, code, 0, 0,
                        KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD);
                inj.invoke(inst, e, 0);
            }
        } catch (Throwable t) { Log.w(TAG, "media key", t); }
    }
}
