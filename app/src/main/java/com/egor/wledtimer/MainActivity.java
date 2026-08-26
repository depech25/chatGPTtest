package com.egor.wledtimer;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final String BASE = "http://192.168.1.250";
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 48, 32, 32);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("WLED Таймер");
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        status = new TextView(this);
        status.setText("WLED: 192.168.1.250");
        status.setTextSize(18);
        status.setPadding(0, 20, 0, 30);
        status.setGravity(Gravity.CENTER);
        root.addView(status, matchWrap());

        addTimerButton(root, "15 минут", 15);
        addTimerButton(root, "30 минут", 30);
        addTimerButton(root, "1 час", 60);
        addTimerButton(root, "2 часа", 120);

        Button cancel = makeButton("Отменить таймер");
        cancel.setOnClickListener(v -> sendJson("{\"nl\":{\"on\":false}}", "Таймер отменён"));
        root.addView(cancel, buttonParams());

        Button refresh = makeButton("Проверить остаток");
        refresh.setOnClickListener(v -> refreshState());
        root.addView(refresh, buttonParams());

        setContentView(root);
        refreshState();
    }

    private void addTimerButton(LinearLayout root, String label, int minutes) {
        Button b = makeButton(label);
        b.setOnClickListener(v -> {
            String json = "{\"nl\":{\"on\":true,\"dur\":" + minutes + ",\"mode\":0,\"tbri\":0}}";
            sendJson(json, "Выключение через " + label.toLowerCase());
        });
        root.addView(b, buttonParams());
    }

    private Button makeButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(20);
        b.setAllCaps(false);
        return b;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams p = matchWrap();
        p.setMargins(0, 10, 0, 10);
        return p;
    }

    private void sendJson(String json, String successMessage) {
        status.setText("Отправляю…");
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(BASE + "/json/state").openConnection();
                c.setRequestMethod("POST");
                c.setConnectTimeout(2500);
                c.setReadTimeout(2500);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = c.getOutputStream()) { os.write(bytes); }
                int code = c.getResponseCode();
                if (code >= 200 && code < 300) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show();
                        refreshState();
                    });
                } else {
                    throw new Exception("HTTP " + code);
                }
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("Нет связи с WLED: " + e.getMessage()));
            }
        }).start();
    }

    private void refreshState() {
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(BASE + "/json/state").openConnection();
                c.setConnectTimeout(2500);
                c.setReadTimeout(2500);
                try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                    JSONObject state = new JSONObject(sb.toString());
                    JSONObject nl = state.getJSONObject("nl");
                    boolean on = nl.getBoolean("on");
                    int rem = nl.optInt("rem", -1);
                    String text;
                    if (on && rem >= 0) {
                        int min = rem / 60;
                        int sec = rem % 60;
                        text = String.format("Таймер активен: %d:%02d", min, sec);
                    } else {
                        text = "Таймер не активен";
                    }
                    runOnUiThread(() -> status.setText(text));
                }
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("WLED недоступен в этой Wi‑Fi сети"));
            }
        }).start();
    }
}
