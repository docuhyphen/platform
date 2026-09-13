package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementRevision
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.util.UUID

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

        assertEquals("\"${request.id}:7\"", InformationRequestETag.aggregateOf(request))
        assertEquals("\"${request.id}:3\"", InformationRequestETag.partiesOf(request))
        assertEquals("\"${party.id}:4\"", InformationRequestETag.partyOf(party))
        assertNotEquals(InformationRequestETag.aggregateOf(request), InformationRequestETag.partiesOf(request))
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
