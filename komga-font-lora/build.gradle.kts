plugins {
    id("com.android.application")
}
android {
    namespace = "com.komgareader.font.lora"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.komgareader.font.lora"
        minSdk = 28
        targetSdk = 34
        versionCode = (project.findProperty("pluginVersionCode") as String).toInt()
        versionName = project.findProperty("pluginVersionName") as String
    }
}
