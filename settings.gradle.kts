pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "KomgaReaderPlugins"
include(":komga-lang-es", ":komga-lang-fr", ":komga-lang-it", ":komga-reader-preset-eink")
