package ir.noorelectric.pelleh;

import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.content.Context;
import android.widget.Toast;
import android.app.Activity;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import android.util.Base64;
import android.content.ContentValues;
import android.os.Build;

public class MainActivity extends Activity {
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private static final int FILE_CHOOSER = 4102;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(android.graphics.Color.rgb(221,227,234));
        getWindow().setNavigationBarColor(android.graphics.Color.rgb(221,227,234));
        webView = new WebView(this);
        setContentView(webView);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setJavaScriptCanOpenWindowsAutomatically(false);
        ws.setSupportMultipleWindows(false);
        ws.setLoadsImagesAutomatically(true);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);
        webView.addJavascriptInterface(new PellehBridge(), "PellehAndroid");
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("intent".equalsIgnoreCase(uri.getScheme())) {
                    try { startActivity(Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)); }
                    catch (Exception e) { toast("برنامهٔ مقصد نصب نیست یا باز نشد."); }
                    return true;
                }
                if ("https".equalsIgnoreCase(uri.getScheme()) || "mailto".equalsIgnoreCase(uri.getScheme()) || "tel".equalsIgnoreCase(uri.getScheme())) {
                    if (!"https".equalsIgnoreCase(uri.getScheme())) {
                        try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) {}
                        return true;
                    }
                }
                return false;
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent intent = params.createIntent();
                try { startActivityForResult(intent, FILE_CHOOSER); }
                catch (ActivityNotFoundException e) { fileCallback = null; callback.onReceiveValue(null); toast("انتخاب فایل در دسترس نیست."); }
                return true;
            }
        });
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            try {
                DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                req.setMimeType(mimeType);
                req.addRequestHeader("User-Agent", userAgent);
                req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "pelleh-backup.json");
                ((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(req);
                toast("دانلود آغاز شد.");
            } catch (Exception e) { toast("دانلود انجام نشد."); }
        });
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER && fileCallback != null) {
            Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            fileCallback.onReceiveValue(result);
            fileCallback = null;
        }
    }

    private void toast(String message) { runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show()); }

    public class PellehBridge {
        @JavascriptInterface public void saveBase64(String filename, String base64) {
            try {
                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                String safe = filename == null ? "pelleh.png" : filename.replaceAll("[^A-Za-z0-9._-]", "_");
                if (!safe.toLowerCase().endsWith(".png")) safe += ".png";
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, safe);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");
                if (Build.VERSION.SDK_INT >= 29) values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Pelleh");
                Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new Exception("media uri");
                try (OutputStream out = getContentResolver().openOutputStream(uri)) { out.write(bytes); }
                toast("تصویر فاکتور در گالری ذخیره شد.");
            } catch (Exception e) { toast("ذخیرهٔ تصویر ناموفق بود."); }
        }
        @JavascriptInterface public void saveTextFile(String filename, String content, String mimeType) {
            try {
                String safe = filename == null ? "pelleh-backup.json" : filename.replaceAll("[^A-Za-z0-9._-]", "_");
                byte[] bytes = (content == null ? "" : content).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, safe);
                    values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType == null ? "application/octet-stream" : mimeType);
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Pelleh");
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) throw new Exception("download uri");
                    try (OutputStream out = getContentResolver().openOutputStream(uri)) { out.write(bytes); }
                } else {
                    File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                    if (dir == null) dir = getFilesDir();
                    try (FileOutputStream out = new FileOutputStream(new File(dir, safe))) { out.write(bytes); }
                }
                toast("فایل در پوشهٔ Downloads/Pelleh ذخیره شد.");
            } catch (Exception e) { toast("ذخیرهٔ فایل ناموفق بود."); }
        }
        @JavascriptInterface public void shareBase64(String filename, String base64, String text) {
            try {
                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                File dir = new File(getCacheDir(), "shared"); dir.mkdirs();
                File file = new File(dir, "pelleh-invoice.png");
                try (FileOutputStream out = new FileOutputStream(file)) { out.write(bytes); }
                Uri uri = FileProvider.getUriForFile(MainActivity.this, "ir.noorelectric.pelleh.fileprovider", file);
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("image/png"); send.putExtra(Intent.EXTRA_STREAM, uri);
                send.putExtra(Intent.EXTRA_TEXT, text == null ? "" : text);
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                runOnUiThread(() -> startActivity(Intent.createChooser(send, "ارسال فاکتور با")));
            } catch (Exception e) { toast("اشتراک‌گذاری ناموفق بود."); }
        }
        @JavascriptInterface public void printPage() { runOnUiThread(() -> printWebPage()); }
    }

    private void printWebPage() {
        try {
            PrintManager pm = (PrintManager)getSystemService(Context.PRINT_SERVICE);
            pm.print("پله - فاکتور", webView.createPrintDocumentAdapter("پله"), new PrintAttributes.Builder().build());
        } catch (Exception e) { toast("چاپ در دسترس نیست."); }
    }

    @Override public void onBackPressed() {
        if (webView == null) { super.onBackPressed(); return; }
        webView.evaluateJavascript("(function(){var ids=['ov','pv','fin','cf','bye','lock','ar','cu','acc','stk','capv','set','mmenu','mode'];for(var i=0;i<ids.length;i++){var e=document.getElementById(ids[i]);if(e&&!e.hidden&&getComputedStyle(e).display!=='none'){e.hidden=true;return 'closed';}}return 'exit';})()", value -> {
            if ("\"exit\"".equals(value)) finish();
        });
    }
    @Override protected void onDestroy() { if (webView != null) webView.destroy(); super.onDestroy(); }
}
