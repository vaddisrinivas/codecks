# AndroidJUnitRunner discovers test classes and @Test methods reflectively.
# Keep only Codecks instrumentation tests; production shrinker rules stay
# independent and fully optimized.
-keep class io.codecks.**Test {
    *;
}
-keep class io.codecks.**InstrumentedTest {
    *;
}

# Error Prone annotations are compile-time metadata. Their javax.lang.model
# member types do not exist on Android and are never read by instrumentation.
-dontwarn javax.lang.model.element.Modifier
-dontwarn com.google.errorprone.annotations.IncompatibleModifiers
