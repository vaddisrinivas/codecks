# JSch resolves SSH algorithms from string configuration at runtime. R8 cannot
# see those reflective references, so release APKs can crash or fail connection
# setup with ClassNotFoundException or IllegalAccessException for classes such as:
# - com.jcraft.jsch.jce.Random
# - com.jcraft.jsch.DHEC256
#
# JSch stores these implementation names in its runtime configuration, then
# constructs them reflectively. Preserve only those configured classes and
# their no-argument constructors; all other members remain shrinkable and
# optimizable.
-keep,allowoptimization class com.jcraft.jsch.CipherNone {
    <init>();
}
-keep,allowoptimization class com.jcraft.jsch.DH* {
    <init>();
}
-keep,allowoptimization class com.jcraft.jsch.UserAuthNone {
    <init>();
}
-keep,allowoptimization class com.jcraft.jsch.UserAuthPassword {
    <init>();
}
-keep,allowoptimization class com.jcraft.jsch.UserAuthPublicKey {
    <init>();
}
-keep,allowoptimization class com.jcraft.jsch.UserAuthKeyboardInteractive {
    <init>();
}
-keep,allowoptimization class com.jcraft.jsch.jbcrypt.JBCrypt {
    <init>();
}
-keep,allowoptimization class com.jcraft.jsch.jce.** {
    <init>();
}
-keep,allowoptimization class com.jcraft.jsch.jzlib.Compression {
    <init>();
}

# AndroidJUnitRunner is loaded into the tested app process and calls this class
# from the target APK. Keep this one cross-APK API; all other AndroidX code
# remains governed by library consumer rules.
-keep,allowoptimization class androidx.tracing.Trace {
    public *;
}

# AndroidX Test's storage bootstrap calls Kotlin's lazy() facade from the test
# APK. Keep only that cross-APK facade and its generated implementation parts.
-keep,allowoptimization class kotlin.LazyKt {
    public *;
}
-keep,allowoptimization class kotlin.LazyKt__* {
    public *;
}
