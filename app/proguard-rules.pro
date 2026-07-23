# Serialization models are discovered through generated serializers.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn org.conscrypt.**

# youtubedl-android loads its runtime wrappers and native assets dynamically.
-keep class com.yausername.youtubedl_android.** { *; }
-keep class com.yausername.ffmpeg.** { *; }
-keep class com.yausername.aria2c.** { *; }

# WorkManager persists worker class names in its database. Keep both active
# workers and compatibility shims stable across an optimized upgrade.
-keep class * extends androidx.work.ListenableWorker { *; }
