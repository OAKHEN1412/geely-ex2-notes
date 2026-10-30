package com.oakhen.ex2dev;

import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognitionService;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ผู้ช่วยสั่งเสียงบนพวงมาลัย: กดปุ่มสั่งเสียง → การ์ตูนเด้งมุมจอ + ลูกโป่งคำพูด → ฟังไทย (SpeechRecognizer th-TH) → แสดงที่ได้ยิน
 * → ทำตามคำสั่ง (VoiceIntent ก่อน ไม่ตรงค่อยถาม VoiceLlm) → พูดตอบสั้น ๆ (TextToSpeech ไทย) → หายเองหลังเสร็จ ~3 วิ
 * ยกเลิก: กดปุ่มสั่งเสียงซ้ำ / แตะที่ใดก็ได้บนจอ
 *  - UI/recognizer/TTS ทำบน main thread · parse + LLM + หาแอพ ทำบนเธรดเบื้องหลัง "ex2-voice" · สั่งรถผ่านเธรด Vhal
 *  - sGen เพิ่มทุกครั้งที่เริ่ม/ยกเลิก: callback เก่าที่มาช้า (recognizer/LLM/TTS) เช็ค gen แล้วทิ้งตัวเอง
 *  - คำสั่งรถใช้ทางเดียวกับ QuickPanel/SwcWatch (Vhal.* ที่มีอยู่) — ไม่เขียน VHAL ดิบเพิ่ม
 */
final class VoiceAssistant {
    static final String TAG = "EX2Voice";
    static final String MSG_NO_RECOGNIZER = "ไม่พบบริการแปลงเสียง — ต้องลงแอป Google";
    static final String GOOGLE_PKG = "com.google.android.googlequicksearchbox";
    static final String YTM_PKG = "com.google.android.apps.youtube.music", SPOTIFY_PKG = "com.spotify.music", MAPS_PKG = "com.google.android.apps.maps";

    private static final int IDLE = 0, LISTEN = 1, THINK = 2, FINISHED = 3;
    private static final int CLAY = 0xFFD97757, DARK = 0xFF1B1C1E, MUTED_DK = 0xFF6B6760;

    // main thread เท่านั้น
    private static int sPhase = IDLE, sGen;
    private static Context sApp;
    private static Handler sMain;
    private static View sRoot;
    private static VoiceMascot sMascot;
    private static TextView sHeard, sReply;
    private static SpeechRecognizer sRec;
    private static Runnable sWatchdog, sDismiss;
    private static int sFinalState = VoiceMascot.DONE;

    private static TextToSpeech sTts;
    private static boolean sTtsReady, sTtsLoading;
    private static boolean sFocusHeld;
    private static final AudioManager.OnAudioFocusChangeListener FOCUS = new AudioManager.OnAudioFocusChangeListener() {
        public void onAudioFocusChange(int change) {}
    };

    private VoiceAssistant() {}

    private static Handler main() {
        if (sMain == null) sMain = new Handler(Looper.getMainLooper());
        return sMain;
    }

    // ---------- เข้า/ออก ----------
    /** ปุ่มสั่งเสียง (เรียกจาก SwcWatch ได้ทุกเธรด): กำลังทำงานอยู่ = ยกเลิก · ไม่ใช่ = เริ่มฟัง */
    static void toggle(Context ctx) {
        final Context app = ctx.getApplicationContext();
        main().post(new Runnable() {
            public void run() {
                sApp = app;
                if (sPhase != IDLE) cancel(); else begin();
            }
        });
    }

    /** เริ่มใหม่ (หลังผู้ใช้อนุญาตไมค์) */
    static void restart(Context ctx) {
        final Context app = ctx.getApplicationContext();
        main().post(new Runnable() {
            public void run() { sApp = app; cancel(); begin(); }
        });
    }

    private static void begin() {
        final Context app = sApp;
        sPhase = LISTEN;
        final int gen = ++sGen;
        show(app);
        setUi(VoiceMascot.LISTENING, "", "กำลังฟัง…");
        warmTts(app);
        warmApps(app);
        if (app.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "ยังไม่มีสิทธิ์ RECORD_AUDIO → ขอสิทธิ์ (หรือ adb shell pm grant " + app.getPackageName() + " android.permission.RECORD_AUDIO)");
            askPermission(app);
            finish(gen, VoiceMascot.ERROR, "ต้องอนุญาตใช้ไมค์ก่อน (แตะ อนุญาต)");
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(app)) {
            Log.w(TAG, "SpeechRecognizer.isRecognitionAvailable() = false — ไม่มี RecognitionService (ต้องมีแอป Google / ตั้ง voice_recognition_service)");
            finish(gen, VoiceMascot.ERROR, MSG_NO_RECOGNIZER);
            return;
        }
        try {
            ComponentName svc = recognizerComponent(app);
            Log.i(TAG, "recognizer = " + (svc == null ? "ค่าเริ่มต้นของระบบ" : svc.flattenToShortString()));
            sRec = svc != null ? SpeechRecognizer.createSpeechRecognizer(app, svc) : SpeechRecognizer.createSpeechRecognizer(app);
            sRec.setRecognitionListener(new Rec(gen));
            requestFocus(app);
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "th-TH")
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "th-TH")
                    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, app.getPackageName());
            sRec.startListening(i);
            arm(20000, gen);
        } catch (Throwable t) {
            Log.w(TAG, "เริ่มฟังไม่ได้", t);
            finish(gen, VoiceMascot.ERROR, "เปิดตัวฟังเสียงไม่ได้");
        }
    }

    /** ยกเลิก/ปิดทุกอย่าง (เรียกบน main thread) */
    static void cancel() {
        sGen++;
        sPhase = IDLE;
        main().removeCallbacks(sWatchdog != null ? sWatchdog : NOOP);
        main().removeCallbacks(sDismiss != null ? sDismiss : NOOP);
        killRecognizer();
        abandonFocus();
        if (sTts != null) { try { sTts.stop(); } catch (Throwable ignored) {} }
        hide();
    }

    private static final Runnable NOOP = new Runnable() { public void run() {} };

    /** จบรอบ: แสดงคำตอบ + พูด + ตั้งเวลาหาย */
    private static void finish(int gen, int state, String reply) {
        if (gen != sGen || sPhase == IDLE || sPhase == FINISHED) return;
        sPhase = FINISHED;
        main().removeCallbacks(sWatchdog != null ? sWatchdog : NOOP);
        killRecognizer();
        abandonFocus();
        sFinalState = state;
        setUi(state, null, reply);
        if (sTtsReady && sTts != null) {
            try {
                sMascot.setState(VoiceMascot.SPEAKING);
                sTts.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "v" + gen);
                scheduleDismiss(gen, 3000 + Math.min(12000, reply.length() * 180L));   // กันกรณี onDone ไม่มา · ถ้ามาจะนับ 3 วิใหม่
                return;
            } catch (Throwable t) { Log.w(TAG, "TTS speak", t); }
        } else {
            Log.i(TAG, "ไม่พูด (TTS ไทยไม่พร้อม) — ข้อความอยู่ในลูกโป่ง");
        }
        scheduleDismiss(gen, 3000);
    }

    private static void scheduleDismiss(final int gen, long ms) {
        main().removeCallbacks(sDismiss != null ? sDismiss : NOOP);
        sDismiss = new Runnable() { public void run() { if (gen == sGen && sPhase != IDLE) cancel(); } };
        main().postDelayed(sDismiss, ms);
    }

    private static void arm(long ms, final int gen) {
        main().removeCallbacks(sWatchdog != null ? sWatchdog : NOOP);
        sWatchdog = new Runnable() {
            public void run() {
                if (gen == sGen && (sPhase == LISTEN || sPhase == THINK)) finish(gen, VoiceMascot.ERROR, "หมดเวลา ลองกดใหม่นะ");
            }
        };
        main().postDelayed(sWatchdog, ms);
    }

    // ---------- ฟัง ----------
    private static final class Rec implements RecognitionListener {
        private final int gen;
        Rec(int gen) { this.gen = gen; }
        private boolean live() { return gen == sGen && sPhase == LISTEN; }

        public void onReadyForSpeech(Bundle p) {}
        public void onBeginningOfSpeech() {}
        public void onBufferReceived(byte[] b) {}
        public void onEndOfSpeech() {}
        public void onEvent(int t, Bundle p) {}
        public void onRmsChanged(float rms) { if (live() && sMascot != null) sMascot.setLevel(rms); }

        public void onPartialResults(Bundle b) {
            if (!live()) return;
            String s = first(b);
            if (s != null) setUi(VoiceMascot.LISTENING, s, null);
        }

        public void onResults(Bundle b) {
            if (!live()) return;
            String s = first(b);
            if (s == null || s.trim().isEmpty()) { finish(gen, VoiceMascot.ERROR, "ไม่ได้ยินเสียง ลองกดใหม่นะ"); return; }
            think(gen, s.trim());
        }

        public void onError(int e) {
            if (!live()) return;
            Log.w(TAG, "SpeechRecognizer error " + e);
            if (e == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) askPermission(sApp);
            finish(gen, VoiceMascot.ERROR, errMessage(e));
        }

        private String first(Bundle b) {
            ArrayList<String> l = b == null ? null : b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            return l == null || l.isEmpty() ? null : l.get(0);
        }
    }

    private static String errMessage(int e) {
        switch (e) {
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
            case SpeechRecognizer.ERROR_NETWORK: return "เชื่อมต่ออินเทอร์เน็ตไม่ได้ — เปิดฮอตสปอตจากมือถือ";
            case SpeechRecognizer.ERROR_AUDIO: return "ใช้ไมค์ไม่ได้";
            case SpeechRecognizer.ERROR_SERVER: return "บริการแปลงเสียงมีปัญหา ลองใหม่นะ";
            case SpeechRecognizer.ERROR_CLIENT: return "ตัวแปลงเสียงทำงานผิดพลาด";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
            case SpeechRecognizer.ERROR_NO_MATCH: return "ไม่ได้ยินเสียง ลองกดใหม่นะ";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "ตัวแปลงเสียงยังไม่ว่าง ลองใหม่อีกครั้ง";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "ต้องอนุญาตใช้ไมค์ก่อน (แตะ อนุญาต)";
            default: return "ฟังไม่สำเร็จ (รหัส " + e + ")";
        }
    }

    /** ตัวแปลงเสียง: ชอบ Google (th-TH) ก่อน → ค่าที่ตั้งในระบบ (voice_recognition_service) → ตัวแรกที่มี */
    private static ComponentName recognizerComponent(Context app) {
        PackageManager pm = app.getPackageManager();
        List<ResolveInfo> l = pm.queryIntentServices(new Intent(RecognitionService.SERVICE_INTERFACE), 0);
        if (l == null || l.isEmpty()) return null;
        for (ResolveInfo r : l) if (GOOGLE_PKG.equals(r.serviceInfo.packageName)) return new ComponentName(r.serviceInfo.packageName, r.serviceInfo.name);
        String set = Settings.Secure.getString(app.getContentResolver(), "voice_recognition_service");
        ComponentName cur = set == null || set.isEmpty() ? null : ComponentName.unflattenFromString(set);
        if (cur != null) for (ResolveInfo r : l) {
            if (cur.getPackageName().equals(r.serviceInfo.packageName) && cur.getClassName().equals(r.serviceInfo.name)) return cur;
        }
        return new ComponentName(l.get(0).serviceInfo.packageName, l.get(0).serviceInfo.name);
    }

    private static void killRecognizer() {
        if (sRec == null) return;
        try { sRec.cancel(); sRec.destroy(); } catch (Throwable ignored) {}
        sRec = null;
    }

    private static void askPermission(Context app) {
        try { app.startActivity(new Intent(app, VoicePermActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Throwable t) { Log.w(TAG, "ขอสิทธิ์ไมค์ไม่ได้", t); }
    }

    // ---------- คิด + ทำ (เธรดเบื้องหลัง) ----------
    private static void think(final int gen, final String heard) {
        sPhase = THINK;
        killRecognizer();
        abandonFocus();
        setUi(VoiceMascot.THINKING, heard, "กำลังคิด…");
        arm(100000, gen);
        final Context app = sApp;
        new Thread(new Runnable() {
            public void run() {
                try {
                    VoiceIntent v = VoiceIntent.parse(heard);
                    boolean llmFailed = false;
                    if (v.kind == VoiceIntent.Kind.UNKNOWN) {
                        if (VoiceLlm.available(app)) {
                            String[] err = new String[1];
                            VoiceIntent l = VoiceLlm.ask(app, heard, err);
                            if (l != null) v = l; else llmFailed = true;
                        } else {
                            Log.i(TAG, "ไม่มี key LLM (ไม่ได้ฝังตอน build) → ไม่เข้าใจ");
                        }
                    }
                    Log.i(TAG, "intent = " + v);
                    execute(app, v, gen, llmFailed);
                } catch (Throwable t) {
                    Log.w(TAG, "execute", t);
                    say(gen, false, "ขอโทษ ทำไม่สำเร็จ");
                }
            }
        }, "ex2-voice").start();
    }

    /** ส่งผลกลับ main thread */
    private static void say(final int gen, final boolean ok, final String msg) {
        main().post(new Runnable() { public void run() { finish(gen, ok ? VoiceMascot.DONE : VoiceMascot.ERROR, msg); } });
    }

    private static void execute(final Context app, VoiceIntent v, final int gen, boolean llmFailed) {
        switch (v.kind) {
            case CANCEL:
                main().post(new Runnable() { public void run() { if (gen == sGen) cancel(); } });
                return;
            case OPEN_APP: {
                AppRec a = findApp(app, v.text);
                if (a == null) { say(gen, false, "ไม่พบแอป " + v.text); return; }
                try {
                    app.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(a.cn)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
                    say(gen, true, "เปิด " + a.label);
                } catch (Throwable t) { Log.w(TAG, "open " + a.cn, t); say(gen, false, "เปิด " + a.label + " ไม่ได้"); }
                return;
            }
            case SPLIT: {
                if (v.apps == null || v.apps.size() < 2) { say(gen, false, "บอกแอปอย่างน้อยสองตัว เช่น แบ่งจอ ยูทูป กับ แผนที่"); return; }
                final List<ComponentName> picked = new ArrayList<ComponentName>();
                List<String> pkgs = new ArrayList<String>(), names = new ArrayList<String>();
                for (String s : v.apps) {
                    AppRec a = findApp(app, s);
                    if (a == null) { say(gen, false, "ไม่พบแอป " + s); return; }
                    if (pkgs.contains(a.pkg)) continue;   // แอพเดียวกันซ้ำ
                    pkgs.add(a.pkg); picked.add(a.cn); names.add(a.label);
                }
                if (picked.size() < 2) { say(gen, false, "เลือกแอปต่างกันอย่างน้อยสองตัว"); return; }
                boolean ready = SplitScreen.freeformReady(app);   // ยังไม่พร้อม → launchSet เปิดหน้าแบ่งจอ (มีปุ่มตั้งค่า) ให้เอง
                main().post(new Runnable() { public void run() { SplitScreen.launchSet(app, picked); } });
                say(gen, ready, ready ? "แบ่งจอ " + join(names) : "ต้องเปิดโหมดแบ่งจอก่อน เปิดหน้าแบ่งจอให้แล้ว");
                return;
            }
            case NAVIGATE: {
                Uri u = Uri.parse("google.navigation:q=" + Uri.encode(v.text));
                try {
                    try { app.startActivity(new Intent(Intent.ACTION_VIEW, u).setPackage(MAPS_PKG).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
                    catch (ActivityNotFoundException e) { app.startActivity(new Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
                    say(gen, true, "นำทางไป " + v.text);
                } catch (Throwable t) { Log.w(TAG, "navigate", t); say(gen, false, "เปิดนำทางไม่ได้"); }
                return;
            }
            case MEDIA_NEXT: mediaKey(gen, KeyEvent.KEYCODE_MEDIA_NEXT, "เพลงถัดไป"); return;
            case MEDIA_PREV: mediaKey(gen, KeyEvent.KEYCODE_MEDIA_PREVIOUS, "เพลงก่อนหน้า"); return;
            case MEDIA_PLAY: mediaKey(gen, KeyEvent.KEYCODE_MEDIA_PLAY, "เล่นเพลง"); return;
            case MEDIA_PAUSE: mediaKey(gen, KeyEvent.KEYCODE_MEDIA_PAUSE, "หยุดเพลง"); return;
            case MEDIA_SEARCH: {
                String pkg = installed(app, YTM_PKG) ? YTM_PKG : installed(app, SPOTIFY_PKG) ? SPOTIFY_PKG : null;
                if (pkg == null) { say(gen, false, "ไม่พบแอปเพลง (YouTube Music / Spotify)"); return; }
                try {
                    try {
                        app.startActivity(new Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).setPackage(pkg)
                                .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                                .putExtra(SearchManager.QUERY, v.text).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (ActivityNotFoundException e) {   // แอพไม่รับ intent ค้นหา → เปิดหน้าค้นหาของ YT Music แทน
                        if (!YTM_PKG.equals(pkg)) throw e;
                        app.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/search?q=" + Uri.encode(v.text)))
                                .setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    }
                    say(gen, true, "เปิดเพลง " + v.text);
                } catch (Throwable t) { Log.w(TAG, "music search", t); say(gen, false, "เปิดเพลงไม่ได้"); }
                return;
            }
            case AC_ON:
            case AC_OFF: {
                final boolean on = v.kind == VoiceIntent.Kind.AC_ON;
                Vhal.thread().post(new Runnable() {
                    public void run() {
                        boolean ok = Vhal.setBool(app, Vhal.HVAC_AC_ON, on, Vhal.HVAC_AREAS);
                        say(gen, ok, ok ? (on ? "เปิดแอร์แล้ว" : "ปิดแอร์แล้ว") : "สั่งแอร์ไม่ได้ (ต่อระบบรถไม่ได้)");
                    }
                });
                return;
            }
            case TEMP: {
                if (v.num == null || v.num < 16 || v.num > 32) { say(gen, false, "ตั้งอุณหภูมิได้ 16 ถึง 32 องศา"); return; }
                final float to = Math.round(v.num.floatValue() * 2f) / 2f;   // QuickPanel ก็ปัดทีละ 0.5
                Vhal.thread().post(new Runnable() {
                    public void run() {
                        boolean ok = Vhal.setFloat(app, Vhal.HVAC_TEMP, to, Vhal.TEMP_AREAS);
                        String t = to == (int) to ? String.valueOf((int) to) : String.format(Locale.US, "%.1f", to);
                        say(gen, ok, ok ? "ตั้งอุณหภูมิ " + t + " องศา" : "ตั้งอุณหภูมิไม่ได้ (ต่อระบบรถไม่ได้)");
                    }
                });
                return;
            }
            case FAN: {
                if (v.num == null || v.num < 1 || v.num > 7) { say(gen, false, "พัดลมตั้งได้ระดับ 1 ถึง 7"); return; }
                final int lvl = (int) Math.round(v.num);
                Vhal.thread().post(new Runnable() {
                    public void run() {
                        boolean ok = Vhal.setInt(app, Vhal.HVAC_FAN, lvl, Vhal.HVAC_AREAS);
                        say(gen, ok, ok ? "พัดลมระดับ " + lvl : "ตั้งพัดลมไม่ได้ (ต่อระบบรถไม่ได้)");
                    }
                });
                return;
            }
            case DRIVE_MODE:
                // เหมือนปุ่มพวงมาลัย "สลับโหมดขับ" ทุกอย่าง (SwcWatch.cycle → Vhal.setInt) — ไม่มีการเขียนค่าใหม่
                main().post(new Runnable() {
                    public void run() {
                        SwcWatch.cycle(app, Vhal.DRIVE_MODE, Vhal.DRIVE_AREAS, new int[]{Vhal.DRIVE_ECO, Vhal.DRIVE_COMFORT, Vhal.DRIVE_DYNAMIC},
                                new String[]{"ECO", "COMFORT", "SPORT"}, "โหมดขับ", cycleDone(gen, "สลับโหมดขับไม่ได้"));
                    }
                });
                return;
            case REGEN:
                main().post(new Runnable() {
                    public void run() {
                        SwcWatch.cycle(app, Vhal.REGEN, Vhal.REGEN_AREAS, new int[]{Vhal.REGEN_LOW, Vhal.REGEN_MID, Vhal.REGEN_HIGH},
                                new String[]{"ต่ำ", "กลาง", "สูง"}, "รีเจน", cycleDone(gen, "สลับรีเจนไม่ได้"));
                    }
                });
                return;
            default:
                say(gen, false, llmFailed ? "ถามระบบ AI ไม่ได้ ตรวจอินเทอร์เน็ตแล้วลองใหม่" : "ขอโทษ ไม่เข้าใจคำสั่ง ลองพูดใหม่นะ");
        }
    }

    private static SwcWatch.Done cycleDone(final int gen, final String failMsg) {
        return new SwcWatch.Done() {
            public void on(boolean ok, String msg) { say(gen, ok, ok ? msg.replace(": ", " ") : failMsg); }
        };
    }

    /** ส่งปุ่มมีเดีย (SwcWatch.media) · รอ 0.35 วิหลังคืน audio focus ให้แอพเพลงที่ถูกพักไว้กลับมาก่อน ไม่งั้นมันเล่นต่อทับคำสั่ง "หยุด" */
    private static void mediaKey(int gen, int code, String msg) {
        try { Thread.sleep(350); } catch (InterruptedException ignored) {}
        SwcWatch.media(code);
        say(gen, true, msg);
    }

    private static boolean installed(Context app, String pkg) {
        try { app.getPackageManager().getPackageInfo(pkg, 0); return true; } catch (Throwable t) { return false; }
    }

    private static String join(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) sb.append(i > 0 ? " กับ " : "").append(l.get(i));
        return sb.toString();
    }

    // ---------- รายชื่อแอพ ----------
    private static final class AppRec {
        final String label, pkg; final ComponentName cn;
        AppRec(String label, String pkg, ComponentName cn) { this.label = label; this.pkg = pkg; this.cn = cn; }
    }
    private static List<AppRec> sApps;
    private static long sAppsAt;

    /** แอพที่มีไอคอนบนหน้าหลัก (+ CarPlay) ตามที่ SplitScreen ใช้ · โหลดช้าบนจอรถ → อุ่นไว้ตอนเริ่มฟัง */
    private static synchronized List<AppRec> loadApps(Context app) {
        if (sApps != null && System.currentTimeMillis() - sAppsAt < 120000) return sApps;
        PackageManager pm = app.getPackageManager();
        List<AppRec> out = new ArrayList<AppRec>();
        for (ResolveInfo r : SplitScreen.launchables(app)) {
            out.add(new AppRec(String.valueOf(r.loadLabel(pm)), r.activityInfo.packageName,
                    new ComponentName(r.activityInfo.packageName, r.activityInfo.name)));
        }
        sApps = out; sAppsAt = System.currentTimeMillis();
        return out;
    }

    private static void warmApps(final Context app) {
        new Thread(new Runnable() { public void run() { try { loadApps(app); } catch (Throwable ignored) {} } }, "ex2-voice-apps").start();
    }

    private static AppRec findApp(Context app, String spoken) {
        List<AppRec> apps = loadApps(app);
        List<String> labels = new ArrayList<String>(), pkgs = new ArrayList<String>();
        for (AppRec a : apps) { labels.add(a.label); pkgs.add(a.pkg); }
        int i = VoiceIntent.matchApp(spoken, labels, pkgs);
        return i < 0 ? null : apps.get(i);
    }

    // ---------- เสียงพูด (TTS) ----------
    private static void warmTts(final Context app) {
        if (sTtsLoading) return;
        if (sTts != null) { if (!sTtsReady) useThai(); return; }
        sTtsLoading = true;
        sTts = new TextToSpeech(app, new TextToSpeech.OnInitListener() {
            public void onInit(final int status) {
                main().post(new Runnable() {
                    public void run() {
                        sTtsLoading = false;
                        if (status != TextToSpeech.SUCCESS) { Log.w(TAG, "TTS init ไม่สำเร็จ (" + status + ") → ไม่พูด"); sTts = null; return; }
                        useThai();
                    }
                });
            }
        });
    }

    private static void useThai() {
        int r = sTts.setLanguage(new Locale("th", "TH"));
        sTtsReady = r >= TextToSpeech.LANG_AVAILABLE;
        if (!sTtsReady) { Log.w(TAG, "TTS ไม่มีเสียงไทย (setLanguage=" + r + ") → ไม่พูด แสดงแต่ข้อความ"); return; }
        sTts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            public void onStart(String id) {}
            public void onDone(String id) { spoken(id); }
            public void onError(String id) { spoken(id); }
            public void onStop(String id, boolean interrupted) { spoken(id); }
        });
    }

    /** พูดจบ (เธรดไหนก็ได้) → การ์ตูนกลับท่าผลลัพธ์ แล้วนับ 3 วิ */
    private static void spoken(final String id) {
        main().post(new Runnable() {
            public void run() {
                if (sPhase != FINISHED || !("v" + sGen).equals(id)) return;
                if (sMascot != null) sMascot.setState(sFinalState);
                scheduleDismiss(sGen, 3000);
            }
        });
    }

    // ---------- audio focus: เพลงที่เล่นอยู่พักระหว่างฟัง (ไม่งั้นไมค์ได้ยินเพลง) ----------
    private static void requestFocus(Context app) {
        try {
            AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
            sFocusHeld = am.requestAudioFocus(FOCUS, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        } catch (Throwable t) { Log.w(TAG, "audio focus", t); }
    }

    private static void abandonFocus() {
        if (!sFocusHeld || sApp == null) return;
        sFocusHeld = false;
        try { ((AudioManager) sApp.getSystemService(Context.AUDIO_SERVICE)).abandonAudioFocus(FOCUS); } catch (Throwable ignored) {}
    }

    // ---------- หน้าต่างลอย (มุมขวาล่าง) ----------
    private static void show(Context app) {
        if (sRoot != null) {   // ยังค้างอยู่ (กำลังจางหาย) → ใช้ตัวเดิม
            sRoot.animate().cancel();
            sRoot.setAlpha(1f); sRoot.setScaleX(1f); sRoot.setScaleY(1f);
            return;
        }
        float d = app.getResources().getDisplayMetrics().density;
        VoiceMascot.BubbleBg bg = new VoiceMascot.BubbleBg(d);

        LinearLayout bubble = new LinearLayout(app);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setBackground(bg);
        bubble.setPadding(dp(d, 16), dp(d, 10), dp(d, 16) + (int) bg.tailWidth(), dp(d, 10));
        sHeard = new TextView(app);
        sHeard.setTextSize(13);
        sHeard.setTextColor(MUTED_DK);
        sHeard.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
        sHeard.setMaxWidth(dp(d, 280));
        sHeard.setMaxLines(2);
        sHeard.setVisibility(View.GONE);
        bubble.addView(sHeard);
        sReply = new TextView(app);
        sReply.setTextSize(17);
        sReply.setTextColor(DARK);
        sReply.setTypeface(Typeface.DEFAULT_BOLD);
        sReply.setMaxWidth(dp(d, 280));
        sReply.setMaxLines(3);
        bubble.addView(sReply);

        LinearLayout root = new LinearLayout(app);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(bubble, new LinearLayout.LayoutParams(-2, -2));
        sMascot = new VoiceMascot(app);
        root.addView(sMascot, new LinearLayout.LayoutParams(-2, -2));

        // แตะตรงไหนก็ยกเลิก (นอกกรอบ = ACTION_OUTSIDE · ในกรอบ = ปล่อยนิ้ว) · NOT_FOCUSABLE ไม่แย่งโฟกัสแอพที่ใช้อยู่
        root.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent e) {
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_OUTSIDE || a == MotionEvent.ACTION_UP) { cancel(); return true; }
                return a == MotionEvent.ACTION_DOWN;
            }
        });
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(-2, -2,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM | Gravity.END;
        lp.x = dp(d, 20); lp.y = dp(d, 20);
        try {
            ((WindowManager) app.getSystemService(Context.WINDOW_SERVICE)).addView(root, lp);
        } catch (Throwable t) {
            Log.w(TAG, "overlay", t);
            sMascot = null; sHeard = null; sReply = null;
            return;
        }
        sRoot = root;
        final View rv = root;
        root.setAlpha(0f); root.setScaleX(0.7f); root.setScaleY(0.7f);
        root.post(new Runnable() {
            public void run() {
                rv.setPivotX(rv.getWidth()); rv.setPivotY(rv.getHeight());   // เด้งออกจากมุมขวาล่าง
                rv.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(240).setInterpolator(new OvershootInterpolator(1.4f)).start();
            }
        });
    }

    private static void hide() {
        final View v = sRoot;
        if (v == null) return;
        sRoot = null; sMascot = null; sHeard = null; sReply = null;
        final Context app = sApp;
        v.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f).setDuration(150).withEndAction(new Runnable() {
            public void run() {
                try { ((WindowManager) app.getSystemService(Context.WINDOW_SERVICE)).removeView(v); } catch (Throwable ignored) {}
            }
        }).start();
    }

    /** heard == null: ไม่แตะข้อความที่ได้ยิน · reply == null: ไม่แตะคำตอบ */
    private static void setUi(int state, String heard, String reply) {
        if (sMascot == null) return;
        sMascot.setState(state);
        if (heard != null) {
            sHeard.setText(heard.isEmpty() ? "" : "“" + heard + "”");
            sHeard.setVisibility(heard.isEmpty() ? View.GONE : View.VISIBLE);
        }
        if (reply != null) sReply.setText(reply);
    }

    private static int dp(float d, int v) { return (int) (v * d + 0.5f); }
}
