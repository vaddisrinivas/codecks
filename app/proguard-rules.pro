# JSch resolves SSH algorithms from string configuration at runtime. R8 cannot
# see those reflective references, so release APKs can crash or fail connection
# setup with ClassNotFoundException or IllegalAccessException for classes such as:
# - com.jcraft.jsch.jce.Random
# - com.jcraft.jsch.DHEC256
#
# Keep JSch package structure/names intact for any classes R8 keeps. Several
# JSch algorithm classes are package-private; if R8 keeps those classes but
# obfuscates package siblings, release builds fail with IllegalAccessException
# during SSH key exchange. This still allows unused optional desktop/BouncyCastle
# integrations to shrink away.
-keepnames class com.jcraft.jsch.** { *; }
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
