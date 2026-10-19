package com.citydiner.billing;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.print.PrintHelper;
import androidx.webkit.WebViewAssetLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF0C2A23);
        getWindow().setNavigationBarColor(0xFF0C2A23);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .setDomain(HOST)
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (HOST.equals(u.getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception ignored) { }
                return true;
            }
        });

        webView.addJavascriptInterface(new Bridge(), "AndroidBridge");
        webView.loadUrl("https://" + HOST + "/assets/index.html");
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    private void toast(final String m) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, m, Toast.LENGTH_LONG).show());
    }

    private String waPackage() {
        String[] pk = {"com.whatsapp.w4b", "com.whatsapp"};
        for (String p : pk) {
            try {
                getPackageManager().getPackageInfo(p, 0);
                return p;
            } catch (PackageManager.NameNotFoundException ignored) { }
        }
        return null;
    }

    private Intent pendingIntent;
    private String pendingPhone, pendingName;

    private boolean hasContactPerm() {
        return Build.VERSION.SDK_INT < 23
                || (checkSelfPermission(Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED);
    }

    private void prepareAndShare(Intent i, String phone, String name) {
        if (phone == null || phone.length() < 10) { startShare(i, phone); return; }
        if (!hasContactPerm()) {
            pendingIntent = i; pendingPhone = phone; pendingName = name;
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS}, 77);
            return;
        }
        saveContactThenShare(i, phone, name);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == 77 && pendingIntent != null) {
            Intent i = pendingIntent; pendingIntent = null;
            saveContactThenShare(i, pendingPhone, pendingName);
        }
    }

    private Intent pendingImage;

    private void deliver(Intent i, String phone) {
        boolean known = false;
        try { known = waKnows(phone); } catch (Exception ignored) { }
        if (known) { safeShare(i, phone); return; }
        // Backup: open the customer's chat with the text bill, then offer the image
        try {
            String text = i.getStringExtra(Intent.EXTRA_TEXT);
            Uri u = Uri.parse("https://wa.me/" + phone + "?text=" + Uri.encode(text == null ? "" : text));
            Intent c = new Intent(Intent.ACTION_VIEW, u);
            String pkg = waPackage();
            if (pkg != null) c.setPackage(pkg);
            pendingImage = i;
            startActivity(c);
        } catch (Exception e) {
            pendingImage = null;
            safeShare(i, phone);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pendingImage != null) {
            final Intent img = pendingImage;
            pendingImage = null;
            new AlertDialog.Builder(this)
                    .setTitle("Bill ki image")
                    .setMessage("Customer ki chat mein text bill bhej diya? Ab bill ki image bhi bhejein. WhatsApp ki list mein customer ki chat \"Recent chats\" mein sabse upar milegi.")
                    .setPositiveButton("Image bhejein", (d, w) -> {
                        img.removeExtra(Intent.EXTRA_TEXT);
                        Intent s = new Intent(img);
                        String pkg = waPackage();
                        if (pkg != null) s.setPackage(pkg);
                        try { startActivity(s); } catch (Exception e) {
                            startActivity(Intent.createChooser(img, "Share bill"));
                        }
                    })
                    .setNegativeButton("Rehne dein", null)
                    .show();
        }
    }

    private void safeShare(Intent i, String phone) {
        try { startShare(i, phone); } catch (Exception e) {
            Toast.makeText(this, "Could not open WhatsApp", Toast.LENGTH_LONG).show();
        }
    }

    private boolean waKnows(String phone) {
        Cursor c = getContentResolver().query(ContactsContract.Data.CONTENT_URI,
                new String[]{ContactsContract.Data.DATA1},
                ContactsContract.Data.MIMETYPE + " IN (?,?) AND " + ContactsContract.Data.DATA1 + " LIKE ?",
                new String[]{"vnd.android.cursor.item/vnd.com.whatsapp.profile",
                        "vnd.android.cursor.item/vnd.com.whatsapp.w4b.profile", phone + "@%"}, null);
        if (c == null) return false;
        boolean f = c.moveToFirst();
        c.close();
        return f;
    }

    private void saveContactThenShare(final Intent i, final String phone, String name) {
        boolean created = false;
        String err = null;
        if (hasContactPerm()) {
            try { created = ensureContact(phone, name); } catch (Exception e) {
                err = "Contact save error: " + e.getClass().getSimpleName();
            }
        } else {
            err = "Contacts permission allow karein (Settings > Apps > City Diner > Permissions)";
        }
        if (err != null) Toast.makeText(this, err, Toast.LENGTH_LONG).show();
        if (!created) { deliver(i, phone); return; }
        Toast.makeText(this, "Customer ka number save ho gaya. WhatsApp pehchan raha hai, 10 second ruken...", Toast.LENGTH_LONG).show();
        final Handler h = new Handler(Looper.getMainLooper());
        final int[] tries = {0};
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                tries[0]++;
                boolean known = false;
                try { known = waKnows(phone); } catch (Exception ignored) { }
                if (known) {
                    h.postDelayed(() -> safeShare(i, phone), 600);
                } else if (tries[0] >= 14) {
                    deliver(i, phone);
                } else {
                    h.postDelayed(this, 700);
                }
            }
        }, 1200);
    }

    private boolean ensureContact(String phone, String name) throws Exception {
        String num = "+" + phone;
        Uri lookup = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(num));
        Cursor c = getContentResolver().query(lookup, new String[]{ContactsContract.PhoneLookup._ID}, null, null, null);
        if (c != null) {
            boolean exists = c.moveToFirst();
            c.close();
            if (exists) return false;
        }
        String label = ((name == null || name.trim().isEmpty()) ? "Customer" : name.trim()) + " (City Diner)";
        ArrayList<ContentProviderOperation> ops = new ArrayList<>();
        ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null).build());
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, label).build());
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, num)
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE).build());
        getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
        return true;
    }

    private void startShare(Intent i, String phone) {
        String pkg = waPackage();
        if (phone != null && phone.length() >= 10 && pkg != null) {
            try {
                Intent w = new Intent(i);
                w.setPackage(pkg);
                w.putExtra("jid", phone + "@s.whatsapp.net");
                startActivity(w);
                return;
            } catch (Exception ignored) { }
        }
        if (pkg != null) {
            try {
                Intent w = new Intent(i);
                w.setPackage(pkg);
                startActivity(w);
                return;
            } catch (Exception ignored) { }
        }
        startActivity(Intent.createChooser(i, "Share bill"));
    }

    private class Bridge {
        @JavascriptInterface
        public void save(String name, String mime, String b64) {
            try {
                byte[] data = Base64.decode(b64, Base64.DEFAULT);
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                    v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                    v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    OutputStream o = getContentResolver().openOutputStream(uri);
                    o.write(data);
                    o.close();
                    toast("Saved in Downloads: " + name);
                } else {
                    File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                    File f = new File(dir, name);
                    FileOutputStream o = new FileOutputStream(f);
                    o.write(data);
                    o.close();
                    toast("Saved: " + f.getAbsolutePath());
                }
            } catch (Exception e) {
                toast("Could not save the file");
            }
        }

        @JavascriptInterface
        public void chat(final String phone, final String text) {
            runOnUiThread(() -> {
                try {
                    Uri u = Uri.parse("https://wa.me/" + phone + "?text=" + Uri.encode(text));
                    Intent i = new Intent(Intent.ACTION_VIEW, u);
                    String pkg = waPackage();
                    if (pkg != null) i.setPackage(pkg);
                    startActivity(i);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Could not open WhatsApp", Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface
        public void share(String name, String mime, String b64, final String text, final String phone, final String cust) {
            try {
                byte[] data = Base64.decode(b64, Base64.DEFAULT);
                File dir = new File(getCacheDir(), "shared");
                dir.mkdirs();
                File f = new File(dir, name);
                FileOutputStream o = new FileOutputStream(f);
                o.write(data);
                o.close();
                final Uri uri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".fileprovider", f);
                final String type = mime;
                runOnUiThread(() -> {
                    try {
                        Intent i = new Intent(Intent.ACTION_SEND);
                        i.setType(type);
                        i.putExtra(Intent.EXTRA_STREAM, uri);
                        i.putExtra(Intent.EXTRA_TEXT, text);
                        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        prepareAndShare(i, phone, cust);
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "Could not open WhatsApp", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                toast("Could not share the bill");
            }
        }

        @JavascriptInterface
        public void print(final String b64) {
            runOnUiThread(() -> {
                try {
                    byte[] data = Base64.decode(b64, Base64.DEFAULT);
                    Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
                    if (bmp == null) {
                        Toast.makeText(MainActivity.this, "Could not start printing", Toast.LENGTH_LONG).show();
                        return;
                    }
                    PrintHelper ph = new PrintHelper(MainActivity.this);
                    ph.setScaleMode(PrintHelper.SCALE_MODE_FIT);
                    ph.setOrientation(PrintHelper.ORIENTATION_PORTRAIT);
                    ph.setColorMode(PrintHelper.COLOR_MODE_COLOR);
                    ph.printBitmap("CityDiner bill", bmp);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Could not start printing", Toast.LENGTH_LONG).show();
                }
            });
        }
    }
}
