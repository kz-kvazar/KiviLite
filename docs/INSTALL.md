# Установка KiviLite и привязка кнопки HOME

Пошаговая инструкция, проверенная на LG 43UK32G (MStar, Android 6.0.1, API 23).
Подойдёт для любой приставки/ТВ на Android 5–7, где вендорский лаунчер можно отключить.

## Что понадобится

- Телевизор или приставка с Android 5–7 и включённой отладкой по ADB
  (по сети: `adb connect <ip>:5555`, либо по USB).
- **root** — нужен только для того, чтобы отключить вендорский лаунчер
  (`su root pm disable`). Без root лаунчер тоже работает, но кнопка HOME может
  остаться за вендорской оболочкой.
- Для сборки из исходников: JDK 8+ и Android SDK build-tools (см. `../README.md`).

## 1. Сборка или готовый APK

```powershell
# в каталоге проекта, пути к JDK/SDK/ключу правятся в начале build.ps1
powershell -ExecutionPolicy Bypass -File build.ps1
# → MiniHome.apk
```

Либо возьмите `MiniHome.apk` из раздела Releases.

## 2. Установка

```bash
A=192.168.1.214:5555            # адрес вашего ТВ
adb connect $A
adb -s $A install -r MiniHome.apk
adb -s $A shell am start -n com.minihome.tv/.MainActivity
```

Лаунчер должен нарисоваться сразу — шапка с часами, шесть плиток, сетка приложений.
Если экран пустой, посмотрите `adb -s $A logcat -d | grep -i minihome`.

## 3. Назначение основным (и привязка кнопки HOME)

`settings put global home_activity` **сам по себе на этой прошивке не работает**:
значение сохраняется, но кнопка HOME продолжает открывать старый лаунчер.
Рабочая последовательность:

```bash
# 3.1 Отключить вендорский лаунчер (именно отключить, не удалять)
adb -s $A shell "su root pm disable --user 0 com.kivi.launcher_v2"
adb -s $A shell "su root pm disable --user 0 com.wezom.kivilauncher"   # если есть

# 3.2 Прописать домашнюю активность
adb -s $A shell "settings put global home_activity com.minihome.tv/.MainActivity"

# 3.3 Перезагрузить
adb -s $A shell "su root reboot"
```

Проверка после загрузки:

```bash
adb -s $A shell "dumpsys activity activities | grep -i category.HOME"
adb -s $A shell "input keyevent 3"        # HOME должен открыть KiviLite
adb -s $A shell "settings get global home_activity"
```

Откат (лаунчер остаётся установленным, вернуться можно в любой момент):

```bash
adb -s $A shell "su root pm enable --user 0 com.kivi.launcher_v2"
adb -s $A shell "su root reboot"
```

## 4. Флаг заставки загрузки (иначе «вечная загрузка»)

Ром держит заставку, пока не увидит `service.launcher.visible=1` — его ставит
**его собственный** лаунчер, а сторонний не ставит. Симптом: система уже загружена
(`uptime` идёт, ADB отвечает), а на экране крутится анимация.

```bash
adb -s $A shell "su root setprop service.launcher.visible 1"   # применить сейчас
```

Чтобы не повторялось после каждой загрузки — одна строка в любом скрипте автозапуска:

```sh
# /data/tv-tune.sh, запускается из /system/bin/install-recovery.sh
(
  i=0
  while [ "$(getprop sys.boot_completed)" != "1" ] && [ $i -lt 180 ]; do sleep 1; i=$((i+1)); done
  sleep 3
  setprop service.launcher.visible 1
) &
```

## 5. Кнопка SOURCE и панель изображения

Кнопка SOURCE приходит в Android как код 255, поэтому её ловит служба доступности
`SourceKeyService` (она видит клавиши раньше приложений и работает поверх любого экрана).

- Лаунчер включает службу сам при запуске (`ensureSourceKeyEnabled`) — для этого в
  манифесте запрошено `WRITE_SECURE_SETTINGS`.
- Если права не выданы, включите вручную: **Настройки → Специальные возможности →
  «Перехват кнопки выбора источника»**.
- Проверка: нажмите SOURCE — поверх текущего экрана появится панель изображения.
- Вендорскую панель лучше держать отключённой, иначе обработчиков будет два.

## 6. Возможные проблемы

| Симптом | Причина | Решение |
|---|---|---|
| Кнопка HOME открывает старый лаунчер | `home_activity` игнорируется, пока вендорский лаунчер включён | шаг 3: отключить вендорский лаунчер + перезагрузка |
| После обновления APK перестала работать кнопка SOURCE | Android отключает службы доступности при обновлении приложения (сама служба включить себя не может: `WRITE_SECURE_SETTINGS` подписи платформы у неё нет) | включить заново: `settings put secure enabled_accessibility_services com.minihome.tv/com.minihome.tv.SourceKeyService` и `settings put secure accessibility_enabled 1`, либо вручную в Настройки → Специальные возможности |
| Бесконечная анимация загрузки | не выставлен `service.launcher.visible` | шаг 4 |
| Приложения в сетке не открываются по ОК | у дочерних карточек перехвачен фокус (актуально при правке кода) | вешать `OnClickListener` на саму карточку в `getView()` |
| Долгое удержание ОК не открывает меню | вендорский слой перехватывает удержание | режим **«Изменить»** или кнопка MENU |
| `adb pull` не находит файл | POSIX-путь | указывайте нативный путь: `adb pull /sdcard/x.png C:/Users/you/x.png` |
| Нет погоды и курсов | нет доступа в интернет или сервис молчит | значения просто остаются последними; проверьте `adb shell ping -c1 api.open-meteo.com` |

## 7. Полное удаление

```bash
adb -s $A shell "su root pm enable --user 0 com.kivi.launcher_v2"   # вернуть вендорский
adb -s $A shell "su root reboot"
adb -s $A uninstall com.minihome.tv
```
