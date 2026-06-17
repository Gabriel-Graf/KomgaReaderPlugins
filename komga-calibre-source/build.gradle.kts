plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.komgareader.plugin.calibre"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.komgareader.plugin.calibre"
        minSdk = 28
        targetSdk = 34
        versionCode = (project.findProperty("pluginVersionCode") as String).toInt()
        versionName = project.findProperty("pluginVersionName") as String
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    // Plugin contract (compileOnly: the host supplies it at runtime). MUST stay compileOnly —
    // ClassCastException on load. Vendored shaded jar (plugin-api + source-api + domain), see libs/.
    compileOnly(files(rootProject.file("libs/plugin-sdk-0.1.0.jar")))

    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation(libs.retrofit.serialization.converter)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    // Make the compileOnly types available to unit tests (host doesn't supply them on the JVM).
    testImplementation(files(rootProject.file("libs/plugin-sdk-0.1.0.jar")))
}
