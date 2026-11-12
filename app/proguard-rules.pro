# sora-editor and its TextMate engine (tm4e, joni, jcodings) load classes reflectively.
-keep class io.github.rosemoe.sora.** { *; }
-keep class org.eclipse.tm4e.** { *; }
-keep class org.joni.** { *; }
-keep class org.jcodings.** { *; }
-dontwarn org.eclipse.tm4e.**
-dontwarn org.joni.**
-dontwarn org.jcodings.**

# Termux terminal: JNI entry points.
-keep class com.termux.terminal.JNI { *; }

# commons-compress optional codecs that are not used.
-dontwarn org.apache.commons.compress.**
-dontwarn org.tukaani.xz.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn org.objectweb.asm.**

# JGit: optional features that are not on Android (Kerberos, JMX, SSH agents) and its logging facade.
-keep class org.eclipse.jgit.** { *; }
-dontwarn org.eclipse.jgit.**
-dontwarn org.ietf.jgss.**
-dontwarn javax.management.**
-dontwarn java.lang.management.**
-dontwarn org.slf4j.**
-dontwarn com.googlecode.javaewah**

# OkHttp optional platforms.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Referenced by sora-editor (ShareableData) through Kotlin interface default methods.
-dontwarn kotlin.Cloneable$DefaultImpls
