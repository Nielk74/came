package com.nielk74.came.photo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FilmExifMetadataTest {
    @Test
    fun encodedFilterMetadataRoundTrips() {
        assertEquals(
            "portra400",
            filmFilterIdFromUserComment(filmFilterUserComment("portra400")),
        )
        assertEquals(
            "Portra 400",
            filmFilterNameFromDescription(filmFilterDescription("Portra 400")),
        )
    }

    @Test
    fun unrelatedOrEmptyMetadataIsNotClaimedAsAFilter() {
        assertNull(filmFilterIdFromUserComment(null))
        assertNull(filmFilterIdFromUserComment("A comment from another editor"))
        assertNull(filmFilterIdFromUserComment("came:film-id=  "))
        assertNull(filmFilterNameFromDescription("Holiday photograph"))
        assertNull(filmFilterNameFromDescription("Film filter:  "))
    }

    @Test
    fun recordedNameWinsAndStableIdRepairsAMissingDescription() {
        assertEquals(
            "Recorded Portra",
            resolvedFilmFilterName(
                description = "Film filter: Recorded Portra",
                userComment = "came:film-id=portra400",
                profileNameForId = { "Current catalog name" },
            ),
        )
        assertEquals(
            "Portra 400",
            resolvedFilmFilterName(
                description = null,
                userComment = "came:film-id=portra400",
                profileNameForId = { id -> if (id == "portra400") "Portra 400" else null },
            ),
        )
        assertNull(
            resolvedFilmFilterName(
                description = "Unrelated description",
                userComment = "Unrelated comment",
                profileNameForId = { "Unexpected" },
            ),
        )
    }
}
