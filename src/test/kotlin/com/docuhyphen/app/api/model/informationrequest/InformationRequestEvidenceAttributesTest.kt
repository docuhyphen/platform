package com.docuhyphen.app.api.model.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDate

class InformationRequestEvidenceAttributesTest
{
    private val stated = InformationRequestEvidenceAttributes(
        issuer = "Process Registry",
        jurisdiction = "ZZ",
        language = "en",
        issuedOn = LocalDate.of(2026, 1, 10),
        expiresOn = LocalDate.of(2027, 1, 10),
        coverage = InformationRequestEvidenceCoverage(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)),
        certificationReference = "CERT-7",
        signatureReference = "SIG-7",
    )

    @Test
    fun `stated attributes survive a write and read through a recorded version`()
    {
        val version = InformationRequestEvidenceVersion()

        InformationRequestEvidenceAttributesMapper.write(version, stated)

        assertEquals("Process Registry", version.issuer)
        assertEquals(LocalDate.of(2026, 6, 30), version.coverageEndsOn)
        assertEquals(stated, InformationRequestEvidenceAttributesMapper.read(version))
    }

    @Test
    fun `no captured attribute is the empty set`()
    {
        val version = InformationRequestEvidenceVersion()

        InformationRequestEvidenceAttributesMapper.write(version, InformationRequestEvidenceAttributes.NONE)

        assertNull(version.issuer)
        assertNull(version.coverageStartsOn)
        assertEquals(InformationRequestEvidenceAttributes.NONE, InformationRequestEvidenceAttributesMapper.read(version))
    }

    @Test
    fun `a blank captured value is refused`()
    {
        assertThrows(IllegalArgumentException::class.java) { stated.copy(issuer = " ") }
        assertThrows(IllegalArgumentException::class.java) { stated.copy(jurisdiction = "") }
        assertThrows(IllegalArgumentException::class.java) { stated.copy(language = "  ") }
        assertThrows(IllegalArgumentException::class.java) { stated.copy(certificationReference = " ") }
        assertThrows(IllegalArgumentException::class.java) { stated.copy(signatureReference = "") }
    }

    @Test
    fun `a captured value longer than its stored field is refused`()
    {
        assertThrows(IllegalArgumentException::class.java) { stated.copy(issuer = "i".repeat(256)) }
        assertThrows(IllegalArgumentException::class.java) { stated.copy(jurisdiction = "j".repeat(65)) }
        assertThrows(IllegalArgumentException::class.java) { stated.copy(language = "l".repeat(36)) }
        assertThrows(IllegalArgumentException::class.java) { stated.copy(certificationReference = "c".repeat(256)) }
        assertThrows(IllegalArgumentException::class.java) { stated.copy(signatureReference = "s".repeat(256)) }
    }

    @Test
    fun `an expiry before the issue date is refused`()
    {
        assertThrows(IllegalArgumentException::class.java)
        {
            stated.copy(issuedOn = LocalDate.of(2026, 2, 1), expiresOn = LocalDate.of(2026, 1, 31))
        }
    }

    @Test
    fun `a coverage period ends on or after it starts`()
    {
        assertThrows(IllegalArgumentException::class.java)
        {
            InformationRequestEvidenceCoverage(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 2, 28))
        }

        val oneDay = InformationRequestEvidenceCoverage(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 1))
        assertEquals(1L, oneDay.days())
    }
}
