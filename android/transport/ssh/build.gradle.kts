plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:common"))
    api(project(":core:security"))
    api(project(":domain:actions"))
    api(project(":domain:targets"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.sshj)

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
