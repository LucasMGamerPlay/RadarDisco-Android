package com.radardisco.android

import com.radardisco.android.core.update.SemverComparator
import org.junit.Assert.assertEquals
import org.junit.Test

class SemverComparatorTest {

    @Test
    fun comparesStandardAndPrefixedVersionsCorrectly() {
        assertEquals(0, SemverComparator.compareSemverVersions("v1.0.0", "1.0.0"))
        assertEquals(1, SemverComparator.compareSemverVersions("1.2.0", "1.1.9"))
        assertEquals(-1, SemverComparator.compareSemverVersions("1.0.0", "v1.0.1"))
        assertEquals(1, SemverComparator.compareSemverVersions("v2.0.0-beta.1", "1.9.9"))
        assertEquals(0, SemverComparator.compareSemverVersions("1.4", "1.4.0"))
    }
}
