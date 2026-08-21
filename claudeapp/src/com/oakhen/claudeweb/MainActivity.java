package com.oakhen.claudeweb;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * ClaudeWeb — WebView ห่อ claude.ai เต็มจอ + ไมค์ (voice) สำหรับจอ Geely EX2
 * แก้ตาม bug-hunt: จำกัด origin ไมค์เฉพาะ claude.ai/anthropic, บล็อกนำทางออกนอกโดเมน (กันไมค์รั่ว),
 * ปิด cleartext/mixed/file-access, pause WebView ตอน background (ไมค์ไม่ค้าง), restoreState มี fallback.
 */
public class MainActivity extends Activity {
    private WebView web;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILECHOOSER = 1001;
    private static final int PERM_AUDIO = 1002;
    private static final String HOME = "https://claude.ai";
    // UA แบบ Chrome จริง — ไม่มี token "; wv" ที่ทำให้เว็บมองว่าเป็น WebView
    private static final String UA = "Mozilla/5.0 (Linux; Android 9; EX2) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36";

    // origin ที่อนุญาต (ไมค์ + นำทางในแอป) — นอกนี้ถือว่าไม่ปลอดภัย
    private static boolean allowedHost(String host) {
        if (host == null) return false;
        host = host.toLowerCase();
        return host.equals("claude.ai") || host.endsWith(".claude.ai")
                || host.equals("anthropic.com") || host.endsWith(".anthropic.com");
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.MODIFY_AUDIO_SETTINGS}, PERM_AUDIO);
        }

        web = new WebView(this);
        setContentView(web);
        hideSystemUi();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString(UA);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);  // ไม่โหลด http subresource (กัน MITM inject)
        s.setAllowFileAccess(false);                                   // remote web app ไม่ต้องแตะไฟล์เครื่อง
        s.setAllowContentAccess(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, String url) {
                try { if (allowedHost(Uri.parse(url).getHost())) return false; } catch (Exception e) {}
                return true;  // บล็อกนำทางออกนอกโดเมน (foreign origin ไม่สืบทอดสิทธิ์ไมค์)
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            // 🎤 grant ไมค์เฉพาะ origin claude.ai/anthropic + เฉพาะ resource เสียง + ต่อเมื่อแอปมีสิทธิ์ RECORD_AUDIO จริง
            @Override public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        String host = null;
                        try { host = Uri.parse(request.getOrigin().toString()).getHost(); } catch (Exception e) {}
                        boolean micOk = checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                                == PackageManager.PERMISSION_GRANTED;
                        if (allowedHost(host) && micOk) {
                            request.grant(new String[]{ PermissionRequest.RESOURCE_AUDIO_CAPTURE });
                        } else {
                            request.deny();
                        }
                    }
                });
            }
            @Override public boolean onShowFileChooser(WebView wv, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = cb;
                try { startActivityForResult(p.createIntent(), FILECHOOSER); }
                catch (Exception e) { filePathCallback = null; return false; }
                return true;
            }
        });

        // restoreState มี fallback: ถ้า restore ไม่สำเร็จ (คืน null) โหลด HOME กันจอว่างถาวร
        if (b == null || web.restoreState(b) == null) web.loadUrl(HOME);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        if (req == FILECHOOSER) {
            Uri[] result = null;
            if (res == RESULT_OK && data != null) {
                if (data.getDataString() != null) {
                    result = new Uri[]{ Uri.parse(data.getDataString()) };
                } else if (data.getClipData() != null) {
                    int n = data.getClipData().getItemCount();
                    result = new Uri[n];
                    for (int i = 0; i < n; i++) result[i] = data.getClipData().getItemAt(i).getUri();
                }
            }
            if (filePathCallback != null) { filePathCallback.onReceiveValue(result); filePathCallback = null; }
        } else super.onActivityResult(req, res, data);
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_BACK && web.canGoBack()) { web.goBack(); return true; }
        return super.onKeyDown(code, e);
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }
    @Override public void onWindowFocusChanged(boolean f) { super.onWindowFocusChanged(f); if (f) hideSystemUi(); }

    @Override protected void onSaveInstanceState(Bundle o) { super.onSaveInstanceState(o); web.saveState(o); }

    // pause WebView ตอน background -> ไมค์/JS/timers หยุด (ไม่ค้าง hot, ไม่กิน CPU)
    @Override protected void onResume() { super.onResume(); if (web != null) { web.onResume(); web.resumeTimers(); } }
    @Override protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
        if (web != null) { web.onPause(); web.pauseTimers(); }
    }
    @Override protected void onDestroy() { if (web != null) web.destroy(); super.onDestroy(); }
}
