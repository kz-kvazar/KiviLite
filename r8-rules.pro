# Правила R8 для релизной (ужатой) сборки MiniHome.
# Манифест ссылается на классы по именам, поэтому имена компонентов сохраняем,
# а всё неиспользуемое — вырезаем и переименовываем.

# Точки входа: манифест ссылается на них по именам, поэтому сохраняем классы целиком.
-keep public class com.minihome.tv.MainActivity { *; }
-keep public class com.minihome.tv.PicturePanelActivity { *; }
-keep public class com.minihome.tv.ApiProbeActivity { *; }
-keep public class com.minihome.tv.SourceKeyService { *; }

# Вендорский API дёргается по строкам через Class.forName — статических ссылок нет,
# поэтому достаточно не ругаться на отсутствующие в android.jar классы.
-dontwarn com.mstar.**
-dontwarn com.hisilicon.**
-dontwarn android.**

# Служебные аннотации и имена вложенных классов не важны для работы.
-dontnote **

# Дожимаем размер: разрешаем менять модификаторы доступа, переносим классы в корень
# (имена короче) и даём больше проходов оптимизации. Свою рефлексию не используем,
# поэтому переименование безопасно.
-allowaccessmodification
-repackageclasses ''
-optimizationpasses 5
-adaptclassstrings
