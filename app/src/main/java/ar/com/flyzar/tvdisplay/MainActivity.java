package ar.com.flyzar.tvdisplay;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceError;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    /* ------------------------------------------------------------------
     * URL por defecto. Si la dejás vacía, la primera vez que abras la app
     * te va a pedir que la cargues. Igual siempre la podés cambiar despues
     * (3 veces ATRAS con el control, o por adb).
     * ------------------------------------------------------------------ */
    private static final String DEFAULT_URL = "https://lvveg.flightpath3d.biz";

    private static final String PREFS = "flyzar_tv";
    private static final String KEY_URL = "url";
    private static final String KEY_REFRESH = "refresh_min";

    private WebView web;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable refreshTask;
    private Runnable retryTask;
    private int backPresses = 0;
    private long lastBack = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0B1220"));
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return false; // todo adentro de la misma WebView
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                if (req != null && !req.isForMainFrame()) return;
                showOffline();
                scheduleRetry();
            }
        });

        hideSystemUi();
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    /* Permite cambiar la URL sin recompilar, desde la compu:
     * adb shell am start -a android.intent.action.VIEW \
     *   -d "https://tu-url" -n ar.com.flyzar.tvdisplay/.MainActivity   */
    private void handleIntent(Intent intent) {
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri data = intent.getData();
            if (data != null) {
                saveUrl(data.toString());
                Toast.makeText(this, "URL actualizada", Toast.LENGTH_SHORT).show();
            }
        }
        String url = getUrl();
        if (url.isEmpty()) {
            openSettings();
        } else {
            web.loadUrl(url);
            scheduleRefresh();
        }
    }

    /* ---------------- preferencias ---------------- */

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private String getUrl() {
        return prefs().getString(KEY_URL, DEFAULT_URL);
    }

    private void saveUrl(String url) {
        prefs().edit().putString(KEY_URL, url).apply();
    }

    private int getRefreshMinutes() {
        return prefs().getInt(KEY_REFRESH, 0);
    }

    /* ---------------- recarga automatica ---------------- */

    private void scheduleRefresh() {
        if (refreshTask != null) handler.removeCallbacks(refreshTask);
        final int min = getRefreshMinutes();
        if (min <= 0) return;
        refreshTask = new Runnable() {
            @Override
            public void run() {
                web.reload();
                handler.postDelayed(this, min * 60_000L);
            }
        };
        handler.postDelayed(refreshTask, min * 60_000L);
    }

    private void scheduleRetry() {
        if (retryTask != null) handler.removeCallbacks(retryTask);
        retryTask = new Runnable() {
            @Override
            public void run() {
                String url = getUrl();
                if (!url.isEmpty()) web.loadUrl(url);
            }
        };
        handler.postDelayed(retryTask, 10_000L);
    }

    private void showOffline() {
        String html = "<html><body style='margin:0;height:100%;display:flex;"
                + "align-items:center;justify-content:center;background:#0B1220;"
                + "color:#8FA3BF;font-family:sans-serif;font-size:28px'>"
                + "Sin conexion &mdash; reintentando&hellip;</body></html>";
        web.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
    }

    /* ---------------- control remoto ---------------- */

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            openSettings();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            long now = System.currentTimeMillis();
            backPresses = (now - lastBack < 2000) ? backPresses + 1 : 1;
            lastBack = now;
            if (backPresses >= 3) {
                backPresses = 0;
                openSettings();
            }
            return true; // kiosco: ATRAS nunca cierra la app
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            web.reload();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void openSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (24 * getResources().getDisplayMetrics().density);
        box.setPadding(p, p, p, p);

        TextView l1 = new TextView(this);
        l1.setText("URL a mostrar");
        final EditText urlField = new EditText(this);
        urlField.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        urlField.setSingleLine(true);
        urlField.setText(getUrl());

        TextView l2 = new TextView(this);
        l2.setText("Recargar cada (minutos, 0 = nunca)");
        final EditText refreshField = new EditText(this);
        refreshField.setInputType(InputType.TYPE_CLASS_NUMBER);
        refreshField.setSingleLine(true);
        refreshField.setText(String.valueOf(getRefreshMinutes()));

        box.addView(l1);
        box.addView(urlField);
        box.addView(l2);
        box.addView(refreshField);

        new AlertDialog.Builder(this)
                .setTitle("Configuracion")
                .setView(box)
                .setPositiveButton("Guardar", (d, w) -> {
                    String url = urlField.getText().toString().trim();
                    int min = 0;
                    try {
                        min = Integer.parseInt(refreshField.getText().toString().trim());
                    } catch (NumberFormatException ignored) {
                    }
                    prefs().edit().putString(KEY_URL, url).putInt(KEY_REFRESH, min).apply();
                    if (!url.isEmpty()) {
                        web.loadUrl(url);
                        scheduleRefresh();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /* ---------------- pantalla completa ---------------- */

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
