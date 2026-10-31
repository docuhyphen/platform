package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementRevision
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.*

class InformationRequestETagTest
{
    @Test
    fun `aggregate and party validators come from persisted revision counters`()
    {
        val request = InformationRequest().apply {
            id = UUID.randomUUID()
            aggregateRevision = 7
            partyRevision = 3
        }
        val party = InformationRequestParty().apply {
            id = UUID.randomUUID()
            partyRevision = 4
        }

        assertEquals("\"aggregate:${request.id}:7\"", InformationRequestETag.aggregateOf(request))
        assertEquals("\"parties:${request.id}:3\"", InformationRequestETag.partiesOf(request))
        assertEquals("\"${party.id}:4\"", InformationRequestETag.partyOf(party))
        assertNotEquals(InformationRequestETag.aggregateOf(request), InformationRequestETag.partiesOf(request))
    }

    @Test
    fun `request validators from different families never satisfy each other even when their counters agree`() {
        val request = InformationRequest().apply {
            id = UUID.randomUUID()
            aggregateRevision = 5
            partyRevision = 5
            responseRevision = 5
        }
        val validators = listOf(
            InformationRequestETag.aggregateOf(request),
            InformationRequestETag.partiesOf(request),
            InformationRequestETag.responsesOf(request),
        )

        assertEquals(3, validators.toSet().size)
        validators.forEach { held ->
            validators.filter { it != held }.forEach { current ->
                assertThrows(CommandPreconditionException::class.java) {
                    CommandPrecondition.ExpectedRevision(held).requireSatisfiedBy(current)
                }
            }
        }
    }

    @Test
    fun `requirement validators name the stable runtime Requirement`()
    {
        val requirementId = UUID.randomUUID()
        val revision = InformationRequestRequirementRevision().apply {
            informationRequestRequirementId = requirementId
            optimisticVersion = 9
        }

        assertEquals("\"$requirementId:9\"", InformationRequestETag.requirementOf(revision))
    }
}
