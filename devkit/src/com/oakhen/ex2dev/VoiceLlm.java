package com.oakhen.ex2dev;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * สำรองของตัวจับคีย์เวิร์ด: ประโยคที่ VoiceIntent.parse ไม่เข้าใจ → ถาม LLM (OpenAI-compatible, TokenMe) ให้คืน JSON intent ชุดเดียวกัน
 *  - key ฝังตอน build จาก _signing-kit/tokenme-key.txt → assets/oakhen_voice.properties (วิธีเดียวกับ token อัปเดตเอง) · ไม่มีไฟล์ = ปิดส่วนนี้
 *  - ห้าม log/พิมพ์ key เด็ดขาด (log เฉพาะรหัส HTTP / finish_reason)
 *  - deepseek-v4-flash เป็นโมเดล reasoning: ต้องให้ max_tokens เยอะ ไม่งั้นคิดไม่จบแล้ว content ว่าง (finish_reason "length") → ลองใหม่ 1 ครั้งด้วยค่าที่ใหญ่ขึ้น
 *  - ต้องส่ง User-Agent ที่ไม่ใช่ค่าเริ่มต้นของ Java ไม่งั้น Cloudflare ตอบ 403/1010
 * เรียกบนเธรดเบื้องหลังเท่านั้น
 */
final class VoiceLlm {
    static final String TAG = "EX2Voice";
    static final String URL = "https://api.tokenme.limited/v1/chat/completions";
    static final String MODEL = "deepseek-ai/deepseek-v4-flash-0731";
    static final int[] MAX_TOKENS = {6000, 12000};

    private static final String SYSTEM =
            "You parse short Thai in-car voice commands (speech-to-text, may contain mistakes) into ONE JSON object. "
            + "Reply with the JSON only: no markdown, no explanation. Keys: "
            + "{\"intent\":string, \"app\":string, \"apps\":[string], \"place\":string, \"query\":string, \"value\":number}. "
            + "intent is exactly one of: "
            + "open_app (app = app name), split (apps = 2 or 3 app names for split screen), navigate (place = destination), "
            + "music (query = song/artist to play; empty = just play), next, prev, play, pause, "
            + "ac_on, ac_off, temp (value = cabin temperature in Celsius), fan (value = fan level 1-7), "
            + "drive (switch drive mode), regen (switch regen level), cancel, unknown. "
            + "Keep app names and places exactly as spoken (Thai or English). Use unknown when the command is not one of these.";

    private static String sKey;
    private static boolean sKeyLoaded;

    private VoiceLlm() {}

    private static synchronized String key(Context c) {
        if (!sKeyLoaded) {
            sKeyLoaded = true;
            Properties p = new Properties();
            try { InputStream in = c.getAssets().open("oakhen_voice.properties"); p.load(in); in.close(); } catch (Throwable ignored) {}
            sKey = p.getProperty("key", "").trim();
        }
        return sKey;
    }

    /** มี key ฝังมาไหม (ไม่ได้ใส่ตอน build = ปิด LLM) */
    static boolean available(Context c) { return !key(c).isEmpty(); }

    /** null = ถามไม่สำเร็จ (err[0] = เหตุผลสั้น ๆ) · ไม่เข้าใจจริง ๆ = VoiceIntent UNKNOWN */
    static VoiceIntent ask(Context c, String heard, String[] err) {
        for (int i = 0; i < MAX_TOKENS.length; i++) {
            try {
                String[] fin = new String[1];
                String content = call(c, heard, MAX_TOKENS[i], fin);
                if (content == null || content.trim().isEmpty()) {
                    if ("length".equals(fin[0]) && i + 1 < MAX_TOKENS.length) { Log.i(TAG, "LLM finish_reason=length → ลองใหม่"); continue; }
                    err[0] = "LLM ตอบว่าง";
                    return null;
                }
                String js = VoiceIntent.extractJson(content);
                if (js == null) {
                    if ("length".equals(fin[0]) && i + 1 < MAX_TOKENS.length) continue;
                    err[0] = "LLM ตอบไม่ใช่ JSON";
                    return null;
                }
                JSONObject o = new JSONObject(js);
                String intent = o.optString("intent", "unknown");
                String arg = intent.equals("open_app") ? o.optString("app")
                        : intent.equals("navigate") ? o.optString("place")
                        : intent.equals("music") ? o.optString("query") : "";
                List<String> apps = new ArrayList<String>();
                JSONArray arr = o.optJSONArray("apps");
                for (int k = 0; arr != null && k < arr.length(); k++) apps.add(arr.optString(k));
                double v = o.optDouble("value", Double.NaN);
                VoiceIntent r = VoiceIntent.fromLlm(intent, arg, apps, Double.isNaN(v) ? null : Double.valueOf(v));
                Log.i(TAG, "LLM → " + r.kind);
                return r;
            } catch (Throwable t) {
                err[0] = t.getClass().getSimpleName();
                Log.w(TAG, "LLM: " + t.getClass().getSimpleName() + " " + t.getMessage());
                return null;
            }
        }
        err[0] = "LLM คิดไม่จบ";
        return null;
    }

    /** คืน message.content · fin[0] = finish_reason · HTTP ไม่ใช่ 200 = โยน IOException (ไม่ใส่ key/เนื้อหาใน message) */
    private static String call(Context c, String heard, int maxTokens, String[] fin) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", MODEL);
        body.put("max_tokens", maxTokens);
        body.put("temperature", 0);
        JSONArray msgs = new JSONArray();
        msgs.put(new JSONObject().put("role", "system").put("content", SYSTEM));
        msgs.put(new JSONObject().put("role", "user").put("content", heard));
        body.put("messages", msgs);

        HttpURLConnection h = (HttpURLConnection) new URL(URL).openConnection();
        try {
            h.setRequestMethod("POST");
            h.setDoOutput(true);
            h.setConnectTimeout(10000);
            h.setReadTimeout(60000);
            h.setRequestProperty("Content-Type", "application/json");
            h.setRequestProperty("Accept", "application/json");
            h.setRequestProperty("User-Agent", "curl/8.5.0");
            h.setRequestProperty("Authorization", "Bearer " + key(c));
            OutputStream o = h.getOutputStream();
            o.write(body.toString().getBytes("UTF-8"));
            o.close();
            int code = h.getResponseCode();
            if (code != 200) throw new java.io.IOException("HTTP " + code);
            JSONObject res = new JSONObject(new String(ApkSource.readAll(h.getInputStream()), "UTF-8"));
            JSONObject ch = res.getJSONArray("choices").getJSONObject(0);
            fin[0] = ch.optString("finish_reason");
            return ch.getJSONObject("message").optString("content", "");
        } finally {
            h.disconnect();
        }
    }
}
