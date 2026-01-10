# Сжимаем ресурсы
-repackageclasses ''
-allowaccessmodification
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*

# Сохраняем только точки входа (Activity, Service)
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver

# Сохраняем методы, которые вызываются из C-кода (JNI)
-keepclasseswithmembernames class * {
    native <methods>;
}

# Удаляем всю отладочную информацию (номера строк, имена файлов)
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
