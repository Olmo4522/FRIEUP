package sk.fireup.app;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.File;

public class MainActivity extends Activity {
    private WebView web;
    private long downloadId = -1;
    private BroadcastReceiver receiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window w = getWindow();
        w.setStatusBarColor(Color.parseColor("#0b0d0b"));
        w.setNavigationBarColor(Color.parseColor("#0b0d0b"));

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0b0d0b"));
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setTextZoom(100);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                String sc = u.getScheme();
                if ("http".equals(sc) || "https".equals(sc)) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception e) { }
                    return true;
                }
                return false;
            }
        });
        web.addJavascriptInterface(new Bridge(), "FireUpNative");
        setContentView(web);

        int version = 0;
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (Exception e) { }
        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl("file:///android_asset/www/index.html?v=" + version);
    }

    private void js(final String code) {
        runOnUiThread(() -> web.evaluateJavascript(code, null));
    }

    private void status(String state, String msg) {
        js("window.__updateStatus&&window.__updateStatus('" + state + "','" + msg.replace("'", "\\'") + "')");
    }

    private void startDownload(String url) {
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            status("error", "Povoľ FireUp inštalovať aplikácie a potom ťukni znova na Aktualizovať.");
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
            } catch (Exception e) { }
            return;
        }
        try {
            File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            File old = new File(dir, "FireUp-update.apk");
            if (old.exists()) old.delete();
            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
            r.setTitle("FireUp aktualizácia");
            r.setMimeType("application/vnd.android.package-archive");
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
            r.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, "FireUp-update.apk");
            if (receiver == null) {
                receiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context c, Intent i) {
                        long id = i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                        if (id != downloadId) return;
                        install();
                    }
                };
                IntentFilter f = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
                if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_EXPORTED);
                else registerReceiver(receiver, f);
            }
            downloadId = dm.enqueue(r);
            status("downloading", "Sťahujem novú verziu…");
        } catch (Exception e) {
            status("error", "Sťahovanie sa nepodarilo. Skontroluj internet.");
        }
    }

    private void install() {
        try {
            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            Uri uri = dm.getUriForDownloadedFile(downloadId);
            if (uri == null) { status("error", "Sťahovanie sa nepodarilo. Skús znova."); return; }
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            status("done", "Potvrď inštaláciu v okne, ktoré sa otvorilo.");
        } catch (Exception e) {
            status("error", "Inštaláciu sa nepodarilo otvoriť.");
        }
    }

    class Bridge {
        @JavascriptInterface
        public void update(String url) {
            if (url == null || !url.startsWith("https://github.com/Olmo4522/FRIEUP/releases/")) return;
            runOnUiThread(() -> startDownload(url));
        }
    }

    @Override
    protected void onDestroy() {
        if (receiver != null) { try { unregisterReceiver(receiver); } catch (Exception e) { } }
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("(window.__back&&window.__back())?'1':'0'", v -> {
            if (v == null || !v.contains("1")) MainActivity.super.onBackPressed();
        });
    }
}
