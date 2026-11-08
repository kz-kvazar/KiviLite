package com.minihome.tv;

import android.app.Activity;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Разовый зонд: выгружает в /sdcard/minihome_api.txt список методов вендорского API
 * управления изображением, чтобы писать панель по реальным сигнатурам, а не по догадкам.
 */
public class ApiProbeActivity extends Activity {

    private final StringBuilder sb = new StringBuilder();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sb.append("=== зонд API изображения ===\n");
        dumpRoot();
        dumpClass("com.mstar.android.tv.TvPictureManager");
        Object pm = pictureManager();
        Object tvpm = tvPictureManager();
        sb.append("TvPictureManager (tvapi) = ").append(tvpm == null ? "нет доступа" : tvpm.getClass().getName()).append("\n");
        if (tvpm != null) {
            for (String g : new String[]{"getBrightness", "getContrast", "getBacklight", "getPictureMode",
                    "getSaturation", "getSharpness", "getColorTemperature"}) {
                try {
                    sb.append("  ").append(g).append(" = ")
                            .append(tvpm.getClass().getMethod(g).invoke(tvpm)).append("\n");
                } catch (Throwable t) {
                    sb.append("  ").append(g).append(" -> ").append(t.getClass().getSimpleName()).append("\n");
                }
            }
        }
        dumpBacklight(pm);
        String before = dumpDb("до изменений");
        testWrite(pm, before);
        dumpDb("после изменений");
        try {
            // Пишем в свой каталог: у приложения нет разрешения на /sdcard.
            java.io.File f = new java.io.File(getFilesDir(), "api.txt");
            FileOutputStream out = new FileOutputStream(f);
            out.write(sb.toString().getBytes("UTF-8"));
            out.close();
        } catch (Throwable t) {
            sb.append("не записалось: ").append(t).append("\n");
        }
        finish();
    }

    private void dumpClass(String name) {
        sb.append("\n--- класс ").append(name).append(" ---\n");
        try {
            Class<?> c = Class.forName(name);
            for (Method m : c.getMethods()) {
                if (m.getDeclaringClass() == Object.class) continue;
                sb.append(Modifier.toString(m.getModifiers())).append(' ')
                        .append(m.getReturnType().getSimpleName()).append(' ')
                        .append(m.getName()).append('(');
                Class<?>[] ps = m.getParameterTypes();
                for (int i = 0; i < ps.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(ps[i].getSimpleName());
                }
                sb.append(")\n");
            }
        } catch (Throwable t) {
            sb.append("ошибка: ").append(t).append("\n");
        }
    }

    private void dumpInstance() {
        sb.append("\n--- живой экземпляр ---\n");
        try {
            Class<?> tm = Class.forName("com.mstar.android.tvapi.common.TvManager");
            Object t = tm.getMethod("getInstance").invoke(null);
            sb.append("TvManager.getInstance() = ").append(t).append("\n");
            Object pm = tm.getMethod("getPictureManager").invoke(t);
            sb.append("getPictureManager() = ").append(pm == null ? "null" : pm.getClass().getName()).append("\n");
            if (pm != null) {
                for (String g : new String[]{"getBrightness", "getContrast", "getBacklight",
                        "getSaturation", "getSharpness", "getPictureMode", "getColorTemprature"}) {
                    try {
                        Object v = pm.getClass().getMethod(g).invoke(pm);
                        sb.append("  ").append(g).append(" = ").append(v).append("\n");
                    } catch (Throwable t2) {
                        sb.append("  ").append(g).append(" -> ").append(t2.getClass().getSimpleName())
                                .append(' ').append(t2.getMessage()).append("\n");
                    }
                }
            }
        } catch (Throwable t) {
            sb.append("ошибка: ").append(t).append("\n");
        }
    }

    private Object pictureManager() {
        try {
            Class<?> tm = Class.forName("com.mstar.android.tvapi.common.TvManager");
            Object t = tm.getMethod("getInstance").invoke(null);
            return tm.getMethod("getPictureManager").invoke(t);
        } catch (Throwable t) {
            return null;
        }
    }

    private Object tvPictureManager() {
        try {
            Class<?> c = Class.forName("com.mstar.android.tv.TvPictureManager");
            return c.getMethod("getInstance").invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Проверяет, даёт ли телевизор root нашему приложению: от этого зависит запуск закрытых окон. */
    private void dumpRoot() {
        sb.append("\n--- root для приложения ---\n");
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
            java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream()), 256);
            String line;
            while ((line = r.readLine()) != null) sb.append("su: ").append(line).append('\n');
            r.close();
            p.waitFor();
            sb.append("код возврата: ").append(p.exitValue()).append('\n');
        } catch (Throwable t) {
            sb.append("su недоступен: ").append(t).append('\n');
        }
    }

    private void dumpBacklight(Object pm) {
        sb.append("\n--- подсветка ---\n");
        if (pm == null) {
            sb.append("нет менеджера\n");
            return;
        }
        for (String m : new String[]{"getBacklight", "getBacklightMinValue", "getBacklightMaxValue"}) {
            try {
                sb.append(m).append(" = ").append(pm.getClass().getMethod(m).invoke(pm)).append("\n");
            } catch (Throwable t) {
                sb.append(m).append(" -> ").append(t.getClass().getSimpleName()).append("\n");
            }
        }
    }

    /** Читает базу настроек изображения; возвращает текущую яркость, чтобы потом вернуть её назад. */
    private String dumpDb(String title) {
        sb.append("\n--- база настроек (").append(title).append(") ---\n");
        String bright = null;
        String[] uris = {
                "content://mstar.tv.usersetting/picmode_setting/inputsrc/",
                "content://mstar.tv.usersetting/picmode_setting",
                "content://mstar.tv.usersetting/"
        };
        for (String u : uris) {
            try {
                Cursor c = getContentResolver().query(Uri.parse(u), null, null, null, null);
                if (c == null) {
                    sb.append(u).append(" -> нет ответа\n");
                    continue;
                }
                sb.append(u).append(" -> строк ").append(c.getCount()).append("\n");
                if (c.moveToFirst()) {
                    StringBuilder line = new StringBuilder();
                    for (int i = 0; i < c.getColumnCount(); i++) {
                        String name = c.getColumnName(i);
                        String val = c.getString(i);
                        if (line.length() < 700) line.append(name).append('=').append(val).append(' ');
                        if (name != null && name.toLowerCase().contains("bright")) bright = val;
                    }
                    sb.append("   ").append(line).append("\n");
                } else {
                    sb.append("   колонки: ").append(java.util.Arrays.toString(c.getColumnNames())).append("\n");
                }
                c.close();
            } catch (Throwable t) {
                sb.append(u).append(" -> ошибка ").append(t).append("\n");
            }
        }
        return bright;
    }

    /** Проверяет, доходят ли значения до телевизора, и возвращает яркость как было. */
    private void testWrite(Object pm, String beforeBrightness) {
        sb.append("\n--- проверка записи ---\n");
        if (pm == null) {
            sb.append("нет менеджера, пропускаю\n");
            return;
        }
        int probe = 55;
        try {
            pm.getClass().getMethod("setPictureModeBrightness", short.class).invoke(pm, (short) probe);
            sb.append("setPictureModeBrightness(").append(probe).append(") — вызвано\n");
        } catch (Throwable t) {
            sb.append("setPictureModeBrightness -> ").append(t).append("\n");
        }
        try {
            pm.getClass().getMethod("setPictureModeContrast", short.class).invoke(pm, (short) probe);
            sb.append("setPictureModeContrast(").append(probe).append(") — вызвано\n");
        } catch (Throwable t) {
            sb.append("setPictureModeContrast -> ").append(t).append("\n");
        }
        try {
            int back = beforeBrightness == null ? 50 : (int) Double.parseDouble(beforeBrightness);
            pm.getClass().getMethod("setPictureModeBrightness", short.class).invoke(pm, (short) back);
            sb.append("яркость возвращена на ").append(back).append("\n");
        } catch (Throwable t) {
            sb.append("возврат яркости -> ").append(t).append("\n");
        }
    }

    private void dumpServiceManager() {
        sb.append("\n--- служба TvPictureManager через ServiceManager ---\n");
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            Object binder = sm.getMethod("getService", String.class).invoke(null, "TvPictureManager");
            sb.append("binder = ").append(binder).append("\n");
        } catch (Throwable t) {
            sb.append("ошибка: ").append(t).append("\n");
        }
    }
}
