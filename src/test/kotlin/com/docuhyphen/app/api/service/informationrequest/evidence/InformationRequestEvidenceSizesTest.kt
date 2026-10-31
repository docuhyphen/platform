package com.docuhyphen.app.api.service.informationrequest.evidence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InformationRequestEvidenceSizesTest {
    @Test
    fun `sizes are described in the binary units the screens use`() {
        assertEquals("1 byte", InformationRequestEvidenceSizes.describe(1))
        assertEquals("1023 bytes", InformationRequestEvidenceSizes.describe(1023))
        assertEquals("1.5 KiB", InformationRequestEvidenceSizes.describe(1536))
        assertEquals("25 MiB", InformationRequestEvidenceSizes.describe(25L * 1024 * 1024))
        assertEquals("2 GiB", InformationRequestEvidenceSizes.describe(2L * 1024 * 1024 * 1024))
    }
}
