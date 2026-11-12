package com.hotstarlite.mobile;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;
import java.io.ByteArrayInputStream;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final String HOME = "https://www.hotstar.com/in";
    // Conservatively filter third-party advertising hosts only, never media/CDN/DRM endpoints.
    private static final String[] AD_HOSTS = {
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adnxs.com", "taboola.com", "outbrain.com"
    };
    private FrameLayout root;
    private LinearLayout toolbar;
    private android.widget.HorizontalScrollView toolbarScroll;
    private WebView web;
    private View fullView;
    private WebChromeClient.CustomViewCallback fullCallback;
    private int oldOrientation;
    private Button filterButton;
    private volatile boolean filterEnabled;
    private boolean autoSkipEnabled;
    private Button skipButton;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean active;
    private static final String SKIP_JS =
        "(function(){try{" +
        "var nodes=document.querySelectorAll('button,[role=button]');" +
        "for(var i=0;i<nodes.length;i++){var b=nodes[i];" +
        "var label=(b.getAttribute('aria-label')||b.innerText||'').trim();" +
        "if(!/^(skip ad|skip ads|skip advertisement|skip commercials?)$/i.test(label))continue;" +
        "var r=b.getBoundingClientRect(),st=getComputedStyle(b);" +
        "if(!b.disabled&&b.getAttribute('aria-disabled')!=='true'&&r.width>0&&r.height>0" +
        "&&st.display!=='none'&&st.visibility!=='hidden'){b.click();break;}" +
        "}}catch(e){}})();";
    private final Runnable skipCheck = new Runnable() {
        @Override public void run() {
            if (!active || web == null) return;
            if (autoSkipEnabled && isHotstarUrl(web.getUrl())) {
                web.evaluateJavascript(SKIP_JS, null);
            }
            handler.postDelayed(this, 1000);
        }
    };
    private static boolean isHotstarUrl(String url) {
        if (url == null) return false;
        Uri u = Uri.parse(url);
        return "https".equalsIgnoreCase(u.getScheme()) && isHotstarHost(u.getHost());
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        filterEnabled = prefs.getBoolean("ad_filter", false);
        autoSkipEnabled = prefs.getBoolean("auto_skip", false);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setBackgroundColor(Color.rgb(20, 30, 52));
        Button home = button("Home");
        Button back = button("Back");
        Button reload = button("Reload");
        filterButton = button("");
        updateFilterLabel();
        Button browse = button("Browse");
        skipButton = button("");
        updateSkipLabel();
        home.setOnClickListener(v -> web.loadUrl(HOME));
        back.setOnClickListener(v -> goBack());
        reload.setOnClickListener(v -> web.reload());
        browse.setOnClickListener(v -> {
            web.requestFocus();
            Toast.makeText(this, "Tap to browse the website", Toast.LENGTH_SHORT).show();
        });
        filterButton.setOnClickListener(v -> {
            filterEnabled = !filterEnabled;
            prefs.edit().putBoolean("ad_filter", filterEnabled).apply();
            updateFilterLabel();
            web.reload();
        });
        toolbar.addView(home);
        toolbar.addView(back);
        toolbar.addView(reload);
        toolbar.addView(filterButton);
        toolbar.addView(skipButton);
        toolbar.addView(browse);
        skipButton.setOnClickListener(v -> {
            autoSkipEnabled = !autoSkipEnabled;
            prefs.edit().putBoolean("auto_skip", autoSkipEnabled).apply();
            updateSkipLabel();
        });
        toolbarScroll = new android.widget.HorizontalScrollView(this);
        toolbarScroll.setHorizontalScrollBarEnabled(false);
        toolbarScroll.addView(toolbar);
        column.addView(toolbarScroll, new LinearLayout.LayoutParams(-1, dp(56)));
        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (filterEnabled && isAdHost(request.getUrl().getHost())) {
                    return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
                }
                return null;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                String host = uri.getHost();
                if ("https".equalsIgnoreCase(scheme) && isHotstarHost(host)) return false;
                if ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                    catch (ActivityNotFoundException e) { toast("No external browser available"); }
                }
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onShowCustomView(View view, CustomViewCallback callback) {
                if (fullView != null) { callback.onCustomViewHidden(); return; }
                fullView = view;
                fullCallback = callback;
                oldOrientation = getRequestedOrientation();
                toolbarScroll.setVisibility(View.GONE);
                web.setVisibility(View.GONE);
                root.addView(view, new FrameLayout.LayoutParams(-1, -1));
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
            }
            @Override public void onHideCustomView() { exitFullscreen(); }
        });
        column.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(column, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        if (state != null) web.restoreState(state); else web.loadUrl(HOME);

    }
    private static boolean isAdHost(String host) {
        if (host == null) return false;
        String h = host.toLowerCase(Locale.ROOT);
        for (String suffix : AD_HOSTS) if (h.equals(suffix) || h.endsWith("." + suffix)) return true;
        return false;
    }
    private static boolean isHotstarHost(String host) {
        if (host == null) return false;
        String h = host.toLowerCase(Locale.ROOT);
        return h.equals("hotstar.com") || h.endsWith(".hotstar.com") ||
            h.equals("jiostar.com") || h.endsWith(".jiostar.com") ||
            h.equals("jiohotstar.com") || h.endsWith(".jiohotstar.com");
    }
    private Button button(String title) {
        Button b = new Button(this);
        b.setText(title);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(8), 0, dp(8), 0);
        b.setFocusable(true);
        return b;
    }
    private void updateFilterLabel() { filterButton.setText(filterEnabled ? "Safe Filter ON" : "Safe Filter OFF"); }
    private void updateSkipLabel() { skipButton.setText(autoSkipEnabled ? "Auto Skip ON" : "Auto Skip OFF"); }
    private int dp(int size) { return Math.round(size * getResources().getDisplayMetrics().density); }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    private void exitFullscreen() {
        if (fullView == null) return;
        root.removeView(fullView);
        fullView = null;
        toolbarScroll.setVisibility(View.VISIBLE);
        web.setVisibility(View.VISIBLE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setRequestedOrientation(oldOrientation);
        WebChromeClient.CustomViewCallback cb = fullCallback;
        fullCallback = null;
        if (cb != null) cb.onCustomViewHidden();
        web.requestFocus();
    }
    private void goBack() {
        if (fullView != null) exitFullscreen();
        else if (web != null && web.canGoBack()) web.goBack();
        else finish();
    }
    @Override public void onBackPressed() { goBack(); }
    @Override protected void onPause() {
        active = false;
        handler.removeCallbacks(skipCheck);
        if (web != null) web.onPause();
        super.onPause();
    }
    @Override protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
        active = true;
        handler.removeCallbacks(skipCheck);
        handler.postDelayed(skipCheck, 1000);
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        if (web != null) web.saveState(out);
        super.onSaveInstanceState(out);
    }
    @Override protected void onDestroy() {
        active = false;
        handler.removeCallbacks(skipCheck);
        if (fullView != null) exitFullscreen();
        if (web != null) {
            ViewGroup parent = (ViewGroup) web.getParent();
            if (parent != null) parent.removeView(web);
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
