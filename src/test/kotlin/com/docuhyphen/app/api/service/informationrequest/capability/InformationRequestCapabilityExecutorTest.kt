package com.docuhyphen.app.api.service.informationrequest.capability

import com.docuhyphen.app.api.model.informationrequest.capability.InformationRequestCapability
import com.docuhyphen.app.api.model.informationrequest.capability.InformationRequestCapabilityRequirement
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class InformationRequestCapabilityExecutorTest
{
    @Test
    fun `an installation with no executor serves nothing`()
    {
        val installed = InstalledCapabilities(emptyList())

        assertEquals(emptySet<InformationRequestCapability>(), installed.installedCapabilities())

        val requirements = InformationRequestCapability.entries.map { capability ->
            InformationRequestCapabilityRequirement(capability, capability.contractVersion)
        }
        assertEquals(requirements, installed.unserved(requirements))
    }

    @Test
    fun `an installed executor serves the contract versions it honours and no others`()
    {
        val installed = InstalledCapabilities(
            listOf(
                StubCapabilityExecutor(
                    InformationRequestCapability.RESPONSE_SUBMISSION,
                    contractVersion = 1,
                    minimumSupportedContractVersion = 1,
                ),
            ),
        )

        assertEquals(
            setOf(InformationRequestCapability.RESPONSE_SUBMISSION),
            installed.installedCapabilities(),
        )
        assertTrue(
            installed.serves(
                InformationRequestCapabilityRequirement(
                    InformationRequestCapability.RESPONSE_SUBMISSION,
                    1,
                ),
            ),
        )

        assertFalse(
            installed.serves(
                InformationRequestCapabilityRequirement(
                    InformationRequestCapability.RESPONSE_SUBMISSION,
                    2,
                ),
            ),
        )

        assertFalse(
            installed.serves(
                InformationRequestCapabilityRequirement(
                    InformationRequestCapability.DOCUMENT_EVIDENCE,
                    1,
                ),
            ),
        )
    }

    @Test
    fun `an executor serving a range keeps every contract in it reachable`()
    {
        val installed = InstalledCapabilities(
            listOf(
                StubCapabilityExecutor(
                    InformationRequestCapability.RESPONSE_SUBMISSION,
                    contractVersion = InformationRequestCapability.RESPONSE_SUBMISSION.contractVersion,
                    minimumSupportedContractVersion = 1,
                ),
            ),
        )

        (1..InformationRequestCapability.RESPONSE_SUBMISSION.contractVersion).forEach { contract ->
            assertTrue(
                installed.serves(
                    InformationRequestCapabilityRequirement(
                        InformationRequestCapability.RESPONSE_SUBMISSION,
                        contract,
                    ),
                ),
                "Contract version $contract should be within the range this executor honours",
            )
        }
    }

    @Test
    fun `two executors cannot both claim one capability`()
    {
        val refusal = assertThrows<IllegalStateException> {
            InstalledCapabilities(
                listOf(
                    StubCapabilityExecutor(InformationRequestCapability.DOCUMENT_EVIDENCE, 1, 1),
                    OtherCapabilityExecutor(InformationRequestCapability.DOCUMENT_EVIDENCE, 1, 1),
                ),
            )
        }

        assertTrue(
            refusal.message.orEmpty().contains("DOCUMENT_EVIDENCE"),
            "The refusal should name the capability both claimed: ${refusal.message}",
        )
    }

    @Test
    fun `an executor cannot implement a contract the platform has not declared`()
    {
        val declared = InformationRequestCapability.RESPONSE_REVIEW.contractVersion
        val refusal = assertThrows<IllegalStateException> {
            InstalledCapabilities(
                listOf(
                    StubCapabilityExecutor(
                        InformationRequestCapability.RESPONSE_REVIEW,
                        contractVersion = declared + 1,
                        minimumSupportedContractVersion = declared + 1,
                    ),
                ),
            )
        }

        assertTrue(
            refusal.message.orEmpty().contains("above the declared"),
            "The refusal should say the contract version is above the declared one: ${refusal.message}",
        )
    }

    @Test
    fun `an executor cannot declare an impossible contract range`()
    {
        val below = assertThrows<IllegalStateException> {
            InstalledCapabilities(
                listOf(
                    StubCapabilityExecutor(
                        InformationRequestCapability.RESPONSE_SUBMISSION,
                        contractVersion = 1,
                        minimumSupportedContractVersion = 0,
                    ),
                ),
            )
        }
        assertTrue(
            below.message.orEmpty().contains("below the first"),
            "The refusal should say the range starts below the first contract: ${below.message}",
        )

        val inverted = assertThrows<IllegalStateException> {
            InstalledCapabilities(
                listOf(
                    StubCapabilityExecutor(
                        InformationRequestCapability.RESPONSE_SUBMISSION,
                        contractVersion = 1,
                        minimumSupportedContractVersion = 2,
                    ),
                ),
            )
        }
        assertTrue(
            inverted.message.orEmpty().contains("empty range"),
            "The refusal should say the range is empty: ${inverted.message}",
        )
    }

    @Test
    fun `every capability declares a contract a runtime can be written against`()
    {
        InformationRequestCapability.entries.forEach { capability ->
            assertTrue(
                capability.contractVersion >= 1,
                "$capability should declare at least its first contract version",
            )
        }

        assertEquals(
            InformationRequestCapability.RESPONSE_SUBMISSION,
            InformationRequestCapability.fromCodeOrNull("  response_submission  "),
        )
        assertNull(InformationRequestCapability.fromCodeOrNull("UNDECLARED_CAPABILITY"))
        assertNull(InformationRequestCapability.fromCodeOrNull(null))
    }

    private class StubCapabilityExecutor(
        override val capability: InformationRequestCapability,
        override val contractVersion: Int,
        override val minimumSupportedContractVersion: Int,
    ) : InformationRequestCapabilityExecutor

    private class OtherCapabilityExecutor(
        override val capability: InformationRequestCapability,
        override val contractVersion: Int,
        override val minimumSupportedContractVersion: Int,
    ) : InformationRequestCapabilityExecutor
}
