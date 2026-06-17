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
include(
    ":komga-lang-es",
    ":komga-lang-fr",
    ":komga-lang-it",
    ":komga-reader-preset-eink",
    ":komga-eink-preset-kindle",
    ":komga-ui-pack-aurora",
    ":komga-ui-pack-sample",
    ":komga-kavita-source",
    ":komga-font-ebgaramond",
    ":komga-font-lora",
    ":komga-font-merriweather",
    ":komga-font-sourceserif",
    ":komga-font-atkinson",
    ":komga-calibre-source",
)

// Local-only YOLO panel-model plugin: gitignored (model is license-encumbered, never pushed).
// Included only when the module is present locally, so a fresh clone still builds.
if (file("komga-panel-model-yolo").exists()) include(":komga-panel-model-yolo")
