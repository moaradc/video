# Mono 混淆规则：保持自身代码（含 JS 桥）完整，仅压缩第三方库
-keep class com.moaradc.mono.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-dontwarn org.checkerframework.**
-dontwarn javax.annotation.**
