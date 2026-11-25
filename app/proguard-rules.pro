# Third-party native wrappers are loaded through JNI and reflection.
-keep class org.libtorrent4j.** { *; }
-keep class com.arthenica.ffmpegkit.** { *; }
-keep class dev.ffmpegkit_maintained.ytdlp.** { *; }
-keepclasseswithmembernames class * { native <methods>; }
-dontwarn org.libtorrent4j.**
-dontwarn com.arthenica.ffmpegkit.**
-dontwarn dev.ffmpegkit_maintained.**
