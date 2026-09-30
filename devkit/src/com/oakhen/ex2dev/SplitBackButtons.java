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
import android.os.PowerManager;
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
 * ปุ่ม ◀ ย้อนกลับ บนแถบบนของแต่ละหน้าต่างแบ่งจอ + ผู้ดูแลสถานะแบ่งจอขณะใช้งาน
 *  - กด ◀: setFocusedTask(task) → ส่ง KEYCODE_BACK (InputManager.injectInputEvent, uid system มี INJECT_EVENTS)
 *  - รอบตรวจทุก 1 วิ (อ่านลำดับหน้าต่างจริงจาก getAllStackInfos + เกียร์) ตัดสินจากของจริง ไม่ใช่ตัวจับเวลา:
 *      · ถูกบัง (เกียร์ R/กล้องถอย, สายเข้า, แอพเต็มจออื่น) → ซ่อนปุ่ม/ตัวเลือกแอพ/เส้นแบ่ง แต่ไม่จบแบ่งจอ (ถอยเสร็จกลับมาครบ)
 *      · หน้าหลักขึ้นบนสุด → จบแบ่งจอ คืนหน้าต่างเป็นเต็มจอ (ไม่งั้นเปิดแอพจากหน้าหลักแล้วได้ครึ่งจอ)
 *      · แอพในชุดถูกเรียกขึ้นมาเดี่ยว ๆ ทั้งที่พื้นดำถูกบัง → ดึงพื้นดำ+ทุกหน้าต่างกลับขึ้นมา (ไม่โผล่ครึ่งจอทับแอพอื่น)
 *      · task หาย: ถ้ามี task ใหม่กว่าของแอพเดียวกัน (แอพเปิดตัวเองใน task ใหม่) → ตามไปจับเข้าช่องเดิม
 *        ไม่มี → ช่องนั้นแสดงตารางแอพ (SlotPicker) · ปิดหมดทุกช่อง = จบแบ่งจอ
 *      · หน้าต่างไปอยู่ใต้พื้นดำ → ดึงขึ้นมา (Android 9 "แตะเพื่อโฟกัส" ดูจากพิกัด: แตะตัวเลือกแอพ/✕/ร่อง
 *        ที่ทับพื้นดำ = ระบบดึงพื้นดำขึ้นบังทุกหน้าต่าง — ยืนยันบนเครื่องจำลอง) · ยกเว้นหลังกด ◀ แล้วแอพย่อตัวเอง = ถือว่าปิด
 *  - จอดับ/รถหลับ (ไม่ interactive) → ไม่ตัดสินอะไร (ตื่นมาแบ่งจอยังอยู่)
 */
final class SplitBackButtons {
    static final String TAG = "EX2SplitBack";
    private static final List<Btn> sBtns = new ArrayList<Btn>();
    private static Handler sMain;
    private static Runnable sTick;

    private static final class Btn {
        int slot; final String pkg; int task; final TextView view; final WindowManager.LayoutParams lp; boolean shown;
        long backAt;   // เวลากด ◀ ล่าสุด — ช่วง 1.5 วิหลังกด ถ้าแอพย่อตัวเองไปข้างหลัง = ผู้ใช้ออกจากแอพ ไม่ต้องดึงกลับ
        Rect lastRect;   // กรอบล่าสุดที่เห็น (ลากเส้นแบ่งแล้วกรอบเปลี่ยน) — ใช้วางตัวเลือกแอพเมื่อ task หาย
        Btn(int slot, String pkg, int task, TextView v, WindowManager.LayoutParams lp, Rect r) {
            this.slot = slot; this.pkg = pkg; this.task = task; this.view = v; this.lp = lp; this.lastRect = r;
        }
    }

    /** ชุดแบ่งจอปัจจุบัน (ช่อง → แอพ, กรอบเริ่มต้น) + กรอบของช่องว่าง (ตัวเลือกแอพ — เก็บไว้แม้กด ✕ เพื่อแตะเปิดใหม่ได้) */
    private static List<ComponentName> sApps;
    private static Rect[] sSlotRects;
    private static final SparseArray<Rect> sPickerRects = new SparseArray<Rect>();
    private static boolean sCovered;

    private SplitBackButtons() {}

    private static Handler main() {
        if (sMain == null) sMain = new Handler(Looper.getMainLooper());
        return sMain;
    }

    /** เรียกจากเธรดเบื้องหลัง — สร้างปุ่มให้แอพที่อยู่ในแบ่งจอ (ลบของเก่าก่อน) · ยกเลิกเองถ้าแบ่งจอจบไปแล้ว */
    static void show(final Context ctx, final List<ComponentName> apps, final Rect[] rects) {
        final Context app = ctx.getApplicationContext();
        final int gen = SplitScreen.sGen;
        final List<SplitScreen.TaskRec> ts = SplitScreen.tasks();
        final List<int[]> tasks = new ArrayList<int[]>();   // {index, taskId}
        for (int i = 0; i < apps.size(); i++) {
            if (apps.get(i) == null) continue;   // ช่องว่าง
            SplitScreen.TaskRec t = find(ts, apps.get(i).getPackageName());
            if (t != null) tasks.add(new int[]{i, t.id});
        }
        main().post(new Runnable() {
            public void run() {
                if (gen != SplitScreen.sGen) return;
                clearNow(app);
                sApps = new ArrayList<ComponentName>(apps);
                sSlotRects = rects;
                SlotPicker.preload(app, null);
                // เส้นแบ่ง (ลาก=ปรับขนาด, แตะ ⇄=สลับ) — เฉพาะเมื่อเจอ task ครบทุกช่อง
                if (rects != null && tasks.size() == apps.size() && apps.size() >= 2 && SplitScreen.isRow(rects)) {
                    int[] ids = new int[tasks.size()];
                    for (int k = 0; k < ids.length; k++) ids[k] = tasks.get(k)[1];
                    SplitDividers.show(app, ids, rects);
                } else if (rects != null && rects.length == 3 && SplitScreen.isCarPlayLayout(apps)) {
                    int[] ids = {-1, -1, -1};
                    for (int[] it : tasks) ids[it[0]] = it[1];
                    if (ids[1] >= 0) CarPlayDivider.show(app, apps, ids, rects);   // ลากปรับขนาด CarPlay
                }
                for (int[] it : tasks) {
                    final String pkg = apps.get(it[0]).getPackageName();
                    final Btn[] self = new Btn[1];
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
                        public void onClick(View x) { self[0].backAt = System.currentTimeMillis(); back(app, self[0].task); }   // task อาจเปลี่ยน (แอพเปิดตัวเองใน task ใหม่)
                    });
                    WindowManager.LayoutParams lp = new WindowManager.LayoutParams(dp(app, 72), dp(app, 34),
                            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                            PixelFormat.TRANSLUCENT);
                    lp.gravity = Gravity.TOP | Gravity.START;
                    self[0] = new Btn(it[0], pkg, it[1], v, lp, rects != null && it[0] < rects.length ? new Rect(rects[it[0]]) : null);
                    sBtns.add(self[0]);
                }
                if (tasks.isEmpty()) {   // เปิดไม่ขึ้นเลยสักแอพ → ไม่ค้างพื้นดำ
                    SplitScreen.worker().post(new Runnable() { public void run() { SplitScreen.endSession(app); } });
                    return;
                }
                // ช่องที่แอพเปิดไม่ขึ้น / ช่องว่าง → ตัวเลือกแอพทันที
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
        sApps = null;
        SlotPicker.clearAll(app);
        CarPlayDivider.clear(app);
        sPickerRects.clear();
        if (sTick != null) main().removeCallbacks(sTick);
        sTick = null;
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
        if (sCovered) SlotPicker.setVisible(false);
    }

    /** แตะพื้นดำ (ช่องว่างที่กด ✕ ปิดตัวเลือกไป / ร่องระหว่างหน้าต่าง) · false = ไม่มีแบ่งจอแล้ว ให้กลับหน้าหลัก */
    static boolean onBackdropTap(Context ctx, float x, float y) {
        Log.i(TAG, "แตะพื้นดำ " + (int) x + "," + (int) y + " ช่องที่มีแอพ=" + sBtns.size());
        if (sApps == null || sBtns.isEmpty()) return false;
        Context app = ctx.getApplicationContext();
        for (int i = 0; i < sPickerRects.size(); i++) {
            int slot = sPickerRects.keyAt(i);
            Rect r = sPickerRects.valueAt(i);
            if (r.contains((int) x, (int) y) && !SlotPicker.showing(slot)) {
                showPicker(app, slot, r);
                return true;
            }
        }
        return true;   // ร่อง/ช่องที่แอพยังเปิดไม่เสร็จ → ไม่ทำอะไร (เดิม: กลับหน้าหลัก = หลุดแบ่งจอโดยไม่ตั้งใจ)
    }

    /** พื้นดำถูกโฟกัส/ขึ้นบนสุด (onResume) ระหว่างแบ่งจอ → ดันทุกหน้าต่างกลับขึ้นไปทันที (ยกเว้นแอพที่เพิ่งกด ◀ แล้วย่อตัวเอง) */
    static void raise(Context ctx) {
        if (sBtns.isEmpty()) return;
        final Context app = ctx.getApplicationContext();
        final int gen = SplitScreen.sGen;
        final List<Integer> ids = new ArrayList<Integer>();
        long now = System.currentTimeMillis();
        for (Btn b : sBtns) if (now - b.backAt > 1500) ids.add(b.task);
        if (ids.isEmpty()) return;
        SplitScreen.worker().post(new Runnable() {
            public void run() {
                if (gen != SplitScreen.sGen) return;
                for (int id : ids) SplitScreen.toFront(app, id);
                Log.i(TAG, "พื้นดำถูกดึงขึ้น → ดันหน้าต่างกลับ " + ids);
            }
        });
    }

    /** ⇄ สลับช่อง i กับ i+1 (จาก SplitDividers) — ให้สถานะช่อง/ชุดล่าสุดตามจริง */
    static void onSwap(Context app, int i, Rect[] rects) {
        if (sApps == null || i + 1 >= sApps.size()) return;
        ComponentName t = sApps.get(i); sApps.set(i, sApps.get(i + 1)); sApps.set(i + 1, t);
        for (Btn b : sBtns) {
            if (b.slot == i) b.slot = i + 1; else if (b.slot == i + 1) b.slot = i;
        }
        sSlotRects = new Rect[rects.length];
        for (int k = 0; k < rects.length; k++) sSlotRects[k] = new Rect(rects[k]);
        Prefs.of(app).edit().putString(SplitScreen.K_LAST, SplitScreen.flatten(sApps)).apply();
    }

    /** เรียกบน main thread (จากตัวเลือกแอพ) */
    private static void replace(final Context app, final int slot, final ComponentName cn) {
        if (sApps == null || slot >= sApps.size()) return;
        final int gen = SplitScreen.sGen;
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
            final List<String> alive = new ArrayList<String>();
            for (Btn b : sBtns) alive.add(b.pkg);
            List<ComponentName> keep = new ArrayList<ComponentName>();
            for (int i = 0; i < apps.size(); i++) {
                ComponentName c = apps.get(i);
                if (c == null) continue;
                if (i == slot || alive.contains(c.getPackageName())) keep.add(c);
            }
            clearNow(app);
            SplitScreen.launchSet(app, keep);
            return;
        }
        Prefs.of(app).edit().putString(SplitScreen.K_LAST, SplitScreen.flatten(apps)).apply();   // "ชุดล่าสุด" = ชุดที่แก้แล้ว
        final Handler h = SplitScreen.worker();
        h.post(new Runnable() {
            public void run() {
                if (gen != SplitScreen.sGen) return;
                SplitScreen.animOff(app);
                if (!SplitScreen.place(app, cn, rects[slot])) Log.w(TAG, "replace: เปิด " + cn + " ไม่สำเร็จ");
            }
        });
        h.postDelayed(new Runnable() {
            public void run() {
                SplitScreen.animRestore(app);
                if (gen != SplitScreen.sGen) return;   // ผู้ใช้ออกจากแบ่งจอระหว่างรอ → ห้ามดึงหน้าต่างกลับขึ้นมา
                SplitScreen.enforce(app, apps.subList(slot, slot + 1), new Rect[]{rects[slot]});
                // ดึงทุกหน้าต่างขึ้นหน้าพื้นดำ (แอพที่เพิ่งเปิดอาจดันหน้าต่างอื่นลง) — แอพใหม่ขึ้นท้ายสุด
                List<SplitScreen.TaskRec> ts = SplitScreen.tasks();
                for (int i = 0; i < apps.size(); i++) {
                    if (i == slot || apps.get(i) == null) continue;
                    SplitScreen.TaskRec t = find(ts, apps.get(i).getPackageName());
                    if (t != null) SplitScreen.toFront(app, t.id);
                }
                SplitScreen.TaskRec t = find(ts, cn.getPackageName());
                if (t != null) SplitScreen.toFront(app, t.id);
                Log.i(TAG, "replace slot " + slot + " -> " + cn.flattenToShortString());
                show(app, apps, rects);
            }
        }, 1500);
    }

    // ---------- รอบตรวจ ----------
    private static void startTicking(final Context app) {
        if (sTick != null) main().removeCallbacks(sTick);
        sTick = new Runnable() {
            public void run() {
                if (sBtns.isEmpty()) return;
                checkNow(app);
                main().postDelayed(this, 1000);
            }
        };
        main().post(sTick);
    }

    /** ตรวจทันที (รอบ 1 วิ / พื้นดำ onStop / หลังกด ◀) · อ่านสถานะบนเธรดเบื้องหลัง แล้วตัดสินบน main */
    static void checkNow(final Context ctx) {
        final Context app = ctx.getApplicationContext();
        if (sBtns.isEmpty()) return;
        final List<Btn> snap = new ArrayList<Btn>(sBtns);
        final int gen = SplitScreen.sGen;
        SplitScreen.worker().post(new Runnable() {
            public void run() {
                final List<SplitScreen.TaskRec> ts = SplitScreen.tasks();
                if (ts.isEmpty()) return;   // อ่านไม่ได้ → ไม่ตัดสินอะไร
                boolean reverse = false;
                try { reverse = ReverseWatch.isReverse(ReverseWatch.readGear(app)); } catch (Throwable ignored) {}
                boolean interactive = true;
                try { interactive = ((PowerManager) app.getSystemService(Context.POWER_SERVICE)).isInteractive(); } catch (Throwable ignored) {}
                Set<String> pkgs = new HashSet<String>();
                for (Btn b : snap) pkgs.add(b.pkg);
                SplitScreen.TaskRec top = ts.get(0);
                boolean backdropVisible = false;
                SplitScreen.TaskRec backdrop = null;
                for (SplitScreen.TaskRec t : ts) if (SplitScreen.isBackdrop(t)) { backdrop = t; backdropVisible = t.visible; break; }
                boolean ours = SplitScreen.isBackdrop(top) || pkgs.contains(top.pkg);
                final boolean covered = reverse || !ours || !backdropVisible;
                // ข้อมูลต่อปุ่ม
                final int n = snap.size();
                final boolean[] alive = new boolean[n], stackVis = new boolean[n];
                final Rect[] rs = new Rect[n];
                final int[] newer = new int[n];
                for (int i = 0; i < n; i++) {
                    Btn b = snap.get(i);
                    newer[i] = -1;
                    SplitScreen.TaskRec r = byId(ts, b.task);
                    alive[i] = r != null;
                    if (r != null) { stackVis[i] = r.visible; rs[i] = r.bounds != null ? r.bounds : SplitScreen.taskBounds(b.task); }
                    else {   // แอพเปิดตัวเองใน task ใหม่ (id มากกว่าเดิม) → ตามไป · task เก่าค้างจากรอบก่อน (id น้อยกว่า) ไม่เอา
                        for (SplitScreen.TaskRec t : ts) if (t.pkg.equals(b.pkg) && t.id > b.task) { newer[i] = t.id; break; }
                    }
                }
                // จบ/ดึงกลับ — เฉพาะตอนจอเปิดใช้งาน และพ้นช่วงกำลังเปิดชุด
                int action = 0;   // 1 = จบแบ่งจอ, 2 = ดึงแบ่งจอกลับขึ้นมา
                if (interactive && !reverse && System.currentTimeMillis() - SplitScreen.sLaunchAt > 3000) {
                    if (top.pkg.equals(SplitScreen.homePkg(app))) action = 1;
                    else if (pkgs.contains(top.pkg) && top.mode == SplitScreen.WINDOWING_MODE_FREEFORM && !backdropVisible) action = 2;
                }
                if (action == 1) {
                    Log.i(TAG, "หน้าหลักขึ้นบนสุด → จบแบ่งจอ");
                    if (gen == SplitScreen.sGen) SplitScreen.endSession(app);
                    return;
                }
                if (action == 0 && !covered && gen == SplitScreen.sGen) {   // หน้าต่างหลุดไปอยู่ใต้พื้นดำ → ดึงขึ้น
                    long now = System.currentTimeMillis();
                    for (int i = 0; i < n; i++) {
                        Btn b = snap.get(i);
                        if (alive[i] && !stackVis[i] && now - b.backAt > 1500) {
                            Log.i(TAG, "หน้าต่าง " + b.pkg + " อยู่ใต้พื้นดำ → ดึงขึ้น");
                            SplitScreen.toFront(app, b.task);
                        }
                    }
                }
                if (action == 2 && gen == SplitScreen.sGen) {
                    Log.i(TAG, "แอพในชุดถูกเปิดขึ้นมาเดี่ยว ๆ (" + top.pkg + ") → ดึงแบ่งจอกลับ");
                    if (backdrop != null) SplitScreen.toFront(app, backdrop.id);
                    for (Btn b : snap) if (!b.pkg.equals(top.pkg)) SplitScreen.toFront(app, b.task);
                    SplitScreen.toFront(app, top.id);
                }
                main().post(new Runnable() {
                    public void run() { if (gen == SplitScreen.sGen) apply(app, snap, covered, alive, stackVis, rs, newer); }
                });
            }
        });
    }

    private static void apply(final Context app, List<Btn> snap, boolean covered, boolean[] alive, boolean[] stackVis,
                              Rect[] rs, int[] newer) {
        WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
        Point scr = new Point();
        wm.getDefaultDisplay().getRealSize(scr);
        sCovered = covered;
        SlotPicker.setVisible(!covered);
        SplitDividers.setVisible(!covered);
        CarPlayDivider.setVisible(!covered);
        for (int i = 0; i < snap.size(); i++) {
            final Btn b = snap.get(i);
            if (!sBtns.contains(b)) continue;
            if (!alive[i]) {
                if (newer[i] >= 0) {   // ตามแอพไป task ใหม่ แล้วจับเข้าช่องเดิม
                    final int nt = newer[i];
                    final Rect r = b.lastRect;
                    Log.i(TAG, b.pkg + " ย้ายไป task ใหม่ " + b.task + " → " + nt);
                    b.task = nt;
                    if (r != null) SplitScreen.worker().post(new Runnable() {
                        public void run() { SplitScreen.moveTask(nt, r); SplitScreen.toFront(app, nt); }
                    });
                    continue;
                }
                if (gone(app, b)) return;
                continue;
            }
            Rect r = rs[i];
            // ไม่รู้ตำแหน่ง / เต็มจอ (กดขยาย) → ซ่อน
            boolean freeform = r != null && !r.isEmpty() && (r.width() < scr.x - 8 || r.top > 8);
            boolean full = r != null && r.left <= 0 && r.width() >= scr.x - 8;
            if (!freeform || full || covered) {
                if (b.shown) { try { wm.removeView(b.view); } catch (Throwable ignored) {} b.shown = false; }
                if (freeform && !full) b.lastRect = new Rect(r);
                continue;
            }
            b.lastRect = new Rect(r);
            b.lp.x = r.left + r.width() / 2 - b.lp.width / 2;   // กลางแถบบน (ปุ่ม ✕/ขยาย ของระบบอยู่ขอบซ้ายหรือขวา)
            b.lp.y = r.top + dp(app, 4);
            try {
                if (b.shown) wm.updateViewLayout(b.view, b.lp);
                else { wm.addView(b.view, b.lp); b.shown = true; }
            } catch (Throwable t) { Log.w(TAG, "layout", t); }
        }
    }

    /** เรียกบน main thread · หน้าต่างของปุ่ม b ปิดไปแล้ว → ลบปุ่ม + เส้นแบ่ง แล้วให้เลือกแอพมาแทนในช่องนั้น
     *  true = ปิดครบทุกช่อง (จบแบ่งจอแล้ว) */
    private static boolean gone(final Context app, Btn b) {
        if (!sBtns.contains(b)) return false;
        Log.i(TAG, "ช่อง " + b.slot + " (" + b.pkg + " task " + b.task + ") ปิดแล้ว → ตัวเลือกแอพ");
        WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
        if (b.shown) { try { wm.removeView(b.view); } catch (Throwable ignored) {} b.shown = false; }
        sBtns.remove(b);
        SplitDividers.clear(app);
        if (b.pkg.startsWith(SplitScreen.CARPLAY_PKG)) CarPlayDivider.clear(app);
        if (sBtns.isEmpty()) {     // ปิดครบทุกหน้าต่าง → จบแบ่งจอ (คืนหน้าต่างที่ถูกย่อไว้เป็นเต็มจอด้วย)
            clearNow(app);
            SplitScreen.worker().post(new Runnable() { public void run() { SplitScreen.endSession(app); } });
            return true;
        }
        showPicker(app, b.slot, b.lastRect);
        return false;
    }

    /** หลังกด ◀: เฝ้าดู task ถี่ ๆ ~1.5 วิ — แอพปิดตัว/ย่อตัวเอง → ขึ้นตัวเลือกแอพทันที ไม่ต้องรอรอบ 1 วิ (เห็นจอดำ) */
    private static void watchGone(final Context app, final int task) {
        new Thread(new Runnable() {
            public void run() {
                for (int i = 0; i < 50; i++) {
                    SystemClock.sleep(30);
                    SplitScreen.TaskRec r = byId(SplitScreen.tasks(), task);
                    if (r == null || !r.visible) {
                        final boolean dead = r == null;
                        main().post(new Runnable() {
                            public void run() {
                                if (dead) { checkNow(app); return; }   // อาจเป็นแอพที่เปิดตัวเองใน task ใหม่ → ให้รอบตรวจตัดสิน
                                for (Btn b : new ArrayList<Btn>(sBtns)) if (b.task == task && !sCovered) gone(app, b);
                            }
                        });
                        return;
                    }
                }
            }
        }, "ex2-back-watch").start();
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
                watchGone(app, task);
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

    /** task บนสุด (ลำดับหน้าต่างจริง) ของแพ็กเกจ */
    private static SplitScreen.TaskRec find(List<SplitScreen.TaskRec> ts, String pkg) {
        for (SplitScreen.TaskRec t : ts) if (pkg.equals(t.pkg)) return t;
        return null;
    }

    private static SplitScreen.TaskRec byId(List<SplitScreen.TaskRec> ts, int id) {
        for (SplitScreen.TaskRec t : ts) if (t.id == id) return t;
        return null;
    }

    private static int dp(Context c, int v) { return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f); }
}
