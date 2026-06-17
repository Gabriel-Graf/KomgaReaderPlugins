package com.komgareader.plugin.calibre

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowsePagingTest {

    @Test fun `pages within series range use SERIES phase`() {
        // seriesTotal=5, pageSize=2 -> series pages 1..3
        assertEquals(BrowsePaging.Slice(BrowsePaging.Phase.SERIES, 0), BrowsePaging.slice(1, 2, 5))
        assertEquals(BrowsePaging.Slice(BrowsePaging.Phase.SERIES, 4), BrowsePaging.slice(3, 2, 5))
    }

    @Test fun `pages past series range use STANDALONE phase with reset offset`() {
        // series pages 1..3 (seriesTotal=5,size=2); page 4 -> standalone offset 0; page 5 -> offset 2
        assertEquals(BrowsePaging.Slice(BrowsePaging.Phase.STANDALONE, 0), BrowsePaging.slice(4, 2, 5))
        assertEquals(BrowsePaging.Slice(BrowsePaging.Phase.STANDALONE, 2), BrowsePaging.slice(5, 2, 5))
    }

    @Test fun `hasNext spans the series-to-standalone boundary`() {
        // last series page (3) still has standalone -> true
        assertTrue(BrowsePaging.hasNext(page = 3, pageSize = 2, seriesTotal = 5, standaloneTotal = 3))
        // last standalone page (5): offset 2 + page 1 item == 3 -> no more
        assertFalse(BrowsePaging.hasNext(page = 5, pageSize = 2, seriesTotal = 5, standaloneTotal = 3))
        // no standalone at all, last series page -> false
        assertFalse(BrowsePaging.hasNext(page = 3, pageSize = 2, seriesTotal = 5, standaloneTotal = 0))
    }
}
