plugins {
    id("com.android.application")
}
android {
    namespace = "com.komgareader.lang.fr"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.komgareader.lang.fr"
        minSdk = 28
        targetSdk = 34
        versionCode = (project.findProperty("pluginVersionCode") as String).toInt()
        versionName = project.findProperty("pluginVersionName") as String
    }
}
