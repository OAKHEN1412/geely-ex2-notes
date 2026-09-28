package com.oakhen.ex2dev;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * กัน Google Play Services crash วน บนจอรถ
 * GMS 26.x: PhoneHubChimeraService เรียก CrossProfileApps ซึ่งจอนี้ไม่มี (getSystemService = null) → NPE วนทุก ~2 วิ
 * `pm disable` ทีเดียวไม่พอ — GMS ตั้งสถานะ component ของตัวเองกลับ (เจอบนรถ 2026-09-28: หายจาก disabledComponents)
 * → เฝ้าไว้: ตอน service เริ่ม, ทุกครั้ง GMS เปลี่ยน/อัปเดต (PACKAGE_CHANGED/REPLACED), และทุก 5 นาที
 * uid system → setComponentEnabledSetting ข้าม package ได้
 */
final class GmsGuard {
    static final String TAG = "EX2GmsGuard";
    static final String GMS = "com.google.android.gms";
    static final String[] OFF = {
            "com.google.android.gms.auth.proximity.phonehub.PhoneHubService",
            "com.google.android.gms.auth.proximity.phonehub.PhoneHubNotificationListenerService",
    };
    private static boolean sStarted;

    private GmsGuard() {}

    static synchronized void start(Context ctx) {
        if (sStarted) return;
        sStarted = true;
        final Context app = ctx.getApplicationContext();
        apply(app);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addDataScheme("package");
        app.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                if (i.getData() != null && GMS.equals(i.getData().getSchemeSpecificPart())) apply(app);
            }
        }, f);

        final Handler h = new Handler(Looper.getMainLooper());
        h.postDelayed(new Runnable() {
            public void run() {
                apply(app);
                h.postDelayed(this, 5 * 60 * 1000L);
            }
        }, 5 * 60 * 1000L);
    }

    static void apply(Context c) {
        PackageManager pm = c.getPackageManager();
        for (String cls : OFF) {
            ComponentName cn = new ComponentName(GMS, cls);
            try {
                if (pm.getComponentEnabledSetting(cn) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                    pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
                    Log.i(TAG, "disabled " + cls);
                }
            } catch (IllegalArgumentException e) {
                // ไม่มี GMS / ไม่มี component นี้ในเวอร์ชันนี้ — ข้าม
            } catch (Throwable t) {
                Log.w(TAG, "disable " + cls, t);
            }
        }
    }
}
