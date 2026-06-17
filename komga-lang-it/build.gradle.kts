plugins {
    id("com.android.application")
}
android {
    namespace = "com.komgareader.lang.it"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.komgareader.lang.it"
        minSdk = 28
        targetSdk = 34
        versionCode = (project.findProperty("pluginVersionCode") as String).toInt()
        versionName = project.findProperty("pluginVersionName") as String
    }
}
