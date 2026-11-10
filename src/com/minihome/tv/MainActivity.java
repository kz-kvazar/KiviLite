package com.minihome.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Мини-лаунчер для Android TV 6.0.1 (LG 43UK32G).
 * Плитки быстрого доступа, сетка приложений, часы и значок состояния сети.
 * Язык интерфейса берётся из настроек телевизора: русский, украинский, английский.
 * Долгое нажатие на приложение — удалить или скрыть. Кнопка «Изменить» рядом с заголовком
 * и кнопка MENU на пульте включают режим управления: скрытые приложения видно и можно вернуть.
 * Весь интерфейс строится кодом, без картинок.
 */
public class MainActivity extends Activity {

    /** Плитки: пакет, класс, цвет, подписи (русский, украинский, английский). */
    private static final String[][] TILES = {
        {"com.mstar.tv.tvplayer.ui", "com.mstar.tv.tvplayer.ui.RootActivity", "5AA9FF", "ТВ", "ТБ", "TV"},
        {"tv.fun.settings", "tv.fun.settings.inputsource.InputSourceActivity", "35C9AE", "HDMI", "HDMI", "HDMI"},
        {"tv.fun.settings", "tv.fun.settings.general.GeneralSettingsActivity", "F0B95A", "Настройки", "Налаштування", "Settings"},
        {"com.android.tv.settings", "com.android.tv.settings.MainSettings", "8CD96A", "Android", "Android", "Android"},
        {"tv.fun.settings", "tv.fun.settings.picaudio.PicAudioSettingsActivity", "E8785A", "Картинка", "Зображення", "Picture"},
        {"tv.fun.filemanager", "tv.fun.filemanager.FunFileManagerActivity", "B39BEA", "Файлы", "Файли", "Files"}
    };

    private static final String[] S_TITLE = {"Телевизор", "Телевізор", "Television"};
    private static final String[] S_APPS = {"Приложения", "Застосунки", "Apps"};
    private static final String[] S_EDIT = {"Изменить", "Змінити", "Edit"};
    private static final String[] S_DONE = {"Готово", "Готово", "Done"};
    private static final String[] S_HINT = {
        "Выберите приложение: удалить, скрыть или вернуть на экран",
        "Виберіть програму: видалити, сховати або повернути на екран",
        "Pick an app: uninstall, hide or restore"};
    private static final String[] S_HIDDEN = {" (скрыто)", " (приховано)", " (hidden)"};
    private static final String[] S_HIDDENCAP = {"Скрытые:", "Приховані:", "Hidden:"};
    private static final String[] S_HIDDEN_TITLE = {"Скрытые приложения", "Приховані програми", "Hidden apps"};
    private static final String[] S_CANCEL = {"Отмена", "Скасувати", "Cancel"};
    private static final int ACCENT_EDIT = 0xFFE06C75;
    private static final int ACCENT_OK = 0xFF7ED07A;

    /** Пакеты, которые в сетке приложений не показываются. */
    private static final String[] SKIP = {
        "com.minihome.tv", "com.kivi.launcher_v2", "tv.fun.settings",
        "tv.fun.filemanager", "com.android.tv.settings", "com.mstar.tv.tvplayer.ui",
        "com.android.providers.downloads.ui", "com.funshion.poweroffdialog"
    };

    private static final int BG_TOP = 0xFF0C1424;      // тёмно-синяя вода
    private static final int BG_BOTTOM = 0xFF101838;   // глубокий синий у низа, без черноты
    private static final int CARD = 0xFF1E2733;
    private static final int CARD_BORDER = 0x33FFFFFF;
    private static final int CARD_FOCUS = 0xFF4FD08A;   // как зелёный провод у значка сети
    private static final int CARD_FOCUS_EDGE = 0xFFDCE9F7;   // и светлая кромка, как корпус коннектора
    private static final int TEXT_MAIN = 0xFFF1F5FA;
    private static final int TEXT_DIM = 0xFF97A7B8;
    private static final int TEXT_OK = 0xFF7ED07A;

    private static final String PREFS = "minihome";
    private static final String KEY_HIDDEN = "hidden";

    private final List<ResolveInfo> apps = new ArrayList<ResolveInfo>();
    private AppAdapter adapter;
    private TextView clockView;
    private TextView dateView;
    private NetIcon netView;
    private final Handler handler = new Handler();
    private Set<String> hidden = new HashSet<String>();
    /** Защита от двойного показа меню, когда долгое нажатие приходит сразу от сетки и от плитки. */
    private long lastMenuAt = 0L;
    /** Отложенный показ меню: если ОК держат дольше LONG_PRESS_MS — открываем меню вместо приложения. */
    private Runnable pendingLong;
    private boolean menuShown;
    private static final long LONG_PRESS_MS = 650L;
    /** Режим управления: кнопка «Изменить» показывает в сетке удаление, скрытие и возврат приложений. */
    private boolean editMode;
    private TextView editButton;
    private TextView captionView;
    private TextView hiddenChip;
    private GridView gridView;
    private int lastGridPos = 0;

    // --- Погода в шапке: значок + температура. Обновляем не чаще раза в час. ---
    private static final String W_PREFS = "weather";
    private static final long WEATHER_TTL_MS = 3600000L;
    private TextView weatherView;
    private WeatherIcon weatherIcon;
    private boolean weatherBusy;

    // --- Курсы валют в шапке: обновляем редко (раз в 3-4 часа), значения округляем до копеек. ---
    private static final String R_PREFS = "rates";
    private static final long RATES_TTL_MS = 12600000L;      // 3,5 часа
    private final TextView[] rateViews = new TextView[4];
    private final CoinIcon[] rateIcons = new CoinIcon[4];
    private boolean ratesBusy;

    /**
     * Часы и состояние сети. Раньше будили лаунчер каждые 20 секунд ради часов,
     * которые меняются раз в минуту: теперь раз в 30 секунд, а сеть проверяем ещё реже —
     * она меняется редко, и её пересчитывает выход на главный экран.
     */
    private int tickCount = 0;
    private LinearLayout rootView;
    private LinearLayout tileRow;
    private View firstTile;
    private boolean lastWasGrid = true;
    private String lastTimeText = null, lastDateText = null;
    private int lastNetKind = -1;
    private final Runnable ticker = new Runnable() {
        public void run() {
            updateClock();
            tickCount++;
            if ((tickCount % 2) == 0) updateNetwork();          // раз в 2 минуты
            if ((tickCount % 30) == 0) {                        // раз в полчаса
                refreshWeatherIfStale();                        // методы сами смотрят срок годности данных
                refreshRatesIfStale();
            }
            handler.postDelayed(this, 60000);                    // раз в минуту: чаще незачем
        }
    };

    private int lang() {
        String l = Locale.getDefault().getLanguage();
        if (l == null) return 0;
        l = l.toLowerCase();
        if (l.startsWith("uk")) return 1;
        if (l.startsWith("en")) return 2;
        return 0;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private Drawable rounded(int fill, int strokeColor, int strokeWidthDp) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setCornerRadius(dp(14));
        g.setColor(fill);
        if (strokeWidthDp > 0) g.setStroke(dp(strokeWidthDp), strokeColor);
        return g;
    }

    private Drawable tileCard(int accent) {
        StateListDrawable s = new StateListDrawable();
        // Выделение везде одно и то же — стекло, как у приложений.
        s.addState(new int[]{android.R.attr.state_focused}, glassCard());
        s.addState(new int[]{android.R.attr.state_pressed}, glassCard());
        s.addState(new int[]{android.R.attr.state_selected}, glassCard());
        s.addState(new int[]{}, rounded(CARD, accent, 3));
        return s;
    }

    // Фон карточек строим один раз, но каждому элементу отдаём свой экземпляр:
    // один общий Drawable запоминает состояние наведения и «залипает» на чужих карточках.
    private android.graphics.drawable.Drawable.ConstantState cardAppState;
    private android.graphics.drawable.Drawable.ConstantState cardEditState, cardEditHiddenState;

    private Drawable appCard() {
        if (cardAppState == null) {
            StateListDrawable s = new StateListDrawable();
            s.addState(new int[]{android.R.attr.state_focused}, glassCard());
            s.addState(new int[]{android.R.attr.state_pressed}, glassCard());
            s.addState(new int[]{android.R.attr.state_selected}, glassCard());
            s.addState(new int[]{}, rounded(CARD, CARD_BORDER, 2));
            cardAppState = s.getConstantState();
        }
        return cardAppState.newDrawable();
    }

    /** Выделение «стекло»: полупрозрачная светлая заливка с бликом сверху и тонкой светлой кромкой. */
    private Drawable glassCard() {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x4DFFFFFF, 0x1FFFFFFF});
        g.setCornerRadius(dp(8));
        g.setStroke(Math.max(1, dp(1.4f)), 0x99DCE9F7);
        return g;
    }

    /** Плитка в режиме управления: красная рамка — можно удалить или скрыть, зелёная — скрытое, можно вернуть. */
    private Drawable editCard(boolean hidden) {
        if (hidden) {
            if (cardEditHiddenState == null) cardEditHiddenState = buildEditCard(true).getConstantState();
            return cardEditHiddenState.newDrawable();
        }
        if (cardEditState == null) cardEditState = buildEditCard(false).getConstantState();
        return cardEditState.newDrawable();
    }

    private Drawable buildEditCard(boolean hidden) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, rounded(CARD_FOCUS, 0xFFFFFFFF, 4));
        s.addState(new int[]{android.R.attr.state_pressed}, rounded(CARD_FOCUS, 0xFFFFFFFF, 4));
        s.addState(new int[]{android.R.attr.state_selected}, rounded(CARD_FOCUS, 0xFFFFFFFF, 4));
        s.addState(new int[]{}, rounded(CARD, hidden ? ACCENT_OK : ACCENT_EDIT, 3));
        return s;
    }

    private ColorStateList tileText() {
        return new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_focused},
                        new int[]{android.R.attr.state_pressed},
                        new int[]{android.R.attr.state_selected},
                        new int[]{}},
                new int[]{Color.WHITE, Color.WHITE, Color.WHITE, TEXT_MAIN});
    }

    /**
     * Значок состояния сети, нарисованный кодом: RJ45 — провод, дуги — Wi-Fi, перечёркнутый круг — офлайн.
     */
    // ======================= Погода =======================

    /**
     * Сразу показывает погоду из памяти, а в сеть идёт ТОЛЬКО если данные старше часа.
     * Никаких служб, будильников и опроса в фоне: один короткий запрос при выходе на главный экран.
     */
    private void refreshWeatherIfStale() {
        final SharedPreferences p = getSharedPreferences(W_PREFS, MODE_PRIVATE);
        showWeather(p.getString("city", null), p.getInt("temp", 999), p.getInt("code", -1));
        long age = System.currentTimeMillis() - p.getLong("ts", 0L);
        if (age < WEATHER_TTL_MS || weatherBusy || !isOnline()) return;
        weatherBusy = true;
        new Thread(new Runnable() {
            public void run() {
                try {
                    String lat = p.getString("lat", null);
                    String lon = p.getString("lon", null);
                    String city = p.getString("city", null);
                    if (lat == null || lon == null) {
                        String geo = httpGet("https://ipwho.is/");
                        lat = jsonNum(geo, "latitude");
                        lon = jsonNum(geo, "longitude");
                        city = jsonStr(geo, "city");
                        if (lat == null || lon == null) return;
                    }
                    String w = httpGet("https://api.open-meteo.com/v1/forecast?latitude=" + lat
                            + "&longitude=" + lon + "&current=temperature_2m,weather_code");
                    String t = jsonNum(w, "temperature_2m");
                    if (t == null) return;
                    final int temp = (int) Math.round(Double.parseDouble(t));
                    String cs = jsonNum(w, "weather_code");
                    final int code = cs == null ? -1 : (int) Double.parseDouble(cs);
                    final String cityFinal = city == null ? "" : city;
                    p.edit().putString("lat", lat).putString("lon", lon).putString("city", cityFinal)
                            .putInt("temp", temp).putInt("code", code)
                            .putLong("ts", System.currentTimeMillis()).commit();
                    runOnUiThread(new Runnable() {
                        public void run() {
                            showWeather(cityFinal, temp, code);
                        }
                    });
                } catch (Throwable ignored) {
                    // Нет сети или сервис молчит — остаётся прошлое значение.
                } finally {
                    weatherBusy = false;
                }
            }
        }).start();
    }

    private void showWeather(String city, int temp, int code) {
        if (weatherView == null || weatherIcon == null) return;
        if (temp == 999) {
            weatherView.setText("");
            weatherIcon.setCode(-1);
            return;
        }
        // Город не показываем: только температура — крупно и ярко.
        weatherView.setText((temp > 0 ? "+" : "") + temp + "\u00B0");
        weatherIcon.setCode(code);
    }

    private boolean isOnline() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm == null ? null : cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Короткий запрос с жёсткими таймаутами: ответ маленький, соединение сразу закрываем. */
    private String httpGet(String url) throws Exception {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        c.setConnectTimeout(5000);
        c.setReadTimeout(5000);
        c.setRequestProperty("User-Agent", "MiniHome/1.0");
        try {
            java.io.InputStream in = c.getInputStream();
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            return new String(bo.toByteArray(), "UTF-8");
        } finally {
            c.disconnect();
        }
    }

    /**
     * Разбор ответов погоды и курсов. Раньше шаблон регулярного выражения собирался
     * заново на каждый ключ (десятки сборок на одно обновление) — теперь он один на всех.
     */
    private static final java.util.HashMap<String, java.util.regex.Pattern> P_CACHE =
            new java.util.HashMap<String, java.util.regex.Pattern>();

    private static java.util.regex.Pattern pattern(String key) {
        java.util.regex.Pattern p = P_CACHE.get(key);
        if (p == null) {
            p = java.util.regex.Pattern.compile("\\\"" + key + "\\\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
            P_CACHE.put(key, p);
        }
        return p;
    }

    private static java.util.regex.Pattern patternStr(String key) {
        java.util.regex.Pattern p = P_CACHE.get("s:" + key);
        if (p == null) {
            p = java.util.regex.Pattern.compile("\\\"" + key + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
            P_CACHE.put("s:" + key, p);
        }
        return p;
    }

    private static String jsonNum(String json, String key) {
        if (json == null) return null;
        java.util.regex.Matcher m = pattern(key).matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private static String jsonStr(String json, String key) {
        if (json == null) return null;
        java.util.regex.Matcher m = patternStr(key).matcher(json);
        return m.find() ? m.group(1) : null;
    }

    /** Минималистичный значок погоды: рисуется кодом, без картинок и ресурсов. */
    private class CoinIcon extends View {
        private final int kind;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        CoinIcon(Context c, int kind) {
            super(c);
            this.kind = kind;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            float cx = w / 2f, cy = h / 2f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(Math.max(2f, dp(1.6f)));

            if (kind == 2) {
                // Биткоин: буква «B» с двумя парами штрихов.
                paint.setColor(0xFFF0A94A);
                canvas.drawLine(w * 0.30f, h * 0.20f, w * 0.30f, h * 0.80f, paint);
                canvas.drawArc(new RectF(w * 0.24f, h * 0.16f, w * 0.86f, h * 0.52f), -90f, 190f, false, paint);
                canvas.drawArc(new RectF(w * 0.24f, h * 0.48f, w * 0.86f, h * 0.84f), -90f, 190f, false, paint);
                canvas.drawLine(w * 0.44f, h * 0.10f, w * 0.44f, h * 0.24f, paint);
                canvas.drawLine(w * 0.60f, h * 0.10f, w * 0.60f, h * 0.24f, paint);
                canvas.drawLine(w * 0.44f, h * 0.76f, w * 0.44f, h * 0.90f, paint);
                canvas.drawLine(w * 0.60f, h * 0.76f, w * 0.60f, h * 0.90f, paint);
                return;
            }

            if (kind == 3) {
                // Эфириум: ромб из четырёх граней.
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFFA9B4FF);
                Path up = new Path();
                up.moveTo(cx, h * 0.08f); up.lineTo(w * 0.84f, h * 0.54f);
                up.lineTo(cx, h * 0.71f); up.lineTo(w * 0.16f, h * 0.54f); up.close();
                canvas.drawPath(up, paint);
                paint.setColor(0xFF7C89E8);
                Path dn = new Path();
                dn.moveTo(cx, h * 0.79f); dn.lineTo(w * 0.84f, h * 0.62f);
                dn.lineTo(cx, h * 0.95f); dn.lineTo(w * 0.16f, h * 0.62f); dn.close();
                canvas.drawPath(dn, paint);
                return;
            }

            paint.setColor(kind == 0 ? 0xFF8FD48F : 0xFF9FC0E8);
            canvas.drawCircle(cx, cy, Math.min(w, h) * 0.44f, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(h * 0.60f);
            paint.setFakeBoldText(true);
            paint.setTextAlign(Paint.Align.CENTER);
            float off = (paint.descent() + paint.ascent()) / 2f;
            canvas.drawText(kind == 0 ? "$" : "€", cx, cy - off, paint);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setFakeBoldText(false);
        }
    }

    /** Значки первого ряда: сплошные яркие фигуры, как значки погоды. */
    private class TileIcon extends View {
        private final int kind;
        private final int accent;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        TileIcon(Context c, int kind, int accent) {
            super(c);
            this.kind = kind;
            this.accent = accent;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            float cx = w / 2f, cy = h / 2f;
            float m = Math.min(w, h);
            int light = 0xFFEAF2FB;                    // светлый элемент, как облако у погоды
            paint.setStyle(Paint.Style.FILL);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);

            switch (kind) {
                case 0:   // телевизор: корпус цветом, экран светлый
                    paint.setColor(accent);
                    canvas.drawRoundRect(new RectF(w * 0.13f, h * 0.22f, w * 0.87f, h * 0.68f), dp(6), dp(6), paint);
                    paint.setColor(light);
                    canvas.drawRoundRect(new RectF(w * 0.19f, h * 0.28f, w * 0.81f, h * 0.62f), dp(4), dp(4), paint);
                    paint.setColor(accent);
                    canvas.drawRoundRect(new RectF(w * 0.40f, h * 0.70f, w * 0.60f, h * 0.76f), dp(2), dp(2), paint);
                    canvas.drawRoundRect(new RectF(w * 0.26f, h * 0.77f, w * 0.74f, h * 0.84f), dp(3), dp(3), paint);
                    break;

                case 1: {  // HDMI: надпись заметно крупнее
                    paint.setColor(accent);
                    paint.setTextAlign(Paint.Align.CENTER);
                    paint.setTextSize(h * 0.34f);
                    paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                    Paint.FontMetrics fm = paint.getFontMetrics();
                    canvas.drawText("HDMI", cx, cy - (fm.ascent + fm.descent) / 2f, paint);
                    paint.setTextAlign(Paint.Align.LEFT);
                    paint.setTypeface(android.graphics.Typeface.DEFAULT);
                    break;
                }

                case 2: {  // шестерёнка: сплошная, втулка светлая
                    int teeth = 8;
                    float rTip = m * 0.46f, rRoot = m * 0.33f;
                    float step = (float) (Math.PI * 2 / teeth), flank = step * 0.19f;
                    Path gear = new Path();
                    for (int i = 0; i < teeth; i++) {
                        float a = i * step;
                        float v1 = a - step * 0.30f, t1 = a - flank, t2 = a + flank, v2 = a + step * 0.30f;
                        float x1 = cx + (float) Math.cos(v1) * rRoot, y1 = cy + (float) Math.sin(v1) * rRoot;
                        float x2 = cx + (float) Math.cos(t1) * rTip, y2 = cy + (float) Math.sin(t1) * rTip;
                        float x3 = cx + (float) Math.cos(t2) * rTip, y3 = cy + (float) Math.sin(t2) * rTip;
                        float x4 = cx + (float) Math.cos(v2) * rRoot, y4 = cy + (float) Math.sin(v2) * rRoot;
                        if (i == 0) gear.moveTo(x1, y1); else gear.lineTo(x1, y1);
                        gear.lineTo(x2, y2); gear.lineTo(x3, y3); gear.lineTo(x4, y4);
                    }
                    gear.close();
                    paint.setColor(accent);
                    canvas.drawPath(gear, paint);
                    paint.setColor(light);
                    canvas.drawCircle(cx, cy, m * 0.14f, paint);
                    break;
                }

                case 3:    // робот Android: сплошная голова со светлыми глазами
                    paint.setColor(accent);
                    canvas.drawRoundRect(new RectF(w * 0.22f, h * 0.36f, w * 0.78f, h * 0.74f), dp(9), dp(9), paint);
                    canvas.drawLine(w * 0.36f, h * 0.36f, w * 0.29f, h * 0.22f, paint);
                    canvas.drawLine(w * 0.64f, h * 0.36f, w * 0.71f, h * 0.22f, paint);
                    paint.setColor(light);
                    canvas.drawCircle(w * 0.38f, h * 0.53f, m * 0.055f, paint);
                    canvas.drawCircle(w * 0.62f, h * 0.53f, m * 0.055f, paint);
                    break;

                case 4:    // картинка: светлые дорожки, цветные ручки
                    for (int i = 0; i < 3; i++) {
                        float y = h * (0.34f + i * 0.16f);
                        paint.setColor(light);
                        canvas.drawRoundRect(new RectF(w * 0.26f, y - dp(1.6f), w * 0.74f, y + dp(1.6f)), dp(1.6f), dp(1.6f), paint);
                        paint.setColor(accent);
                        float kx = w * (0.40f + i * 0.11f);
                        canvas.drawCircle(kx, y, dp(4.2f), paint);
                    }
                    break;

                default: {  // файлы: флешка. Все размеры от min(w,h) — пропорции не плывут
                    float len = m * 1.25f;                 // длина — короче, чтобы вписаться в обводку
                    float th = m * 0.50f;                  // толщина
                    float x0 = cx - len / 2f, y0 = cy - th / 2f;
                    float plug = len * 0.30f;              // разъём — треть длины
                    paint.setColor(0xFFB9C6D8);            // металл разъёма
                    canvas.drawRoundRect(new RectF(x0, y0 + th * 0.16f, x0 + plug, y0 + th * 0.84f), dp(2), dp(2), paint);
                    paint.setColor(light);                 // контактные полоски
                    canvas.drawRect(x0 + plug * 0.14f, y0 + th * 0.30f, x0 + plug * 0.86f, y0 + th * 0.40f, paint);
                    canvas.drawRect(x0 + plug * 0.14f, y0 + th * 0.60f, x0 + plug * 0.86f, y0 + th * 0.70f, paint);
                    paint.setColor(accent);                // корпус
                    canvas.drawRoundRect(new RectF(x0 + plug, y0, x0 + len, y0 + th), th * 0.22f, th * 0.22f, paint);
                    paint.setColor(light);                 // светлая полоса на корпусе
                    canvas.drawRoundRect(new RectF(x0 + plug + len * 0.06f, y0 + th * 0.26f,
                            x0 + len * 0.62f, y0 + th * 0.44f), dp(2), dp(2), paint);
                    paint.setColor(0xFF1E2733);            // отверстие под шнурок
                    canvas.drawCircle(x0 + len * 0.87f, cy, th * 0.14f, paint);
                    break;
                }
            }
        }
    }

    /** Фон лаунчера: тёмный градиент, бледные дуги и точки, мягкое свечение — чтению не мешает. */

    /** Крипта — целыми числами, с разделителем разрядов (биткоин и эфириум без копеек). */
    private static String money0(String raw) {
        try {
            return String.format(java.util.Locale.US, "%,.0f", Double.parseDouble(raw)).replace(',', ' ');
        } catch (Throwable t) {
            return raw;
        }
    }

    private static String money(String raw) {
        try {
            return String.format(java.util.Locale.US, "%.2f", Double.parseDouble(raw));
        } catch (Throwable t) {
            return raw;
        }
    }

    private void showRates(String usd, String eur, String btc, String eth) {
        String[] vals = {usd, eur, btc, eth};
        for (int i = 0; i < 4; i++) {
            if (rateViews[i] != null) rateViews[i].setText(vals[i] == null ? "—" : vals[i]);
        }
    }

    private void refreshRatesIfStale() {
        final SharedPreferences p = getSharedPreferences(R_PREFS, MODE_PRIVATE);
        showRates(p.getString("uah", null), p.getString("eur", null), p.getString("btc", null),
                p.getString("eth", null));
        long age = System.currentTimeMillis() - p.getLong("ts", 0L);
        if (age < RATES_TTL_MS || ratesBusy || !isOnline()) return;
        ratesBusy = true;
        new Thread(new Runnable() {
            public void run() {
                try {
                    String usd = null, eur = null, btc = null, eth = null;
                    String er = httpGet("https://open.er-api.com/v6/latest/USD");
                    String uah = jsonNum(er, "UAH");            // доллар в гривне
                    String eurInUsd = jsonNum(er, "EUR");
                    if (uah != null) usd = money(uah);
                    if (uah != null && eurInUsd != null) {
                        double e = Double.parseDouble(eurInUsd);
                        if (e > 0) eur = money(String.valueOf(Double.parseDouble(uah) / e));   // евро в гривне
                    }
                    String cg = httpGet("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd");
                    String b = jsonNum(cg, "usd");              // биткоин в долларах
                    if (b != null) btc = money0(b);
                    String cg2 = httpGet("https://api.coingecko.com/api/v3/simple/price?ids=ethereum&vs_currencies=usd");
                    String e2 = jsonNum(cg2, "usd");            // эфириум в долларах
                    if (e2 != null) eth = money0(e2);
                    if (usd == null && btc == null && eth == null) return;
                    p.edit().putString("uah", usd).putString("eur", eur).putString("btc", btc).putString("eth", eth)
                            .putLong("ts", System.currentTimeMillis()).commit();
                    runOnUiThread(new Runnable() {
                        public void run() {
                            showRates(p.getString("uah", null), p.getString("eur", null),
                                    p.getString("btc", null), p.getString("eth", null));
                        }
                    });
                } catch (Throwable ignored) {
                } finally {
                    ratesBusy = false;
                }
            }
        }).start();
    }

    /** Фон лаунчера: тёмное небо, занавеси северного сияния и мягкое затухание к низу. */
    private class BgDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private android.graphics.Bitmap cache;
        private int cacheW, cacheH;

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (cache == null || cacheW != b.width() || cacheH != b.height()) {
                buildCache(b.width(), b.height());
            }
            if (cache != null) {
                canvas.drawBitmap(cache, null, b, paint);   // фон уже нарисован — просто копируем
                return;
            }
            paintBackground(canvas, b);
        }

        /** Весь узор рисуем один раз в половинном разрешении: дешевле по процессору и по памяти. */
        private void buildCache(int w, int h) {
            cacheW = w;
            cacheH = h;
            try {
                // Четверть разрешения: фон состоит из плавных градиентов, при растяжении
                // разницы не видно, а памяти уходит вчетверо меньше.
                cache = android.graphics.Bitmap.createBitmap(
                        Math.max(1, w / 4), Math.max(1, h / 4), android.graphics.Bitmap.Config.ARGB_8888);
            } catch (Throwable t) {
                cache = null;
                return;
            }
            Canvas c = new Canvas(cache);
            c.scale(0.25f, 0.25f);
            paintBackground(c, new Rect(0, 0, w, h));
        }

        private void paintBackground(Canvas canvas, Rect b) {
            float w = b.width(), h = b.height();

            // Ночное небо
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(0, b.top, 0, b.bottom, BG_TOP, BG_BOTTOM, Shader.TileMode.CLAMP));
            canvas.drawRect(b, paint);
            paint.setShader(null);

            // Занавеси северного сияния — без точек и звёзд
            curtain(canvas, b, 0.09f, 0.32f, 0.95f, 0x3BE8A8, 0x52);   // зелёный
            curtain(canvas, b, 0.32f, 0.48f, 1.00f, 0x2FD8D8, 0x44);   // бирюзовый до самого низа
            curtain(canvas, b, 0.56f, 0.30f, 0.88f, 0x8A6CF0, 0x3E);   // фиолетовый
            curtain(canvas, b, 0.79f, 0.36f, 0.98f, 0x3BE8A8, 0x30);   // ещё зелёный
            curtain(canvas, b, 0.98f, 0.26f, 0.84f, 0x5AD8FF, 0x28);   // голубой у правого края

            // Мягкое свечение у верхней кромки
            paint.setShader(new RadialGradient(w * 0.5f, b.top, w * 0.75f,
                    new int[]{0x225FE8C0, 0x00000000}, null, Shader.TileMode.CLAMP));
            canvas.drawRect(b.left, b.top, b.right, h * 0.5f, paint);
            paint.setShader(null);

            // Плавное затухание до самого низа: без чёрного, в тёмно-синий
            paint.setShader(new LinearGradient(0, b.top, 0, b.bottom,
                    new int[]{0x00000000, 0x100A1024, 0x260C1430, 0x440C1430, 0x620E1630, 0x7A0E1630},
                    new float[]{0f, 0.25f, 0.48f, 0.68f, 0.88f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(b, paint);
            paint.setShader(null);

            // Виньетка по краям
            paint.setShader(new RadialGradient(w * 0.5f, h * 0.5f, Math.max(w, h) * 0.76f,
                    new int[]{0x00000000, 0x00000000, 0x380C1430}, new float[]{0f, 0.62f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(b, paint);
            paint.setShader(null);
        }

        /**
         * Занавесь сияния: волнистая вертикальная полоса.
         * Края мягкие по бокам, а книзу полоса растворяется — резкой границы нет.
         */
        private void curtain(Canvas canvas, Rect b, float cxF, float hwF, float botF, int color, int alpha) {
            float w = b.width(), h = b.height();
            float cx = b.left + w * cxF, hw = w * hwF, bot = b.top + h * botF;
            int c = (alpha << 24) | (color & 0x00FFFFFF);
            Path p = new Path();
            p.moveTo(cx - hw, b.top - dp(4));
            p.cubicTo(cx - hw * 1.18f, h * 0.22f, cx - hw * 0.68f, h * 0.48f, cx - hw * 0.92f, bot);
            p.lineTo(cx + hw * 0.92f, bot);
            p.cubicTo(cx + hw * 0.68f, h * 0.48f, cx + hw * 1.18f, h * 0.22f, cx + hw, b.top - dp(4));
            p.close();

            // Мягкие края по бокам
            Shader side = new LinearGradient(cx - hw, 0, cx + hw, 0,
                    new int[]{0x00000000, c, c, 0x00000000}, new float[]{0f, 0.32f, 0.68f, 1f}, Shader.TileMode.CLAMP);
            // И плавное растворение книзу — строго до нуля у нижней кромки
            Shader fadeOut = new LinearGradient(0, b.top, 0, bot,
                    new int[]{0xFFFFFFFF, 0xFFFFFFFF, 0xA0FFFFFF, 0x28FFFFFF, 0x00FFFFFF},
                    new float[]{0f, 0.30f, 0.62f, 0.86f, 1f}, Shader.TileMode.CLAMP);
            paint.setShader(new android.graphics.ComposeShader(side, fadeOut, android.graphics.PorterDuff.Mode.DST_IN));
            canvas.drawPath(p, paint);
            paint.setShader(null);
        }

        @Override
        public void setAlpha(int alpha) {
            // Плотность фона задаётся самим рисунком, отдельная прозрачность не нужна.
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter cf) {
            // Фильтры не используются.
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.OPAQUE;
        }
    }

    /** Значок погоды: рисуется кодом, яркими цветами — видно с дивана. */
    private class WeatherIcon extends View {
        private int code = -1;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        WeatherIcon(Context c) {
            super(c);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        void setCode(int c) {
            if (code != c) {
                code = c;
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (code < 0) return;
            float w = getWidth(), h = getHeight();
            float m = Math.min(w, h);
            boolean sun = code == 0 || code == 1 || code == 2;
            boolean rain = (code >= 51 && code <= 67) || (code >= 80 && code <= 82);
            boolean snow = (code >= 71 && code <= 77) || code == 85 || code == 86;
            boolean storm = code >= 95;
            Paint p = paint;

            if ((sun || storm) && !rain && !snow) {
                // Солнце: тёплый диск и лучи
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFFFC24D);
                canvas.drawCircle(w * 0.5f, h * 0.5f, m * 0.24f, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(2f, m * 0.075f));
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * i / 4.0;
                    canvas.drawLine(w * 0.5f + (float) Math.cos(a) * m * 0.33f,
                            h * 0.5f + (float) Math.sin(a) * m * 0.33f,
                            w * 0.5f + (float) Math.cos(a) * m * 0.46f,
                            h * 0.5f + (float) Math.sin(a) * m * 0.46f, p);
                }
                return;
            }

            // Облако — светлое, читается на тёмном фоне
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFDCE9F7);
            float cy = h * 0.50f;
            canvas.drawCircle(w * 0.36f, cy, w * 0.15f, p);
            canvas.drawCircle(w * 0.56f, cy - h * 0.08f, w * 0.21f, p);
            canvas.drawCircle(w * 0.75f, cy, w * 0.15f, p);
            canvas.drawRect(w * 0.36f, cy, w * 0.75f, cy + w * 0.15f, p);

            if (rain || storm) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(2f, m * 0.06f));
                p.setColor(storm ? 0xFFFFD84D : 0xFF7FC8FF);
                for (int i = 0; i < 3; i++) {
                    float x = w * (0.36f + i * 0.16f);
                    canvas.drawLine(x, cy + w * 0.22f, x - w * 0.05f, cy + w * 0.38f, p);
                }
                if (storm) {
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(0xFFFFE066);
                    Path b = new Path();
                    b.moveTo(w * 0.52f, cy + w * 0.20f);
                    b.lineTo(w * 0.41f, cy + w * 0.46f);
                    b.lineTo(w * 0.50f, cy + w * 0.46f);
                    b.lineTo(w * 0.43f, cy + w * 0.64f);
                    b.lineTo(w * 0.62f, cy + w * 0.36f);
                    b.lineTo(w * 0.53f, cy + w * 0.36f);
                    b.close();
                    canvas.drawPath(b, p);
                }
                return;
            }
            if (snow) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(2f, m * 0.05f));
                p.setColor(0xFFFFFFFF);
                for (int i = 0; i < 3; i++) {
                    float x = w * (0.38f + i * 0.16f), y = cy + w * 0.30f;
                    canvas.drawLine(x, y - w * 0.07f, x, y + w * 0.07f, p);
                    canvas.drawLine(x - w * 0.06f, y, x + w * 0.06f, y, p);
                }
            }
        }
    }

    /** Значок сети: рисуется кодом, яркими цветами — как значки погоды, видно с дивана. */
    private class NetIcon extends View {
        private int kind = 0;

        NetIcon(Context c) {
            super(c);
        }

        void set(int k) {
            if (kind != k) {
                kind = k;
                invalidate();
            }
        }

        protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight();
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);

            if (kind == 2) {
                // Сетевой кабель: витая пара с наконечником — крупно и ярко.
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(3f, h * 0.17f));
                p.setColor(0xFF4FD08A);                                  // зелёный провод
                Path cable = new Path();
                cable.moveTo(w * 0.03f, h * 0.64f);
                cable.lineTo(w * 0.24f, h * 0.64f);
                cable.cubicTo(w * 0.35f, h * 0.64f, w * 0.35f, h * 0.42f, w * 0.46f, h * 0.42f);
                cable.lineTo(w * 0.60f, h * 0.42f);
                cv.drawPath(cable, p);

                // Светлый корпус коннектора — как облако у значка погоды
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFDCE9F7);
                float r = h * 0.09f;
                cv.drawRoundRect(new RectF(w * 0.58f, h * 0.25f, w * 0.97f, h * 0.59f), r, r, p);

                // Золотистая контактная площадка и защёлка сверху
                p.setColor(0xFFFFC24D);
                cv.drawRect(w * 0.91f, h * 0.31f, w * 0.97f, h * 0.53f, p);
                p.setColor(0xFF9BE8B8);
                cv.drawRoundRect(new RectF(w * 0.66f, h * 0.12f, w * 0.80f, h * 0.27f), r * 0.8f, r * 0.8f, p);
                return;
            }

            if (kind == 1) {
                // Wi-Fi: дуги толще и ярче, точка крупнее
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(3f, h * 0.14f));
                p.setColor(0xFF4FD08A);
                float cx = w / 2f, cy = h * 0.94f;
                for (int i = 3; i >= 1; i--) {
                    float rr = i * h * 0.27f;
                    cv.drawArc(new RectF(cx - rr, cy - rr, cx + rr, cy + rr), 210, 120, false, p);
                }
                p.setStyle(Paint.Style.FILL);
                cv.drawCircle(cx, cy - h * 0.06f, Math.max(3f, h * 0.13f), p);
                return;
            }

            // Сети нет: приглушённый перечёркнутый значок
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(3f, h * 0.13f));
            p.setColor(0xFF8A94A6);
            float rr = Math.min(w, h) * 0.32f;
            cv.drawCircle(w / 2f, h / 2f, rr, p);
            cv.drawLine(w * 0.30f, h * 0.70f, w * 0.70f, h * 0.30f, p);
        }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        hidden = new HashSet<String>(getSharedPreferences(PREFS, MODE_PRIVATE)
                .getStringSet(KEY_HIDDEN, new HashSet<String>()));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        rootView = root;
        // Фон: градиент + очень бледная векторная графика (дуги, точки) — читаемость не страдает.
        root.setBackgroundDrawable(new BgDrawable());

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(40), dp(0), dp(40), dp(0));

        // Вместо названия «Телевизор» — погода: минималистичный значок и температура.
        LinearLayout weatherBox = new LinearLayout(this);
        weatherBox.setOrientation(LinearLayout.HORIZONTAL);
        weatherBox.setGravity(Gravity.CENTER_VERTICAL);

        // Курсы валют: значок вместо названия, значение с копейками. Обновление раз в 3-4 часа.
        for (int i = 0; i < 4; i++) {
            LinearLayout rateBox = new LinearLayout(this);
            rateBox.setOrientation(LinearLayout.HORIZONTAL);
            rateBox.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rbp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rbp.setMargins(dp(24), 0, 0, 0);
            rateBox.setLayoutParams(rbp);

            rateIcons[i] = new CoinIcon(this, i);
            rateIcons[i].setLayoutParams(new LinearLayout.LayoutParams(dp(26), dp(26)));
            rateBox.addView(rateIcons[i]);

            TextView rv = new TextView(this);
            rv.setTextColor(TEXT_MAIN);
            rv.setTextSize(16f);
            rv.setTypeface(Typeface.DEFAULT_BOLD);
            LinearLayout.LayoutParams rvp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rvp.setMargins(dp(6), 0, 0, 0);
            rv.setLayoutParams(rvp);
            rateBox.addView(rv);
            rateViews[i] = rv;

            weatherBox.addView(rateBox);
        }

        header.addView(weatherBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Значок сети в мягкой подложке — читается как индикатор состояния.
        LinearLayout netChip = new LinearLayout(this);
        netChip.setGravity(Gravity.CENTER);
        netChip.setBackgroundDrawable(rounded(0x1FFFFFFF, 0, 0));
        netChip.setPadding(dp(10), dp(6), dp(10), dp(6));
        netView = new NetIcon(this);
        netView.setLayoutParams(new LinearLayout.LayoutParams(dp(46), dp(40)));
        netChip.addView(netView);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        np.setMargins(0, 0, dp(26), 0);
        netChip.setLayoutParams(np);
        header.addView(netChip);

        // Погода перенесена к часам: значок и температура рядом с временем, крупно и ярко.
        LinearLayout clockBox = new LinearLayout(this);
        clockBox.setOrientation(LinearLayout.HORIZONTAL);
        clockBox.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout weatherRow = new LinearLayout(this);
        weatherRow.setOrientation(LinearLayout.HORIZONTAL);
        weatherRow.setGravity(Gravity.CENTER_VERTICAL);

        weatherIcon = new WeatherIcon(this);
        weatherIcon.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(42)));
        weatherRow.addView(weatherIcon);

        weatherView = new TextView(this);
        weatherView.setTextColor(TEXT_MAIN);
        weatherView.setTextSize(30f);
        weatherView.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wlp.setMargins(dp(10), 0, 0, 0);
        weatherView.setLayoutParams(wlp);
        weatherRow.addView(weatherView);

        // Погода теперь слева от значка сети: порядок в шапке — курсы, погода, сеть, часы.
        LinearLayout.LayoutParams wrp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wrp.setMargins(0, 0, dp(26), 0);
        header.addView(weatherRow, 1, wrp);

        LinearLayout timeCol = new LinearLayout(this);
        timeCol.setOrientation(LinearLayout.VERTICAL);
        timeCol.setGravity(Gravity.RIGHT);

        clockView = new TextView(this);
        clockView.setTextColor(TEXT_MAIN);
        clockView.setTextSize(54f);
        clockView.setTypeface(Typeface.DEFAULT_BOLD);
        clockView.setGravity(Gravity.RIGHT);
        timeCol.addView(clockView);

        dateView = new TextView(this);
        dateView.setTextColor(0xFFDCE9F7);
        dateView.setTextSize(20f);
        dateView.setGravity(Gravity.RIGHT);
        timeCol.addView(dateView);
        clockBox.addView(timeCol);

        header.addView(clockBox);
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // Плитки: одинаковая высота, подпись в одну строку по центру — ничего не съезжает.
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(26), dp(14), dp(26), dp(0));
        tileRow = row;
        for (int i = 0; i < TILES.length; i++) {
            final String pkg = TILES[i][0];
            final String cls = TILES[i][1];
            final int accent = Color.parseColor("#" + TILES[i][2]);

            // Плитка: значок вместо надписи — крупнее и читается с дивана.
            TileIcon tile = new TileIcon(this, i, accent);
            if (i == 0) firstTile = tile;
            tile.setContentDescription(TILES[i][3 + lang()]);   // подпись остаётся для доступности
            tile.setFocusable(true);
            tile.setClickable(true);
            tile.setBackgroundDrawable(tileCard(accent));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(80), 1f);
            lp.setMargins(dp(7), dp(6), dp(7), dp(6));
            tile.setLayoutParams(lp);
            tile.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    // Первая плитка — всегда телевизор, а не последний вход.
                    if ("com.mstar.tv.tvplayer.ui".equals(pkg)) switchToTv();
                    launch(pkg, cls);
                }
            });
            addFocusFx(tile);
            row.addView(tile);
        }
        root.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // Тонкая светящаяся линия под верхним рядом — визуально отделяет шапку от приложений.
        View divider = new View(this);
        divider.setBackgroundDrawable(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x002F76C4, 0x662F76C4, 0x002F76C4}));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
        dlp.setMargins(dp(40), dp(0), dp(40), 0);
        root.addView(divider, dlp);

        LinearLayout captionRow = new LinearLayout(this);
        captionRow.setOrientation(LinearLayout.HORIZONTAL);
        captionRow.setGravity(Gravity.CENTER_VERTICAL);
        captionRow.setPadding(dp(36), dp(14), dp(36), 0);

        captionView = new TextView(this);
        captionView.setText(S_APPS[lang()]);
        captionView.setTextColor(TEXT_DIM);
        captionView.setTextSize(15f);
        captionRow.addView(captionView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Кнопка «Скрытые: N» — здесь видно, куда уходят скрытые приложения, и их можно вернуть.
        hiddenChip = new TextView(this);
        hiddenChip.setTextSize(15f);
        hiddenChip.setTypeface(Typeface.DEFAULT_BOLD);
        hiddenChip.setTextColor(TEXT_MAIN);
        hiddenChip.setFocusable(true);
        hiddenChip.setClickable(true);
        hiddenChip.setPadding(dp(16), dp(8), dp(16), dp(8));
        hiddenChip.setBackgroundDrawable(tileCard(ACCENT_OK));
        hiddenChip.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { hiddenMenu(); }
        });
        LinearLayout.LayoutParams hcp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hcp.setMargins(0, 0, dp(12), 0);
        hiddenChip.setLayoutParams(hcp);
        addFocusFx(hiddenChip);
        captionRow.addView(hiddenChip);

        // Отдельная кнопка управления приложениями: работает всегда, не зависит от долгого нажатия.
        editButton = new TextView(this);
        editButton.setText(S_EDIT[lang()]);
        editButton.setTextSize(15f);
        editButton.setTypeface(Typeface.DEFAULT_BOLD);
        editButton.setTextColor(TEXT_MAIN);
        editButton.setFocusable(true);
        editButton.setClickable(true);
        editButton.setPadding(dp(16), dp(8), dp(16), dp(8));
        editButton.setBackgroundDrawable(tileCard(ACCENT_EDIT));
        editButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { setEditMode(!editMode); }
        });
        // Кнопку «Змінити» не добавляем: ровно то же самое делает долгое нажатие
        // на карточке приложения — скрыть, вернуть, удалить.

        root.addView(captionRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        GridView grid = new GridView(this);
        grid.setNumColumns(5);
        grid.setHorizontalSpacing(dp(14));
        grid.setVerticalSpacing(dp(14));
        grid.setPadding(dp(34), dp(0), dp(34), dp(34));
        grid.setFocusable(true);
        grid.setClipToPadding(false);
        adapter = new AppAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (editMode) appMenuOnce(position);
                else openApp(position);
            }
        });
        // Запоминаем, где стоял курсор: после скрытия или возврата приложения
        // сетка перестраивается, и курсор нужно вернуть на прежнее место.
        grid.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                lastWasGrid = true;
                lastGridPos = position;
            }

            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        // Долгое нажатие ОК на плитке приложения — меню «удалить / скрыть».
        grid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                appMenuOnce(position);
                return true;
            }
        });
        gridView = grid;
        root.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        // Следим за установкой и удалением приложений: только тогда и пересобираем список.
        IntentFilter pkgFilter = new IntentFilter();
        pkgFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        pkgFilter.addDataScheme("package");
        try {
            registerReceiver(pkgReceiver, pkgFilter);
        } catch (Throwable ignored) {
        }
        loadApps();
        updateClock();
        updateNetwork();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(pkgReceiver);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Система просит освободить память — отдаём кэши значков и названий.
     * Они восстановятся сами, а держать в лаунчере десятки мегабайт картинок незачем.
     */
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            iconCache.clear();
            labelText.clear();
            labelCache.clear();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (appsDirty) loadApps();       // перебор приложений только при изменениях
        updateClock();
        updateNetwork();
        refreshWeatherIfStale();
        refreshRatesIfStale();
        ensureFocus();
        handler.removeCallbacks(ticker);
        handler.postDelayed(ticker, 60000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
        cancelPendingLong();
    }

    private void cancelPendingLong() {
        if (pendingLong != null) {
            handler.removeCallbacks(pendingLong);
            pendingLong = null;
        }
    }

    // Форматы даты и времени держим готовыми: раньше они создавались заново на каждое обновление часов.
    private static SimpleDateFormat fmtTime, fmtDate;

    private void updateClock() {
        if (clockView == null) return;
        Date now = new Date();
        Locale loc = Locale.getDefault();
        if (fmtTime == null) {
            fmtTime = new SimpleDateFormat("HH:mm", loc);
            fmtDate = new SimpleDateFormat("d MMMM, EEEE", loc);
        }
        // Перерисовку вызываем только когда значение реально изменилось:
        // время — раз в минуту, дата — раз в сутки.
        String t = fmtTime.format(now), d = fmtDate.format(now);
        if (!t.equals(lastTimeText)) {
            lastTimeText = t;
            clockView.setText(t);
        }
        if (!d.equals(lastDateText)) {
            lastDateText = d;
            dateView.setText(d);
        }
    }

    private int netKind() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo wifi = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            if (wifi != null && wifi.isConnected()) return 1;
        } catch (Exception ignored) {
        }
        boolean hasEth = false;
        boolean hasWlan = false;
        try {
            java.util.Enumeration<java.net.NetworkInterface> list = java.net.NetworkInterface.getNetworkInterfaces();
            while (list != null && list.hasMoreElements()) {
                java.net.NetworkInterface ni = list.nextElement();
                if (ni == null || !ni.isUp() || ni.isLoopback()) continue;
                boolean hasV4 = false;
                java.util.Enumeration<java.net.InetAddress> addrs = ni.getInetAddresses();
                while (addrs != null && addrs.hasMoreElements()) {
                    java.net.InetAddress a = addrs.nextElement();
                    if (a instanceof java.net.Inet4Address && !a.isLoopbackAddress()) {
                        hasV4 = true;
                        break;
                    }
                }
                if (!hasV4) continue;
                String name = ni.getName() == null ? "" : ni.getName().toLowerCase();
                if (name.startsWith("eth")) hasEth = true;
                else if (name.startsWith("wlan")) hasWlan = true;
            }
        } catch (Exception ignored) {
        }
        if (hasEth) return 2;
        if (hasWlan) return 1;
        return 0;
    }

    private void updateNetwork() {
        if (netView == null) return;
        int k = netKind();
        if (k == lastNetKind) return;      // состояние не изменилось — не трогаем отрисовку
        lastNetKind = k;
        netView.set(k);
    }

    private void openApp(int position) {
        if (position < 0 || position >= apps.size()) return;
        ResolveInfo ri = apps.get(position);
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        i.setComponent(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(i);
        } catch (Exception e) {
            showError(e.toString());
        }
    }

    /** Долгое нажатие может прийти и от сетки, и от самой плитки — показываем меню один раз. */
    private void appMenuOnce(int position) {
        long now = System.currentTimeMillis();
        if (now - lastMenuAt < 900L) return;
        lastMenuAt = now;
        appMenu(position);
    }

    /** Меню приложения: удалить, скрыть с главного экрана или вернуть скрытое обратно. */
    private void appMenu(final int position) {
        if (position < 0 || position >= apps.size()) return;
        ResolveInfo ri = apps.get(position);
        appMenuForPackage(ri.activityInfo.packageName,
                String.valueOf(ri.loadLabel(getPackageManager())),
                hidden.contains(ri.activityInfo.packageName));
    }

    /** Действия для конкретного приложения — вызывается и из сетки, и из списка скрытых. */
    private void appMenuForPackage(final String pkg, String label, final boolean hid) {
        int li = lang();
        final String[] items;
        if (hid) {
            items = li == 1
                    ? new String[]{"Повернути на головний екран", "Видалити програму", "Скасувати"}
                    : li == 2
                    ? new String[]{"Restore to home screen", "Uninstall app", "Cancel"}
                    : new String[]{"Вернуть на главный экран", "Удалить приложение", "Отмена"};
        } else {
            items = li == 1
                    ? new String[]{"Видалити програму", "Сховати з головного екрана", "Скасувати"}
                    : li == 2
                    ? new String[]{"Uninstall app", "Hide from home screen", "Cancel"}
                    : new String[]{"Удалить приложение", "Скрыть с главного экрана", "Отмена"};
        }
        new AlertDialog.Builder(this)
                .setTitle(label)
                .setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        if (hid) {
                            if (which == 0) {
                                hidden.remove(pkg);
                                saveHidden();
                                loadApps();
                                restoreGridFocus(lastGridPos);
                            } else if (which == 1) {
                                uninstall(pkg);
                            }
                        } else {
                            if (which == 0) {
                                uninstall(pkg);
                            } else if (which == 1) {
                                hidden.add(pkg);
                                saveHidden();
                                loadApps();
                                restoreGridFocus(lastGridPos);
                            }
                        }
                    }
                })
                .show();
    }

    private void saveHidden() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putStringSet(KEY_HIDDEN, hidden).commit();
    }

    /**
     * Возврат курсора в сетку приложений после её перестройки.
     * Без этого после скрытия или возврата приложения выделение пропадает,
     * потому что сфокусированная карточка исчезает вместе со старым списком.
     */
    private void restoreGridFocus(final int position) {
        if (gridView == null) return;
        gridView.post(new Runnable() {
            public void run() {
                int count = apps.size();
                if (count == 0) {
                    if (editButton != null) editButton.requestFocus();
                    return;
                }
                int p = position;
                if (p < 0) p = 0;
                if (p > count - 1) p = count - 1;
                gridView.setSelection(p);
                gridView.requestFocus();
                View child = gridView.getChildAt(p - gridView.getFirstVisiblePosition());
                if (child != null) child.requestFocus();
            }
        });
    }

    private void uninstall(String pkg) {
        try {
            Intent del = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg));
            del.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(del);
        } catch (Exception e) {
            showError(e.toString());
        }
    }

    /** Режим управления приложениями: включён — плитки не запускаются, а открывают меню. */
    private void setEditMode(boolean on) {
        editMode = on;
        cancelPendingLong();
        if (editButton != null) {
            editButton.setText(on ? S_DONE[lang()] : S_EDIT[lang()]);
            editButton.setBackgroundDrawable(tileCard(on ? ACCENT_OK : ACCENT_EDIT));
        }
        if (captionView != null) captionView.setText(on ? S_HINT[lang()] : S_APPS[lang()]);
        loadApps();
        ensureFocus();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent ev) {
        // MENU на пульте переключает режим управления, BACK из него выходит.
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            setEditMode(!editMode);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && editMode) {
            setEditMode(false);
            return true;
        }
        return super.onKeyDown(keyCode, ev);
    }

    /**
     * Размер подписи плитки верхнего ряда. Плиток стало шесть — при прежнем
     * размере (19) длинные подписи вроде «Налаштування» обрезались бы,
     * поэтому при шести и более плитках подпись чуть меньше.
     */
    private float tileTextSize() {
        return TILES.length > 5 ? 17f : 19f;
    }

    /**
     * Кнопка «Назад» на главном экране НЕ закрывает лаунчер.
     * Иначе система показывает приложение, из которого только что вышли
     * (это и было замечено: после выхода на лаунчер «Назад» возвращал в приложение).
     * Из режима управления — выходим из режима, в остальных случаях ничего не делаем.
     */
    @Override
    public void onBackPressed() {
        if (editMode) {
            setEditMode(false);
            return;
        }
        // Намеренно пусто: главный экран остаётся открытым.
    }

    /** Лёгкая анимация фокуса: карточка чуть подрастает и приподнимается над фоном. */
    /**
     * Подсветка при наведении. Анимаций нет — по просьбе владельца.
     * Тень скруглённая: контур по форме карточки, а не квадратом.
     */
    private void addFocusFx(final View v) {
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            v.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(14));
                }
            });
            v.setClipToOutline(false);
        }
        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View view, boolean has) {
                if (has) lastWasGrid = false;          // пользователь на плитке верхнего ряда
                if (android.os.Build.VERSION.SDK_INT >= 21) {
                    view.setElevation(has ? dp(6) : 0f);
                }
            }
        });
    }

    /**
     * Плитка «ТВ» должна открывать телевизор, а не последний вход: приложение само
     * возобновляет аналог, цифру или HDMI. Поэтому сначала переключаем источник
     * на цифровое ТВ через вендорский API, затем запускаем приложение.
     */
    private void switchToTv() {
        try {
            Class<?> cm = Class.forName("com.mstar.android.tv.TvCommonManager");
            Object mgr = cm.getMethod("getInstance").invoke(null);
            Class<?> src = Class.forName("com.mstar.android.tvapi.common.vo.EnumInputSource");
            Object dtv = src.getField("E_INPUT_SOURCE_DTV").get(null);
            cm.getMethod("setInputSource", src).invoke(mgr, dtv);
        } catch (Throwable ignored) {
        }
    }

    /** Кнопка «Скрытые: N» — показывает, куда уходят скрытые приложения. */
    private void updateHiddenChip() {
        if (hiddenChip == null) return;
        if (hidden.isEmpty()) {
            hiddenChip.setVisibility(View.GONE);
        } else {
            hiddenChip.setVisibility(View.VISIBLE);
            hiddenChip.setText(S_HIDDENCAP[lang()] + " " + hidden.size());
        }
    }

    /**
     * Страховка фокуса. Пульт на телевизоре управляет только тем, на чём стоит выделение:
     * если после возврата из приложения, закрытия диалога или перестройки сетки выделение
     * потерялось, экран перестаёт реагировать. Здесь возвращаем его сами.
     */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) ensureFocus();
    }

    private void ensureFocus() {
        final View root = rootView;
        if (root == null) return;
        root.post(new Runnable() {
            public void run() {
                if (hasUsefulFocus()) return;                    // выделение на элементе — не мешаем
                if (lastWasGrid && gridView != null && apps.size() > 0) {
                    restoreGridFocus(lastGridPos);               // возвращаем в список приложений
                }
                if (hasUsefulFocus()) return;
                if (firstTile != null) firstTile.requestFocus(); // иначе — на первую плитку
                if (!hasUsefulFocus() && editButton != null) editButton.requestFocus();
            }
        });
    }

    /**
     * Настоящее ли это выделение. Корневой узел тоже может быть «в фокусе», но подсветки
     * на нём нет и пульт не двигает выбор — именно это и выглядело как пропавший курсор.
     */
    private boolean hasUsefulFocus() {
        View f = getCurrentFocus();
        if (f == null) return false;
        if (f == firstTile || f == gridView || f == editButton || f == hiddenChip) return true;
        if (gridView != null) {
            for (int i = 0; i < gridView.getChildCount(); i++) {
                if (gridView.getChildAt(i) == f) return true;
            }
        }
        if (tileRow != null) {
            for (int i = 0; i < tileRow.getChildCount(); i++) {
                if (tileRow.getChildAt(i) == f) return true;
            }
        }
        return false;
    }

    /** Список скрытых приложений: нажатие по строке даёт вернуть его на главный экран. */
    private void hiddenMenu() {
        final List<String> pkgs = new ArrayList<String>(hidden);
        Collections.sort(pkgs);
        final String[] labels = new String[pkgs.size()];
        for (int i = 0; i < pkgs.size(); i++) labels[i] = labelOf(pkgs.get(i));
        new AlertDialog.Builder(this)
                .setTitle(S_HIDDEN_TITLE[lang()])
                .setItems(labels, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        appMenuForPackage(pkgs.get(which), labels[which], true);
                    }
                })
                .setNegativeButton(S_CANCEL[lang()], null)
                .show();
    }

    private String labelOf(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (Exception e) {
            return pkg;
        }
    }

    private void launch(String pkg, String cls) {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.setComponent(new ComponentName(pkg, cls));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (Exception e) {
            showError(pkg + ": " + e);
        }
    }

    private void showError(String s) {
        Toast.makeText(this, "Не удалось открыть: " + s, Toast.LENGTH_LONG).show();
    }

    /**
     * Система гасит нашу службу перехвата кнопки SOURCE при каждом обновлении приложения,
     * из-за чего кнопка «немеет» до перезагрузки. Возвращаем её сами при выходе на главный экран.
     */
    private void ensureSourceKeyEnabled() {
        try {
            String me = getPackageName() + "/" + SourceKeyService.class.getName();
            String cur = android.provider.Settings.Secure.getString(getContentResolver(),
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (cur != null && cur.contains(SourceKeyService.class.getName())) return;
            android.provider.Settings.Secure.putString(getContentResolver(),
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    (cur == null || cur.length() == 0) ? me : cur + ":" + me);
            android.provider.Settings.Secure.putInt(getContentResolver(),
                    android.provider.Settings.Secure.ACCESSIBILITY_ENABLED, 1);
        } catch (Throwable ignored) {
            // прав нет — тогда выручает включение в автозапуске при загрузке
        }
    }

    /**
     * Список приложений. Раньше он пересобирался при каждом выходе на главный экран,
     * а подписи запрашивались у системы заново при каждом сравнении при сортировке
     * (десятки обращений). Теперь подписи кэшируются, а если набор приложений не изменился —
     * список вообще не перебирается.
     */
    private final java.util.HashMap<String, String> labelCache = new java.util.HashMap<String, String>();
    /** Названия для показа на карточках (в исходном регистре) — чтобы не звать loadLabel каждый раз. */
    private final java.util.HashMap<String, String> labelText = new java.util.HashMap<String, String>();
    private final java.util.HashMap<String, Drawable> iconCache = new java.util.HashMap<String, Drawable>();
    private String appsSignature = null;

    /**
     * Список приложений меняется редко, а перебор установленных программ (queryIntentActivities)
     * — один из самых дорогих вызовов к системе. Поэтому пересобираем список не на каждом
     * возврате на главный экран, а только когда система сама сообщила об установке или удалении.
     */
    private boolean appsDirty = true;
    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            appsDirty = true;
        }
    };

    private void loadApps() {
        appsDirty = false;
        PackageManager pm = pkgMgr();
        Intent main = new Intent(Intent.ACTION_MAIN);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(main, 0);

        StringBuilder sig = new StringBuilder();
        for (int i = 0; i < found.size(); i++) {
            if (found.get(i).activityInfo != null) {
                sig.append(found.get(i).activityInfo.packageName).append(',');
            }
        }
        sig.append('|').append(editMode ? 1 : 0).append('|').append(hidden.size());
        if (sig.toString().equals(appsSignature)) {
            updateHiddenChip();
            return;                     // ничего не изменилось — сетку не перестраиваем
        }
        appsSignature = sig.toString();
        iconCache.clear();
        labelText.clear();      // обновилось приложение — название могло измениться

        apps.clear();
        for (int i = 0; i < found.size(); i++) {
            ResolveInfo ri = found.get(i);
            if (ri.activityInfo == null) continue;
            String p = ri.activityInfo.packageName;
            if (getPackageName().equals(p)) continue;
            if (!editMode && hidden.contains(p)) continue;
            boolean skip = false;
            for (int s = 0; s < SKIP.length; s++) {
                if (SKIP[s].equals(p)) { skip = true; break; }
            }
            if (skip) continue;
            if (!labelCache.containsKey(p)) {
                labelCache.put(p, String.valueOf(ri.loadLabel(pm)).toLowerCase());
            }
            apps.add(ri);
        }
        Collections.sort(apps, new Comparator<ResolveInfo>() {
            public int compare(ResolveInfo a, ResolveInfo b) {
                String la = labelCache.get(a.activityInfo.packageName);
                String lb = labelCache.get(b.activityInfo.packageName);
                int c = (la == null ? "" : la).compareTo(lb == null ? "" : lb);
                return c != 0 ? c : a.activityInfo.packageName.compareTo(b.activityInfo.packageName);
            }
        });
        if (adapter != null) adapter.notifyDataSetChanged();
        updateHiddenChip();
    }

    /**
     * Держатель элементов карточки. Слушатели, контур тени и фон ставятся один раз при создании
     * строки, а не на каждую перерисовку — раньше на каждый вызов getView создавалось три
     * слушателя, OutlineProvider и Drawable, и это било по памяти и по плавности прокрутки.
     */
    private static class Holder {
        LinearLayout box;
        ImageView img;
        TextView tv;
        int position;
        String pkg;
        String label;
        boolean bgEdit;
        boolean bgHidden;
        boolean hasBg;
    }

    /** PackageManager берём один раз: каждый вызов getPackageManager() — обращение к системе. */
    private PackageManager pmCache;

    private PackageManager pkgMgr() {
        if (pmCache == null) pmCache = getPackageManager();
        return pmCache;
    }

    private class AppAdapter extends BaseAdapter {
        /** Слушатели создаются вместе с адаптером и переиспользуются всеми карточками. */
        private final View.OnClickListener click = new View.OnClickListener() {
            public void onClick(View v) {
                Holder h = (Holder) v.getTag();
                if (editMode) appMenuOnce(h.position);
                else openApp(h.position);
            }
        };
        private final View.OnLongClickListener longClick = new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                appMenuOnce(((Holder) v.getTag()).position);
                return true;
            }
        };
        private final View.OnFocusChangeListener focus = new View.OnFocusChangeListener() {
            public void onFocusChange(View view, boolean has) {
                if (has) lastWasGrid = false;
                if (android.os.Build.VERSION.SDK_INT >= 21) view.setElevation(has ? dp(6) : 0f);
            }
        };
        /** Пульт: MENU — действия для приложения; ОК, удержанное дольше LONG_PRESS_MS, — тоже меню. */
        private final View.OnKeyListener key = new View.OnKeyListener() {
            public boolean onKey(View v, int code, KeyEvent ev) {
                final Holder h = (Holder) v.getTag();
                if (code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_BUTTON_Y
                        || code == KeyEvent.KEYCODE_PROG_RED) {
                    if (ev.getAction() == KeyEvent.ACTION_DOWN) appMenu(h.position);
                    return true;
                }
                boolean okKey = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                        || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
                if (!okKey) return false;
                if (ev.getAction() == KeyEvent.ACTION_DOWN) {
                    if (pendingLong == null) {
                        final int pos = h.position;
                        menuShown = false;
                        pendingLong = new Runnable() {
                            public void run() {
                                pendingLong = null;
                                menuShown = true;
                                appMenuOnce(pos);
                            }
                        };
                        handler.postDelayed(pendingLong, LONG_PRESS_MS);
                    }
                    return false;
                }
                cancelPendingLong();
                if (menuShown) {
                    menuShown = false;
                    return true;
                }
                return false;
            }
        };

        public int getCount() { return apps.size(); }
        public Object getItem(int p) { return apps.get(p); }
        public long getItemId(int p) { return p; }

        public View getView(int position, View convert, ViewGroup parent) {
            Holder h;
            if (convert == null) {
                h = new Holder();
                LinearLayout ll = new LinearLayout(MainActivity.this);
                ll.setOrientation(LinearLayout.VERTICAL);
                ll.setGravity(Gravity.CENTER);
                ll.setFocusable(true);
                ll.setClickable(true);
                ll.setLongClickable(true);
                ll.setPadding(dp(6), dp(0), dp(6), dp(0));
                ll.setTag(h);
                ll.setOnClickListener(click);
                ll.setOnLongClickListener(longClick);
                ll.setOnKeyListener(key);
                ll.setOnFocusChangeListener(focus);
                if (android.os.Build.VERSION.SDK_INT >= 21) {
                    ll.setOutlineProvider(new android.view.ViewOutlineProvider() {
                        @Override
                        public void getOutline(View view, android.graphics.Outline outline) {
                            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(14));
                        }
                    });
                    ll.setClipToOutline(false);
                }

                ImageView img = new ImageView(MainActivity.this);
                img.setLayoutParams(new LinearLayout.LayoutParams(dp(112), dp(112)));
                ll.addView(img);

                TextView tv = new TextView(MainActivity.this);
                tv.setTextColor(TEXT_MAIN);
                tv.setTextSize(20f);
                tv.setGravity(Gravity.CENTER);
                tv.setMaxLines(2);
                tv.setEllipsize(TextUtils.TruncateAt.END);
                ll.addView(tv);

                h.box = ll;
                h.img = img;
                h.tv = tv;
                convert = ll;
            } else {
                h = (Holder) convert.getTag();
            }

            h.position = position;
            ResolveInfo ri = apps.get(position);
            String pkg = ri.activityInfo.packageName;
            // Название и значок читаем только при смене приложения в этой ячейке.
            if (!pkg.equals(h.pkg)) {
                h.pkg = pkg;
                String label = labelText.get(pkg);
                if (label == null) {
                    label = String.valueOf(ri.loadLabel(pkgMgr()));
                    labelText.put(pkg, label);
                }
                h.label = label;
                Drawable icon = iconCache.get(pkg);
                if (icon == null) {
                    icon = ri.loadIcon(pkgMgr());     // значок грузим один раз и держим в кэше
                    iconCache.put(pkg, icon);
                }
                h.img.setImageDrawable(icon);
                h.hasBg = false;                       // новая карточка — новый фон
            }
            if (editMode) {
                boolean hid = hidden.contains(pkg);
                h.tv.setText(h.label + (hid ? S_HIDDEN[lang()] : ""));
                h.tv.setTextColor(hid ? TEXT_DIM : TEXT_MAIN);
                if (!h.hasBg || !h.bgEdit || h.bgHidden != hid) {
                    h.box.setBackgroundDrawable(editCard(hid));
                    h.bgEdit = true;
                    h.bgHidden = hid;
                    h.hasBg = true;
                }
            } else {
                h.tv.setText(h.label);
                h.tv.setTextColor(TEXT_MAIN);
                if (!h.hasBg || h.bgEdit) {
                    h.box.setBackgroundDrawable(appCard());
                    h.bgEdit = false;
                    h.hasBg = true;
                }
            }
            return h.box;
        }
    }
}
