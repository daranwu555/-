package com.soxl.calc;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.TextView;
import java.util.Locale;

public class MainActivity extends Activity {
    private EditText soxxPrev, soxlPrev, soxxEst, leverage;
    private TextView result, soxlChg, soxxChg;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.main);
        prefs = getSharedPreferences("soxl", MODE_PRIVATE);
        soxxPrev = findViewById(R.id.soxxPrev);
        soxlPrev = findViewById(R.id.soxlPrev);
        soxxEst = findViewById(R.id.soxxEst);
        leverage = findViewById(R.id.leverage);
        result = findViewById(R.id.result);
        soxlChg = findViewById(R.id.soxlChg);
        soxxChg = findViewById(R.id.soxxChg);
        if (state == null) {
            soxxPrev.setText(prefs.getString("soxxPrev", ""));
            soxlPrev.setText(prefs.getString("soxlPrev", ""));
            soxxEst.setText(prefs.getString("soxxEst", ""));
            leverage.setText(prefs.getString("leverage", "3"));
        }
        TextWatcher w = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable e) { calc(); }
        };
        soxxPrev.addTextChangedListener(w);
        soxlPrev.addTextChangedListener(w);
        soxxEst.addTextChangedListener(w);
        leverage.addTextChangedListener(w);
        findViewById(R.id.clear).setOnClickListener(v -> { soxxEst.setText(""); soxxEst.requestFocus(); });
        calc();
    }

    @Override
    protected void onPause() {
        super.onPause();
        prefs.edit().putString("soxxPrev", soxxPrev.getText().toString()).putString("soxlPrev", soxlPrev.getText().toString()).putString("soxxEst", soxxEst.getText().toString()).putString("leverage", leverage.getText().toString()).apply();
    }

    private static double num(EditText e) {
        try { return Double.parseDouble(e.getText().toString().trim()); } catch (NumberFormatException ex) { return Double.NaN; }
    }

    private static String pct(double r) {
        return String.format(Locale.US, "%+.2f%%", r * 100);
    }

    private void calc() {
        double a = num(soxxPrev), b = num(soxlPrev), c = num(soxxEst), lev = num(leverage);
        int neutral = getColor(R.color.sub);
        if (!(a > 0) || !(b > 0) || !(c > 0) || !(lev > 0)) {
            result.setText("—");
            result.setTextColor(getColor(R.color.text));
            soxlChg.setText("SOXL 漲跌：—");
            soxlChg.setTextColor(neutral);
            soxxChg.setText("SOXX 漲跌：—");
            soxxChg.setTextColor(neutral);
            return;
        }
        double r = c / a - 1;
        double lr = Math.max(lev * r, -1);
        double price = b * (1 + lr);
        int color = r > 0 ? getColor(R.color.up) : r < 0 ? getColor(R.color.down) : getColor(R.color.text);
        result.setText(String.format(Locale.US, "$%.2f", price));
        result.setTextColor(color);
        soxlChg.setText(String.format(Locale.US, "SOXL 漲跌：%s（%+.2f）", pct(lr), price - b));
        soxlChg.setTextColor(r == 0 ? neutral : color);
        soxxChg.setText(String.format(Locale.US, "SOXX 漲跌：%s（%+.2f）", pct(r), c - a));
        soxxChg.setTextColor(neutral);
    }
}
