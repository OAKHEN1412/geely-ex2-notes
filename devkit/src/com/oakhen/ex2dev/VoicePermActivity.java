package com.oakhen.ex2dev;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

/**
 * ขอสิทธิ์ไมค์ (RECORD_AUDIO) ตอนรันไทม์ให้ผู้ช่วยสั่งเสียง — โปร่งใส ไม่มี UI ของตัวเอง ขึ้นแค่กล่องขอสิทธิ์ของระบบ
 * อนุญาตแล้วเริ่มฟังต่อเลย · ข้ามขั้นนี้ได้ด้วย: adb shell pm grant com.geely.ex2.tools android.permission.RECORD_AUDIO
 */
public class VoicePermActivity extends Activity {
    private static final int REQ = 7;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { finish(); return; }
        try {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, REQ);
        } catch (Throwable t) {
            Log.w(VoiceAssistant.TAG, "requestPermissions", t);
            finish();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        boolean ok = res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED;
        Log.i(VoiceAssistant.TAG, "สิทธิ์ไมค์: " + (ok ? "อนุญาต" : "ไม่อนุญาต"));
        if (ok) VoiceAssistant.restart(getApplicationContext());
        finish();
    }
}
