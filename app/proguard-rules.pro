# Lindungi engine yt-dlp, common utils, dan FFmpeg
-keep class com.yausername.** { *; }
-dontwarn com.yausername.**

# Lindungi Apache Commons Compress (dipakai ZipUtils.unzip saat ekstrak Python & FFmpeg)
-keep class org.apache.commons.compress.** { *; }
-dontwarn org.apache.commons.compress.**

# Lindungi paket aplikasi utama
-keep class com.zeinatsu.aiovideodownloader.** { *; }

# Pertahankan metadata refleksi & anotasi
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions
-dontwarn org.slf4j.**
-dontwarn javax.naming.**
-dontwarn java.lang.management.**
