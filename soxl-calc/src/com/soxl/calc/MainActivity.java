package com.soxl.calc;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
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
    private static final String SCAN_URL = "https://scanner.tradingview.com/global/scan";
    private static final String SCAN_BODY = "{\"symbols\":{\"tickers\":[\"NASDAQ:SOXX\",\"AMEX:SOXL\",\"AMEX:USD\",\"DJ:DJUSSC\",\"FX_IDC:USDTWD\"]},\"columns\":[\"close\",\"close[1]\",\"current_session\",\"time\",\"premarket_close\",\"postmarket_close\"]}";
    private static final String[] KEYS = {"soxxPrev", "soxlPrev", "djPrev", "usdPrev", "soxxEst", "djEst", "amount"};
    private EditText soxxPrev, soxlPrev, djPrev, usdPrev, soxxEst, djEst, amount, rate;
    private EditText[] fields;
    private TextView result, soxlChg, resultUsd, usdChg, resultUsdSoxx, usdSoxxChg, hint, soxxChg, djChg, status, soxxLive, djLive, soxlShares, usdSoxxShares, usdShares, curTwd, curUsd, rateInfo;
    private View rateRow;
    private boolean twd, rateManual, settingRate;
    private SharedPreferences prefs;
    private boolean fetching;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.main);
        prefs = getSharedPreferences("soxl", MODE_PRIVATE);
        soxxPrev = findViewById(R.id.soxxPrev);
        soxlPrev = findViewById(R.id.soxlPrev);
        djPrev = findViewById(R.id.djPrev);
        usdPrev = findViewById(R.id.usdPrev);
        soxxEst = findViewById(R.id.soxxEst);
        djEst = findViewById(R.id.djEst);
        amount = findViewById(R.id.amount);
        rate = findViewById(R.id.rate);
        rateRow = findViewById(R.id.rateRow);
        rateInfo = findViewById(R.id.rateInfo);
        curTwd = findViewById(R.id.curTwd);
        curUsd = findViewById(R.id.curUsd);
        soxlShares = findViewById(R.id.soxlShares);
        usdSoxxShares = findViewById(R.id.usdSoxxShares);
        usdShares = findViewById(R.id.usdShares);
        fields = new EditText[] {soxxPrev, soxlPrev, djPrev, usdPrev, soxxEst, djEst, amount};
        result = findViewById(R.id.result);
        soxlChg = findViewById(R.id.soxlChg);
        resultUsd = findViewById(R.id.resultUsd);
        usdChg = findViewById(R.id.usdChg);
        resultUsdSoxx = findViewById(R.id.resultUsdSoxx);
        usdSoxxChg = findViewById(R.id.usdSoxxChg);
        hint = findViewById(R.id.hint);
        soxxChg = findViewById(R.id.soxxChg);
        djChg = findViewById(R.id.djChg);
        status = findViewById(R.id.status);
        soxxLive = findViewById(R.id.soxxLive);
        djLive = findViewById(R.id.djLive);
        TextWatcher w = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable e) { calc(); }
        };
        for (int i = 0; i < fields.length; i++) {
            if (state == null) fields[i].setText(prefs.getString(KEYS[i], ""));
            fields[i].addTextChangedListener(w);
        }
        twd = state == null ? prefs.getBoolean("twd", true) : state.getBoolean("twd", true);
        rateManual = prefs.getBoolean("rateManual", false);
        if (state == null) setRate(prefs.getString("rate", ""));
        rate.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable e) {
                if (!settingRate && rate.hasFocus()) { rateManual = true; rateInfo.setText("自訂 · 1 美金兌台幣"); }
                calc();
            }
        });
        if (rateManual) rateInfo.setText("自訂 · 1 美金兌台幣");
        curTwd.setOnClickListener(v -> setCurrency(true));
        curUsd.setOnClickListener(v -> setCurrency(false));
        setCurrency(twd);
        findViewById(R.id.refresh).setOnClickListener(v -> { rateManual = false; fetch(); });
        findViewById(R.id.soxxMinus).setOnClickListener(v -> step(soxxPrev, soxxEst, -0.005));
        findViewById(R.id.soxxPlus).setOnClickListener(v -> step(soxxPrev, soxxEst, 0.005));
        findViewById(R.id.djMinus).setOnClickListener(v -> step(djPrev, djEst, -0.005));
        findViewById(R.id.djPlus).setOnClickListener(v -> step(djPrev, djEst, 0.005));
        calc();
        fetch();
    }

    @Override
    protected void onPause() {
        super.onPause();
        SharedPreferences.Editor e = prefs.edit();
        for (int i = 0; i < fields.length; i++) e.putString(KEYS[i], fields[i].getText().toString());
        e.putString("rate", rate.getText().toString()).putBoolean("twd", twd).putBoolean("rateManual", rateManual);
        e.apply();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean("twd", twd);
    }

    private void setRate(String v) {
        settingRate = true;
        rate.setText(v);
        settingRate = false;
    }

    private void setCurrency(boolean useTwd) {
        twd = useTwd;
        curTwd.setBackgroundResource(twd ? R.drawable.seg_on : 0);
        curUsd.setBackgroundResource(twd ? 0 : R.drawable.seg_on);
        rateRow.setVisibility(twd ? View.VISIBLE : View.GONE);
        calc();
    }

    /** 可用的美金預算；金額或匯率未填時為 NaN。 */
    private double budgetUsd() {
        double a = num(amount);
        if (!(a > 0)) return Double.NaN;
        if (!twd) return a;
        double r = num(rate);
        return r > 0 ? a / r : Double.NaN;
    }

    private static double num(EditText e) {
        try { return Double.parseDouble(e.getText().toString().trim()); } catch (NumberFormatException ex) { return Double.NaN; }
    }

    private static String money(double v) {
        return String.format(Locale.US, "%.2f", v);
    }

    private static void setEst(EditText est, double v) {
        est.setText(money(v));
        est.setSelection(est.getText().length());
    }

    private void step(EditText prev, EditText est, double pct) {
        double a = num(prev), c = num(est);
        if (!(a > 0)) return;
        setEst(est, Math.max((c > 0 ? c : a) + a * pct, 0.01));
    }

    private void calc() {
        double rSoxx = change(soxxPrev, soxxEst, soxxChg);
        double rDj = change(djPrev, djEst, djChg);
        double budget = budgetUsd();
        show(result, soxlChg, soxlShares, num(soxlPrev), 3, rSoxx, budget);
        show(resultUsdSoxx, usdSoxxChg, usdSoxxShares, num(usdPrev), 2, rSoxx, budget);
        show(resultUsd, usdChg, usdShares, num(usdPrev), 2, rDj, budget);
        hint.setVisibility(Double.isNaN(rSoxx) && Double.isNaN(rDj) ? View.VISIBLE : View.GONE);
    }

    private double change(EditText prev, EditText est, TextView label) {
        double a = num(prev), c = num(est);
        double r = a > 0 && c > 0 ? c / a - 1 : Double.NaN;
        label.setText(Double.isNaN(r) ? "預估價" : String.format(Locale.US, "%+.2f%%", r * 100));
        label.setTextColor(r > 0 ? getColor(R.color.up) : r < 0 ? getColor(R.color.down) : getColor(R.color.sub));
        return r;
    }

    private void show(TextView price, TextView pill, TextView shares, double prev, double leverage, double r, double budget) {
        if (Double.isNaN(r) || !(prev > 0)) {
            price.setText("—");
            pill.setVisibility(View.INVISIBLE);
            shares.setText("");
            return;
        }
        double lr = Math.max(leverage * r, -1);
        double v = prev * (1 + lr);
        price.setText(money(v));
        pill.setText(String.format(Locale.US, "%+.2f%%  %+.2f", lr * 100, v - prev));
        pill.setBackgroundTintList(ColorStateList.valueOf(getColor(r > 0 ? R.color.up : r < 0 ? R.color.down : R.color.flat)));
        pill.setVisibility(View.VISIBLE);
        shares.setText(budget > 0 && v > 0 ? String.format(Locale.US, "可買 %,d 股", (long) Math.floor(budget / v)) : "");
    }

    /** 開盤中取前一根日線收盤，其餘時段取最近一根日線收盤。 */
    private static double base(JSONArray d) throws Exception {
        return d.getDouble("market".equals(d.optString(2)) ? 1 : 0);
    }

    /** 目前可用的即時價：盤中現價、盤前價或盤後價；沒有則為 NaN。 */
    private static double live(JSONArray d) {
        String s = d.optString(2);
        return "market".equals(s) ? d.optDouble(0) : "pre_market".equals(s) ? d.optDouble(4) : "post_market".equals(s) ? d.optDouble(5) : Double.NaN;
    }

    private static String liveLabel(JSONArray d) {
        String s = d.optString(2);
        return "market".equals(s) ? "現價" : "pre_market".equals(s) ? "盤前價" : "盤後價";
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
                JSONArray soxx = null, soxl = null, usd = null, dj = null, fx = null;
                for (int i = 0; i < data.length(); i++) {
                    JSONObject row = data.getJSONObject(i);
                    String s = row.getString("s");
                    if (s.endsWith(":SOXX")) soxx = row.getJSONArray("d");
                    if (s.endsWith(":SOXL")) soxl = row.getJSONArray("d");
                    if (s.endsWith(":USD")) usd = row.getJSONArray("d");
                    if (s.endsWith(":DJUSSC")) dj = row.getJSONArray("d");
                    if (s.endsWith(":USDTWD")) fx = row.getJSONArray("d");
                }
                if (soxx == null || soxl == null || usd == null || dj == null) throw new Exception("missing symbol");
                double[] bases = {base(soxx), base(soxl), base(dj), base(usd)};
                double fxRate = fx == null ? Double.NaN : fx.optDouble(0);
                double soxxNow = live(soxx), djNow = live(dj);
                String soxxNowLabel = liveLabel(soxx), djNowLabel = liveLabel(dj);
                String when;
                if ("market".equals(soxx.optString(2))) {
                    when = "前一交易日收盤";
                } else {
                    SimpleDateFormat f = new SimpleDateFormat("M/d", Locale.US);
                    f.setTimeZone(TimeZone.getTimeZone("America/New_York"));
                    when = f.format(new Date(soxx.getLong(3) * 1000)) + " 收盤";
                }
                runOnUiThread(() -> {
                    fetching = false;
                    applyBase("fetchedSoxx", bases[0], soxxPrev, soxxEst);
                    applyBase("fetchedDj", bases[2], djPrev, djEst);
                    soxlPrev.setText(money(bases[1]));
                    usdPrev.setText(money(bases[3]));
                    applyLive(soxxLive, soxxEst, "SOXX " + soxxNowLabel, soxxNow);
                    applyLive(djLive, djEst, "指數" + djNowLabel, djNow);
                    if (fxRate > 0 && !rateManual) { setRate(String.format(Locale.US, "%.3f", fxRate)); rateInfo.setText("最新 · 1 美金兌台幣"); calc(); }
                    status.setText("TradingView · " + when + " · 延遲約 15 分");
                });
            } catch (Exception e) {
                runOnUiThread(() -> { fetching = false; status.setText("取得報價失敗，可手動輸入昨收或按更新重試"); });
            }
        }).start();
    }

    /** 填入昨收；昨收換了一天就清掉舊的預估值。 */
    private void applyBase(String key, double value, EditText prev, EditText est) {
        String v = money(value);
        if (!v.equals(prefs.getString(key, v))) est.setText("");
        prefs.edit().putString(key, v).apply();
        prev.setText(v);
    }

    private void applyLive(TextView chip, EditText est, String label, double price) {
        if (price > 0) {
            chip.setText("帶入" + label + " " + money(price));
            chip.setOnClickListener(v -> setEst(est, price));
            chip.setVisibility(View.VISIBLE);
        } else {
            chip.setVisibility(View.GONE);
        }
    }
}
