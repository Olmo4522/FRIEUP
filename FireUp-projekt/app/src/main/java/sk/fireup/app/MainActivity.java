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

        @JavascriptInterface
        public void setReminders(String json) {
            Reminders.schedule(getApplicationContext(), json);
        }

        @JavascriptInterface
        public boolean notifAllowed() {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) return false;
            android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            return nm == null || nm.areNotificationsEnabled();
        }

        @JavascriptInterface
        public void askNotif() {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 77);
                } else if (!notifAllowed()) {
                    try {
                        Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                        i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                        startActivity(i);
                    } catch (Exception e) { }
                }
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == 77) js("window.__notifRefresh&&window.__notifRefresh()");
    }

    /* ---------- pripomienky k úlohám ---------- */
    public static class Reminders {
        static final String PREFS = "fireup_rem";
        static final String CH = "fireup_tasks";

        static void channel(Context c) {
            android.app.NotificationManager nm = (android.app.NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(CH) == null) {
                android.app.NotificationChannel ch = new android.app.NotificationChannel(CH, "Pripomienky úloh", android.app.NotificationManager.IMPORTANCE_HIGH);
                ch.setDescription("Upozornenia na úlohy vo FireUp");
                nm.createNotificationChannel(ch);
            }
        }

        static android.app.PendingIntent pi(Context c, int id, String title, String body) {
            Intent i = new Intent(c, ReminderReceiver.class);
            i.setAction("sk.fireup.app.REMIND." + id);
            i.putExtra("id", id);
            i.putExtra("t", title);
            i.putExtra("b", body);
            return android.app.PendingIntent.getBroadcast(c, 9000 + id, i, android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
        }

        static synchronized void schedule(Context c, String json) {
            android.app.AlarmManager am = (android.app.AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            android.content.SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            try {
                org.json.JSONArray old = new org.json.JSONArray(sp.getString("plan", "[]"));
                for (int k = 0; k < old.length(); k++) {
                    org.json.JSONObject o = old.getJSONObject(k);
                    am.cancel(pi(c, o.getInt("id"), o.optString("t"), o.optString("b")));
                }
            } catch (Exception e) { }
            org.json.JSONArray plan;
            try { plan = new org.json.JSONArray(json == null ? "[]" : json); } catch (Exception e) { plan = new org.json.JSONArray(); }
            long now = System.currentTimeMillis();
            boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
            for (int k = 0; k < plan.length(); k++) {
                try {
                    org.json.JSONObject o = plan.getJSONObject(k);
                    long at = o.getLong("at");
                    if (at <= now) continue;
                    android.app.PendingIntent p = pi(c, o.getInt("id"), o.optString("t"), o.optString("b"));
                    if (exact) am.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, at, p);
                    else am.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, at, p);
                } catch (Exception e) { }
            }
            sp.edit().putString("plan", plan.toString()).apply();
        }
    }

    public static class ReminderReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            try {
                Reminders.channel(c);
                Intent open = new Intent(c, MainActivity.class);
                open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                android.app.PendingIntent tap = android.app.PendingIntent.getActivity(c, 1, open, android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
                String body = i.getStringExtra("b");
                android.app.Notification n = new android.app.Notification.Builder(c, Reminders.CH)
                        .setSmallIcon(R.drawable.ic_flame)
                        .setContentTitle(i.getStringExtra("t"))
                        .setContentText(body)
                        .setStyle(new android.app.Notification.BigTextStyle().bigText(body))
                        .setColor(Color.parseColor("#3ee05a"))
                        .setAutoCancel(true)
                        .setContentIntent(tap)
                        .build();
                android.app.NotificationManager nm = (android.app.NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) nm.notify(i.getIntExtra("id", 0), n);
            } catch (Exception e) { }
        }
    }

    public static class BootReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            if (Intent.ACTION_BOOT_COMPLETED.equals(a) || "android.intent.action.MY_PACKAGE_REPLACED".equals(a)) {
                Reminders.schedule(c, c.getSharedPreferences(Reminders.PREFS, Context.MODE_PRIVATE).getString("plan", "[]"));
            }
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
