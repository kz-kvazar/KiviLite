package com.minihome.tv;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * Перехват кнопки SOURCE на пульте.
 *
 * Кнопка приходит в Android как код 255 («неизвестная клавиша»), потому что в раскладке пульта
 * ей не назначено имя. Прошивка параллельно пытается открыть свою вендорскую панель; та программа
 * отключена, поэтому единственный обработчик — мы. Работает поверх любого экрана: служба доступности
 * видит нажатия раньше приложений.
 */
public class SourceKeyService extends AccessibilityService {

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // События экрана не нужны, работаем только с клавишами.
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != 255) return false;    // прочие клавиши не трогаем и не журналируем
        note("нажатие SOURCE, действие " + event.getAction());
        if (event.getAction() != KeyEvent.ACTION_UP) return true;   // поглощаем и нажатие, и отпускание

        if (PicturePanelActivity.visible) {     // панель уже на экране — закрываем
            PicturePanelActivity.requestClose();
        } else {
            openPanel();
        }
        return true;
    }

    /**
     * Открывает собственную панель. Раньше кнопка пыталась запускать чужой экран телевизора
     * прямо из службы — а служба работает в фоне, и такие запуски система молча съедает:
     * в журнале «открыл», на экране ничего. Свой экран запускается всегда, а уже из него
     * (когда наша программа на переднем плане) открываются вендорские меню.
     */
    private void openPanel() {
        Intent i = new Intent();
        i.setComponent(new ComponentName(getPackageName(),
                "com.minihome.tv.PicturePanelActivity"));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
            note("открыл панель изображения");
        } catch (Throwable t) {
            note("не смог открыть панель: " + t);
        }
    }

    /**
     * Открывает вендорский экран изображения и нажимает в нём верхний пункт
     * «Параметры изображения» — именно он показывает нужное всплывающее окно.
     * Окно самого пункта не экспортируется, поэтому нажать его можно только через службу доступности.
     */
    private void openPicturePopup() {
        Intent i = new Intent();
        i.setComponent(new ComponentName("tv.fun.settings",
                "tv.fun.settings.picaudio.PicAudioSettingsActivity"));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
            note("открыл экран изображения");
        } catch (Throwable t) {
            note("не смог открыть экран изображения: " + t);
            return;
        }
        new Thread(new Runnable() {
            public void run() {
                for (int attempt = 0; attempt < 20; attempt++) {
                    if (clickPictureItem()) {           // пробуем сразу, без паузы
                        note("нажал верхний пункт настроек изображения");
                        return;
                    }
                    try {
                        Thread.sleep(120);
                    } catch (InterruptedException ignored) {
                    }
                }
                note("верхний пункт не нашёлся, экран остался открытым");
            }
        }).start();
    }

    /** Ищет в текущем окне пункт про изображение и нажимает его. */
    private boolean clickPictureItem() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        try {
            java.util.List<AccessibilityNodeInfo> nodes =
                    root.findAccessibilityNodeInfosByText("Параметр");
            for (AccessibilityNodeInfo n : nodes) {
                CharSequence text = n.getText();
                if (text == null) continue;
                String s = text.toString().toLowerCase();
                if (!s.contains("изображен") && !s.contains("зображенн")) continue;
                AccessibilityNodeInfo target = n;
                while (target != null && !target.isClickable()) target = target.getParent();
                if (target != null && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
                if (n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** Короткий журнал: пишем в отдельном потоке, чтобы не тормозить доставку нажатий. */
    private void note(final String s) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    java.io.FileOutputStream out = new java.io.FileOutputStream(
                            new java.io.File(getFilesDir(), "sourcekey.txt"), true);
                    out.write(("[" + new java.util.Date().toString() + "] " + s + "\n").getBytes("UTF-8"));
                    out.close();
                } catch (Throwable ignored) {
                }
            }
        }).start();
    }
}
