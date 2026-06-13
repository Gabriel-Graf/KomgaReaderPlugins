package com.komgareader.plugin.kavita

import com.komgareader.domain.source.BrowsableSource
import com.komgareader.plugin.ConfigField
import com.komgareader.plugin.ConfigSchema
import com.komgareader.plugin.FieldType
import com.komgareader.plugin.PluginMetadata
import com.komgareader.plugin.SourcePlugin
import com.komgareader.plugin.kavita.client.buildKavitaClient

/**
 * Entry-Point des Kavita-Quellen-Plugins.
 *
 * Der Host instanziiert diese Klasse über Reflection:
 *   `getDeclaredConstructor().newInstance()`
 * Daher ist ein **öffentlicher, parameterloser Konstruktor** zwingend erforderlich.
 *
 * Der vollständige Klassenname `com.komgareader.plugin.kavita.KavitaSourcePlugin`
 * ist im AndroidManifest unter `com.komgareader.plugin.SOURCE` deklariert.
 *
 * config-Schlüssel:
 *   - "url"    → Basis-URL des Kavita-Servers (z.B. "http://192.168.1.10:5000")
 *   - "apiKey" → API-Schlüssel aus Kavita → Einstellungen → API-Schlüssel
 */
class KavitaSourcePlugin : SourcePlugin {

    // Öffentlicher No-Arg-Konstruktor ist implizit durch Kotlin, aber explizit dokumentiert.

    override val metadata: PluginMetadata = PluginMetadata(displayName = "Kavita")

    override fun configSchema(): ConfigSchema = ConfigSchema(
        fields = listOf(
            ConfigField(
                key = "url",
                label = "Server-URL",
                type = FieldType.URL,
                required = true,
                default = "",
            ),
            ConfigField(
                key = "apiKey",
                label = "API-Schlüssel",
                type = FieldType.SECRET,
                required = true,
                default = "",
            ),
        ),
    )

    /**
     * Erzeugt eine [KavitaSource] aus den Nutzereingaben.
     *
     * Erwartet:
     *   - config["url"]    → Basis-URL (Pflicht, nicht leer)
     *   - config["apiKey"] → API-Schlüssel (Pflicht, nicht leer)
     *
     * @throws IllegalArgumentException wenn Pflichtfelder fehlen.
     */
    override fun create(config: Map<String, String>): BrowsableSource {
        val url = config["url"]?.trimEnd('/')?.ifBlank { null }
            ?: throw IllegalArgumentException("Kavita-Plugin: 'url' fehlt in der Konfiguration")
        val apiKey = config["apiKey"]?.ifBlank { null }
            ?: throw IllegalArgumentException("Kavita-Plugin: 'apiKey' fehlt in der Konfiguration")

        val api = buildKavitaClient(
            baseUrl = url,
            apiKey = apiKey,
            pluginName = "Komga-Reader",
        )

        return KavitaSource(
            api = api,
            baseUrl = url,
            apiKey = apiKey,
            name = "Kavita @ ${extractHost(url)}",
        )
    }

    // ------------------------------------------------------------------
    // Hilfsmethode
    // ------------------------------------------------------------------

    /** Extrahiert den Host aus einer URL für einen lesbaren Source-Namen. */
    private fun extractHost(url: String): String = try {
        java.net.URL(url).host
    } catch (_: Exception) {
        url
    }
}
