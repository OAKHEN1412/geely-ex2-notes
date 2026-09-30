package com.oakhen.ex2dev;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;

/**
 * พื้นหลังดำเต็มจอใต้หน้าต่างแบ่งจอ — ไม่ให้เห็นวอลเปเปอร์ตามช่อง/แถบบน
 * หน้าต่าง freeform อยู่เหนือ stack fullscreen เสมอ → activity นี้ (fullscreen) อยู่ข้างหลังพอดี
 * ถ้าปิดหน้าต่างหมดจะเห็นหน้านี้: แตะ = กลับหน้าหลัก
 */
public class SplitBackdropActivity extends Activity {
    private static WeakReference<SplitBackdropActivity> sCurrent;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sCurrent = new WeakReference<SplitBackdropActivity>(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        TextView t = new TextView(this);
        t.setText("แตะเพื่อกลับหน้าหลัก");
        t.setTextColor(0xFF5F6368);
        t.setTextSize(16);
        root.addView(t, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        root.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { goHome(); }
        });
        setContentView(root);
    }

    private static volatile boolean sVisible;

    /** พื้นดำยังมองเห็นอยู่ = แบ่งจอยังอยู่บนจอ (ไม่ถูกหน้าหลัก/แอพเต็มจอบัง) */
    static boolean isVisible() { return sVisible; }

    @Override protected void onStart() { super.onStart(); sVisible = true; }
    @Override protected void onStop() {
        super.onStop();
        sVisible = false;
        if (isFinishing()) return;
        // ถูกบังค้าง (ไม่ใช่แค่วูบตอนจัดหน้าต่าง) → จบแบ่งจอ คืนทุกหน้าต่างเป็นเต็มจอ
        final Context app = getApplicationContext();
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            public void run() {
                if (sVisible || System.currentTimeMillis() - SplitScreen.sLaunchAt < 4000) return;
                SplitScreen.worker().post(new Runnable() { public void run() { SplitScreen.endSession(app); } });
            }
        }, 400);
    }

    private void goHome() {
        final Context app = getApplicationContext();   // จบแบ่งจอ: หน้าต่างกลับเต็มจอ ปุ่ม/ตัวเลือกแอพไม่ลอยค้าง
        SplitScreen.worker().post(new Runnable() { public void run() { SplitScreen.endSession(app); } });
        finish();
        try {
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable ignored) {}
    }

    /** ปิดพื้นหลัง (ตอนคืนเต็มจอ) */
    static void dismiss() {
        final SplitBackdropActivity a = sCurrent != null ? sCurrent.get() : null;
        if (a != null && !a.isFinishing()) {   // อาจถูกเรียกจากเธรดเบื้องหลัง (exitAll) → ปิดบน UI thread
            a.runOnUiThread(new Runnable() { public void run() { if (!a.isFinishing()) a.finish(); } });
        }
    }
}
