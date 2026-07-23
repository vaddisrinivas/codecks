# JSch resolves its Android/JCE crypto implementations from class names at
# runtime. R8 cannot see those reflective references, so keeping only the
# directly referenced API classes produces a release APK that crashes while
# opening an SSH session (for example: ClassNotFoundException:
# com.jcraft.jsch.jce.Random). Do not keep all of JSch: its optional desktop
# integrations reference Windows, Kerberos, JNA, and BouncyCastle classes that
# are intentionally not packaged in Android.
-keep class com.jcraft.jsch.jce.** { *; }
