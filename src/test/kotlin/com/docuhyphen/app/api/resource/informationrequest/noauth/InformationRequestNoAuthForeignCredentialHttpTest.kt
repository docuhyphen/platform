package com.docuhyphen.app.api.resource.informationrequest.noauth

import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionRequest
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.communication.OtpService
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.conformance.PublishedRequestSupport
import com.docuhyphen.app.api.service.informationrequest.conformance.model.PublishedRequest
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestContactProofService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseWorkspaceService
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationWriter
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateMaterializer
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusMock
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import io.restassured.response.Response
import io.restassured.specification.RequestSpecification
import jakarta.inject.Inject
import jakarta.ws.rs.core.HttpHeaders
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestNoAuthForeignCredentialHttpTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var requirementRepository: InformationRequestRequirementRepository

    @Inject
    lateinit var definitionRepository: InformationRequestTemplateDefinitionRepository

    @Inject
    lateinit var versionRepository: InformationRequestTemplateVersionRepository

    @Inject
    lateinit var capabilityRepository: InformationRequestTemplateVersionCapabilityRepository

    @Inject
    lateinit var configurationWriter: InformationRequestTemplateConfigurationWriter

    @Inject
    lateinit var materializer: InformationRequestTemplateMaterializer

    @Inject
    lateinit var contactProof: InformationRequestContactProofService

    @Inject
    lateinit var otp: OtpService

    @Inject
    lateinit var workspaces: InformationRequestResponseWorkspaceService

    @Inject
    lateinit var displays: com.docuhyphen.app.api.service.identity.PrincipalDisplayService

    @Test
    fun `an access-link party read retains its safe label and the party collection revision`() {
        val request = issue()
        val token = "party-read-${UUID.randomUUID()}"
        val linkId = insertRequestLink(request, token)
        val session = openRequestSession(token, linkId)
        val expectedName = displays.display(request.principal("CONTRIBUTOR")).name
        org.junit.jupiter.api.Assertions.assertTrue(!expectedName.isNullOrBlank())
        val response = linked(token, session).get("/no-auth/information-requests/${request.requestId}/parties")
        assertEquals(200, response.statusCode, response.asString())
        val expectedETag = com.docuhyphen.app.api.service.informationrequest.InformationRequestETag.partiesOf(
            requireNotNull(requestRepository.findById(request.requestId)),
        )
        assertAll(
            { assertEquals(expectedETag, response.header("ETag")) },
            {
                assertEquals(
                    expectedName,
                    response.jsonPath()
                        .getString("find { it.id == '${request.parties.getValue("CONTRIBUTOR")}' }.label")
                )
            },
        )
    }

    private val published by lazy {
        PublishedRequestSupport(
            dataSource,
            requestRepository,
            requirementRepository,
            definitionRepository,
            versionRepository,
            capabilityRepository,
            configurationWriter,
            materializer,
        )
    }

    @Test
    fun `an Exchange direct-grant link token neither proves contact nor opens a request beside a valid request session`() {
        val request = issue()
        val requestToken = "request-link-${UUID.randomUUID()}"
        val requestLinkId = insertRequestLink(request, requestToken)
        val exchangeToken = "exchange-link-${UUID.randomUUID()}"
        val exchangeLinkId = insertExchangeDirectGrantLink(request, exchangeToken)
        val email = mock<EmailService>()
        QuarkusMock.installMockForType(email, EmailService::class.java)

        val challenge = given()
            .header(InformationRequestCommandHttp.ACCESS_LINK_TOKEN_HEADER, exchangeToken)
            .contentType(ContentType.JSON)
            .post("/no-auth/information-request-access-links/challenges")
        val session = given()
            .header(InformationRequestCommandHttp.ACCESS_LINK_TOKEN_HEADER, exchangeToken)
            .contentType(ContentType.JSON)
            .body("{\"otp\":\"123456\"}")
            .post("/no-auth/information-request-access-links/sessions")

        assertRefusedAsInvalidLink(challenge)
        assertRefusedAsInvalidLink(session)
        verifyNoInteractions(email)

        val sessionToken = openRequestSession(requestToken, requestLinkId)
        val own = workspace(request, requestToken, sessionToken)
        val swapped = workspace(request, exchangeToken, sessionToken)

        assertEquals(200, own.statusCode, own.body.asString())
        assertRefusedAsInvalidLink(swapped)
        dataSource.connection.use { connection ->
            assertEquals(
                1,
                queryInt(
                    connection,
                    "SELECT count(*) FROM request_access_session WHERE share_link_id = ?",
                    requestLinkId
                )
            )
            assertEquals(
                0,
                queryInt(
                    connection,
                    "SELECT count(*) FROM request_access_session WHERE share_link_id = ?",
                    exchangeLinkId
                )
            )
        }
    }

    @Test
    fun `a verified session for one request answers not found on another request's routes without spending a use`() {
        val own = issue()
        val other = issue()
        val token = "request-link-${UUID.randomUUID()}"
        val linkId = insertRequestLink(own, token)
        val sessionToken = openRequestSession(token, linkId)
        val otherRoot = "/no-auth/information-requests/${other.requestId}"
        val otherRequirementId = published.requirement(other, FIELD_KEY).id
        val reads = listOf(
            "",
            "/parties",
            "/response-workspace",
            "/submissions",
            "/submission-preview",
            "/review-results",
            "/amendments",
            "/accepted-fact-offers",
            "/carry-forwards",
            "/requirements/$otherRequirementId/evidence-artifacts",
        )

        val refusals =
            reads.map { route -> "GET $route" to linked(token, sessionToken).get("$otherRoot$route") } + listOf(
                "PATCH /responses" to command(token, sessionToken, "cross-request-patch")
                    .body("{\"patches\":[{\"requirementId\":\"$otherRequirementId\",\"disposition\":\"PROVIDED\"}]}")
                    .patch("$otherRoot/responses"),
                "POST /submissions" to command(token, sessionToken, "cross-request-submit")
                    .body("{}")
                    .post("$otherRoot/submissions"),
            )

        assertAll(
            refusals.map { (route, response) ->
                Executable {
                    assertEquals(404, response.statusCode, "$route ${response.body.asString()}")
                    assertEquals(
                        InformationRequestErrorCatalog.NOT_FOUND,
                        response.jsonPath().getString("reasonCode"),
                        route
                    )
                }
            } + Executable { assertEquals(0, sessionUses(linkId)) },
        )
        assertEquals(200, workspace(own, token, sessionToken).statusCode)
        assertEquals(1, sessionUses(linkId))
    }

    @Test
    fun `a signed-in respondent keeps the authenticated workspace while the Exchange requires recipient sign-in`() {
        val request = issue(participants = emptySet())
        dataSource.connection.use { connection ->
            execute(connection, "UPDATE exchange SET require_recipient_sign_in = TRUE WHERE id = ?", request.exchangeId)
        }

        val workspace =
            QuarkusTransaction.requiringNew().call { workspaces.load(request.requestId, request.access("CONTRIBUTOR")) }

        assertEquals(
            setOf(FIELD_KEY),
            workspace.templateVersion.sections.flatMap { it.requirements }.map { it.requirementKey }.toSet(),
        )
    }

    private fun assertRefusedAsInvalidLink(response: Response) {
        assertEquals(409, response.statusCode, response.body.asString())
        assertEquals(InformationRequestErrorCatalog.ACCESS_LINK_INVALID, response.jsonPath().getString("reasonCode"))
    }

    private fun workspace(request: PublishedRequest, accessToken: String, sessionToken: String): Response =
        given()
            .header(InformationRequestCommandHttp.ACCESS_LINK_TOKEN_HEADER, accessToken)
            .header(InformationRequestCommandHttp.SESSION_TOKEN_HEADER, sessionToken)
            .get("/no-auth/information-requests/${request.requestId}/response-workspace")

    private fun linked(accessToken: String, sessionToken: String): RequestSpecification =
        given()
            .header(InformationRequestCommandHttp.ACCESS_LINK_TOKEN_HEADER, accessToken)
            .header(InformationRequestCommandHttp.SESSION_TOKEN_HEADER, sessionToken)

    private fun command(accessToken: String, sessionToken: String, idempotencyKey: String): RequestSpecification =
        linked(accessToken, sessionToken)
            .header(HttpHeaders.IF_MATCH, "\"cross-request\"")
            .header(InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER, idempotencyKey)
            .contentType(ContentType.JSON)

    private fun sessionUses(linkId: UUID): Int =
        dataSource.connection.use { connection ->
            queryInt(connection, "SELECT use_count FROM request_access_session WHERE share_link_id = ?", linkId)
        }

    private fun openRequestSession(requestToken: String, linkId: UUID): String {
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                UPDATE share_link SET contact_otp_hash = ?, contact_otp_expires_at = now() + interval '10 minutes',
                    contact_otp_challenge_count = 1
                WHERE id = ?
                """.trimIndent(),
                otp.hashOtp("123456"),
                linkId,
            )
        }
        return contactProof.verifyChallenge(requestToken, "123456").sessionToken
    }

    private fun issue(participants: Set<String> = setOf("CONTRIBUTOR")): PublishedRequest =
        published.issue(
            "foreign-credential-request",
            listOf(FIELD_KEY),
            listOf("CONTRIBUTOR"),
            participants = participants,
        ) { schemaVersionId, fields ->
            InformationRequestTemplateConfigurationRequest(
                schemaVersionId = schemaVersionId,
                sections = listOf(
                    InformationRequestTemplateSectionRequest(
                        sectionKey = "responses",
                        title = "Responses",
                        requirements = listOf(
                            InformationRequestTemplateRequirementRequest(
                                requirementKey = FIELD_KEY,
                                requirementType = InformationRequestRequirementType.FIELD,
                                prompt = "Describe the item",
                                responseMode = InformationRequestResponseMode.PROVIDE,
                                requiredness = InformationRequestRequiredness.REQUIRED,
                                contributorRole = InformationRequestContributorRole.CONTRIBUTOR,
                                collectedFieldDefinitionId = fields.getValue(FIELD_KEY).fieldDefinitionId,
                                permittedDispositions = listOf(InformationRequestResponseDisposition.PROVIDED),
                            ),
                        ),
                    ),
                ),
            )
        }

    private fun insertRequestLink(request: PublishedRequest, rawToken: String): UUID {
        val linkId = UUID.randomUUID()
        dataSource.connection.use { connection ->
            val shareId = queryString(
                connection,
                "SELECT share_id::text FROM information_request_party WHERE id = ?",
                request.parties.getValue("CONTRIBUTOR"),
            )
            execute(
                connection,
                """
                INSERT INTO share_link
                    (id, share_id, token_hash, status, link_mode, used_count, require_mfa, contact_otp_failed_attempts,
                     contact_otp_challenge_count, rotation_count, max_uses, expires_at, created_at, verification_strength)
                VALUES (?, ?, ?, 'ACTIVE', 'VERIFICATION_BOOTSTRAP', 0, FALSE, 0, 0, 0, 5, now() + interval '30 days', now(), 'EMAIL_OTP')
                """.trimIndent(),
                linkId,
                UUID.fromString(shareId),
                CommandRequestFingerprint.sha256Hex(rawToken),
            )
        }
        return linkId
    }

    private fun insertExchangeDirectGrantLink(request: PublishedRequest, rawToken: String): UUID {
        val recipientShareId = UUID.randomUUID()
        val linkId = UUID.randomUUID()
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
                VALUES (?, 'EXCHANGE', ?, 'USER', ?, 'VIEWER', 'DIRECT', 'ACTIVE', now())
                """.trimIndent(),
                recipientShareId,
                request.exchangeId,
                UUID.randomUUID(),
            )
            execute(
                connection,
                """
                INSERT INTO share_link (id, share_id, token_hash, status, created_at, link_mode)
                VALUES (?, ?, ?, 'ACTIVE', now(), 'DIRECT_GRANT')
                """.trimIndent(),
                linkId,
                recipientShareId,
                CommandRequestFingerprint.sha256Hex(rawToken),
            )
        }
        return linkId
    }

    private companion object {
        const val FIELD_KEY = "recorded-note"
    }
}
