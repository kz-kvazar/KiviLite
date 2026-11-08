package com.minihome.tv;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Панель настроек изображения поверх любого экрана: подсветка, яркость, контрастность,
 * насыщенность, резкость, оттенок. Значения применяются вендорским API телевизора
 * (com.mstar.android.tvapi.common.PictureManager) через отражение — особых прав не требуется.
 * Подсветка читается и пишется по-настоящему; остальные значения хранятся в памяти панели.
 */
public class PicturePanelActivity extends Activity {

    private static final String PREFS = "picpanel";
    private static final int[] DEFAULTS = {50, 50, 50, 50, 50, 50};

    /** Подписи строк и имена столбцов в базе настроек изображения телевизора. */
    private static final String[] LABELS = {
            "Подсветка", "Яркость", "Контрастность", "Насыщенность", "Резкость", "Оттенок"};
    private static final String[] COLUMN = {
            "u8Backlight", "u8Brightness", "u8Contrast", "u8Saturation", "u8Sharpness", "u8Hue"};
    /** Дополнительный вызов вендорского API, чтобы телевизор применил значение сразу. */
    private static final String[] NUDGE = {
            null, "setPictureModeBrightness", "setPictureModeContrast",
            "setPictureModeColor", "setPictureModeSharpness", "setPictureModeTint"};
    private static final String AUTHORITY = "content://mstar.tv.usersetting/picmode_setting";

    private final int[] value = new int[LABELS.length];
    private final List<View> rows = new ArrayList<View>();
    private final List<TextView> valueViews = new ArrayList<TextView>();
    private final List<Bar> bars = new ArrayList<Bar>();
    private int focusIdx = 0;

    /** Панель сейчас на экране: по этому признаку кнопка SOURCE закрывает её повторным нажатием. */
    static boolean visible;
    private static PicturePanelActivity instance;

    private Object pm;              // PictureManager
    private int backMin = 0, backMax = 100;
    private int srcType = 1, modeType = 0;      // текущий источник и режим изображения (строка в базе)
    private long lastApply = 0L;
    private final StringBuilder log = new StringBuilder();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        findManager();
        readValues();
        buildUi();
        saveLog();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Повторное нажатие кнопки на пульте просто обновляет панель.
        findManager();
        readValues();
        for (int i = 0; i < valueViews.size(); i++) {
            valueViews.get(i).setText(String.valueOf(value[i]));
            bars.get(i).setValue(value[i]);
        }
        saveLog();
    }

    // ------------------------- работа с API телевизора -------------------------

    private void findManager() {
        try {
            Class<?> tm = Class.forName("com.mstar.android.tvapi.common.TvManager");
            Object tv = tm.getMethod("getInstance").invoke(null);
            pm = tm.getMethod("getPictureManager").invoke(tv);
            log.append("менеджер: ").append(pm == null ? "нет" : "есть").append('\n');
        } catch (Throwable t) {
            pm = null;
            log.append("менеджер: ошибка ").append(t).append('\n');
        }
    }

    private void readValues() {
        resolveContext();
        Cursor c = null;
        try {
            c = getContentResolver().query(Uri.parse(AUTHORITY), null,
                    "InputSrcType=? AND PictureModeType=?",
                    new String[]{String.valueOf(srcType), String.valueOf(modeType)}, null);
            if (c == null || !c.moveToFirst()) {
                if (c != null) c.close();
                c = getContentResolver().query(Uri.parse(AUTHORITY), null, null, null, null);
                if (c != null && c.moveToFirst()) {
                    srcType = c.getInt(c.getColumnIndex("InputSrcType"));
                    modeType = c.getInt(c.getColumnIndex("PictureModeType"));
                    log.append("строка не нашлась, взял первую: источник=").append(srcType)
                            .append(" режим=").append(modeType).append('\n');
                }
            }
            if (c != null && c.getCount() > 0 && c.getPosition() >= 0) {
                for (int i = 0; i < COLUMN.length; i++) {
                    int idx = c.getColumnIndex(COLUMN[i]);
                    if (idx >= 0) value[i] = c.getInt(idx);
                }
                log.append("прочитано: подсветка=").append(value[0]).append(" яркость=").append(value[1])
                        .append(" контрастность=").append(value[2]).append(" насыщенность=").append(value[3])
                        .append(" резкость=").append(value[4]).append(" оттенок=").append(value[5]).append('\n');
            }
        } catch (Throwable t) {
            log.append("чтение базы: ошибка ").append(t).append('\n');
        } finally {
            if (c != null) c.close();
        }
    }

    /** Текущий источник и режим изображения — по ним выбирается строка в базе телевизора. */
    private void resolveContext() {
        try {
            Class<?> tm = Class.forName("com.mstar.android.tvapi.common.TvManager");
            Object tv = tm.getMethod("getInstance").invoke(null);
            Object src = tm.getMethod("getCurrentInputSource").invoke(tv);
            if (src instanceof Enum<?>) srcType = ((Enum<?>) src).ordinal();
        } catch (Throwable ignored) {
        }
        try {
            Class<?> c = Class.forName("com.mstar.android.tv.TvPictureManager");
            Object m = c.getMethod("getInstance").invoke(null);
            Object mode = c.getMethod("getPictureMode").invoke(m);
            if (mode instanceof Integer) modeType = (Integer) mode;
        } catch (Throwable ignored) {
        }
        log.append("источник=").append(srcType).append(" режим=").append(modeType).append('\n');
    }

    /** Записывает значение строки в базу телевизора и просит API применить его сразу. */
    private void applyValue(int idx) {
        long now = System.currentTimeMillis();
        if (now - lastApply < 100) return;
        lastApply = now;
        try {
            android.content.ContentValues cv = new android.content.ContentValues();
            cv.put(COLUMN[idx], value[idx]);
            int n = getContentResolver().update(Uri.parse(AUTHORITY), cv,
                    "InputSrcType=? AND PictureModeType=?",
                    new String[]{String.valueOf(srcType), String.valueOf(modeType)});
            log.append("записано ").append(COLUMN[idx]).append('=').append(value[idx])
                    .append(" (строк ").append(n).append(")\n");
        } catch (Throwable t) {
            log.append("запись ").append(COLUMN[idx]).append(": ошибка ").append(t).append('\n');
        }
        nudge(idx);
    }

    /** Дополнительный вызов вендорского API — телевизор применяет значение, не дожидаясь перезагрузки. */
    private void nudge(int idx) {
        if (pm == null) return;
        try {
            if (idx == 0) {
                Class<?> c = Class.forName("com.mstar.android.tv.TvPictureManager");
                Object m = c.getMethod("getInstance").invoke(null);
                c.getMethod("setBacklight", int.class).invoke(m, value[0]);
            } else {
                pm.getClass().getMethod(NUDGE[idx], short.class).invoke(pm, (short) value[idx]);
            }
        } catch (Throwable ignored) {
        }
    }

    private void remember(int idx) {
        // Значения живут в базе телевизора — своего дубля не держим.
    }

    // ------------------------------- интерфейс -------------------------------

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0x99000000);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundDrawable(rounded(0xF012161C, 14));
        panel.setPadding(dp(28), dp(16), dp(28), dp(16));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("Изображение");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(20f);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView hint = new TextView(this);
        hint.setText("◀ ▶ изменить   •   НАЗАД закрыть");
        hint.setTextColor(0xFF8FA3B8);
        hint.setTextSize(13f);
        head.addView(hint);
        panel.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        for (int i = 0; i < LABELS.length; i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setFocusable(true);
            row.setPadding(dp(14), dp(8), dp(14), dp(8));
            row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                public void onFocusChange(View v, boolean has) {
                    v.setBackgroundDrawable(has ? rounded(0xFF2A3947, 10) : null);
                    focusIdx = has ? idx : focusIdx;
                }
            });

            TextView label = new TextView(this);
            label.setText(LABELS[i]);
            label.setTextSize(18f);
            label.setTextColor(0xFFE6EDF5);
            row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView val = new TextView(this);
            val.setText(String.valueOf(value[i]));
            val.setTextSize(18f);
            val.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            val.setTextColor(0xFFFFFFFF);
            val.setGravity(Gravity.RIGHT);
            val.setMinWidth(dp(56));
            row.addView(val);
            valueViews.add(val);

            Bar bar = new Bar(this);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(220), dp(8));
            blp.setMargins(dp(16), 0, 0, 0);
            bar.setLayoutParams(blp);
            bar.setValue(value[i]);
            row.addView(bar);
            bars.add(bar);

            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.setMargins(0, dp(2), 0, dp(2));
            panel.addView(row, rlp);
            rows.add(row);

            // Кнопки влево/вправо меняют значение прямо на строке.
            row.setOnKeyListener(new View.OnKeyListener() {
                public boolean onKey(View v, int code, KeyEvent e) {
                    if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                    if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        int step = (code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
                        value[idx] = Math.max(0, Math.min(100, value[idx] + step));
                        valueViews.get(idx).setText(String.valueOf(value[idx]));
                        bars.get(idx).setValue(value[idx]);
                        applyValue(idx);
                        remember(idx);
                        return true;
                    }
                    return false;
                }
            });
        }

        // Отдельная строка-переход: режим изображения, аспект и звук живут в меню телевизора.
        LinearLayout open = new LinearLayout(this);
        open.setOrientation(LinearLayout.HORIZONTAL);
        open.setGravity(Gravity.CENTER_VERTICAL);
        open.setFocusable(true);
        open.setPadding(dp(14), dp(8), dp(14), dp(8));
        final int openIdx = LABELS.length;
        open.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) {
                v.setBackgroundDrawable(has ? rounded(0xFF2A3947, 10) : null);
                if (has) focusIdx = openIdx;
            }
        });
        TextView openText = new TextView(this);
        openText.setText("Режим, аспект и звук телевизора  →");
        openText.setTextSize(17f);
        openText.setTextColor(0xFFBFD4E8);
        open.addView(openText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        open.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int code, KeyEvent e) {
                if (e.getAction() == KeyEvent.ACTION_DOWN
                        && (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                        || code == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                    openVendorPicture();
                    return true;
                }
                return false;
            }
        });
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        olp.setMargins(0, dp(8), 0, 0);
        panel.addView(open, olp);
        rows.add(open);

        // Второй переход: служебное меню телевизора, там «Дисплей» и разрешение.
        LinearLayout disp = new LinearLayout(this);
        disp.setOrientation(LinearLayout.HORIZONTAL);
        disp.setGravity(Gravity.CENTER_VERTICAL);
        disp.setFocusable(true);
        disp.setPadding(dp(14), dp(8), dp(14), dp(8));
        final int dispIdx = LABELS.length + 1;
        disp.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) {
                v.setBackgroundDrawable(has ? rounded(0xFF2A3947, 10) : null);
                if (has) focusIdx = dispIdx;
            }
        });
        TextView dispText = new TextView(this);
        dispText.setText("Экран и разрешение телевизора  →");
        dispText.setTextSize(17f);
        dispText.setTextColor(0xFFBFD4E8);
        disp.addView(dispText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        disp.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int code, KeyEvent e) {
                if (e.getAction() == KeyEvent.ACTION_DOWN
                        && (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                        || code == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                    openVendorDisplay();
                    return true;
                }
                return false;
            }
        });
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.setMargins(0, dp(2), 0, 0);
        panel.addView(disp, dlp);
        rows.add(disp);

        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.gravity = Gravity.BOTTOM;
        plp.setMargins(dp(40), 0, dp(40), dp(40));
        root.addView(panel, plp);

        setContentView(root);
        rows.get(0).requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        instance = this;
    }

    @Override
    protected void onPause() {
        super.onPause();
        visible = false;
        saveLog();          // журнал пишем при уходе с панели, чтобы не трогать диск на каждое нажатие
    }

    /** Закрыть панель по повторному нажатию кнопки SOURCE. */
    static void requestClose() {
        final PicturePanelActivity a = instance;
        if (a != null) {
            a.runOnUiThread(new Runnable() {
                public void run() {
                    a.finish();
                }
            });
        }
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_BACK || code == 255) {     // НАЗАД или повторный SOURCE
            finish();
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
            int next = code == KeyEvent.KEYCODE_DPAD_DOWN
                    ? Math.min(rows.size() - 1, focusIdx + 1)
                    : Math.max(0, focusIdx - 1);
            rows.get(next).requestFocus();
            return true;
        }
        return super.onKeyDown(code, e);
    }

    /** Открывает вендорский экран изображения и звука (там режим, цветовая температура, аспект). */
    private void openVendorPicture() {
        try {
            Intent i = new Intent("tv.fun.settings.action.picture");
            i.setComponent(new ComponentName("tv.fun.settings",
                    "tv.fun.settings.picaudio.PicAudioSettingsActivity"));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            finish();
        } catch (Throwable ignored) {
        }
    }

    /** Служебное меню телевизора: там пункт «Дисплей» с областью экрана и разрешением. */
    private void openVendorDisplay() {
        try {
            Intent i = new Intent();
            i.setComponent(new ComponentName("com.mstar.miscsetting",
                    "com.mstar.miscsetting.activity.MainActivity"));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            finish();
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------- мелочи -------------------------------

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void saveLog() {
        try {
            FileOutputStream out = new FileOutputStream(new java.io.File(getFilesDir(), "picpanel.txt"));
            out.write(log.toString().getBytes("UTF-8"));
            out.close();
        } catch (Throwable ignored) {
        }
    }

    /** Тонкая полоска, показывающая значение. */
    private class Bar extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int v = 50;

        Bar(android.content.Context c) {
            super(c);
            p.setColor(0xFF6FC3FF);
        }

        void setValue(int val) {
            v = val;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            p.setColor(0x33FFFFFF);
            canvas.drawRoundRect(new RectF(0, 0, getWidth(), getHeight()), getHeight() / 2f, getHeight() / 2f, p);
            p.setColor(0xFF6FC3FF);
            canvas.drawRoundRect(new RectF(0, 0, getWidth() * v / 100f, getHeight()),
                    getHeight() / 2f, getHeight() / 2f, p);
        }
    }
}
