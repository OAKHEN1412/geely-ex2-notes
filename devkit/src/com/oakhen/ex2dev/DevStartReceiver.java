package com.oakhen.ex2dev;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * สตาร์ท DevService เมื่อ: บูตเสร็จ / แอปถูกติดตั้งทับ (MY_PACKAGE_REPLACED) / broadcast เอง
 *   ครั้งแรกหลัง adb install -r:  adb shell am broadcast -a com.oakhen.ex2dev.START
 *   หลังจากนั้น install -r ทับ -> MY_PACKAGE_REPLACED สตาร์ทให้อัตโนมัติ (ไม่ต้องรีบูต)
 */
public class DevStartReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        Intent s = new Intent(ctx, DevService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(s);
            else ctx.startService(s);
        } catch (Throwable ignored) {
        }
    }
}
