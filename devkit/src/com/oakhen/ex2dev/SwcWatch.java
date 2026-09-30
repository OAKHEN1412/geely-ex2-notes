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

    static final String[] ACTIONS = {"none", "drive", "regen", "next", "prev", "play"};
    static final String[] ACTION_LABELS = {"ไม่ทำอะไร", "สลับโหมดขับ", "สลับรีเจน", "เพลงถัดไป", "เพลงก่อนหน้า", "เล่น/หยุด"};

    /** รหัสที่เห็นแล้ว → ค่าล่าสุด/จำนวนครั้ง (ให้หน้า OAKHEN แสดงตอนดักปุ่ม) */
    static final class Seen { int prop, last, count; long at; }
    private static final Map<Integer, Seen> sSeen = new LinkedHashMap<Integer, Seen>();
    private static volatile String sStatus = "ยังไม่เริ่ม";
    private static boolean sStarted;
    private static Handler sMain;

    private SwcWatch() {}

    static String status() { return sStatus; }

    static synchronized List<Seen> seen() {
        List<Seen> out = new ArrayList<Seen>();
        for (Seen s : sSeen.values()) { Seen c = new Seen(); c.prop = s.prop; c.last = s.last; c.count = s.count; c.at = s.at; out.add(c); }
        return out;
    }

    static String action(Context c, int prop) { return Prefs.of(c).getString(key(prop), "none"); }
    static void setAction(Context c, int prop, String a) { Prefs.of(c).edit().putString(key(prop), a).apply(); }
    private static String key(int prop) { return "swc_" + Integer.toHexString(prop); }

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
            if (props.isEmpty()) { props.add(0x41020400); props.add(0x41020200); props.add(0x41020500); }   // รายชื่อไม่บอก → ใช้ชุดที่ CentralEXAuto ใช้
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
        synchronized (SwcWatch.class) {
            Seen s = sSeen.get(prop);
            if (s == null) { s = new Seen(); s.prop = prop; sSeen.put(prop, s); }
            s.last = val; s.count++; s.at = System.currentTimeMillis();
        }
        Log.i(TAG, String.format("ปุ่ม 0x%08x = %d", prop, val));
        if (val != CLICK) return;
        String a = action(app, prop);
        if ("drive".equals(a)) cycle(app, Vhal.DRIVE_MODE, Vhal.DRIVE_AREAS,
                new int[]{Vhal.DRIVE_ECO, Vhal.DRIVE_COMFORT, Vhal.DRIVE_DYNAMIC}, new String[]{"ECO", "COMFORT", "SPORT"}, "โหมดขับ");
        else if ("regen".equals(a)) cycle(app, Vhal.REGEN, Vhal.REGEN_AREAS,
                new int[]{Vhal.REGEN_LOW, Vhal.REGEN_MID, Vhal.REGEN_HIGH}, new String[]{"ต่ำ", "กลาง", "สูง"}, "รีเจน");
        else if ("next".equals(a)) media(KeyEvent.KEYCODE_MEDIA_NEXT);
        else if ("prev".equals(a)) media(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        else if ("play".equals(a)) media(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
    }

    /** อ่านค่าปัจจุบันจากรถ → ตั้งค่าถัดไปในรอบ → แจ้งบนจอ */
    private static void cycle(final Context app, final int prop, final int[] areas, final int[] vals, final String[] names, final String what) {
        Vhal.thread().post(new Runnable() {
            public void run() {
                Integer cur = Vhal.getInt(app, prop, areas);
                int i = 0;
                if (cur != null) for (int k = 0; k < vals.length; k++) if (vals[k] == cur) i = (k + 1) % vals.length;
                final boolean ok = Vhal.setInt(app, prop, vals[i], areas);
                final String msg = what + ": " + (ok ? names[i] : "สั่งไม่ได้");
                sMain.post(new Runnable() { public void run() { Toast.makeText(app, msg, Toast.LENGTH_SHORT).show(); } });
            }
        });
    }

    private static void media(int code) {
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
