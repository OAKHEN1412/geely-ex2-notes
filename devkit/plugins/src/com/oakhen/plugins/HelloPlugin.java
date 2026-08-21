package com.oakhen.plugins;

import android.content.Context;
import com.oakhen.ex2dev.Plugin;

/**
 * ตัวอย่าง plugin — push เข้า EX2 BY OAKHEN ผ่าน WiFi แล้วรันสด (ไม่ reinstall)
 * คืนสตริงกลับไปให้ client. รันด้วยสิทธิ์ host (android.uid.system) เข้าถึง VHAL/settings ได้
 * คอมไพล์: ดู devkit/build_plugin.sh -> ได้ .dex แล้ว push ด้วย tools/push_plugin.py
 */
public class HelloPlugin implements Plugin {
    @Override
    public String run(Context ctx, String arg) {
        StringBuilder sb = new StringBuilder();
        sb.append("hello from HelloPlugin\n");
        sb.append("arg = ").append(arg).append("\n");
        sb.append("pkg = ").append(ctx.getPackageName()).append("\n");
        sb.append("uid = ").append(android.os.Process.myUid()).append("\n");
        sb.append("model = ").append(android.os.Build.MODEL).append("\n");
        sb.append("android = ").append(android.os.Build.VERSION.SDK_INT);
        return sb.toString();
    }
}
