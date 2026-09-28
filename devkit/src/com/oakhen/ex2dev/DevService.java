package com.oakhen.ex2dev;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

/**
 * OakhenService (คงชื่อคลาส DevService ให้ manifest เดิมชี้ได้) — foreground service เล็ก ๆ
 * หน้าที่: คงโปรเซสให้ overlay กล่องลอยอยู่ + โชว์กล่องตอนบูต (ตามสวิตช์ในหน้าตั้งค่า) + กู้จอถ้า backlight ค้างดำ
 * ⚠️ ถอด socket/DexClassLoader/RCE ออกทั้งหมดตามผล /scrutinize (กล่องลอย baked ในแอปแล้ว ไม่ต้องใช้ push)
 */
public class DevService extends Service {
    static final String TAG = "EX2Dev";
    static final String CH = "ex2dev";
    private boolean started = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundSafe();
        if (!started) {
            started = true;
            final android.content.Context app = getApplicationContext();
            // กู้ backlight เผื่อค้างดำจากรอบก่อน (process ตายตอนปิดจอ)
            try { FloatingBox.recoverBacklight(app); } catch (Throwable ignored) {}
            // Wi-Fi เปิดเสมอ + สแกนหาเครือข่ายเดิม (ตามสวิตช์ในหน้าตั้งค่า)
            try { WifiKeeper.start(app); } catch (Throwable t) { Log.e(TAG, "WifiKeeper", t); }
            // กัน Play Services crash วน (PhoneHub → CrossProfileApps ที่จอไม่มี)
            try { GmsGuard.start(app); } catch (Throwable t) { Log.e(TAG, "GmsGuard", t); }
            // โผล่กล่องตอนบูตเฉพาะเมื่อ เปิดกล่องลอย + เปิด "เริ่มอัตโนมัติหลังบูต" (สวิตช์แยกในหน้าตั้งค่า)
            if (Prefs.get(app, Prefs.KEY_FLOATING, true) && Prefs.get(app, Prefs.KEY_AUTOSTART, true)) {
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    public void run() { try { FloatingBox.show(app); } catch (Throwable ignored) {} }
                }, 4000);
            }
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void startForegroundSafe() {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                NotificationChannel c = new NotificationChannel(CH, "EX2 BY OAKHEN", NotificationManager.IMPORTANCE_MIN);
                if (nm != null) nm.createNotificationChannel(c);
                Notification n = new Notification.Builder(this, CH)
                        .setContentTitle("EX2 BY OAKHEN")
                        .setContentText("กล่องลอยทำงาน")
                        .setSmallIcon(android.R.drawable.stat_notify_sync)
                        .setOngoing(true)
                        .build();
                startForeground(4711, n);
            }
        } catch (Throwable t) {
            Log.e(TAG, "startForeground failed", t);
            // ต้องเรียก startForeground ให้ได้ ไม่งั้น system ฆ่า service + START_STICKY วนลูป -> fallback ขั้นต่ำ / stopSelf
            try {
                if (Build.VERSION.SDK_INT >= 26) {
                    NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    if (nm != null) nm.createNotificationChannel(new NotificationChannel(CH, "EX2", NotificationManager.IMPORTANCE_MIN));
                    Notification n = new Notification.Builder(this, CH)
                            .setSmallIcon(android.R.drawable.stat_notify_sync).build();
                    startForeground(4711, n);
                }
            } catch (Throwable t2) {
                Log.e(TAG, "fallback foreground failed, stopping", t2);
                try { stopSelf(); } catch (Throwable ignored) {}
            }
        }
    }
}
