package com.oakhen.ex2dev;

import android.util.Log;
import android.view.View;

/**
 * Android 9 "แตะเพื่อโฟกัส" (TaskTapPointerEventListener) ดูแค่พิกัด: แตะปุ่มลอยของเราที่ทับพื้นดำ
 * = ระบบ setFocusedTask(พื้นดำ) → พื้นดำขึ้นบังทุกหน้าต่าง (วัดบนเครื่องจำลอง: จอดำ ~0.4 วิทุกครั้งที่แตะตัวเลือกแอพ/✕/⇄)
 * แก้: บอก WM ว่าหน้าต่างลอยนี้เป็น "tap exclude region" (API ซ่อนที่ ActivityView ใช้:
 * IWindowSession.updateTapExcludeRegion) → แตะตรงนี้ระบบไม่ย้ายโฟกัส
 */
final class TapExclude {
    private TapExclude() {}

    /** เรียกหลัง addView/updateViewLayout · กันทั้งหน้าต่าง ตามตำแหน่งใน lp (หน้าต่างลอยของเราวาง TOP|START พิกัดจอ)
     *  พิกัดต้องเป็นพิกัดจอ (Android 9 เอากรอบไป intersect กับกรอบหน้าต่างบนจอ — ส่งพิกัดในหน้าต่างแล้วไม่มีผล ทดสอบแล้ว) */
    static void apply(final View v, final android.view.WindowManager.LayoutParams lp) {
        final int x = lp.x, y = lp.y, w = Math.max(1, lp.width), h = Math.max(1, lp.height);
        v.post(new Runnable() {
            public void run() {
                try {
                    Object root = View.class.getMethod("getViewRootImpl").invoke(v);
                    if (root == null) return;
                    java.lang.reflect.Field fw = root.getClass().getDeclaredField("mWindow");
                    fw.setAccessible(true);
                    Object window = fw.get(root);
                    Object session = Class.forName("android.view.WindowManagerGlobal").getMethod("getWindowSession").invoke(null);
                    Class<?> iw = Class.forName("android.view.IWindow");
                    session.getClass().getMethod("updateTapExcludeRegion", iw, int.class, int.class, int.class, int.class, int.class)
                            .invoke(session, window, 0, x, y, w, h);
                } catch (Throwable t) { Log.w("EX2Split", "updateTapExcludeRegion", t); }
            }
        });
    }
}
