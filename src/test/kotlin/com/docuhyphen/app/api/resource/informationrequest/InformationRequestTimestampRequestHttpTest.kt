package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.exception.RequestBodySerializationExceptionMapper
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionRequest
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.resource.informationrequest.model.DatedRequestField
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.conformance.PublishedRequestSupport
import com.docuhyphen.app.api.service.informationrequest.conformance.model.PublishedRequest
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationWriter
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateMaterializer
import com.docuhyphen.app.api.service.user.AppUserService
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.restassured.response.Response
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestTimestampRequestHttpTest {
    @Test
    fun `management reads do not record a respondent first view`() {
        val request = issuedRequest()
        dataSource.connection.use { connection ->
            val partyId = request.parties.getValue("CONTRIBUTOR")
            execute(
                connection,
                "UPDATE share SET principal_id = ? WHERE id = (SELECT share_id FROM information_request_party WHERE id = ?)",
                request.ownerId,
                partyId
            )
            execute(
                connection,
                "UPDATE information_request_party SET principal_id = ? WHERE id = ?",
                request.ownerId,
                partyId
            )
        }
        val caller = InformationRequestSignedInCaller.signIn(appUsers, request.ownerId)
        val managed = caller.authorized().queryParam("purpose", "MANAGEMENT")
            .get("/information-requests/${request.requestId}/response-workspace")
        assertEquals(200, managed.statusCode, managed.asString())
        val viewedAfterManagement = firstViewedAt(request.requestId)
        val response = caller.authorized().get("/information-requests/${request.requestId}/response-workspace")
        assertEquals(200, response.statusCode, response.asString())
        assertAll(
            { assertEquals(null, viewedAfterManagement) },
            { assertNotEquals(null, firstViewedAt(request.requestId)) },
        )
    }

    @Test
    fun `a respondent cannot request a management workspace`() {
        val request = issuedRequest()
        val caller = InformationRequestSignedInCaller.signIn(appUsers, request.users.getValue("CONTRIBUTOR"))
        val response = caller.authorized().queryParam("purpose", "MANAGEMENT")
            .get("/information-requests/${request.requestId}/response-workspace")
        assertEquals(403, response.statusCode, response.asString())
    }

    private fun firstViewedAt(requestId: UUID): String? = dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT first_viewed_at::text FROM information_request WHERE id = ?")
            .use { statement ->
                statement.setObject(1, requestId)
                statement.executeQuery().use { rows ->
                    check(rows.next())
                    rows.getString(1)
                }
            }
    }

    @Test
    fun `a respondent receives the same Template Version identity in the header and workspace`() {
        val request = issuedRequest()
        val caller = InformationRequestSignedInCaller.signIn(appUsers, request.users.getValue("CONTRIBUTOR"))
        val workspace = caller.authorized().get("/information-requests/${request.requestId}/response-workspace")
        val header = caller.authorized().get("/information-requests/${request.requestId}")
        assertEquals(200, workspace.statusCode, workspace.asString())
        assertEquals(200, header.statusCode, header.asString())
        assertEquals(request.templateVersionId.toString(), workspace.jsonPath().getString("templateVersion.id"))
        assertEquals(request.templateVersionId.toString(), header.jsonPath().getString("templateVersionId"))
    }

    @Test
    fun `oversized command keys are refused over HTTP before recording a decision`() {
        val request = issuedRequest()
        val caller = InformationRequestSignedInCaller.signIn(appUsers, request.ownerId)
        fun body(process: String) = """{"owningProcessKey":"$process","outcomeCode":"completed","decidedAt":${
            Instant.now().minusSeconds(300).toEpochMilli()
        }}"""

        val accepted = caller.command("a".repeat(128)).body(body("process.first"))
            .post("/information-requests/${request.requestId}/business-decisions")
        assertEquals(201, accepted.statusCode, accepted.asString())
        val refused = caller.command("a".repeat(129)).body(body("process.second"))
            .post("/information-requests/${request.requestId}/business-decisions")
        assertEquals(400, refused.statusCode, refused.asString())
        assertEquals("COMMAND_IDEMPOTENCY_KEY_INVALID", refused.jsonPath().getString("reasonCode"))
    }

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

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = [REVIEWER_DUE, FACT_VALID_FROM, FACT_VALID_TO, FACT_EXPIRES, DECISION_DECIDED, CLOCK_RECEIVED])
    fun `a date sent as an ISO string is refused as an unreadable body`(field: String) {
        val response = send(field, "\"2026-09-01T08:00:00.000Z\"")

        assertEquals(400, response.statusCode, response.asString())
        assertEquals(RequestBodySerializationExceptionMapper.REASON_CODE, response.jsonPath().getString("reasonCode"))
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = [REVIEWER_DUE, FACT_VALID_FROM, FACT_VALID_TO, FACT_EXPIRES, DECISION_DECIDED, CLOCK_RECEIVED])
    fun `a date sent as epoch milliseconds is read by the request`(field: String) {
        val response = send(field, "1788249600000")

        assertReadable(response)
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = [REVIEWER_DUE, FACT_VALID_FROM, FACT_VALID_TO, FACT_EXPIRES, CLOCK_RECEIVED])
    fun `an optional date left out is read by the request`(field: String) {
        val response = send(field, null)

        assertReadable(response)
    }

    @Test
    fun `a business decision without its decided time is refused as missing it`() {
        val caller = InformationRequestSignedInCaller.signIn(appUsers, support.outsider())
        val response = caller.command("decision-without-time")
            .body("""{"owningProcessKey":"record-review","outcomeCode":"completed"}""")
            .post("/information-requests/${UUID.randomUUID()}/business-decisions")

        assertEquals(400, response.statusCode, response.asString())
        assertTrue(response.jsonPath().getString("errorMessage").contains("decidedAt"), response.asString())
    }

    @Test
    fun `a business decision sent with epoch milliseconds is recorded at that time`() {
        val request = issuedRequest()
        val caller = InformationRequestSignedInCaller.signIn(appUsers, request.ownerId)
        val decidedAt = Instant.now().minus(Duration.ofMinutes(5)).truncatedTo(ChronoUnit.MILLIS)

        val response = caller.command("decision-${request.requestId}")
            .body(
                """{"owningProcessKey":"record-review","outcomeCode":"completed","kind":"ORIGINAL","decidedAt":${decidedAt.toEpochMilli()}}""",
            )
            .post("/information-requests/${request.requestId}/business-decisions")

        assertEquals(201, response.statusCode, response.asString())
        assertEquals(decidedAt.toEpochMilli(), response.jsonPath().getLong("decidedAt"))
        assertEquals("record-review", response.jsonPath().getString("owningProcessKey"))
    }

    private fun send(name: String, date: String?): Response {
        val field = DATED_FIELDS.getValue(name)
        val caller = InformationRequestSignedInCaller.signIn(appUsers, support.outsider())
        val command = caller.command("${field.dateName}-${UUID.randomUUID()}").body(field.body(date))
        val prepared = field.ifMatch?.let { command.header("If-Match", it) } ?: command
        return prepared.post(field.path(UUID.randomUUID()))
    }

    private fun assertReadable(response: Response) {
        val reasonCode = runCatching { response.jsonPath().getString("reasonCode") }.getOrNull()
        assertNotEquals(RequestBodySerializationExceptionMapper.REASON_CODE, reasonCode, response.asString())
        assertTrue(response.statusCode in setOf(403, 404), "${response.statusCode} ${response.asString()}")
    }

    private fun issuedRequest(): PublishedRequest =
        support.issue(
            "timestamp-${UUID.randomUUID()}",
            listOf("record-identifier"),
            listOf("CONTRIBUTOR", "DECISION_MAKER")
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
        }.also { request ->
            dataSource.connection.use { connection ->
                val partyId = request.parties.getValue("DECISION_MAKER")
                execute(
                    connection,
                    "UPDATE share SET principal_id = ? WHERE id = (SELECT share_id FROM information_request_party WHERE id = ?)",
                    request.ownerId,
                    partyId,
                )
                execute(
                    connection,
                    "UPDATE information_request_party SET principal_id = ? WHERE id = ?",
                    request.ownerId,
                    partyId
                )
            }
        }

    private companion object {
        const val REVIEWER_DUE = "reviewer dueAt"
        const val FACT_VALID_FROM = "fact validFrom"
        const val FACT_VALID_TO = "fact validTo"
        const val FACT_EXPIRES = "fact expiresAt"
        const val DECISION_DECIDED = "decision decidedAt"
        const val CLOCK_RECEIVED = "clock receivedAt"

        val ID: UUID = UUID.randomUUID()

        val FACT_FIELDS = "\"packageId\":\"$ID\",\"submissionItemId\":\"$ID\",\"purposeKey\":\"record-review\"," +
                "\"policyBasisKey\":\"retained-confirmation\",\"visibility\":\"REQUESTING_SIDE\""

        val DATED_FIELDS = mapOf(
            REVIEWER_DUE to DatedRequestField(
                "reviews/$ID/assignments",
                "\"stageKey\":\"content\",\"reviewerPartyId\":\"$ID\"",
                "dueAt",
                "\"$ID:1\"",
            ),
            FACT_VALID_FROM to DatedRequestField("accepted-facts", FACT_FIELDS, "validFrom"),
            FACT_VALID_TO to DatedRequestField("accepted-facts", FACT_FIELDS, "validTo"),
            FACT_EXPIRES to DatedRequestField("accepted-facts", FACT_FIELDS, "expiresAt"),
            DECISION_DECIDED to DatedRequestField(
                "business-decisions",
                "\"owningProcessKey\":\"record-review\",\"outcomeCode\":\"completed\",\"kind\":\"ORIGINAL\"",
                "decidedAt",
            ),
            CLOCK_RECEIVED to DatedRequestField(
                "clocks",
                "\"clockKey\":\"response\",\"policyVersionId\":\"$ID\",\"urgency\":\"STANDARD\"",
                "receivedAt",
            ),
        )
    }
}
