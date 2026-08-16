# Serialization models are discovered through generated serializers.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn org.conscrypt.**

# youtubedl-android loads its runtime wrappers and native assets dynamically.
-keep class com.yausername.youtubedl_android.** { *; }
-keep class com.yausername.ffmpeg.** { *; }
-keep class com.yausername.aria2c.** { *; }

# youtubedl-common uses Class.newInstance() to register ZIP extra-field handlers while
# extracting the packaged Python runtime. R8 cannot infer those reflective constructor and
# interface calls, so a fresh optimized install otherwise fails with an obfuscated
# ExtraFieldUtils initialization error before yt-dlp can start.
-keep,allowoptimization,allowobfuscation class * implements org.apache.commons.compress.archivers.zip.ZipExtraField {
    public *;
}

# WorkManager persists worker class names in its database. Keep both active
# workers and compatibility shims stable across an optimized upgrade.
-keep class * extends androidx.work.ListenableWorker { *; }
