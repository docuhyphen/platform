package com.docuhyphen.app.api.service.informationrequest.record

import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.*

class InformationRequestRecordRelatedRequestsTest {
    private val sourceId = UUID.randomUUID()
    private val replacementId = UUID.randomUUID()
    private val factSourceId = UUID.randomUUID()
    private val factPackageId = UUID.randomUUID()
    private val factEvidenceVersionId = UUID.randomUUID()

    @Test
    fun `a replacement its reader may not view is left out of the request with a marker and one it may view stays`() {
        val content = recordOf(replacement = replacementId.toString())

        val withheld = InformationRequestRecordRelatedRequests.withheld(content) { false }
        val shown = InformationRequestRecordRelatedRequests.withheld(content) { it == replacementId }

        val request = withheld.content.getValue("request").jsonObject
        assertEquals(JsonNull, request.getValue("supersededByRequestId"))
        assertEquals(true, request.getValue("supersededByRequestRedacted").jsonPrimitive.boolean)
        assertEquals(
            listOf("id", "state", "supersededAt", "supersededByRequestId", "expiredAt", "supersededByRequestRedacted"),
            request.keys.toList(),
        )
        assertEquals(1, withheld.withheldCount)
        assertFalse(withheld.content.toString().contains(replacementId.toString()))
        assertEquals(content, shown.content)
        assertEquals(0, shown.withheldCount)
    }

    @Test
    fun `a recertification keeps its own reuse and leaves out the source request and its records when that source may not be viewed`() {
        val content = recordOf(recertificationSource = factSourceId.toString())

        val withheld = InformationRequestRecordRelatedRequests.withheld(content) { false }
        val shown = InformationRequestRecordRelatedRequests.withheld(content) { it == factSourceId }

        val recertification = withheld.content.getValue("recertifications").jsonArray.single().jsonObject
        listOf(
            "sourceInformationRequestId",
            "sourcePackageId",
            "sourceSubmissionItemId",
            "sourceRequirementId",
            "sourceFieldValueRevisionId",
            "sourceReviewId",
            "evidenceVersionIds",
        ).forEach { assertEquals(JsonNull, recertification.getValue(it), it) }
        assertEquals(true, recertification.getValue("sourceRedacted").jsonPrimitive.boolean)
        assertEquals("recertification-1", recertification.getValue("recertificationId").jsonPrimitive.content)
        assertEquals("fact-1", recertification.getValue("factId").jsonPrimitive.content)
        assertEquals("policy.reuse", recertification.getValue("policyBasisKey").jsonPrimitive.content)
        assertEquals(1, withheld.withheldCount)
        listOf(factSourceId, factPackageId, factEvidenceVersionId).forEach {
            assertFalse(withheld.content.toString().contains(it.toString()), it.toString())
        }
        assertEquals(content, shown.content)
        assertEquals(0, shown.withheldCount)
    }

    @Test
    fun `each request of a subject record is decided on its own and the withheld references add up`() {
        val visibleReplacement = UUID.randomUUID()
        val shownRecord = recordOf(replacement = visibleReplacement.toString())
        val content = buildJsonObject {
            put("schemaVersion", 4)
            put("subjectIdentityRefId", UUID.randomUUID().toString())
            putJsonArray("requests") {
                add(recordOf(replacement = replacementId.toString()))
                add(shownRecord)
                add(recordOf(recertificationSource = factSourceId.toString()))
            }
        }

        val projection = InformationRequestRecordRelatedRequests.withheld(content) { it == visibleReplacement }

        val requests = projection.content.getValue("requests").jsonArray.map { it.jsonObject }
        assertEquals(2, projection.withheldCount)
        assertEquals(JsonNull, requests[0].getValue("request").jsonObject.getValue("supersededByRequestId"))
        assertEquals(shownRecord, requests[1])
        assertEquals(
            true,
            requests[2].getValue("recertifications").jsonArray.single().jsonObject.getValue("sourceRedacted").jsonPrimitive.boolean,
        )
        assertEquals(content.getValue("subjectIdentityRefId"), projection.content.getValue("subjectIdentityRefId"))
    }

    @Test
    fun `content frozen without its related requests is returned as it is without asking about any`() {
        val frozen = InformationRequestRecordRelatedRequests.withheld(
            recordOf(replacement = replacementId.toString(), recertificationSource = factSourceId.toString()),
        ) { false }.content

        val projection =
            InformationRequestRecordRelatedRequests.withheld(frozen) { fail("no related request is named") }

        assertSame(frozen, projection.content)
        assertEquals(0, projection.withheldCount)
    }

    @Test
    fun `a request that names no replacement is returned as it is without asking`() {
        val content = recordOf(replacement = null)

        val projection =
            InformationRequestRecordRelatedRequests.withheld(content) { fail("no related request is named") }

        assertSame(content, projection.content)
        assertEquals(0, projection.withheldCount)
    }

    @Test
    fun `a related request reference that is not a request id is left out without asking about it`() {
        val asked = mutableListOf<UUID>()

        val projection = InformationRequestRecordRelatedRequests.withheld(recordOf(replacement = "not-a-request-id")) {
            asked += it
            true
        }

        assertEquals(JsonNull, projection.content.getValue("request").jsonObject.getValue("supersededByRequestId"))
        assertEquals(1, projection.withheldCount)
        assertTrue(asked.isEmpty())
    }

    @Test
    fun `content without a request record is returned as it is`() {
        val content = buildJsonObject { put("request", sourceId.toString()) }

        val projection =
            InformationRequestRecordRelatedRequests.withheld(content) { fail("no related request is named") }

        assertSame(content, projection.content)
        assertEquals(0, projection.withheldCount)
    }

    private fun recordOf(replacement: String? = null, recertificationSource: String? = null) = buildJsonObject {
        put("schemaVersion", 4)
        putJsonObject("request") {
            put("id", sourceId.toString())
            put("state", if (replacement == null) "ISSUED" else "SUPERSEDED")
            put("supersededAt", if (replacement == null) null else "2026-10-25T09:00:00Z")
            put("supersededByRequestId", replacement)
            put("expiredAt", null as String?)
        }
        putJsonArray("history") { }
        putJsonArray("recertifications") {
            if (recertificationSource != null) {
                addJsonObject {
                    put("recertificationId", "recertification-1")
                    put("requirementId", "requirement-1")
                    put("responseId", "response-1")
                    put("responseRevision", 2)
                    put("factId", "fact-1")
                    put("purposeKey", "profile.reuse")
                    put("policyBasisKey", "policy.reuse")
                    put("valueType", "TEXT")
                    put("sourceInformationRequestId", recertificationSource)
                    put("sourcePackageId", factPackageId.toString())
                    put("sourceSubmissionItemId", UUID.randomUUID().toString())
                    put("sourceRequirementId", UUID.randomUUID().toString())
                    put("sourceFieldValueRevisionId", UUID.randomUUID().toString())
                    put("sourceReviewId", null as String?)
                    put("assentedByPrincipalKind", "USER")
                    put("assentedByPrincipalId", UUID.randomUUID().toString())
                    put("assentedAt", "2026-10-25T09:00:00Z")
                    putJsonArray("evidenceVersionIds") { add(JsonPrimitive(factEvidenceVersionId.toString())) }
                }
            }
        }
    }
}
