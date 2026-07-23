# JSch resolves SSH algorithms from string configuration at runtime. R8 cannot
# see those reflective references, so release APKs can crash or fail connection
# setup with ClassNotFoundException for classes such as:
# - com.jcraft.jsch.jce.Random
# - com.jcraft.jsch.DHEC256
#
# Keep the Android-relevant algorithm surface only. Do not keep all of JSch:
# optional desktop integrations reference Windows, Kerberos, JNA, and
# BouncyCastle classes that are intentionally not packaged in Android.
-keep class com.jcraft.jsch.DH* { *; }
-keep class com.jcraft.jsch.ECDH { *; }
-keep class com.jcraft.jsch.XDH { *; }
-keep class com.jcraft.jsch.Signature* { *; }
-keep class com.jcraft.jsch.KeyPairGen* { *; }
-keep class com.jcraft.jsch.UserAuthNone { *; }
-keep class com.jcraft.jsch.UserAuthPassword { *; }
-keep class com.jcraft.jsch.UserAuthPublicKey { *; }
-keep class com.jcraft.jsch.UserAuthKeyboardInteractive { *; }
-keep class com.jcraft.jsch.jce.** { *; }
-keep class com.jcraft.jsch.jzlib.** { *; }
