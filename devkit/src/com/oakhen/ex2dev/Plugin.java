package com.oakhen.ex2dev;

import android.content.Context;

/**
 * สัญญาปลั๊กอิน: dex ที่ push เข้ามาต้องมีคลาสที่ implement ตัวนี้
 * (หรืออย่างน้อยมีเมธอด public String run(Context, String) — host จะ reflect เรียกให้)
 * รันด้วยสิทธิ์ของแอป host (android.uid.system) — เข้าถึง VHAL / secure settings ได้เต็ม
 */
public interface Plugin {
    String run(Context ctx, String arg);
}
