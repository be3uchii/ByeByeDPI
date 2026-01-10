# Общие оптимизации
-repackageclasses ''
-allowaccessmodification
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*

# Сохраняем точки входа
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver

# КРИТИЧНО: Не переименовывать классы, содержащие нативные методы
# Иначе C++ код не сможет их найти и приложение упадет
-keepclasseswithmembernames class * {
    native <methods>;
}

# КРИТИЧНО: Явно сохраняем классы с JNI (на всякий случай)
-keep class io.github.dovecoteescapee.byedpi.core.** { *; }
-keep class io.github.dovecoteescapee.byedpi.services.** { *; }

# Удаляем отладочную инфу
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}
