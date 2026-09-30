package com.oakhen.ex2dev;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.Settings;
import android.util.Log;
import android.view.WindowManager;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * แบ่งจอ 2–3 แอพ = โหมดหน้าต่างอิสระ (freeform) ของ Android 9
 *  - เปิดครั้งแรก: ตั้ง Settings.Global 3 ค่า (development_settings_enabled, enable_freeform_support,
 *    force_resizable_activities) = 1 แล้ว "รีบูตจอ 1 ครั้ง" — วิธีเดียวกับ CentralEXAuto (SplitScreen) ที่ใช้กับจอนี้
 *  - เปิดแอพ: ActivityOptions.setLaunchBounds + setLaunchWindowingMode(FREEFORM=5) (hidden → reflection)
 *    แอพที่เปิดค้างอยู่แล้ว task เดิมจะไม่ยอมเปลี่ยนโหมด → ย้ายด้วย IActivityManager.setTaskWindowingMode
 *    + resizeTask (= `am task resize`) ถ้าไม่สำเร็จค่อย force-stop แล้วเปิดใหม่
 *  เราเป็น uid system: เขียน Settings.Global / เรียก API ซ่อน / force-stop ได้เอง ไม่ต้องพึ่ง adb
 */
final class SplitScreen {
    static final String TAG = "EX2Split";
    static final int WINDOWING_MODE_FULLSCREEN = 1;
    static final int WINDOWING_MODE_FREEFORM = 5;
    static final String[] KEYS = {"development_settings_enabled", "enable_freeform_support", "force_resizable_activities"};

    private SplitScreen() {}

    private static Handler sWorker;
    /** เธรดเบื้องหลังเดียวสำหรับสั่งหน้าต่าง (ทำตามลำดับ ไม่บล็อกหน้าจอ) */
    static synchronized Handler worker() {
        if (sWorker == null) {
            HandlerThread t = new HandlerThread("ex2-split");
            t.start();
            sWorker = new Handler(t.getLooper());
        }
        return sWorker;
    }

    static boolean freeformReady(Context c) {
        for (String k : KEYS) {
            if (Settings.Global.getInt(c.getContentResolver(), k, 0) != 1) return false;
        }
        return true;
    }

    /** ตั้ง 3 ค่า — true = มีค่าที่เปลี่ยน (ต้องรีบูตจอให้มีผล) */
    static boolean enableFreeform(Context c) {
        boolean changed = false;
        for (String k : KEYS) {
            if (Settings.Global.getInt(c.getContentResolver(), k, 0) != 1) {
                Settings.Global.putInt(c.getContentResolver(), k, 1);
                changed = true;
            }
        }
        return changed;
    }

    static final String[] EXTRA = {"com.njda.carplay/.platform.adapter.CarplayActivity"};

    /** แอพที่มีไอคอนบนหน้าหลัก (ยกเว้นตัวเอง) + EXTRA เรียงตามชื่อ */
    static List<ResolveInfo> launchables(Context c) {
        final PackageManager pm = c.getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> all = pm.queryIntentActivities(i, 0);
        List<ResolveInfo> out = new ArrayList<ResolveInfo>();
        for (ResolveInfo r : all) {
            if (!c.getPackageName().equals(r.activityInfo.packageName)) out.add(r);
        }
        // แอพระบบที่ไม่มีไอคอน launcher แต่เปิดเป็นหน้าต่างได้ (ทดสอบบนรถ 2026-09-29: CarPlay ย่อครึ่งจอได้)
        for (String s : EXTRA) {
            try {
                ResolveInfo r = pm.resolveActivity(new Intent(Intent.ACTION_MAIN)
                        .setComponent(ComponentName.unflattenFromString(s)), 0);
                if (r != null && r.activityInfo != null) out.add(r);
            } catch (Throwable ignored) {}
        }
        Collections.sort(out, new Comparator<ResolveInfo>() {
            public int compare(ResolveInfo a, ResolveInfo b) {
                return String.valueOf(a.loadLabel(pm)).compareToIgnoreCase(String.valueOf(b.loadLabel(pm)));
            }
        });
        return out;
    }

    /** พื้นที่ใช้งาน (ใต้แถบบน, เหนือแถบล่าง) แบ่งเป็น n คอลัมน์เท่ากัน */
    static Rect[] layout(Context c, int n) {
        WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        Point p = new Point();
        wm.getDefaultDisplay().getSize(p);   // ขนาดพื้นที่แอพ (ไม่รวมแถบล่าง)
        int top = 0;
        int id = c.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (id > 0) top = c.getResources().getDimensionPixelSize(id);
        int gap = 4, w = (p.x - gap * (n - 1)) / n;
        Rect[] r = new Rect[n];
        for (int k = 0; k < n; k++) {
            int left = k * (w + gap);
            r[k] = new Rect(left, top, (k == n - 1) ? p.x : left + w, p.y);
        }
        return r;
    }

    // ---------- CarPlay: ภาพจาก iPhone ตายตัว 1920×968 (log [DADAO_CP] mViewArea) — ย่อแล้วต้องคงสัดส่วน ไม่งั้นถูกบีบ ----------
    static final String CARPLAY_PKG = "com.njda.carplay";
    static final int CP_W = 1920, CP_H = 968, CP_WIN_W = 1280;
    static final String K_CP_W = "split_cp_w";   // ความกว้าง CarPlay ที่ผู้ใช้ลากไว้ล่าสุด = ค่าเริ่มต้นครั้งต่อไป

    static boolean isCarPlay(ComponentName c) { return c != null && CARPLAY_PKG.equals(c.getPackageName()); }

    /** ชุดที่มี CarPlay → เรียงเป็น [ซ้ายเต็มสูง, CarPlay ขวาบน, ขวาล่าง] (null = ช่องว่าง ให้เลือกแอพ) · ไม่มี CarPlay = คืนเดิม */
    static List<ComponentName> arrange(List<ComponentName> apps) {
        ComponentName cp = null;
        List<ComponentName> others = new ArrayList<ComponentName>();
        for (ComponentName c : apps) {
            if (c == null) continue;
            if (isCarPlay(c)) cp = c; else others.add(c);
        }
        if (cp == null) return others;
        List<ComponentName> out = new ArrayList<ComponentName>();
        out.add(others.size() > 0 ? others.get(0) : null);
        out.add(cp);
        out.add(others.size() > 1 ? others.get(1) : null);
        return out;
    }

    /** ชุดที่ arrange แล้ว → กรอบแต่ละช่อง */
    static Rect[] layoutFor(Context c, List<ComponentName> apps) {
        if (!isCarPlayLayout(apps)) return layout(c, apps.size());
        Rect[] out = {new Rect(), new Rect(), new Rect()};
        CarPlayDivider.compute(layout(c, 1)[0], Prefs.of(c).getInt(K_CP_W, CP_WIN_W), out);
        return out;
    }

    static boolean isCarPlayLayout(List<ComponentName> apps) { return apps.size() == 3 && isCarPlay(apps.get(1)); }

    /** กรอบเรียงเป็นแถวเดียวซ้าย→ขวา (เส้นแบ่งลากได้เฉพาะแบบนี้) */
    static boolean isRow(Rect[] r) {
        for (int i = 1; i < r.length; i++) if (r[i].top != r[0].top || r[i].bottom != r[0].bottom || r[i].left < r[i - 1].right) return false;
        return true;
    }

    static String flatten(List<ComponentName> apps) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < apps.size(); i++) {
            ComponentName c = apps.get(i);
            if (i > 0) sb.append('|');
            sb.append(c == null ? "_" : c.flattenToString());
        }
        return sb.toString();
    }

    /** เปิด (หรือย้าย) แอพให้อยู่ในกรอบ bounds แบบหน้าต่างอิสระ */
    static boolean place(Context c, ComponentName cn, Rect bounds) {
        Integer task = runningTaskOf(c, cn.getPackageName());
        if (task != null && moveTask(task, bounds)) {
            toFront(c, task);
            Log.i(TAG, cn.getPackageName() + " task " + task + " -> " + bounds.toShortString());
            return true;
        }
        // task เดิมย้ายไม่ได้ → ไม่ force-stop (เคยทำ: ตัด CarPlay/นำทางที่กำลังใช้) — เปิดด้วย ActivityOptions ต่อไป
        if (task != null) Log.w(TAG, "place: ย้าย task " + task + " ไม่ได้ (" + cn.getPackageName() + ")");
        try {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(cn)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            ActivityOptions o = ActivityOptions.makeBasic();
            o.setLaunchBounds(bounds);
            try {
                ActivityOptions.class.getMethod("setLaunchWindowingMode", int.class).invoke(o, WINDOWING_MODE_FREEFORM);
            } catch (Throwable t) { Log.w(TAG, "setLaunchWindowingMode", t); }
            c.startActivity(i, o.toBundle());
            Log.i(TAG, "start " + cn.flattenToShortString() + " @ " + bounds.toShortString());
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "start " + cn, t);
            return false;
        }
    }

    /**
     * ย้ำให้แอพอยู่ในกรอบของมัน (เรียกหลังเปิดครบ)
     * บนจอรถ (ทดสอบ 2026-09-28): แอพแรกเข้า freeform ตาม ActivityOptions แต่แอพที่ 2 ที่เปิดตามติด ๆ
     * ไปโผล่ใน stack fullscreen → ย้าย task ด้วย setTaskWindowingMode(FREEFORM)+resizeTask ใช้ได้จริง
     * (เทียบ `am stack move-task` + `am task resize` บนรถแล้ว)
     */
    static void enforce(Context c, List<ComponentName> apps, Rect[] r) {
        for (int k = 0; k < apps.size() && k < r.length; k++) {
            if (apps.get(k) == null) continue;
            Integer task = runningTaskOf(c, apps.get(k).getPackageName());
            if (task == null) { Log.w(TAG, "enforce: ไม่เจอ task " + apps.get(k).getPackageName()); continue; }
            boolean ok = moveTask(task, r[k]);
            toFront(c, task);
            Log.i(TAG, "enforce " + apps.get(k).getPackageName() + " task " + task + " -> " + r[k].toShortString() + " " + ok);
        }
    }

    /** เปิดพื้นหลังดำเต็มจอ (บังวอลเปเปอร์) — ต้องเปิดก่อนแอพที่แบ่ง */
    static void showBackdrop(Context c) {
        try {
            Intent i = new Intent(c, SplitBackdropActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            ActivityOptions o = ActivityOptions.makeBasic();
            try {   // บังคับเต็มจอ — เปิด freeform ไว้ ระบบอาจเปิดเป็นหน้าต่าง
                ActivityOptions.class.getMethod("setLaunchWindowingMode", int.class).invoke(o, WINDOWING_MODE_FULLSCREEN);
            } catch (Throwable t) { Log.w(TAG, "backdrop windowing mode", t); }
            c.startActivity(i, o.toBundle());
        } catch (Throwable t) { Log.w(TAG, "backdrop", t); }
    }

    /** ปุ่ม "คืนเต็มจอ" = จบแบ่งจอแบบเดียวกับ endSession
     *  (ตัวเก่าสั่งเต็มจอ+ขึ้นหน้าทุก task เรียงใหม่→เก่า = แอพเก่าสุดโผล่บนสุด และไม่ resize ก่อน = ค้างครึ่งจอ) */
    static void exitAll(Context c) { endSession(c); }

    /** เวลาที่เริ่มเปิดชุดล่าสุด — ช่วงจัดหน้าต่างพื้นดำอาจถูกบังชั่วคราว ห้ามจบแบ่งจอ */
    static volatile long sLaunchAt;
    /** รอบของแบ่งจอ: เพิ่มทุกครั้งที่เปิดชุดใหม่/จบ — งานที่ตั้งเวลาไว้เช็คแล้วยกเลิกตัวเองถ้ารอบเปลี่ยน
     *  (กันงาน enforce/toFront ค้างคิวดึงหน้าต่างทับกล้องถอย/หน้าหลักหลังผู้ใช้ออกจากแบ่งจอแล้ว) */
    static volatile int sGen;

    // ---------- ลำดับหน้าต่างจริงของจอหลัก (IActivityManager.getAllStackInfos: stack บน→ล่าง) ----------
    static final class TaskRec {
        int id, mode; String pkg, name; boolean visible; Rect bounds;
        public String toString() { return id + ":" + pkg + (visible ? "" : "(hidden)") + " m" + mode; }
    }

    /** task บน display 0 เรียงบน→ล่าง (รวมข้อมูลโหมด/มองเห็น/กรอบ) · ว่าง = อ่านไม่ได้ */
    static List<TaskRec> tasks() {
        List<TaskRec> out = new ArrayList<TaskRec>();
        try {
            Object s = iam();
            List<?> stacks = (List<?>) s.getClass().getMethod("getAllStackInfos").invoke(s);
            for (Object si : stacks) {
                Class<?> k = si.getClass();
                if (k.getField("displayId").getInt(si) != 0) continue;   // จอคลัสเตอร์/virtual display ไม่ยุ่ง
                boolean vis = k.getField("visible").getBoolean(si);
                int[] ids = (int[]) k.getField("taskIds").get(si);
                String[] names = (String[]) k.getField("taskNames").get(si);
                Rect[] tb = (Rect[]) k.getField("taskBounds").get(si);
                int mode = 0;
                try {
                    Object cfg = k.getField("configuration").get(si);
                    Object wc = cfg.getClass().getField("windowConfiguration").get(cfg);
                    mode = (Integer) wc.getClass().getMethod("getWindowingMode").invoke(wc);
                } catch (Throwable ignored) {}
                for (int i = ids.length - 1; i >= 0; i--) {   // task ท้าย array = บนสุดของ stack
                    TaskRec r = new TaskRec();
                    r.id = ids[i]; r.mode = mode; r.visible = vis;
                    r.name = names != null && i < names.length ? names[i] : "";
                    ComponentName cn = ComponentName.unflattenFromString(r.name);
                    r.pkg = cn != null ? cn.getPackageName() : r.name;
                    r.bounds = tb != null && i < tb.length ? tb[i] : null;
                    out.add(r);
                }
            }
        } catch (Throwable t) { Log.w(TAG, "getAllStackInfos", t); }
        return out;
    }

    private static String sHome;
    static String homePkg(Context c) {
        if (sHome != null) return sHome;
        try {
            ResolveInfo h = c.getPackageManager().resolveActivity(
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0);
            if (h != null) return sHome = h.activityInfo.packageName;
        } catch (Throwable ignored) {}
        return "";
    }

    static boolean isBackdrop(TaskRec r) { return r.name != null && r.name.endsWith("SplitBackdropActivity"); }

    /**
     * แบ่งจอถูกบัง (กดหน้าหลัก / เปิดแอพเต็มจออื่น) → จบแบ่งจอ: เอาปุ่ม/เส้นแบ่ง/พื้นดำออก และคืนหน้าต่างทุกบานเป็นเต็มจอ
     * ไม่งั้น task ค้างโหมดหน้าต่าง → เปิดแอพจากหน้าหลักรถทีหลัง ระบบดึงหน้าต่างครึ่งจอเดิมขึ้นมา (บั๊กที่เจอกับ YouTube)
     * task บนสุดที่ไม่ใช่หน้าหลัก (= แอพที่เพิ่งกดเปิด) ขึ้นหน้าเป็นเต็มจอ ที่เหลือคืนแบบอยู่ข้างหลัง
     */
    static void endSession(Context c) {
        sGen++;
        SplitBackButtons.clear(c);
        SplitDividers.clear(c);
        animRestore(c);
        String home = homePkg(c);
        Point scr = new Point();
        ((WindowManager) c.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRealSize(scr);
        try {
            boolean first = true;   // task แรกที่เจอ (ถ้าไม่มีหน้าหลัก/หน้าของเราอยู่บนกว่า) = แอพที่ผู้ใช้เพิ่งเปิด → ขึ้นหน้าเป็นเต็มจอ
            for (TaskRec t : tasks()) {
                String pkg = t.pkg;
                if (isBackdrop(t)) continue;
                if (c.getPackageName().equals(pkg) || pkg.equals(home)) { first = false; continue; }   // หน้า OAKHEN/หน้าหลักอยู่บน = ให้อยู่บนต่อ
                if (t.mode != WINDOWING_MODE_FREEFORM && t.mode != WINDOWING_MODE_FULLSCREEN && t.mode != 0) { first = false; continue; }   // PiP/แบ่งจอระบบ ไม่ยุ่ง
                Rect b = t.bounds != null ? t.bounds : taskBounds(t.id);
                boolean windowed = t.mode == WINDOWING_MODE_FREEFORM
                        || (b != null && !b.isEmpty() && (b.width() < scr.x - 8 || b.height() < scr.y - 8));   // เต็มจอแต่ค้างกรอบครึ่งจอ
                if (windowed) {
                    // เปลี่ยนโหมดอย่างเดียว task ยังจำกรอบครึ่งจอ (เห็นครึ่งจอ+ดำ บนเครื่องจำลอง) → ขยายกรอบเต็มจอก่อน
                    Rect all = new Rect(0, 0, scr.x, scr.y);
                    if (!resize(t.id, all)) {
                        // task ค้างจากรุ่นก่อน: อยู่โหมดเต็มจอแล้วแต่ยังจำกรอบครึ่งจอ → resize ไม่ได้ ต้องกลับเป็นหน้าต่างก่อน
                        setMode(t.id, WINDOWING_MODE_FREEFORM, false);
                        resize(t.id, all);
                    }
                    setMode(t.id, WINDOWING_MODE_FULLSCREEN, first);
                    Log.i(TAG, "endSession: task " + t.id + " " + pkg + " -> fullscreen" + (first ? " (top)" : ""));
                }
                if (t.visible) first = false;
            }
        } catch (Throwable t) { Log.w(TAG, "endSession", t); }
        SplitBackdropActivity.dismiss();
    }

    private static Integer runningTaskOf(Context c, String pkg) {
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            for (ActivityManager.RunningTaskInfo t : am.getRunningTasks(30)) {
                ComponentName base = t.baseActivity != null ? t.baseActivity : t.topActivity;
                if (base != null && pkg.equals(base.getPackageName())) return t.id;
            }
        } catch (Throwable t) { Log.w(TAG, "getRunningTasks", t); }
        return null;
    }

    private static Object iam() throws Exception {
        return ActivityManager.class.getMethod("getService").invoke(null);
    }

    private static boolean setMode(int taskId, int mode) { return setMode(taskId, mode, true); }

    private static boolean setMode(int taskId, int mode, boolean toTop) {
        try {
            Object s = iam();
            Method m = s.getClass().getMethod("setTaskWindowingMode", int.class, int.class, boolean.class);
            Object r = m.invoke(s, taskId, mode, toTop);
            return !(r instanceof Boolean) || (Boolean) r;
        } catch (Throwable t) {
            Log.w(TAG, "setTaskWindowingMode " + taskId, t);
            return false;
        }
    }

    /** ดึง task ขึ้นหน้าสุด — Android 9 stack หน้าต่างอิสระไม่ได้อยู่บน stack เต็มจอเสมอ (พื้นดำที่เปิดทีหลังบังได้) */
    static void toFront(Context c, int taskId) {
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            am.moveTaskToFront(taskId, 0);
        } catch (Throwable t) { Log.w(TAG, "moveTaskToFront " + taskId, t); }
    }

    /** กรอบปัจจุบันของ task — ว่าง/null = เต็มจอ (ใน Android 9 task เต็มจอมี bounds ว่าง) */
    static Rect taskBounds(int taskId) {
        try {
            Object s = iam();
            return (Rect) s.getClass().getMethod("getTaskBounds", int.class).invoke(s, taskId);
        } catch (Throwable t) { return null; }
    }

    /** ปรับกรอบ task ที่เป็นหน้าต่างอยู่แล้ว (คำสั่งเดียว ไม่กระโดด) */
    static boolean resize(int taskId, Rect bounds) {
        try {
            Object s = iam();
            s.getClass().getMethod("resizeTask", int.class, Rect.class, int.class).invoke(s, taskId, bounds, 0);
            return true;
        } catch (Throwable t) {
            // เคสปกติของ task เต็มจอ ("resizeTask not allowed") — log สั้น ๆ ไม่ต้อง stack
            Log.i(TAG, "resizeTask " + taskId + ": " + (t.getCause() != null ? t.getCause().getMessage() : t));
            return false;
        }
    }

    /**
     * ย้าย task เข้ากรอบ — ทำเท่าที่จำเป็น (วัดจากวิดีโอเครื่องจำลอง: การสั่งซ้ำ/เปลี่ยนโหมด = หน้าต่างกระโดด)
     *  - อยู่ถูกที่แล้ว → ไม่ทำอะไร
     *  - เป็นหน้าต่างอยู่แล้ว → resizeTask อย่างเดียว
     *  - เต็มจอ → ต้อง setTaskWindowingMode ก่อน (resizeTask บน task เต็มจอ = "resizeTask not allowed" ใน Android 9)
     *    จังหวะกลางหน้าต่างจะอยู่ขนาดเริ่มต้นกลางจอ → ปิดแอนิเมชันระบบช่วงนั้น (animOff) ให้แวบสั้นที่สุด
     */
    static boolean moveTask(int taskId, Rect bounds) {
        Rect cur = taskBounds(taskId);
        if (cur != null && cur.equals(bounds)) return true;
        // task เต็มจอบางตัวรายงาน bounds = เต็มจอ (ไม่ใช่ว่าง) → resize จะโดน "not allowed"
        // ลอง resize ก่อน (ถ้าเป็นหน้าต่างอยู่แล้ว = คำสั่งเดียว) ไม่ผ่านค่อยเปลี่ยนโหมด — ไม่ต้อง force-stop แอพ
        if (cur != null && !cur.isEmpty() && resize(taskId, bounds)) return true;
        if (!setMode(taskId, WINDOWING_MODE_FREEFORM)) return false;
        return resize(taskId, bounds);
    }

    // ---------- ปิดแอนิเมชันชั่วคราวระหว่างจัดหน้าต่าง ----------
    static final String[] ANIM = {"window_animation_scale", "transition_animation_scale", "animator_duration_scale"};
    private static String[] sAnimSaved;

    static synchronized void animOff(Context c) {
        if (sAnimSaved != null) return;
        sAnimSaved = new String[ANIM.length];
        android.content.SharedPreferences.Editor ed = Prefs.of(c).edit();
        for (int i = 0; i < ANIM.length; i++) {
            String v = Settings.Global.getString(c.getContentResolver(), ANIM[i]);
            sAnimSaved[i] = v;
            ed.putString("anim_saved_" + i, v == null ? "" : v);   // เผื่อ process ตายกลางทาง → DevService คืนค่า
            Settings.Global.putString(c.getContentResolver(), ANIM[i], "0");
        }
        ed.putBoolean("anim_off", true).apply();
    }

    static synchronized void animRestore(Context c) {
        android.content.SharedPreferences sp = Prefs.of(c);
        if (sAnimSaved == null && !sp.getBoolean("anim_off", false)) return;
        for (int i = 0; i < ANIM.length; i++) {
            String v = sAnimSaved != null ? sAnimSaved[i] : sp.getString("anim_saved_" + i, "");
            Settings.Global.putString(c.getContentResolver(), ANIM[i], (v == null || v.isEmpty()) ? "1" : v);
        }
        sAnimSaved = null;
        sp.edit().putBoolean("anim_off", false).apply();
    }

    // ---------- เปิดชุดแบ่งจอ (ใช้ทั้งหน้าแบ่งจอ และปุ่มในกล่องลอย) ----------
    static final String K_LAST = "split_last";

    static List<ComponentName> parse(String s) {
        List<ComponentName> out = new ArrayList<ComponentName>();
        if (s == null) return out;
        for (String p : s.split("\\|")) {
            if ("_".equals(p)) { out.add(null); continue; }   // ช่องว่าง (ชุด CarPlay)
            ComponentName c = ComponentName.unflattenFromString(p);
            if (c != null) out.add(c);
        }
        return out;
    }

    static void launchSet(Context ctx, List<ComponentName> picked) {
        if (picked == null || picked.isEmpty()) return;
        final Context app = ctx.getApplicationContext();
        if (!freeformReady(app)) {   // ปิดโหมดนักพัฒนา/รีเซ็ต → เปิดหน้าแบ่งจอ (มีปุ่มตั้งค่า) แทนการพยายามย้ายหน้าต่างที่ย้ายไม่ได้
            try { app.startActivity(new Intent(app, SplitActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Throwable ignored) {}
            return;
        }
        final List<ComponentName> apps = arrange(picked);
        sLaunchAt = System.currentTimeMillis();
        final int gen = ++sGen;
        int n = 0;
        for (ComponentName c : apps) if (c != null) n++;
        Prefs.of(app).edit().putString(K_LAST, flatten(apps)).putInt("split_count", Math.max(2, Math.min(3, n))).apply();

        final Rect[] r = layoutFor(app, apps);
        SplitBackButtons.clear(app);
        SplitDividers.clear(app);
        showBackdrop(app);   // พื้นดำเต็มจอข้างหลัง — ไม่เห็นวอลเปเปอร์
        final Handler h = worker();
        h.post(new Runnable() { public void run() { if (gen == sGen) animOff(app); } });
        for (int i = 0; i < apps.size(); i++) {
            final int k = i;
            // ทีละแอพห่างกันเล็กน้อย ให้ WM จัดการ task ก่อนหน้าเสร็จ
            h.postDelayed(new Runnable() {
                public void run() {
                    if (gen != sGen || apps.get(k) == null) return;   // จบ/เปิดชุดใหม่แล้ว · ช่องว่าง → ตัวเลือกแอพ
                    if (!place(app, apps.get(k), r[k])) Log.w(TAG, "เปิด " + apps.get(k) + " ไม่สำเร็จ");
                }
            }, 300L * i + 100);
        }
        // ย้ำกรอบ (ทำเฉพาะแอพที่หลุด — moveTask ข้ามถ้าอยู่ถูกที่แล้ว)
        long t = 300L * apps.size() + 900;
        h.postDelayed(new Runnable() { public void run() { if (gen == sGen) enforce(app, apps, r); } }, t);
        h.postDelayed(new Runnable() { public void run() { if (gen == sGen) enforce(app, apps, r); animRestore(app); } }, t + 1500);
        // ปุ่ม ◀ + เส้นแบ่ง
        h.postDelayed(new Runnable() { public void run() { if (gen == sGen) SplitBackButtons.show(app, apps, r); } }, t + 1700);
    }

}
