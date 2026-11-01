package com.docuhyphen.app.api.service.informationrequest.noauth

import com.docuhyphen.app.api.exception.InformationRequestVerificationRequiredException
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.migration.queryStrings
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.dto.InformationRequestResponseWorkspaceDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionRequest
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.FieldValueEntry
import com.docuhyphen.app.api.model.fields.FieldsPrecondition
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestResponsePatch
import com.docuhyphen.app.api.model.informationrequest.response.PatchInformationRequestResponsesCommand
import com.docuhyphen.app.api.model.informationrequest.response.ResponseFieldValuesPatch
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.service.audit.AuditCaptureFailedException
import com.docuhyphen.app.api.model.audit.AuditCaptureResult
import com.docuhyphen.app.api.model.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.communication.OtpService
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.capability.model.WalkingSkeletonField
import com.docuhyphen.app.api.service.informationrequest.conformance.PublishedRequestSupport
import com.docuhyphen.app.api.service.informationrequest.conformance.model.PublishedRequest
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseDraftService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseWorkspaceService
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationWriter
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateMaterializer
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.security.ForbiddenException
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusMock
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import jakarta.inject.Inject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestAccessSessionStepUpTransactionTest {
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
    lateinit var stepUp: InformationRequestAccessSessionStepUpService

    @Inject
    lateinit var readAccess: InformationRequestNoAuthReadAccessService

    @Inject
    lateinit var workspaces: InformationRequestResponseWorkspaceService

    @Inject
    lateinit var drafts: InformationRequestResponseDraftService

    @Inject
    lateinit var otp: OtpService

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
    fun `an access-link respondent sees and answers a protected Requirement only while recently verified`() {
        val scenario = scenario()
        val email = installEmailCapture()
        val sessionToken = scenario.openSession()
        scenario.ageSession()

        val locked = scenario.workspace(sessionToken)
        assertEquals(1, locked.requirementsAwaitingStepUp)
        assertEquals(setOf(OPEN_KEY), requirementKeys(locked))
        assertThrows(ForbiddenException::class.java) {
            scenario.answer(
                sessionToken,
                "restricted detail",
                "save-before-step-up"
            )
        }

        stepUp.issueChallenge(scenario.rawToken, sessionToken)
        val code = sentCode(email)
        val verified = stepUp.verify(scenario.rawToken, sessionToken, code)

        assertTrue(verified.verifiedUntil.toInstant().isAfter(Instant.now().plus(14, ChronoUnit.MINUTES)))
        val open = scenario.workspace(sessionToken)
        assertEquals(0, open.requirementsAwaitingStepUp)
        assertEquals(setOf(OPEN_KEY, PROTECTED_KEY), requirementKeys(open))
        scenario.answer(sessionToken, "restricted detail", "save-after-step-up")
        assertEquals(1, scenario.protectedResponseCount())

        scenario.expireStepUp()
        val relocked = scenario.workspace(sessionToken)
        assertEquals(1, relocked.requirementsAwaitingStepUp)
        assertEquals(setOf(OPEN_KEY), requirementKeys(relocked))

        assertEquals(
            listOf(
                "ISSUE_ACCESS_SESSION|",
                "ISSUE_CONTACT_CHALLENGE|ACCESS_SESSION_STEP_UP",
                "STEP_UP_ACCESS_SESSION|"
            ),
            scenario.credentialHistory(),
        )
        assertEquals(1, scenario.recorded("information_request.access_session.step_up"))
        assertEquals(1, scenario.recorded("information_request.contact_challenge.issue"))
        scenario.assertNoContactMaterial(listOf(code, scenario.email, sessionToken.substringAfter('.')))
    }

    @Test
    fun `a step-up code is sent only once its challenge is committed and the request is no longer locked`() {
        val scenario = scenario()
        val email = installEmailCapture()
        val sessionToken = scenario.openSession()
        val seenAtSend = mutableListOf<Pair<String?, Boolean>>()
        doAnswer {
            seenAtSend += CompletableFuture
                .supplyAsync { scenario.sessionValue("step_up_challenge_count") to scenario.exchangeLockable() }
                .get(10, TimeUnit.SECONDS)
            null
        }.whenever(email).sendEmail(any(), any(), any(), any())

        stepUp.issueChallenge(scenario.rawToken, sessionToken)

        assertEquals(listOf("1" to true), seenAtSend)
    }

    @Test
    fun `refused step-up codes are recorded and the lockout outlives the refused transactions`() {
        val scenario = scenario()
        val sessionToken = scenario.openSession()
        scenario.storeStepUpCode("654321")

        repeat(5) {
            val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
                stepUp.verify(scenario.rawToken, sessionToken, "000000")
            }
            assertEquals(InformationRequestErrorCatalog.CONTACT_PROOF_INVALID, refusal.reasonCode)
        }

        assertEquals(
            listOf("ISSUE_ACCESS_SESSION|") + List(5) { "REFUSE_CONTACT_CHALLENGE|CONTACT_PROOF_INVALID" },
            scenario.credentialHistory()
        )
        assertEquals(
            setOf(scenario.sessionId().toString()),
            scenario.payloadValues("information_request.contact_challenge.refuse", "accessSessionId")
        )
        assertNotNull(scenario.sessionValue("step_up_locked_until::text"))
        assertNull(scenario.sessionValue("stepped_up_at::text"))
    }

    @Test
    fun `a step-up that cannot be recorded leaves the session unverified and its code unspent`() {
        val scenario = scenario()
        val sessionToken = scenario.openSession()
        scenario.storeStepUpCode("654321")
        installDegradedAudit("information_request.access_session.step_up")

        val refusal = assertThrows(Throwable::class.java) { stepUp.verify(scenario.rawToken, sessionToken, "654321") }

        assertTrue(generateSequence(refusal) { it.cause }.any { it is AuditCaptureFailedException })
        assertNull(scenario.sessionValue("stepped_up_at::text"))
        assertNotNull(scenario.sessionValue("step_up_code_hash"))
        assertEquals(listOf("ISSUE_ACCESS_SESSION|"), scenario.credentialHistory())
    }

    @Test
    fun `an access resolved while recently verified answers a protected Requirement only while that verification lasts`() {
        val scenario = scenario()
        val sessionToken = scenario.openSession()
        val held = readAccess.resolve(scenario.request.requestId, scenario.rawToken, sessionToken)
        assertTrue(held.authorization.mfaSatisfied)
        scenario.ageSession()

        val refused = assertThrows(InformationRequestVerificationRequiredException::class.java) {
            scenario.answer(sessionToken, "restricted detail", "save-after-verification-lapsed", held = held)
        }

        assertEquals("This protected item needs you to verify again before it can be saved", refused.message)
        assertEquals(0, scenario.responseCount(PROTECTED_KEY))
        scenario.answer(sessionToken, "item detail", "save-open-item-after-verification-lapsed", OPEN_KEY, held)
        assertEquals(1, scenario.responseCount(OPEN_KEY))
    }

    @Test
    fun `a save naming a protected Requirement after verification lapsed answers that it needs verification`() {
        val scenario = scenario()
        val sessionToken = scenario.openSession()
        scenario.ageSession()

        val lapsed = scenario.patchOverHttp(sessionToken, published.requirement(scenario.request, PROTECTED_KEY).id)
        val unknown = scenario.patchOverHttp(sessionToken, UUID.randomUUID())

        assertEquals(403, lapsed.statusCode, lapsed.asString())
        assertEquals(InformationRequestErrorCatalog.CONFIDENTIALITY_DENIED, lapsed.jsonPath().getString("reasonCode"))
        assertEquals(403, unknown.statusCode, unknown.asString())
        assertNotEquals(
            InformationRequestErrorCatalog.CONFIDENTIALITY_DENIED,
            unknown.jsonPath().getString("reasonCode")
        )
        assertEquals(0, scenario.protectedResponseCount())
    }

    @Test
    fun `a workspace read with an access whose verification has since lapsed shows only what current verification allows`() {
        val scenario = scenario()
        val sessionToken = scenario.openSession()
        val held = readAccess.resolve(scenario.request.requestId, scenario.rawToken, sessionToken)
        val verified = readAccess.readCurrent(scenario.request.requestId, held) {
            workspaces.load(scenario.request.requestId, it)
        }
        assertEquals(setOf(OPEN_KEY, PROTECTED_KEY), requirementKeys(verified))
        scenario.ageSession()

        val lapsed = readAccess.readCurrent(scenario.request.requestId, held) {
            workspaces.load(scenario.request.requestId, it)
        }

        assertEquals(setOf(OPEN_KEY), requirementKeys(lapsed))
        assertEquals(1, lapsed.requirementsAwaitingStepUp)
    }

    private fun scenario(): StepUpScenario {
        val issued = published.issue(
            "protected-access-link-request",
            listOf(OPEN_KEY, PROTECTED_KEY),
            listOf("CONTRIBUTOR"),
            participants = setOf("CONTRIBUTOR"),
        ) { schemaVersionId, fields ->
            InformationRequestTemplateConfigurationRequest(
                schemaVersionId = schemaVersionId,
                sections = listOf(
                    InformationRequestTemplateSectionRequest(
                        sectionKey = "responses",
                        title = "Responses",
                        requirements = listOf(
                            fieldRequirement(OPEN_KEY, "Describe the item", fields.getValue(OPEN_KEY), null),
                            fieldRequirement(
                                PROTECTED_KEY,
                                "Describe the restricted item",
                                fields.getValue(PROTECTED_KEY),
                                "restricted-response"
                            ),
                        ),
                    ),
                ),
            )
        }
        val partyId = issued.parties.getValue("CONTRIBUTOR")
        val rawToken = "step-up-token-${UUID.randomUUID()}"
        val linkId = UUID.randomUUID()
        dataSource.connection.use { connection ->
            val shareId =
                queryString(connection, "SELECT share_id::text FROM information_request_party WHERE id = ?", partyId)
            execute(
                connection, """
                INSERT INTO share_link
                    (id, share_id, token_hash, status, link_mode, used_count, require_mfa, contact_otp_failed_attempts,
                     contact_otp_challenge_count, rotation_count, max_uses, expires_at, created_at, verification_strength)
                VALUES (?, ?, ?, 'ACTIVE', 'VERIFICATION_BOOTSTRAP', 0, FALSE, 0, 0, 0, 1, now() + interval '30 days', now(), 'EMAIL_OTP')
            """.trimIndent(), linkId, UUID.fromString(shareId), CommandRequestFingerprint.sha256Hex(rawToken)
            )
        }
        val email = dataSource.connection.use {
            requireNotNull(
                queryString(
                    it,
                    "SELECT email FROM external_participant WHERE id = ?",
                    issued.users.getValue("CONTRIBUTOR")
                )
            )
        }
        return StepUpScenario(issued, partyId, rawToken, linkId, email)
    }

    private fun fieldRequirement(key: String, prompt: String, field: WalkingSkeletonField, compartment: String?) =
        InformationRequestTemplateRequirementRequest(
            requirementKey = key,
            requirementType = InformationRequestRequirementType.FIELD,
            prompt = prompt,
            responseMode = InformationRequestResponseMode.PROVIDE,
            requiredness = InformationRequestRequiredness.REQUIRED,
            contributorRole = InformationRequestContributorRole.CONTRIBUTOR,
            collectedFieldDefinitionId = field.fieldDefinitionId,
            confidentialityCompartmentKey = compartment,
            permittedDispositions = listOf(InformationRequestResponseDisposition.PROVIDED),
        )

    private fun requirementKeys(workspace: InformationRequestResponseWorkspaceDto): Set<String> =
        workspace.templateVersion.sections.flatMap { it.requirements }.map { it.requirementKey }.toSet()

    private fun installEmailCapture(): EmailService = mock<EmailService>().also {
        QuarkusMock.installMockForType(it, EmailService::class.java)
    }

    private fun sentCode(email: EmailService): String {
        val body = argumentCaptor<String>()
        verify(email).sendEmail(any(), any(), body.capture(), any())
        return requireNotNull(Regex("\\d{6}").find(body.lastValue)).value
    }

    private fun installDegradedAudit(eventTypeKey: String) {
        val recorder = mock<AuditRecorder>()
        whenever(recorder.record(any())).thenAnswer { invocation ->
            val draft = invocation.getArgument<AuditEventDraft>(0)
            if (draft.eventTypeKey == eventTypeKey) AuditCaptureResult.Degraded(
                draft.eventId,
                "Audit outbox unavailable"
            )
            else AuditCaptureResult.Captured(UUID.randomUUID(), draft.eventId)
        }
        QuarkusMock.installMockForType(recorder, AuditRecorder::class.java)
    }

    private inner class StepUpScenario(
        val request: PublishedRequest,
        val partyId: UUID,
        val rawToken: String,
        val linkId: UUID,
        val email: String,
    ) {
        fun openSession(): String {
            dataSource.connection.use { connection ->
                execute(
                    connection, """
                    UPDATE share_link SET contact_otp_hash = ?, contact_otp_expires_at = now() + interval '10 minutes',
                        contact_otp_challenge_count = 1
                    WHERE id = ?
                """.trimIndent(), otp.hashOtp("123456"), linkId
                )
            }
            return contactProof.verifyChallenge(rawToken, "123456").sessionToken
        }

        fun ageSession() = updateSession("issued_at = now() - interval '1 hour'")

        fun expireStepUp() = updateSession("stepped_up_at = now() - interval '1 hour'")

        fun storeStepUpCode(code: String) {
            dataSource.connection.use { connection ->
                execute(
                    connection, """
                    UPDATE request_access_session SET step_up_code_hash = ?, step_up_code_expires_at = now() + interval '10 minutes',
                        step_up_challenge_count = 1
                    WHERE id = ?
                """.trimIndent(), otp.hashOtp(code), sessionId()
                )
            }
        }

        fun workspace(sessionToken: String): InformationRequestResponseWorkspaceDto =
            workspaces.load(request.requestId, readAccess.resolve(request.requestId, rawToken, sessionToken))

        fun answer(
            sessionToken: String,
            value: String,
            idempotencyKey: String,
            key: String = PROTECTED_KEY,
            held: RequestAccessContext? = null,
        ) {
            val access = held ?: readAccess.resolve(request.requestId, rawToken, sessionToken)
            val requirement = published.requirement(request, key)
            QuarkusTransaction.requiringNew().call {
                drafts.patch(
                    PatchInformationRequestResponsesCommand(
                        requestId = request.requestId,
                        access = access,
                        precondition = CommandPrecondition.ExpectedRevision(published.responseETag(request)),
                        idempotencyKey = idempotencyKey,
                        patches = listOf(
                            InformationRequestResponsePatch(
                                requirementId = requirement.id,
                                disposition = InformationRequestResponseDisposition.PROVIDED,
                                fieldValues = ResponseFieldValuesPatch(
                                    entries = listOf(
                                        FieldValueEntry(
                                            request.fields.getValue(key).fieldContractId,
                                            JsonPrimitive(value)
                                        )
                                    ),
                                    precondition = FieldsPrecondition.Unconditioned,
                                ),
                            ),
                        ),
                    ),
                )
            }
        }

        fun patchOverHttp(sessionToken: String, requirementId: UUID): io.restassured.response.Response =
            given()
                .header(InformationRequestCommandHttp.ACCESS_LINK_TOKEN_HEADER, rawToken)
                .header(InformationRequestCommandHttp.SESSION_TOKEN_HEADER, sessionToken)
                .header(InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER, "lapsed-${UUID.randomUUID()}")
                .header("If-Match", published.responseETag(request))
                .contentType(ContentType.JSON)
                .body("{\"patches\":[{\"requirementId\":\"$requirementId\",\"narrative\":\"Neutral note\"}]}")
                .patch("/no-auth/information-requests/${request.requestId}/responses")

        fun protectedResponseCount(): Int = responseCount(PROTECTED_KEY)

        fun responseCount(key: String): Int {
            val requirement = published.requirement(request, key)
            return dataSource.connection.use {
                queryInt(
                    it,
                    "SELECT count(*) FROM information_request_response WHERE information_request_requirement_id = ?",
                    requirement.id
                )
            }
        }

        fun sessionId(): UUID = dataSource.connection.use {
            UUID.fromString(
                requireNotNull(
                    queryString(
                        it,
                        "SELECT id::text FROM request_access_session WHERE share_link_id = ?",
                        linkId
                    )
                )
            )
        }

        fun sessionValue(column: String): String? = dataSource.connection.use {
            queryString(it, "SELECT $column FROM request_access_session WHERE share_link_id = ?", linkId)
        }

        fun exchangeLockable(): Boolean = dataSource.connection.use { probe ->
            probe.autoCommit = false
            try {
                probe.prepareStatement(
                    """
                    SELECT exchange.id FROM exchange JOIN information_request request ON request.exchange_id = exchange.id
                    WHERE request.id = ? FOR UPDATE OF exchange NOWAIT
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, request.requestId)
                    statement.executeQuery().close()
                }
                true
            } catch (_: java.sql.SQLException) {
                false
            } finally {
                probe.rollback()
                probe.autoCommit = true
            }
        }

        fun credentialHistory(): List<String> = dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT mutation, reason_code FROM information_request_transition
                WHERE information_request_id = ? AND mutation IN
                    ('ISSUE_ACCESS_SESSION', 'ISSUE_CONTACT_CHALLENGE', 'REFUSE_CONTACT_CHALLENGE', 'STEP_UP_ACCESS_SESSION')
                ORDER BY sequence_number
            """.trimIndent()
            ).use { statement ->
                statement.setObject(1, request.requestId)
                statement.executeQuery().use { rows ->
                    generateSequence {
                        if (rows.next()) "${rows.getString(1)}|${
                            rows.getString(2).orEmpty()
                        }"
                        else null
                    }.toList()
                }
            }
        }

        fun recorded(eventTypeKey: String): Int = dataSource.connection.use { connection ->
            queryInt(
                connection, """
                SELECT count(*) FROM audit_outbox a
                JOIN information_request_transition t ON t.id::text = a.business_transaction_id
                WHERE a.target_id = ? AND a.event_type_key = ? AND a.actor_kind = 'PUBLIC_LINK' AND a.session_id = ?
                  AND t.party_id = ? AND t.actor_kind = 'PARTICIPANT'
            """.trimIndent(), request.requestId.toString(), eventTypeKey, sessionId().toString(), partyId
            )
        }

        fun payloadValues(eventTypeKey: String, key: String): Set<String?> = dataSource.connection.use { connection ->
            queryStrings(
                connection,
                "SELECT payload_json::jsonb ->> ? FROM audit_outbox WHERE target_id = ? AND event_type_key = ?",
                key,
                request.requestId.toString(),
                eventTypeKey
            )
        }

        fun assertNoContactMaterial(secrets: List<String>) {
            dataSource.connection.use { connection ->
                val payloads = queryStrings(
                    connection,
                    "SELECT payload_json FROM audit_outbox WHERE target_id = ?",
                    request.requestId.toString()
                ) +
                        queryStrings(
                            connection, "SELECT envelope_json FROM workflow_event_outbox WHERE ordering_key = ?",
                            InformationRequestTransitionHistoryService.orderingKeyOf(request.requestId)
                        )
                secrets.filter { it.isNotBlank() }.forEach { secret ->
                    assertTrue(payloads.none { it.contains(secret) }, "Recorded payloads must not contain $secret")
                }
            }
        }

        private fun updateSession(assignment: String) {
            dataSource.connection.use {
                execute(
                    it,
                    "UPDATE request_access_session SET $assignment WHERE share_link_id = ?",
                    linkId
                )
            }
        }
    }

    private companion object {
        const val OPEN_KEY = "item-detail"
        const val PROTECTED_KEY = "restricted-detail"
    }
}
