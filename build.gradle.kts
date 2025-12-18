plugins {
    // Kotlin Multiplatform
    alias(libs.plugins.kotlinMultiplatform) apply false

    // Android
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false

    // Compose Multiplatform
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
}
