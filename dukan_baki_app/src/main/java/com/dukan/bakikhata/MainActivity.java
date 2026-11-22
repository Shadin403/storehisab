package com.dukan.bakikhata;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class MainActivity extends Activity {

    private WebView mWebView;
    private static final String BACKUP_FILE_NAME = "dukan_baki_persistent_data.json";
    public static final int FILECHOOSER_RESULTCODE = 1001;
    public ValueCallback<Uri[]> mUploadMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mWebView = new WebView(this);
        setContentView(mWebView);

        WebSettings settings = mWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(false);
        settings.setDefaultTextEncodingName("utf-8");

        // Add JavaScript Interface for persistent device file storage & native PDF printing
        mWebView.addJavascriptInterface(new AndroidBridge(this, mWebView), "AndroidBridge");

        mWebView.setWebChromeClient(new AppWebChromeClient(this));
        mWebView.setWebViewClient(new AppWebViewClient(this));

        mWebView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    public void onBackPressed() {
        if (mWebView != null && mWebView.canGoBack()) {
            mWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILECHOOSER_RESULTCODE) {
            if (mUploadMessage == null) return;
            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK) {
                try {
                    results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (results == null && data != null) {
                    if (data.getData() != null) {
                        results = new Uri[]{data.getData()};
                    } else if (data.getDataString() != null) {
                        results = new Uri[]{Uri.parse(data.getDataString())};
                    } else if (data.getClipData() != null) {
                        int count = data.getClipData().getItemCount();
                        results = new Uri[count];
                        for (int i = 0; i < count; i++) {
                            results[i] = data.getClipData().getItemAt(i).getUri();
                        }
                    }
                }
            }
            mUploadMessage.onReceiveValue(results);
            mUploadMessage = null;
        }
    }

    // Bridge for direct hardware file persistence & native PDF printing
    public static class AndroidBridge {
        private final Activity activity;
        private final WebView webView;

        public AndroidBridge(Activity act, WebView wv) {
            this.activity = act;
            this.webView = wv;
        }

        @JavascriptInterface
        public void saveToFile(String jsonData) {
            try {
                // 1. Save to internal persistent app storage (survives webview clear cache)
                FileOutputStream fos = activity.openFileOutput(BACKUP_FILE_NAME, Context.MODE_PRIVATE);
                OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF-8");
                osw.write(jsonData);
                osw.flush();
                osw.close();
                fos.close();

                // 2. Also save to Downloads / external public folder if accessible
                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (downloadsDir != null && downloadsDir.exists()) {
                    File extFile = new File(downloadsDir, "DukanBaki_AutoBackup.json");
                    FileOutputStream extFos = new FileOutputStream(extFile);
                    OutputStreamWriter extOsw = new OutputStreamWriter(extFos, "UTF-8");
                    extOsw.write(jsonData);
                    extOsw.flush();
                    extOsw.close();
                    extFos.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @JavascriptInterface
        public String readFromFile() {
            try {
                File file = activity.getFileStreamPath(BACKUP_FILE_NAME);
                if (!file.exists()) {
                    File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    file = new File(downloadsDir, "DukanBaki_AutoBackup.json");
                }
                if (!file.exists()) {
                    return "";
                }

                FileInputStream fis = new FileInputStream(file);
                BufferedReader reader = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                reader.close();
                fis.close();
                return sb.toString();
            } catch (Exception e) {
                e.printStackTrace();
                return "";
            }
        }

        // Native PDF Export / Print dialog in Android
        @JavascriptInterface
        public void printDocument(final String jobName) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        PrintManager printManager = (PrintManager) activity.getSystemService(Context.PRINT_SERVICE);
                        if (printManager != null && webView != null) {
                            String name = (jobName != null && jobName.trim().length() > 0) ? jobName : "Dukan_Baki_Report";
                            PrintDocumentAdapter printAdapter = webView.createPrintDocumentAdapter(name);
                            printManager.print(name, printAdapter, new PrintAttributes.Builder().build());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(activity, "PDF প্রিন্ট উইন্ডো খুলতে সমস্যা হয়েছে", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        @JavascriptInterface
        public void shareDataText(String title, String text) {
            try {
                Intent sendIntent = new Intent();
                sendIntent.setAction(Intent.ACTION_SEND);
                sendIntent.putExtra(Intent.EXTRA_SUBJECT, title);
                sendIntent.putExtra(Intent.EXTRA_TEXT, text);
                sendIntent.setType("text/plain");

                Intent shareIntent = Intent.createChooser(sendIntent, title);
                activity.startActivity(shareIntent);
            } catch (Exception e) {
                Toast.makeText(activity, "শেয়ার করা সম্ভব হয়নি", Toast.LENGTH_SHORT).show();
            }
        }

        @JavascriptInterface
        public void showToast(final String message) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    static class AppWebChromeClient extends WebChromeClient {
        private final MainActivity activity;

        public AppWebChromeClient(MainActivity act) {
            this.activity = act;
        }

        @Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
            if (activity.mUploadMessage != null) {
                activity.mUploadMessage.onReceiveValue(null);
                activity.mUploadMessage = null;
            }
            activity.mUploadMessage = filePathCallback;

            Intent intent = null;
            try {
                intent = fileChooserParams.createIntent();
            } catch (Exception e) {
                intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
            }

            try {
                activity.startActivityForResult(intent, FILECHOOSER_RESULTCODE);
            } catch (Exception e) {
                try {
                    Intent chooser = Intent.createChooser(intent, "ছবি সিলেক্ট করুন");
                    activity.startActivityForResult(chooser, FILECHOOSER_RESULTCODE);
                } catch (Exception e2) {
                    if (activity.mUploadMessage != null) {
                        activity.mUploadMessage.onReceiveValue(null);
                        activity.mUploadMessage = null;
                    }
                    return false;
                }
            }
            return true;
        }
    }

    static class AppWebViewClient extends WebViewClient {
        private final Activity activity;

        public AppWebViewClient(Activity act) {
            this.activity = act;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (url == null) return false;

            if (url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("whatsapp:") || url.contains("wa.me")) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    activity.startActivity(intent);
                    return true;
                } catch (Exception e) {
                    Toast.makeText(activity, "অ্যাপটি চালু করা যায়নি", Toast.LENGTH_SHORT).show();
                    return true;
                }
            }

            if (url.startsWith("file:///android_asset/")) {
                return false;
            }

            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                activity.startActivity(intent);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }
}
