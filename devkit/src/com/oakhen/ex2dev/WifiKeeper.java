package com.oakhen.ex2dev;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * Wi-Fi เปิดเสมอ (สวิตช์ในหน้าตั้งค่า, default เปิด) — DevService เรียก start() ตอนบูต/สตาร์ทรถ
 *  - บูต: เปิด Wi-Fi (ลองซ้ำ 0/5/15 วิ เผื่อ WifiService ยังไม่พร้อม) · จอตื่นจาก sleep (SCREEN_ON): ลองที่ 1/6 วิ
 *  - ไม่แตะเลยถ้า hotspot รถเปิด / โหมดเครื่องบิน / จอไม่ interactive (ดู blocked())
 *  - มีอะไรปิด Wi-Fi ระหว่างรถติด -> เปิดคืนใน 3 วิ
 *  - ยังไม่ต่อเครือข่าย -> สั่งสแกนทุก 20 วิ ให้เจอ Wi-Fi ที่เคยเชื่อมต่อเร็วขึ้น (ต่อเองโดยระบบ Android)
 * uid system -> setWifiEnabled/startScan ได้ ไม่โดน throttle สแกน (มี NETWORK_SETTINGS)
 * ตอนดับรถ จอหลับลึก/ไฟตัด = วิทยุ Wi-Fi ดับตามฮาร์ดแวร์ ทำอะไรไม่ได้ (และไม่ควร — กินแบต 12V)
 */
final class WifiKeeper {
    static final String TAG = "EX2Wifi";
    private static final long SCAN_MS = 20000;
    private static boolean sStarted;
    private static Handler sH;
    private static Context sApp;

    private WifiKeeper() {}

    static synchronized void start(Context ctx) {
        if (sStarted) return;
        sStarted = true;
        sApp = ctx.getApplicationContext();
        sH = new Handler(Looper.getMainLooper());

        IntentFilter f = new IntentFilter();
        f.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        f.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        f.addAction(Intent.ACTION_SCREEN_ON);   // สตาร์ทรถแบบ "ตื่นจาก sleep" (ไม่ใช่รีบูต) ไม่มี BOOT_COMPLETED
        sApp.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                if (Intent.ACTION_SCREEN_ON.equals(i.getAction())) {
                    for (long d : new long[]{1000, 6000}) {
                        sH.postDelayed(new Runnable() { public void run() { ensureOn(); } }, d);
                    }
                } else if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(i.getAction())) {
                    int st = i.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
                    if (st == WifiManager.WIFI_STATE_DISABLED && enabledPref()) {
                        Log.i(TAG, "Wi-Fi ถูกปิด -> เปิดคืนใน 3 วิ");
                        sH.postDelayed(new Runnable() { public void run() { ensureOn(); } }, 3000);
                    }
                } else {
                    NetworkInfo ni = i.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                    if (ni != null && ni.isConnected()) Log.i(TAG, "ต่อ Wi-Fi แล้ว");
                }
            }
        }, f);

        for (long d : new long[]{0, 5000, 15000}) {
            sH.postDelayed(new Runnable() { public void run() { ensureOn(); } }, d);
        }
        sH.postDelayed(new Runnable() {
            public void run() {
                try { scanIfDisconnected(); } catch (Throwable ignored) {}
                sH.postDelayed(this, SCAN_MS);
            }
        }, SCAN_MS);
    }

    private static boolean enabledPref() {
        return sApp != null && Prefs.get(sApp, Prefs.KEY_WIFI_ON, true);
    }

    private static WifiManager wm() {
        return (WifiManager) sApp.getSystemService(Context.WIFI_SERVICE);
    }

    // ห้ามเปิด Wi-Fi ทับ: (1) hotspot รถกำลังเปิด — AOSP 9 WifiServiceImpl.setWifiEnabled ยอมให้ uid ที่มี
    // NETWORK_SETTINGS (= เรา) toggle ได้แม้ SoftAP เปิด -> จะไปแย่ง/ตัด hotspot  (2) โหมดเครื่องบิน
    // (3) จอไม่ interactive = กำลังดับรถ/หลับ — ไม่ฝืนเปิดสวนตอนระบบปิดเพื่อเข้า sleep
    private static final int WIFI_AP_STATE_ENABLING = 12, WIFI_AP_STATE_ENABLED = 13;

    private static boolean blocked(WifiManager w) {
        try {
            int ap = (Integer) WifiManager.class.getMethod("getWifiApState").invoke(w);
            if (ap == WIFI_AP_STATE_ENABLED || ap == WIFI_AP_STATE_ENABLING) { Log.i(TAG, "hotspot เปิดอยู่ -> ไม่แตะ Wi-Fi"); return true; }
        } catch (Throwable ignored) {}
        try {
            if (android.provider.Settings.Global.getInt(sApp.getContentResolver(), android.provider.Settings.Global.AIRPLANE_MODE_ON, 0) == 1) return true;
        } catch (Throwable ignored) {}
        try {
            android.os.PowerManager pm = (android.os.PowerManager) sApp.getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isInteractive()) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    static void ensureOn() {
        if (!enabledPref()) return;
        try {
            if (blocked(wm())) return;
            WifiManager w = wm();
            if (w == null) return;
            int st = w.getWifiState();
            if (st == WifiManager.WIFI_STATE_DISABLED || st == WifiManager.WIFI_STATE_UNKNOWN) {
                boolean ok = w.setWifiEnabled(true);
                Log.i(TAG, "เปิด Wi-Fi: " + ok);
            }
        } catch (Throwable t) {
            Log.w(TAG, "setWifiEnabled failed", t);
        }
    }

    private static void scanIfDisconnected() {
        if (!enabledPref()) return;
        WifiManager w = wm();
        if (w == null) return;
        if (!w.isWifiEnabled()) { ensureOn(); return; }
        if (blocked(w)) return;
        android.net.wifi.WifiInfo info = w.getConnectionInfo();
        boolean connected = info != null && info.getNetworkId() != -1
                && info.getSupplicantState() == android.net.wifi.SupplicantState.COMPLETED;
        if (!connected) {
            boolean ok = w.startScan();
            Log.i(TAG, "ยังไม่ต่อ -> สแกนหา Wi-Fi ที่เคยต่อ: " + ok);
        }
    }
}
