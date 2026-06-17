package com.komgareader.plugin.calibre

import com.komgareader.domain.source.BrowsableSource
import com.komgareader.plugin.ConfigField
import com.komgareader.plugin.ConfigSchema
import com.komgareader.plugin.FieldType
import com.komgareader.plugin.PluginMetadata
import com.komgareader.plugin.SourcePlugin
import com.komgareader.plugin.calibre.client.buildCalibreClient
import kotlinx.coroutines.runBlocking

/**
 * Entry point of the Calibre source plugin. The host instantiates this via reflection
 * (`getDeclaredConstructor().newInstance()`), so a public no-arg constructor is required.
 *
 * config keys: "url" (Content Server base URL), "username"/"password" (optional Basic-Auth),
 * "library" (optional; blank → server's default_library).
 */
class CalibreSourcePlugin : SourcePlugin {

    override val metadata: PluginMetadata = PluginMetadata(displayName = "Calibre")

    override fun configSchema(): ConfigSchema = ConfigSchema(
        fields = listOf(
            ConfigField(key = "url", label = "Server-URL", type = FieldType.URL, required = true, default = ""),
            ConfigField(key = "username", label = "Benutzername", type = FieldType.TEXT, required = false, default = ""),
            ConfigField(key = "password", label = "Passwort", type = FieldType.SECRET, required = false, default = ""),
            ConfigField(key = "library", label = "Bibliothek", type = FieldType.TEXT, required = false, default = ""),
        ),
    )

    override fun create(config: Map<String, String>): BrowsableSource {
        val url = config["url"]?.trim()?.trimEnd('/')?.ifBlank { null }
            ?: throw IllegalArgumentException("Calibre plugin: 'url' missing")
        val username = config["username"].orEmpty().trim()
        val password = config["password"].orEmpty()
        val api = buildCalibreClient(baseUrl = "$url/", username = username, password = password)

        val library = config["library"]?.trim()?.ifBlank { null }
            ?: runBlocking { runCatching { api.libraryInfo().default_library }.getOrNull() }
            ?: throw IllegalArgumentException("Calibre plugin: cannot resolve library")

        return CalibreSource(api = api, baseUrl = url, library = library, name = "Calibre @ ${hostOf(url)}")
    }

    private fun hostOf(url: String): String = try {
        java.net.URL(url).host
    } catch (_: Exception) { url }
}
