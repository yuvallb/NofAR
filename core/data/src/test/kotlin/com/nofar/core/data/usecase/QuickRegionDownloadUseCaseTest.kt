package com.nofar.core.data.usecase

import com.nofar.core.data.prepare.RegionNamePolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickRegionDownloadUseCaseTest {
    @Test
    fun autoNameFormat_matchesRegionNamePolicy() {
        assertEquals("Region near 32.00, 35.00", RegionNamePolicy.formatAutoName(32.0, 35.0))
        assertEquals("Region near -32.10, 35.20", RegionNamePolicy.formatAutoName(-32.1, 35.2))
    }
}
