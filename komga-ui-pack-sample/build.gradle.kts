plugins {
    id("com.android.application")
}
android {
    namespace = "com.komgareader.uipack.sample"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.komgareader.uipack.sample"
        minSdk = 28
        targetSdk = 34
        versionCode = (project.findProperty("pluginVersionCode") as String).toInt()
        versionName = project.findProperty("pluginVersionName") as String
    }
}
