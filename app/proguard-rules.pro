# Calendo — add rules when enabling minify for release builds.

-keepclassmembers class * {
    @com.google.api.client.util.Key <fields>;
}
-dontwarn com.google.api.client.extensions.android.**
-dontwarn com.google.android.gms.**
