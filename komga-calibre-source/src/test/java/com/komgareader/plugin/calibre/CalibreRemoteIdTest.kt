package com.komgareader.plugin.calibre

import org.junit.Assert.assertEquals
import org.junit.Test

class CalibreRemoteIdTest {

    @Test fun `series round-trips a name with slash and quotes`() {
        val name = "Berserk / \"Deluxe\""
        val rid = CalibreRemoteId.forSeries(name)
        assertEquals(false, rid.contains('/'))
        assertEquals(CalibreRemoteId.Parsed.Series(name), CalibreRemoteId.decode(rid))
    }

    @Test fun `book round-trips an id`() {
        val rid = CalibreRemoteId.forBook("123")
        assertEquals(false, rid.contains('/'))
        assertEquals(CalibreRemoteId.Parsed.Book("123"), CalibreRemoteId.decode(rid))
    }
}
