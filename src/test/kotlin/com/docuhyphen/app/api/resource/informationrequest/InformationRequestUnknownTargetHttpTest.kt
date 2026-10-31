package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionRequest
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.resource.informationrequest.model.UnknownTargetFixture
import com.docuhyphen.app.api.resource.informationrequest.model.UnknownTargetRoute
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.conformance.PublishedRequestSupport
import com.docuhyphen.app.api.service.informationrequest.conformance.model.PublishedRequest
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationWriter
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateMaterializer
import com.docuhyphen.app.api.service.user.AppUserService
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.restassured.http.ContentType
import io.restassured.response.Response
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Files
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestUnknownTargetHttpTest {
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
    @MethodSource("routes")
    fun `a signed-in caller cannot tell a request hidden from them from one that does not exist`(name: String) {
        val route = ROUTES.getValue(name)
        val prepared = fixture()

        val hidden = send(route, prepared.caller, prepared.request.requestId)
        val unknown = send(route, prepared.caller, UUID.randomUUID())

        assertEquals(route.refusalStatus, hidden.statusCode, hidden.asString())
        assertEquals(hidden.statusCode, unknown.statusCode, unknown.asString())
        if (route.refusalStatus == 403) {
            assertEquals("INFORMATION_REQUEST_FORBIDDEN", hidden.jsonPath().getString("reasonCode"), hidden.asString())
            assertEquals(
                "INFORMATION_REQUEST_FORBIDDEN",
                unknown.jsonPath().getString("reasonCode"),
                unknown.asString()
            )
        }
        assertEquals(hidden.asString(), unknown.asString())
    }

    @Test
    fun `a signed-in caller cannot tell an Exchange hidden from them from one that does not exist when listing its requests`() {
        val prepared = fixture()

        val hidden = listing(prepared.caller, prepared.request.exchangeId)
        val unknown = listing(prepared.caller, UUID.randomUUID())

        assertEquals(403, hidden.statusCode, hidden.asString())
        assertEquals(hidden.statusCode, unknown.statusCode, unknown.asString())
        assertEquals(hidden.asString(), unknown.asString())
    }

    private fun listing(caller: InformationRequestSignedInCaller, exchangeId: UUID): Response =
        caller.authorized().get("/exchanges/$exchangeId/information-requests")

    private fun send(route: UnknownTargetRoute, caller: InformationRequestSignedInCaller, requestId: UUID): Response {
        val spec = caller.authorized()
            .header("Idempotency-Key", "unknown-target-${UUID.randomUUID()}")
            .header("If-Match", "\"unknown-target\"")
            .headers(route.headers)
        val prepared = when {
            route.multipart -> spec.multiPart("file", uploadFile(), "application/pdf")
            route.body != null -> spec.contentType(ContentType.JSON).body(route.body)
            else -> spec.contentType(ContentType.JSON)
        }
        return prepared.request(route.verb, route.path(requestId, OTHER))
    }

    private fun uploadFile() = Files.createTempFile("request-target-", ".pdf").toFile().apply {
        writeText("%PDF-1.4")
        deleteOnExit()
    }

    private fun fixture(): UnknownTargetFixture =
        prepared ?: UnknownTargetFixture(
            hiddenRequest(),
            InformationRequestSignedInCaller.signIn(appUsers, support.outsider()),
        ).also { prepared = it }

    private fun hiddenRequest(): PublishedRequest =
        support.issue(
            "unknown-target-${UUID.randomUUID()}",
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
        }

    companion object {
        private var prepared: UnknownTargetFixture? = null

        private val OTHER: UUID = UUID.randomUUID()
        private const val BASE = "/information-requests/{id}"
        private const val EVIDENCE = "$BASE/requirements/{other}/evidence-artifacts"
        private const val REVIEW = "$BASE/reviews/{other}"
        private const val ASSIGNMENT = "$REVIEW/assignments/{other}"
        private const val ITEM = "\"submissionItemId\":\"{other}\""
        private const val SESSION_TOKEN = "X-Request-Session-Token"

        private val ROUTE_LIST = listOf(
            UnknownTargetRoute("GET", BASE),
            UnknownTargetRoute("GET", "$BASE/response-workspace"),
            UnknownTargetRoute("POST", "$BASE/issuance"),
            UnknownTargetRoute("POST", "$BASE/cancellation", "{\"reasonCode\":\"no_longer_needed\"}"),
            UnknownTargetRoute(
                "POST",
                "$BASE/supersession",
                "{\"supersededByRequestId\":\"{other}\",\"reasonCode\":\"replaced\"}"
            ),
            UnknownTargetRoute("GET", "$BASE/accepted-fact-offers"),
            UnknownTargetRoute(
                "POST", "$BASE/accepted-fact-offers/{other}/recertifications",
                "{\"requirementId\":\"{other}\",\"assented\":true}",
            ),
            UnknownTargetRoute("GET", "$BASE/accepted-facts"),
            UnknownTargetRoute(
                "POST", "$BASE/accepted-facts",
                "{\"packageId\":\"{other}\",$ITEM,\"purposeKey\":\"record-review\",\"policyBasisKey\":\"retained-confirmation\"}",
            ),
            UnknownTargetRoute("POST", "$BASE/accepted-facts/{other}/revocation", "{\"reasonCode\":\"withdrawn\"}"),
            UnknownTargetRoute("POST", "$BASE/access-links", "{\"partyId\":\"{other}\"}"),
            UnknownTargetRoute("GET", "$BASE/access-links"),
            UnknownTargetRoute("POST", "$BASE/access-links/{other}/replacement", "{}"),
            UnknownTargetRoute("POST", "$BASE/access-links/{other}/revocation"),
            UnknownTargetRoute("POST", "$BASE/access-links/{other}/rotation"),
            UnknownTargetRoute("POST", "$BASE/amendments", "{\"targetTemplateVersionId\":\"{other}\"}"),
            UnknownTargetRoute("GET", "$BASE/amendments"),
            UnknownTargetRoute("GET", "$BASE/audit-events"),
            UnknownTargetRoute("GET", "$BASE/audit-reconciliation"),
            UnknownTargetRoute("GET", "$BASE/business-decisions"),
            UnknownTargetRoute(
                "POST", "$BASE/business-decisions",
                "{\"owningProcessKey\":\"record-review\",\"outcomeCode\":\"completed\",\"kind\":\"ORIGINAL\",\"decidedAt\":1788249600000}",
            ),
            UnknownTargetRoute("GET", "$BASE/carry-forwards"),
            UnknownTargetRoute("GET", "$BASE/clocks"),
            UnknownTargetRoute("POST", "$BASE/clocks", "{\"clockKey\":\"response\",\"policyVersionId\":\"{other}\"}"),
            UnknownTargetRoute(
                "POST",
                "$BASE/clocks/{other}/extensions",
                "{\"reasonCode\":\"more_time\",\"extensionMinutes\":60}"
            ),
            UnknownTargetRoute("POST", "$BASE/clocks/{other}/pauses", "{\"reasonCode\":\"waiting\"}"),
            UnknownTargetRoute("POST", "$BASE/clocks/{other}/resumptions", "{\"reasonCode\":\"ready\"}"),
            UnknownTargetRoute("PUT", "$BASE/completion-gate", "{\"gatesExchangeClosure\":false}"),
            UnknownTargetRoute("GET", "$BASE/connector-exchanges"),
            UnknownTargetRoute(
                "POST",
                "$BASE/connector-exchanges",
                "{\"requirementId\":\"{other}\",\"connectorKey\":\"records\"}"
            ),
            UnknownTargetRoute(
                "POST", "$BASE/delegated-authorities",
                "{\"assignedPartyId\":\"{other}\",\"delegatePrincipalKind\":\"USER\",\"delegatePrincipalId\":\"{other}\"}",
            ),
            UnknownTargetRoute("GET", "$BASE/delegated-authorities"),
            UnknownTargetRoute("POST", "$BASE/delegated-authorities/{other}/revocations", "{}"),
            UnknownTargetRoute("GET", "$BASE/disposal-standing"),
            UnknownTargetRoute("GET", "$BASE/document-placeholders"),
            UnknownTargetRoute("GET", "$BASE/generated-outputs"),
            UnknownTargetRoute(
                "POST", "$BASE/generated-outputs",
                "{\"outputKey\":\"summary\",\"externalReference\":\"summary-1\",\"producedBySource\":\"records\",\"producedAt\":1788249600000}",
            ),
            UnknownTargetRoute("POST", "$BASE/group-occurrences", "{\"groupKey\":\"entries\"}"),
            UnknownTargetRoute(
                "PATCH",
                "$BASE/group-occurrences/order",
                "{\"groupKey\":\"entries\",\"occurrenceIds\":[\"{other}\"]}"
            ),
            UnknownTargetRoute("DELETE", "$BASE/group-occurrences/{other}"),
            UnknownTargetRoute(
                "POST", "$BASE/imported-value-discrepancies/{other}/resolutions",
                "{\"resolution\":\"RESPONSE_STANDS\",\"reasonCode\":\"checked\"}",
            ),
            UnknownTargetRoute("POST", "$BASE/imported-value-reconciliations"),
            UnknownTargetRoute("GET", "$BASE/imported-values"),
            UnknownTargetRoute(
                "POST", "$BASE/imported-values",
                "{\"requirementId\":\"{other}\",\"resultKey\":\"count\",\"valueType\":\"SHORT_TEXT\",\"value\":\"12\"," +
                        "\"sourceReference\":\"records\",\"provenanceReference\":\"records-1\"}",
            ),
            UnknownTargetRoute(
                "POST",
                "$BASE/imported-values/{other}/decisions",
                "{\"decision\":\"ACCEPTED\",\"reasonCode\":\"checked\"}"
            ),
            UnknownTargetRoute("GET", "$BASE/notices"),
            UnknownTargetRoute(
                "POST", "$BASE/participant-account-links", "{\"sessionId\":\"{other}\"}",
                headers = mapOf(SESSION_TOKEN to "unknown-target-session"),
                refusalStatus = 409,
            ),
            UnknownTargetRoute(
                "POST",
                "$BASE/parties",
                "{\"roleKey\":\"CONTRIBUTOR\",\"email\":\"member@process.test\"}"
            ),
            UnknownTargetRoute("GET", "$BASE/parties"),
            UnknownTargetRoute("POST", "$BASE/parties/{other}/reassignment", "{\"userId\":\"{other}\"}"),
            UnknownTargetRoute("GET", "$BASE/parties/{other}/recipient-preview"),
            UnknownTargetRoute("POST", "$BASE/parties/{other}/revocation"),
            UnknownTargetRoute("POST", "$BASE/record-exports", "{}"),
            UnknownTargetRoute("GET", "$BASE/record-exports"),
            UnknownTargetRoute("GET", "$BASE/record-exports/{other}"),
            UnknownTargetRoute(
                "POST", "$BASE/recurrences",
                "{\"intervalUnit\":\"MONTH\",\"intervalCount\":1,\"firstDueAt\":\"2027-01-01T00:00:00Z\"}",
            ),
            UnknownTargetRoute("POST", "$BASE/recurrences/{other}/occurrences"),
            UnknownTargetRoute(
                "POST",
                "$BASE/refresh-rules",
                "{\"requirementKey\":\"record-identifier\",\"leadDays\":30}"
            ),
            UnknownTargetRoute("POST", "$BASE/refresh-rules/{other}/refreshes"),
            UnknownTargetRoute("POST", "$BASE/requirements/{other}/attestations", "{\"decision\":\"ASSENTED\"}"),
            UnknownTargetRoute("GET", EVIDENCE),
            UnknownTargetRoute("POST", EVIDENCE, multipart = true),
            UnknownTargetRoute("GET", "$EVIDENCE/{other}"),
            UnknownTargetRoute("POST", "$EVIDENCE/{other}/removals", "{}"),
            UnknownTargetRoute("POST", "$EVIDENCE/{other}/versions", multipart = true),
            UnknownTargetRoute("GET", "$EVIDENCE/{other}/versions/{other}/content"),
            UnknownTargetRoute("GET", "$EVIDENCE/{other}/versions/{other}/preview"),
            UnknownTargetRoute("POST", "$EVIDENCE/{other}/withdrawals", "{}"),
            UnknownTargetRoute("PATCH", "$BASE/responses", "{\"patches\":[{\"requirementId\":\"{other}\"}]}"),
            UnknownTargetRoute("GET", "$BASE/review-results"),
            UnknownTargetRoute("GET", "$BASE/reviews"),
            UnknownTargetRoute("GET", REVIEW),
            UnknownTargetRoute("POST", "$REVIEW/appeals", "{\"reason\":\"reconsider_outcome\"}"),
            UnknownTargetRoute(
                "POST",
                "$REVIEW/assignments",
                "{\"stageKey\":\"review\",\"reviewerPartyId\":\"{other}\"}"
            ),
            UnknownTargetRoute("POST", "$ASSIGNMENT/decisions"),
            UnknownTargetRoute(
                "POST",
                "$ASSIGNMENT/delegation",
                "{\"reasonCode\":\"away\",\"delegatePartyId\":\"{other}\"}"
            ),
            UnknownTargetRoute("POST", "$ASSIGNMENT/recusal", "{\"reasonCode\":\"conflict\"}"),
            UnknownTargetRoute("POST", "$ASSIGNMENT/revocation", "{\"reasonCode\":\"reassigned\"}"),
            UnknownTargetRoute("PATCH", "$ASSIGNMENT/worksheet", "{\"entries\":[{$ITEM,\"outcome\":\"SATISFIED\"}]}"),
            UnknownTargetRoute("POST", "$REVIEW/comments", "{$ITEM,\"body\":\"A comment\"}"),
            UnknownTargetRoute(
                "POST", "$REVIEW/findings",
                "{$ITEM,\"reasonCode\":\"missing_record\",\"narrative\":\"A record is missing\",\"severity\":\"MINOR\"," +
                        "\"visibility\":\"REVIEWERS_ONLY\"}",
            ),
            UnknownTargetRoute(
                "POST", "$REVIEW/overrides",
                "{\"stageKey\":\"review\",$ITEM,\"outcome\":\"SATISFIED\",\"narrative\":\"Checked again\"}",
            ),
            UnknownTargetRoute("POST", "$REVIEW/reconsiderations", "{\"reason\":\"reconsider_outcome\"}"),
            UnknownTargetRoute("POST", "$BASE/subjects", "{\"subjectKind\":\"RECORD\"}"),
            UnknownTargetRoute("GET", "$BASE/submission-preview"),
            UnknownTargetRoute("GET", "$BASE/submissions"),
            UnknownTargetRoute("POST", "$BASE/submissions", "{}"),
            UnknownTargetRoute("GET", "$BASE/submissions/{other}"),
            UnknownTargetRoute("GET", "$BASE/submissions/{other}/evidence/{other}/content"),
            UnknownTargetRoute("GET", "$BASE/submissions/{other}/evidence/{other}/preview"),
            UnknownTargetRoute("POST", "$BASE/submissions/{other}/withdrawal", "{}"),
            UnknownTargetRoute("POST", "$BASE/successors", "{\"kind\":\"SUPPLEMENT\",\"reasonCode\":\"more_records\"}"),
            UnknownTargetRoute("GET", "$BASE/successors"),
            UnknownTargetRoute("GET", "$BASE/trusted-recipient-candidates"),
        )

        private val ROUTES: Map<String, UnknownTargetRoute> =
            ROUTE_LIST.associateBy { "${it.verb} ${it.route.removePrefix(BASE).ifEmpty { "/" }}" }
                .mapValues { (_, route) -> route.copy(body = route.body?.replace("{other}", OTHER.toString())) }

        @JvmStatic
        fun routes(): List<String> = ROUTES.keys.toList()
    }
}
