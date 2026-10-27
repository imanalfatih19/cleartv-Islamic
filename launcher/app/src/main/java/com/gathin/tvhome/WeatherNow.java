package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/** Weather for Merchang, Marang (Open-Meteo, no key): a small glass card on Home, every 30 minutes. */
final class WeatherNow {
    private static final String TAG = "TvHome.Weather";
    private static final String API = "https://api.open-meteo.com/v1/forecast?latitude=5.05&longitude=103.25"
            + "&current=temperature_2m,weather_code&timezone=Asia%2FKuala_Lumpur";
    private static final long REFRESH_MS = 30 * 60_000L, RETRY_MS = 5 * 60_000L, MAX_AGE_MS = 3 * 60 * 60_000L;

    private final Context ctx;
    private final View card;
    private final TextView temp, desc;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean loading;
    private long lastTry;

    WeatherNow(Context context, View card, TextView temp, TextView desc, TextView place) {
        this.ctx = context.getApplicationContext();
        this.card = card;
        this.temp = temp;
        this.desc = desc;
        if (card == null) return;
        float d = ctx.getResources().getDisplayMetrics().density;
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x80000000);
        g.setStroke(Math.max(1, Math.round(1.5f * d)), 0x44FFFFFF);
        g.setCornerRadius(24 * d);
        card.setBackground(g);
        if (place != null) place.setText("Merchang");
        show();
    }

    private SharedPreferences prefs() {
        return ctx.getSharedPreferences("weather", Context.MODE_PRIVATE);
    }

    /** Shows the last reading and fetches a new one when it is older than 30 minutes. */
    void update() {
        if (card == null) return;
        show();
        long now = System.currentTimeMillis();
        if (loading || now - prefs().getLong("at", 0) < REFRESH_MS || now - lastTry < RETRY_MS) return;
        loading = true;
        lastTry = now;
        new Thread(() -> {
            try {
                fetch();
            } catch (Exception e) {
                Log.w(TAG, "weather failed: " + e.getMessage());
            }
            ui.post(() -> {
                loading = false;
                show();
            });
        }, "weather").start();
    }

    private void fetch() throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(API).openConnection();
        try {
            c.setConnectTimeout(10_000);
            c.setReadTimeout(10_000);
            if (c.getResponseCode() != 200) return;
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            JSONObject cur = new JSONObject(sb.toString()).getJSONObject("current");
            prefs().edit()
                    .putFloat("temp", (float) cur.getDouble("temperature_2m"))
                    .putInt("code", cur.getInt("weather_code"))
                    .putLong("at", System.currentTimeMillis())
                    .apply();
        } finally {
            c.disconnect();
        }
    }

    private void show() {
        SharedPreferences p = prefs();
        long at = p.getLong("at", 0);
        if (at == 0 || System.currentTimeMillis() - at > MAX_AGE_MS) {
            card.setVisibility(View.GONE);
            return;
        }
        temp.setText(Math.round(p.getFloat("temp", 0f)) + "\u00b0C");
        desc.setText(describe(p.getInt("code", 0)));
        card.setVisibility(View.VISIBLE);
    }

    /** WMO weather code to Malay. */
    static String describe(int code) {
        switch (code) {
            case 0: return "Cerah";
            case 1: return "Kebanyakan cerah";
            case 2: return "Sebahagian berawan";
            case 3: return "Mendung";
            case 45: case 48: return "Berkabus";
            case 51: case 53: case 55: case 56: case 57: return "Hujan renyai";
            case 61: case 63: case 66: return "Hujan";
            case 65: case 67: return "Hujan lebat";
            case 80: case 81: return "Hujan seketika";
            case 82: return "Hujan sangat lebat";
            case 95: case 96: case 99: return "Ribut petir";
            default: return "";
        }
    }
}
