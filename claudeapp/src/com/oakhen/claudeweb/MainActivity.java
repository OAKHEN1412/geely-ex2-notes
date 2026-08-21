package com.oakhen.claudeweb;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * ClaudeWeb — WebView ห่อ claude.ai เต็มจอ + ไมค์ (voice) สำหรับจอ Geely EX2
 *
 * UI/UX: splash (ธีมหน้าต่าง) -> overlay โหลดมีโลโก้ Claude + สปินเนอร์ -> หน้า claude.ai
 *        ถ้าเน็ตหลุด/โหลดพลาด -> หน้า "เชื่อมต่อไม่ได้" + ปุ่มลองใหม่ (ไม่ค้างจอขาว/ดำ)
 *
 * ความปลอดภัย (คงจาก bug-hunt): จำกัด origin ไมค์เฉพาะ claude.ai/anthropic, บล็อกนำทางออกนอกโดเมน,
 *        ปิด cleartext/mixed/file-access, pause WebView ตอน background (ไมค์ไม่ค้าง), restoreState มี fallback.
 */
public class MainActivity extends Activity {
    private WebView web;
    private View loadingView, offlineView;
    private ValueCallback<Uri[]> filePathCallback;
    private boolean pageLoadedOnce = false;

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
        setTheme(R.style.AppTheme);   // ทิ้ง splash-window drawable หลัง cold start
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.MODIFY_AUDIO_SETTINGS}, PERM_AUDIO);
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(getColor(R.color.claude_bg));

        web = new WebView(this);
        root.addView(web, matchParent());
        configureWeb();

        loadingView = buildLoadingView();
        root.addView(loadingView, matchParent());

        offlineView = buildOfflineView();
        offlineView.setVisibility(View.GONE);
        root.addView(offlineView, matchParent());

        setContentView(root);
        hideSystemUi();

        // restoreState มี fallback: ถ้า restore ไม่สำเร็จ (คืน null) โหลด HOME กันจอว่างถาวร
        if (b == null || web.restoreState(b) == null) web.loadUrl(HOME);
        else pageLoadedOnce = true;   // มีสถานะเดิม -> ไม่ต้องค้าง overlay โหลด
    }

    private void configureWeb() {
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
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                return blockForeign(req.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, String url) {
                return blockForeign(Uri.parse(url));
            }
            @Override public void onPageFinished(WebView v, String url) {
                pageLoadedOnce = true;
                hideLoading();
            }
            // เน็ตหลุด/โหลด main frame พลาด -> โชว์หน้าออฟไลน์ (ไม่ทับ error ของ subresource)
            @Override public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                if (req.isForMainFrame()) showOffline();
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
            @Override public void onProgressChanged(WebView v, int p) {
                if (p >= 100 && pageLoadedOnce) hideLoading();
            }
            @Override public boolean onShowFileChooser(WebView wv, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = cb;
                try { startActivityForResult(p.createIntent(), FILECHOOSER); }
                catch (Exception e) { filePathCallback = null; return false; }
                return true;
            }
        });
    }

    private boolean blockForeign(Uri uri) {
        try { if (allowedHost(uri.getHost())) return false; } catch (Exception e) {}
        return true;  // บล็อกนำทางออกนอกโดเมน (foreign origin ไม่สืบทอดสิทธิ์ไมค์)
    }

    // ---------- overlay: โหลด ----------
    private View buildLoadingView() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setGravity(Gravity.CENTER);
        v.setBackgroundColor(getColor(R.color.claude_bg));
        v.setClickable(true);   // กินทัชไม่ให้ทะลุไป WebView ระหว่างโหลด

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_logo);
        int sz = dp(96);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sz, sz);
        v.addView(logo, lp);

        ProgressBar pb = new ProgressBar(this);
        pb.getIndeterminateDrawable().setColorFilter(getColor(R.color.claude_clay), PorterDuff.Mode.SRC_IN);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pp.topMargin = dp(24);
        v.addView(pb, pp);

        v.addView(text(getString(R.string.loading), 15, R.color.claude_muted, false, dp(16)));
        return v;
    }

    // ---------- overlay: ออฟไลน์ + ลองใหม่ ----------
    private View buildOfflineView() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setGravity(Gravity.CENTER);
        v.setBackgroundColor(getColor(R.color.claude_bg));
        v.setClickable(true);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_logo);
        logo.setAlpha(0.5f);
        int sz = dp(72);
        v.addView(logo, new LinearLayout.LayoutParams(sz, sz));

        v.addView(text(getString(R.string.offline_title), 19, R.color.claude_cream, true, dp(20)));
        v.addView(text(getString(R.string.offline_msg), 14, R.color.claude_muted, false, dp(6)));

        Button retry = new Button(this);
        retry.setText(getString(R.string.retry));
        retry.setAllCaps(false);
        retry.setTextColor(getColor(R.color.claude_cream));
        retry.setTextSize(16);
        retry.getBackground().setColorFilter(getColor(R.color.claude_clay), PorterDuff.Mode.SRC_IN);
        retry.setOnClickListener(new View.OnClickListener() {
            public void onClick(View x) {
                offlineView.setVisibility(View.GONE);
                showLoading();
                web.loadUrl(HOME);
            }
        });
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(dp(160), dp(48));
        rp.topMargin = dp(24);
        v.addView(retry, rp);
        return v;
    }

    private TextView text(String str, int sizeSp, int colorRes, boolean bold, int topMargin) {
        TextView t = new TextView(this);
        t.setText(str);
        t.setTextColor(getColor(colorRes));
        t.setTextSize(sizeSp);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = topMargin;
        t.setLayoutParams(lp);
        return t;
    }

    private void showLoading() { if (loadingView != null) loadingView.setVisibility(View.VISIBLE); }
    private void hideLoading() { if (loadingView != null) loadingView.setVisibility(View.GONE); }
    private void showOffline() {
        pageLoadedOnce = false;
        hideLoading();
        if (offlineView != null) offlineView.setVisibility(View.VISIBLE);
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
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
