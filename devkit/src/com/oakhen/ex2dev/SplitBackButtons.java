package com.oakhen.ex2dev;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ปุ่ม ◀ ย้อนกลับ บนแถบบนของแต่ละหน้าต่างแบ่งจอ
 *  - กด: setFocusedTask(task) → ส่ง KEYCODE_BACK (InputManager.injectInputEvent, uid system มี INJECT_EVENTS)
 *    ปุ่ม back ของระบบจะไปที่หน้าต่างที่ focus อยู่ → โฟกัสหน้าต่างนั้นก่อนแล้วค่อยส่ง
 *  - ตามตำแหน่งหน้าต่างทุก 1 วิ (getTaskBounds) · task กลายเป็นเต็มจอ = ซ่อนปุ่ม
 *  - task หาย (ปิด/crash/◀ จนหลุดแอพ) หรือเปิดไม่ขึ้น → ช่องนั้นแสดงตารางแอพ (SlotPicker) ให้เลือกแอพมาแทน
 *    ปิดหมดทุกช่อง = ออกจากแบ่งจอ
 */
final class SplitBackButtons {
    static final String TAG = "EX2SplitBack";
    private static final List<Btn> sBtns = new ArrayList<Btn>();
    private static Handler sMain;
    private static Runnable sTick;

    private static final class Btn {
        final int slot; final String pkg; final int task; final TextView view; final WindowManager.LayoutParams lp; boolean shown;
        Rect lastRect;   // กรอบล่าสุดที่เห็น (ลากเส้นแบ่งแล้วกรอบเปลี่ยน) — ใช้วางตัวเลือกแอพเมื่อ task หาย
        Btn(int slot, String pkg, int task, TextView v, WindowManager.LayoutParams lp, Rect r) {
            this.slot = slot; this.pkg = pkg; this.task = task; this.view = v; this.lp = lp; this.lastRect = r;
        }
    }

    /** ชุดแบ่งจอปัจจุบัน (ช่อง → แอพ, กรอบเริ่มต้น) + กรอบของช่องที่กำลังแสดงตัวเลือกแอพ */
    private static List<ComponentName> sApps;
    private static Rect[] sSlotRects;
    private static final SparseArray<Rect> sPickerRects = new SparseArray<Rect>();

    private SplitBackButtons() {}

    private static Handler main() {
        if (sMain == null) sMain = new Handler(Looper.getMainLooper());
        return sMain;
    }

    /** เรียกจากเธรดไหนก็ได้ — สร้างปุ่มให้แอพที่อยู่ในแบ่งจอ (ลบของเก่าก่อน) */
    static void show(final Context ctx, final List<ComponentName> apps, final Rect[] rects) {
        final Context app = ctx.getApplicationContext();
        final List<int[]> tasks = new ArrayList<int[]>();   // {index, taskId}
        for (int i = 0; i < apps.size(); i++) {
            if (apps.get(i) == null) continue;   // ช่องว่าง
            Integer t = taskOf(app, apps.get(i).getPackageName());
            if (t != null) tasks.add(new int[]{i, t});
        }
        main().post(new Runnable() {
            public void run() {
                clearNow(app);
                sApps = new ArrayList<ComponentName>(apps);
                sSlotRects = rects;
                SlotPicker.preload(app, null);
                // เส้นแบ่ง (ลาก=ปรับขนาด, แตะ ⇄=สลับ) — เฉพาะเมื่อเจอ task ครบทุกช่อง
                if (rects != null && tasks.size() == apps.size() && apps.size() >= 2 && SplitScreen.isRow(rects)) {
                    int[] ids = new int[tasks.size()];
                    for (int k = 0; k < ids.length; k++) ids[k] = tasks.get(k)[1];
                    SplitDividers.show(app, ids, rects);
                }
                WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
                for (int[] it : tasks) {
                    final String pkg = apps.get(it[0]).getPackageName();
                    final int task = it[1];
                    TextView v = new TextView(app);
                    v.setText("◀");
                    v.setTextColor(0xFFF2EFE9);
                    v.setTextSize(18);
                    v.setGravity(Gravity.CENTER);
                    GradientDrawable bg = new GradientDrawable();
                    bg.setColor(0xCC1B1C1E);
                    bg.setCornerRadius(dp(app, 16));
                    v.setBackground(bg);
                    v.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View x) { back(app, task); }
                    });
                    WindowManager.LayoutParams lp = new WindowManager.LayoutParams(dp(app, 72), dp(app, 34),
                            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                            PixelFormat.TRANSLUCENT);
                    lp.gravity = Gravity.TOP | Gravity.START;
                    Btn b = new Btn(it[0], pkg, task, v, lp, rects != null && it[0] < rects.length ? new Rect(rects[it[0]]) : null);
                    sBtns.add(b);
                }
                // ช่องที่แอพเปิดไม่ขึ้น → ตัวเลือกแอพทันที (ถ้าไม่ขึ้นเลยสักแอพ ไม่ต้องค้างพื้นดำ)
                if (tasks.isEmpty()) { SplitBackdropActivity.dismiss(); return; }
                if (rects != null) {
                    for (int i = 0; i < apps.size() && i < rects.length; i++) {
                        boolean has = false;
                        for (int[] it : tasks) if (it[0] == i) has = true;
                        if (!has) showPicker(app, i, rects[i]);
                    }
                }
                startTicking(app);
            }
        });
    }

    /** ลบปุ่มทั้งหมด (คืนเต็มจอ / แบ่งจอชุดใหม่) */
    static void clear(final Context ctx) {
        main().post(new Runnable() { public void run() { clearNow(ctx.getApplicationContext()); } });
    }

    private static void clearNow(Context app) {
        WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
        for (Btn b : sBtns) {
            if (b.shown) { try { wm.removeView(b.view); } catch (Throwable ignored) {} }
        }
        sBtns.clear();
        SlotPicker.clearAll(app);
        sPickerRects.clear();
        if (sTick != null) main().removeCallbacks(sTick);
    }

    /** ช่องว่าง → ตารางแอพในกรอบช่อง · แตะแอพ = เปิดเข้าช่องนั้นแล้วจัดปุ่ม/เส้นแบ่งใหม่ทั้งชุด */
    private static void showPicker(final Context app, int slot, Rect r) {
        if (r == null || SlotPicker.showing(slot)) return;
        Set<String> used = new HashSet<String>();
        for (Btn b : sBtns) used.add(b.pkg);
        sPickerRects.put(slot, new Rect(r));
        SlotPicker.show(app, slot, r, used, new SlotPicker.OnPick() {
            public void pick(int s, ComponentName cn) { replace(app, s, cn); }
        });
    }

    /** เรียกบน main thread (จากตัวเลือกแอพ) */
    private static void replace(final Context app, final int slot, final ComponentName cn) {
        if (sApps == null || slot >= sApps.size()) return;
        // กรอบทุกช่อง ณ ตอนนี้: ช่องที่มีแอพ = กรอบล่าสุดของหน้าต่าง, ช่องว่าง = กรอบที่ตัวเลือกแอพใช้
        final Rect[] rects = new Rect[sApps.size()];
        for (int i = 0; i < rects.length; i++) {
            Rect pr = sPickerRects.get(i);
            if (pr != null) rects[i] = new Rect(pr);
            else if (sSlotRects != null && i < sSlotRects.length) rects[i] = new Rect(sSlotRects[i]);
        }
        for (Btn b : sBtns) if (b.lastRect != null && b.slot < rects.length) rects[b.slot] = new Rect(b.lastRect);
        if (rects[slot] == null) return;
        ComponentName old = sApps.get(slot);
        sPickerRects.remove(slot);
        sApps.set(slot, cn);
        final List<ComponentName> apps = new ArrayList<ComponentName>(sApps);
        // CarPlay เข้า/ออกชุด → กรอบทั้งชุดเปลี่ยน (CarPlay ต้องคงสัดส่วน) → จัดใหม่ทั้งชุด
        if (SplitScreen.isCarPlay(cn) || SplitScreen.isCarPlay(old)) {
            List<ComponentName> keep = new ArrayList<ComponentName>();
            for (int i = 0; i < apps.size(); i++) {
                ComponentName c = apps.get(i);
                if (c == null) continue;
                if (i == slot || taskOf(app, c.getPackageName()) != null) keep.add(c);
            }
            clearNow(app);
            SplitScreen.launchSet(app, keep);
            return;
        }
        Prefs.of(app).edit().putString(SplitScreen.K_LAST, SplitScreen.flatten(apps)).apply();   // "ชุดล่าสุด" = ชุดที่แก้แล้ว
        final Handler h = SplitScreen.worker();
        h.post(new Runnable() {
            public void run() {
                SplitScreen.animOff(app);
                if (!SplitScreen.place(app, cn, rects[slot])) Log.w(TAG, "replace: เปิด " + cn + " ไม่สำเร็จ");
            }
        });
        h.postDelayed(new Runnable() {
            public void run() {
                SplitScreen.enforce(app, apps.subList(slot, slot + 1), new Rect[]{rects[slot]});
                SplitScreen.animRestore(app);
                // ดึงทุกหน้าต่างขึ้นหน้าพื้นดำ (แอพที่เพิ่งเปิดอาจดันหน้าต่างอื่นลง) — แอพใหม่ขึ้นท้ายสุด
                for (int i = 0; i < apps.size(); i++) {
                    if (i == slot || apps.get(i) == null) continue;
                    Integer t = taskOf(app, apps.get(i).getPackageName());
                    if (t != null) SplitScreen.toFront(app, t);
                }
                Integer t = taskOf(app, cn.getPackageName());
                if (t != null) SplitScreen.toFront(app, t);
                Log.i(TAG, "replace slot " + slot + " -> " + cn.flattenToShortString());
                show(app, apps, rects);
            }
        }, 1500);
    }

    private static void startTicking(final Context app) {
        if (sTick != null) main().removeCallbacks(sTick);
        sTick = new Runnable() {
            public void run() {
                if (sBtns.isEmpty()) return;
                // หน้าหลัก/แอพเต็มจอบังแบ่งจออยู่ → ซ่อนตัวเลือกแอพ/เส้นแบ่ง (ไม่ลอยค้างทับหน้าอื่น)
                boolean vis = SplitBackdropActivity.isVisible();
                SlotPicker.setVisible(vis);
                SplitDividers.setVisible(vis);
                // อ่านตำแหน่งหน้าต่างบนเธรดเบื้องหลัง (binder) แล้วค่อยวางปุ่มบน main
                final List<Btn> snap = new ArrayList<Btn>(sBtns);
                SplitScreen.worker().post(new Runnable() {
                    public void run() {
                        final List<Rect> rs = new ArrayList<Rect>();
                        final List<Boolean> alive = new ArrayList<Boolean>();
                        for (Btn b : snap) { alive.add(taskAlive(app, b.task)); rs.add(taskBounds(b.task)); }
                        main().post(new Runnable() {
                            public void run() { layoutButtons(app, snap, rs, alive); }
                        });
                    }
                });
                main().postDelayed(this, 1000);
            }
        };
        main().post(sTick);
    }

    private static void layoutButtons(Context app, List<Btn> snap, List<Rect> rs, List<Boolean> alive) {
        WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
        Point scr = new Point();
        wm.getDefaultDisplay().getRealSize(scr);
        for (int i = 0; i < snap.size(); i++) {
            Btn b = snap.get(i);
            if (!sBtns.contains(b)) continue;
            Rect r = rs.get(i);
            if (!alive.get(i)) {           // ปิดหน้าต่างแล้ว → ลบปุ่ม + เส้นแบ่ง แล้วให้เลือกแอพมาแทนในช่องนั้น
                if (b.shown) { try { wm.removeView(b.view); } catch (Throwable ignored) {} }
                sBtns.remove(b);
                SplitDividers.clear(app);
                if (sBtns.isEmpty()) {     // ปิดครบทุกหน้าต่าง → ออกจากแบ่งจอ
                    clearNow(app);
                    SplitBackdropActivity.dismiss();
                    return;
                }
                showPicker(app, b.slot, b.lastRect);
                continue;
            }
            // ไม่รู้ตำแหน่ง / เต็มจอ (กดขยาย) → ซ่อน
            boolean freeform = r != null && !r.isEmpty() && (r.width() < scr.x - 8 || r.top > 8);
            boolean full = r != null && r.left <= 0 && r.width() >= scr.x - 8;
            if (!freeform || full) {
                if (b.shown) { try { wm.removeView(b.view); } catch (Throwable ignored) {} b.shown = false; }
                continue;
            }
            b.lastRect = new Rect(r);
            if (!SplitBackdropActivity.isVisible()) {
                if (b.shown) { try { wm.removeView(b.view); } catch (Throwable ignored) {} b.shown = false; }
                continue;
            }
            b.lp.x = r.left + r.width() / 2 - b.lp.width / 2;   // กลางแถบบน (ปุ่ม ✕/ขยาย ของระบบอยู่ขอบซ้ายหรือขวา)
            b.lp.y = r.top + dp(app, 4);
            try {
                if (b.shown) wm.updateViewLayout(b.view, b.lp);
                else { wm.addView(b.view, b.lp); b.shown = true; }
            } catch (Throwable t) { Log.w(TAG, "layout", t); }
        }
    }

    private static void back(final Context app, final int task) {
        SplitScreen.worker().post(new Runnable() {
            public void run() {
                try {
                    Object s = ActivityManager.class.getMethod("getService").invoke(null);
                    s.getClass().getMethod("setFocusedTask", int.class).invoke(s, task);
                } catch (Throwable t) { Log.w(TAG, "setFocusedTask " + task, t); }
                SystemClock.sleep(80);
                injectBack();
                Log.i(TAG, "back -> task " + task);
            }
        });
    }

    private static void injectBack() {
        try {
            Class<?> im = Class.forName("android.hardware.input.InputManager");
            Object inst = im.getMethod("getInstance").invoke(null);
            java.lang.reflect.Method inj = im.getMethod("injectInputEvent", android.view.InputEvent.class, int.class);
            long now = SystemClock.uptimeMillis();
            for (int action : new int[]{KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP}) {
                KeyEvent e = new KeyEvent(now, now, action, KeyEvent.KEYCODE_BACK, 0, 0,
                        KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD);
                inj.invoke(inst, e, 0);   // 0 = INJECT_INPUT_EVENT_MODE_ASYNC
            }
        } catch (Throwable t) { Log.w(TAG, "injectBack", t); }
    }

    private static Integer taskOf(Context c, String pkg) {
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            for (ActivityManager.RunningTaskInfo t : am.getRunningTasks(30)) {
                ComponentName base = t.baseActivity != null ? t.baseActivity : t.topActivity;
                if (base != null && pkg.equals(base.getPackageName())) return t.id;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static boolean taskAlive(Context c, int task) {
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            for (ActivityManager.RunningTaskInfo t : am.getRunningTasks(30)) if (t.id == task) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    private static Rect taskBounds(int task) {
        try {
            Object s = ActivityManager.class.getMethod("getService").invoke(null);
            return (Rect) s.getClass().getMethod("getTaskBounds", int.class).invoke(s, task);
        } catch (Throwable t) { return null; }
    }

    private static int dp(Context c, int v) { return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f); }
}
