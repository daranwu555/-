package com.soxl.calc;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final String SCAN_URL = "https://scanner.tradingview.com/america/scan";
    private static final String SCAN_BODY = "{\"symbols\":{\"tickers\":[\"NASDAQ:SOXX\",\"AMEX:SOXL\",\"AMEX:USD\"]},\"columns\":[\"close\",\"close[1]\",\"current_session\",\"time\",\"premarket_close\",\"postmarket_close\"]}";
    private EditText soxxPrev, soxlPrev, usdPrev, soxxEst;
    private TextView result, soxlChg, resultUsd, usdChg, hint, soxxChg, status, useLive;
    private SharedPreferences prefs;
    private double livePrice = Double.NaN;
    private boolean fetching;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.main);
        prefs = getSharedPreferences("soxl", MODE_PRIVATE);
        soxxPrev = findViewById(R.id.soxxPrev);
        soxlPrev = findViewById(R.id.soxlPrev);
        usdPrev = findViewById(R.id.usdPrev);
        resultUsd = findViewById(R.id.resultUsd);
        usdChg = findViewById(R.id.usdChg);
        hint = findViewById(R.id.hint);
        soxxEst = findViewById(R.id.soxxEst);
        result = findViewById(R.id.result);
        soxlChg = findViewById(R.id.soxlChg);
        soxxChg = findViewById(R.id.soxxChg);
        status = findViewById(R.id.status);
        useLive = findViewById(R.id.useLive);
        if (state == null) {
            soxxPrev.setText(prefs.getString("soxxPrev", ""));
            soxlPrev.setText(prefs.getString("soxlPrev", ""));
            usdPrev.setText(prefs.getString("usdPrev", ""));
            soxxEst.setText(prefs.getString("soxxEst", ""));
        }
        TextWatcher w = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable e) { calc(); }
        };
        soxxPrev.addTextChangedListener(w);
        soxlPrev.addTextChangedListener(w);
        usdPrev.addTextChangedListener(w);
        soxxEst.addTextChangedListener(w);
        findViewById(R.id.refresh).setOnClickListener(v -> fetch());
        findViewById(R.id.m1).setOnClickListener(v -> step(-0.01));
        findViewById(R.id.m05).setOnClickListener(v -> step(-0.005));
        findViewById(R.id.p05).setOnClickListener(v -> step(0.005));
        findViewById(R.id.p1).setOnClickListener(v -> step(0.01));
        useLive.setOnClickListener(v -> { if (livePrice > 0) setEst(livePrice); });
        calc();
        fetch();
    }

    @Override
    protected void onPause() {
        super.onPause();
        prefs.edit().putString("soxxPrev", soxxPrev.getText().toString()).putString("soxlPrev", soxlPrev.getText().toString()).putString("usdPrev", usdPrev.getText().toString()).putString("soxxEst", soxxEst.getText().toString()).apply();
    }

    private static double num(EditText e) {
        try { return Double.parseDouble(e.getText().toString().trim()); } catch (NumberFormatException ex) { return Double.NaN; }
    }

    private static String money(double v) {
        return String.format(Locale.US, "%.2f", v);
    }

    private void setEst(double v) {
        soxxEst.setText(money(v));
        soxxEst.setSelection(soxxEst.getText().length());
    }

    private void step(double pct) {
        double a = num(soxxPrev), c = num(soxxEst);
        if (!(a > 0)) return;
        setEst(Math.max((c > 0 ? c : a) + a * pct, 0.01));
    }

    private void calc() {
        double a = num(soxxPrev), c = num(soxxEst);
        boolean ready = a > 0 && c > 0;
        double r = ready ? c / a - 1 : Double.NaN;
        show(result, soxlChg, num(soxlPrev), 3, r);
        show(resultUsd, usdChg, num(usdPrev), 2, r);
        hint.setVisibility(ready ? View.GONE : View.VISIBLE);
        hint.setText(a > 0 ? "輸入 SOXX 預估價開始推算" : "請先取得或輸入昨日收盤價");
        soxxChg.setText(ready ? String.format(Locale.US, "%+.2f%%", r * 100) : "");
        soxxChg.setTextColor(r > 0 ? getColor(R.color.up) : r < 0 ? getColor(R.color.down) : getColor(R.color.sub));
    }

    private void show(TextView price, TextView chg, double prev, double leverage, double r) {
        if (Double.isNaN(r) || !(prev > 0)) {
            price.setText("—");
            price.setTextColor(0xFFFFFFFF);
            chg.setText("");
            return;
        }
        double lr = Math.max(leverage * r, -1);
        double v = prev * (1 + lr);
        int color = r > 0 ? getColor(R.color.heroUp) : r < 0 ? getColor(R.color.heroDown) : 0xFFFFFFFF;
        price.setText(money(v));
        price.setTextColor(color);
        chg.setText(String.format(Locale.US, "%+.2f%%  %+.2f", lr * 100, v - prev));
        chg.setTextColor(color);
    }

    private void fetch() {
        if (fetching) return;
        fetching = true;
        status.setText("正在從 TradingView 取得報價…");
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(SCAN_URL).openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) { os.write(SCAN_BODY.getBytes("UTF-8")); }
                if (conn.getResponseCode() != 200) throw new Exception("HTTP " + conn.getResponseCode());
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                try (InputStream in = conn.getInputStream()) {
                    byte[] chunk = new byte[4096];
                    int n;
                    while ((n = in.read(chunk)) > 0) buf.write(chunk, 0, n);
                }
                JSONArray data = new JSONObject(buf.toString("UTF-8")).getJSONArray("data");
                JSONArray soxx = null, soxl = null, usd = null;
                for (int i = 0; i < data.length(); i++) {
                    JSONObject row = data.getJSONObject(i);
                    if (row.getString("s").endsWith(":SOXX")) soxx = row.getJSONArray("d");
                    if (row.getString("s").endsWith(":SOXL")) soxl = row.getJSONArray("d");
                    if (row.getString("s").endsWith(":USD")) usd = row.getJSONArray("d");
                }
                if (soxx == null || soxl == null || usd == null) throw new Exception("missing symbol");
                String session = soxx.optString(2, "");
                boolean open = "market".equals(session);
                double baseSoxx = soxx.getDouble(open ? 1 : 0);
                double baseSoxl = soxl.getDouble(open ? 1 : 0);
                double baseUsd = usd.getDouble(open ? 1 : 0);
                double live = open ? soxx.optDouble(0) : "pre_market".equals(session) ? soxx.optDouble(4) : "post_market".equals(session) ? soxx.optDouble(5) : Double.NaN;
                String liveLabel = open ? "現價" : "pre_market".equals(session) ? "盤前價" : "盤後價";
                String when;
                if (open) {
                    when = "前一交易日收盤";
                } else {
                    SimpleDateFormat f = new SimpleDateFormat("M/d", Locale.US);
                    f.setTimeZone(TimeZone.getTimeZone("America/New_York"));
                    when = f.format(new Date(soxx.getLong(3) * 1000)) + " 收盤";
                }
                runOnUiThread(() -> applyQuote(baseSoxx, baseSoxl, baseUsd, live, liveLabel, when));
            } catch (Exception e) {
                runOnUiThread(() -> { fetching = false; status.setText("取得報價失敗，可手動輸入昨收或按更新重試"); });
            }
        }).start();
    }

    private void applyQuote(double baseSoxx, double baseSoxl, double baseUsd, double live, String liveLabel, String when) {
        fetching = false;
        String a = money(baseSoxx), b = money(baseSoxl);
        if (!a.equals(prefs.getString("fetchedSoxx", a))) soxxEst.setText("");
        prefs.edit().putString("fetchedSoxx", a).apply();
        soxxPrev.setText(a);
        soxlPrev.setText(b);
        usdPrev.setText(money(baseUsd));
        status.setText("TradingView · " + when + " · 延遲約 15 分");
        livePrice = live;
        if (live > 0) {
            useLive.setText("帶入 SOXX " + liveLabel + " " + money(live));
            useLive.setVisibility(View.VISIBLE);
        } else {
            useLive.setVisibility(View.GONE);
        }
    }
}
