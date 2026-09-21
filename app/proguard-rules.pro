-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-keep class pro.sketchware.** { *; }
-keep class a.a.a.** { *; }
-keep class com.besome.sketch.** { *; }
-keep class mod.** { *; }

-keep class * implements android.os.Parcelable { *; }
-keep class * extends android.app.Activity { *; }
-keep class * extends android.app.Service { *; }
-keep class * extends android.content.BroadcastReceiver { *; }
-keep class * extends android.app.Application { *; }

-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keep class * {
    native <methods>;
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep class com.google.gson.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

-keep class io.github.rosemoe.sora.** { *; }

-keep class com.google.firebase.** { *; }

-keep class pro.sketchware.plugins.** { *; }
-keep class pro.sketchware.debugger.** { *; }
-keep class pro.sketchware.lsp.** { *; }
-keep class pro.sketchware.kmp.** { *; }
-keep class pro.sketchware.metrics.** { *; }
-keep class pro.sketchware.ai.** { *; }

-keep class kellinwood.** { *; }

-dontwarn com.google.errorprone.**
-dontwarn javax.xml.stream.**
-dontwarn org.codehaus.stax2.**
