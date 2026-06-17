package com.komgareader.plugin.calibre

import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Opaque, Base64URL-encoded remote-ids. The app threads remoteIds as single nav-path
 * segments, so they must not contain '/'. A tag character distinguishes series from standalone book.
 *
 * Format: Base64URL(tag + space + value), where tag is 'S' (series) or 'B' (book),
 * followed by a literal space, then the value (may contain spaces).
 */
object CalibreRemoteId {

    sealed interface Parsed {
        data class Series(val name: String) : Parsed
        data class Book(val id: String) : Parsed
    }

    fun forSeries(name: String): String = encode("S", name)
    fun forBook(bookId: String): String = encode("B", bookId)

    fun decode(remoteId: String): Parsed {
        val raw = String(Base64.getUrlDecoder().decode(remoteId), StandardCharsets.UTF_8)
        val tag = raw.substring(0, 1)
        val value = raw.substring(2) // on-wire format is "<tag><space><value>", so skip 2 chars
        return when (tag) {
            "S" -> Parsed.Series(value)
            "B" -> Parsed.Book(value)
            else -> throw IllegalArgumentException("Unknown Calibre remote-id tag: '$tag'")
        }
    }

    private fun encode(tag: String, value: String): String {
        val raw = "$tag $value".toByteArray(StandardCharsets.UTF_8)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
    }
}
