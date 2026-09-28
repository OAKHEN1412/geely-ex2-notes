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

    /** แอพที่มีไอคอนบนหน้าหลัก (ยกเว้นตัวเอง) เรียงตามชื่อ */
    static List<ResolveInfo> launchables(Context c) {
        final PackageManager pm = c.getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> all = pm.queryIntentActivities(i, 0);
        List<ResolveInfo> out = new ArrayList<ResolveInfo>();
        for (ResolveInfo r : all) {
            if (!c.getPackageName().equals(r.activityInfo.packageName)) out.add(r);
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

    private static boolean moveTask(int taskId, Rect bounds) {
        if (!setMode(taskId, WINDOWING_MODE_FREEFORM)) return false;
        try {
            Object s = iam();
            s.getClass().getMethod("resizeTask", int.class, Rect.class, int.class).invoke(s, taskId, bounds, 0);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "resizeTask " + taskId, t);
            return false;
        }
    }

    private static void forceStop(Context c, String pkg) {
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.class.getMethod("forceStopPackage", String.class).invoke(am, pkg);
        } catch (Throwable t) { Log.w(TAG, "forceStop " + pkg, t); }
    }
}
