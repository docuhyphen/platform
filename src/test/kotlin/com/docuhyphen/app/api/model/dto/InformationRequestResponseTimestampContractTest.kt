@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.acceptedfact.InformationRequestAcceptedFactFreshness
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestExecutionStandingKind
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.oversight.InformationRequestSlaStatus
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestSignedInCaller
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.conformance.PublishedRequestSupport
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationWriter
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateMaterializer
import com.docuhyphen.app.api.service.user.AppUserService
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.sql.Timestamp
import java.time.Instant
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestResponseTimestampContractTest {
    @Inject
    lateinit var json: Json

    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var appUsers: AppUserService

    @Inject
    lateinit var requests: InformationRequestRepository

    @Inject
    lateinit var requirements: InformationRequestRequirementRepository

    @Inject
    lateinit var definitions: InformationRequestTemplateDefinitionRepository

    @Inject
    lateinit var versions: InformationRequestTemplateVersionRepository

    @Inject
    lateinit var capabilities: InformationRequestTemplateVersionCapabilityRepository

    @Inject
    lateinit var writer: InformationRequestTemplateConfigurationWriter

    @Inject
    lateinit var materializer: InformationRequestTemplateMaterializer

    private val support by lazy {
        PublishedRequestSupport(
            dataSource,
            requests,
            requirements,
            definitions,
            versions,
            capabilities,
            writer,
            materializer
        )
    }

    @Test
    fun `a review decision is sent with its decided time as a number of epoch milliseconds`() {
        val decidedAt = Instant.parse("2026-09-26T09:00:00Z")

        val payload = json.encodeToJsonElement(
            InformationRequestReviewDecisionDto(
                id = UUID.randomUUID(),
                stageKey = "review",
                submissionItemId = UUID.randomUUID(),
                kind = InformationRequestReviewDecisionKind.REVIEWER,
                outcome = InformationRequestReviewOutcome.SATISFIED,
                decidedAt = Timestamp.from(decidedAt),
                decidedByCaller = true,
            ),
        ).jsonObject

        assertEpochMillis(decidedAt.toEpochMilli(), payload, "decidedAt")
    }

    @Test
    fun `an access link is sent with its expiry and creation times as numbers of epoch milliseconds`() {
        val createdAt = Instant.parse("2019-12-01T08:00:00Z")
        val expiresAt = Instant.parse("2020-01-01T08:00:00Z")

        val payload = json.encodeToJsonElement(
            InformationRequestAccessLinkDto(
                shareLinkId = UUID.randomUUID(),
                status = ShareLinkStatus.ACTIVE,
                expiresAt = Timestamp.from(expiresAt),
                rotationCount = 0,
                partyId = UUID.randomUUID(),
                createdAt = Timestamp.from(createdAt),
            ),
        ).jsonObject

        assertEpochMillis(expiresAt.toEpochMilli(), payload, "expiresAt")
        assertEpochMillis(createdAt.toEpochMilli(), payload, "createdAt")
    }

    @Test
    fun `a reusable answer without an end is sent with explicit nulls, and an end at epoch zero as zero`() {
        val unbounded =
            json.encodeToJsonElement(offer(validTo = null, expiresAt = null)).jsonObject.getValue("fact").jsonObject
        val epochEnd = json.encodeToJsonElement(
            offer(
                validTo = Timestamp(0),
                expiresAt = null
            )
        ).jsonObject.getValue("fact").jsonObject

        assertEquals(JsonNull, unbounded["validTo"], unbounded.toString())
        assertEquals(JsonNull, unbounded["expiresAt"], unbounded.toString())
        assertEpochMillis(0, epochEnd, "validTo")
        assertEquals(JsonNull, epochEnd["expiresAt"], epochEnd.toString())
    }

    @Test
    fun `an operations row without a deadline is sent with an explicit null nearest due time`() {
        val payload = json.encodeToJsonElement(
            InformationRequestOperationsRowDto(
                requestId = UUID.randomUUID(),
                exchangeId = UUID.randomUUID(),
                title = "Process collection",
                assignees = emptyList(),
                templateVersionId = UUID.randomUUID(),
                state = InformationRequestState.ISSUED,
                gatesExchangeClosure = true,
                createdAt = Timestamp.from(Instant.parse("2026-09-20T08:00:00Z")),
                ageSeconds = 3600,
                slaStatus = InformationRequestSlaStatus.NO_CLOCK,
                clockCount = 0,
                reminderCount = 0,
                noticeCounts = emptyMap(),
                exceptionCounts = emptyMap(),
                executionStanding = InformationRequestExecutionStandingDto(InformationRequestExecutionStandingKind.ACTIVE),
            ),
        ).jsonObject

        assertEquals(JsonNull, payload["nearestDueAt"], payload.toString())
    }

    @Test
    fun `a due date policy version without escalation is sent with an explicit null, distinct from escalation at the due time`() {
        val disabled = json.encodeToJsonElement(policyVersion(escalationAfterMinutes = null)).jsonObject
        val atDue = json.encodeToJsonElement(policyVersion(escalationAfterMinutes = 0)).jsonObject
        val later = json.encodeToJsonElement(policyVersion(escalationAfterMinutes = 120)).jsonObject

        assertEquals(JsonNull, disabled["escalationAfterMinutes"], disabled.toString())
        assertEquals(JsonNull, disabled["reminderCommunicationId"], disabled.toString())
        assertEquals(JsonNull, disabled["overdueCommunicationId"], disabled.toString())
        assertEquals(JsonPrimitive(0), atDue["escalationAfterMinutes"], atDue.toString())
        assertEquals(JsonPrimitive(120), later["escalationAfterMinutes"], later.toString())
    }

    @Test
    fun `the configured JSON writes every unset nullable property as an explicit null`() {
        assertTrue(json.configuration.encodeDefaults, json.configuration.toString())
        assertTrue(json.configuration.explicitNulls, json.configuration.toString())
    }

    @Test
    fun `a template group without a parent or maximum and a schedule without disposal are sent with explicit nulls`() {
        val group = json.encodeToJsonElement(
            InformationRequestTemplateGroupDto(id = UUID.randomUUID(), groupKey = "lines", minOccurrences = 1),
        ).jsonObject
        val schedule = json.encodeToJsonElement(
            RecordRetentionScheduleVersionDto(
                id = UUID.randomUUID(),
                versionNumber = 2,
                minimumRetentionDays = 365,
                recordedByPrincipalKind = PrincipalKind.USER,
                recordedByPrincipalId = UUID.randomUUID(),
                recordedAt = Timestamp.from(Instant.parse("2026-09-01T08:00:00Z")),
            ),
        ).jsonObject

        assertEquals(JsonNull, group["parentGroupKey"], group.toString())
        assertEquals(JsonNull, group["maxOccurrences"], group.toString())
        assertEquals(JsonNull, schedule["disposalAfterDays"], schedule.toString())
    }

    @Test
    fun `the operations queue read over HTTP sends a request without a clock with an explicit null nearest due time`() {
        val request = support.issue(
            "timestamp-${UUID.randomUUID()}",
            listOf("record-identifier"),
            listOf("CONTRIBUTOR")
        ) { schema, fields ->
            InformationRequestTemplateConfigurationRequest(
                schemaVersionId = schema,
                sections = listOf(
                    InformationRequestTemplateSectionRequest(
                        sectionKey = "records",
                        title = "Records",
                        requirements = listOf(
                            InformationRequestTemplateRequirementRequest(
                                requirementKey = "record-identifier",
                                requirementType = InformationRequestRequirementType.FIELD,
                                prompt = "Provide a record identifier",
                                responseMode = InformationRequestResponseMode.PROVIDE,
                                requiredness = InformationRequestRequiredness.OPTIONAL,
                                contributorRole = InformationRequestContributorRole.CONTRIBUTOR,
                                collectedFieldDefinitionId = fields.getValue("record-identifier").fieldDefinitionId,
                                permittedDispositions = listOf(InformationRequestResponseDisposition.PROVIDED),
                            ),
                        ),
                    ),
                ),
            )
        }
        val caller = InformationRequestSignedInCaller.signIn(appUsers, request.ownerId)

        val response = caller.authorized()
            .queryParam("exchangeId", request.exchangeId.toString())
            .get("/information-request-operations")

        assertEquals(200, response.statusCode, response.asString())
        val row =
            json.parseToJsonElement(response.asString()).jsonObject.getValue("items").jsonArray.single().jsonObject
        assertEquals(JsonPrimitive(request.requestId.toString()), row["requestId"], row.toString())
        assertEquals(JsonPrimitive(InformationRequestSlaStatus.NO_CLOCK.name), row["slaStatus"], row.toString())
        assertEquals(JsonNull, row["nearestDueAt"], row.toString())
    }

    private fun offer(validTo: Timestamp?, expiresAt: Timestamp?): InformationRequestAcceptedFactOfferDto =
        InformationRequestAcceptedFactOfferDto(
            requirementId = UUID.randomUUID(),
            requirementKey = "recorded-note",
            fact = InformationRequestReusableFactDto(
                id = UUID.randomUUID(),
                purposeKey = "profile.reuse",
                policyBasisKey = "policy.reuse",
                valueType = FieldValueType.SHORT_TEXT,
                value = JsonPrimitive("Recorded answer"),
                confidence = InformationRequestAcceptedFactConfidence.REVIEWED,
                validFrom = Timestamp.from(Instant.parse("2026-09-01T08:00:00Z")),
                validTo = validTo,
                expiresAt = expiresAt,
                freshness = InformationRequestAcceptedFactFreshness.CURRENT,
            ),
            reconfirmationRequired = false,
        )

    private fun policyVersion(escalationAfterMinutes: Int?): InformationRequestClockPolicyVersionDto =
        InformationRequestClockPolicyVersionDto(
            id = UUID.randomUUID(),
            versionNumber = 1,
            clockType = InformationRequestClockType.CALENDAR,
            businessTimezone = "UTC",
            standardDurationMinutes = 1440,
            urgentDurationMinutes = 240,
            escalationAfterMinutes = escalationAfterMinutes,
            dueEffect = InformationRequestClockDueEffect.MARK_OVERDUE,
            workingPeriods = emptyList(),
            holidays = emptyList(),
            reminderMinutesBeforeDue = emptyList(),
            publishedAt = Timestamp.from(Instant.parse("2026-09-01T08:00:00Z")),
        )

    private fun assertEpochMillis(expected: Long, payload: JsonObject, field: String) {
        val value = payload.getValue(field).jsonPrimitive
        assertFalse(value.isString, payload.toString())
        assertEquals(expected, value.long)
    }
}
