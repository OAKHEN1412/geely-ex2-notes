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

    /** เปิด (หรือย้าย) แอพให้อยู่ในกรอบ bounds แบบหน้าต่างอิสระ */
    static boolean place(Context c, ComponentName cn, Rect bounds) {
        Integer task = runningTaskOf(c, cn.getPackageName());
        if (task != null && moveTask(task, bounds)) {
            toFront(c, task);
            Log.i(TAG, cn.getPackageName() + " task " + task + " -> " + bounds.toShortString());
            return true;
        }
        if (task != null) forceStop(c, cn.getPackageName());   // task เดิมย้ายไม่ได้ → เปิดใหม่ทั้ง task
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

    /** คืนทุกแอพที่อยู่ในหน้าต่างอิสระกลับเป็นเต็มจอ */
    static void exitAll(Context c) {
        SplitBackdropActivity.dismiss();
        SplitBackButtons.clear(c);
        SplitDividers.clear(c);
        animRestore(c);
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            for (ActivityManager.RunningTaskInfo t : am.getRunningTasks(30)) {
                if (t.topActivity != null && c.getPackageName().equals(t.topActivity.getPackageName())) continue;
                setMode(t.id, WINDOWING_MODE_FULLSCREEN);
            }
        } catch (Throwable t) { Log.w(TAG, "exitAll", t); }
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

    private static boolean setMode(int taskId, int mode) {
        try {
            Object s = iam();
            Method m = s.getClass().getMethod("setTaskWindowingMode", int.class, int.class, boolean.class);
            Object r = m.invoke(s, taskId, mode, true);
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
    private static boolean moveTask(int taskId, Rect bounds) {
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
            ComponentName c = ComponentName.unflattenFromString(p);
            if (c != null) out.add(c);
        }
        return out;
    }

    static void launchSet(Context ctx, final List<ComponentName> apps) {
        if (apps == null || apps.isEmpty()) return;
        final Context app = ctx.getApplicationContext();
        StringBuilder sb = new StringBuilder();
        for (ComponentName c : apps) sb.append(sb.length() > 0 ? "|" : "").append(c.flattenToString());
        Prefs.of(app).edit().putString(K_LAST, sb.toString()).putInt("split_count", apps.size()).apply();

        final Rect[] r = layout(app, apps.size());
        SplitBackButtons.clear(app);
        SplitDividers.clear(app);
        showBackdrop(app);   // พื้นดำเต็มจอข้างหลัง — ไม่เห็นวอลเปเปอร์
        final Handler h = worker();
        h.post(new Runnable() { public void run() { animOff(app); } });
        for (int i = 0; i < apps.size(); i++) {
            final int k = i;
            // ทีละแอพห่างกันเล็กน้อย ให้ WM จัดการ task ก่อนหน้าเสร็จ
            h.postDelayed(new Runnable() {
                public void run() {
                    if (!place(app, apps.get(k), r[k])) Log.w(TAG, "เปิด " + apps.get(k) + " ไม่สำเร็จ");
                }
            }, 300L * i + 100);
        }
        // ย้ำกรอบ (ทำเฉพาะแอพที่หลุด — moveTask ข้ามถ้าอยู่ถูกที่แล้ว)
        long t = 300L * apps.size() + 900;
        h.postDelayed(new Runnable() { public void run() { enforce(app, apps, r); } }, t);
        h.postDelayed(new Runnable() { public void run() { enforce(app, apps, r); animRestore(app); } }, t + 1500);
        // ปุ่ม ◀ + เส้นแบ่ง
        h.postDelayed(new Runnable() { public void run() { SplitBackButtons.show(app, apps, r); } }, t + 1700);
    }

    private static void forceStop(Context c, String pkg) {
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.class.getMethod("forceStopPackage", String.class).invoke(am, pkg);
        } catch (Throwable t) { Log.w(TAG, "forceStop " + pkg, t); }
    }
}
