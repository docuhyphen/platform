package com.docuhyphen.app.api.service.informationrequest.evidence

import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.document.DocumentVersionContent
import com.docuhyphen.app.api.model.document.DocumentVersionReadHandle
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceAttributes
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceSurface
import com.docuhyphen.app.api.model.informationrequest.evidence.UploadInformationRequestEvidenceCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestParentSnapshot
import com.docuhyphen.app.api.repository.informationrequest.noauth.RequestAccessSessionRepository
import com.docuhyphen.app.api.resource.informationrequest.evidence.InformationRequestNoAuthEvidenceResource
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestQueryService
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.access.RequestAccessSessionStepUpWindow
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestContactProofService
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthReadAccessService
import com.docuhyphen.app.api.service.informationrequest.noauth.RequestAccessSessionService
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.StreamingOutput
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.kotlin.*
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.*

class InformationRequestNoAuthEvidenceContentSessionTest {
    private val fixture = InformationRequestEvidenceServiceFixture()
    private val sessionRows = mutableMapOf<UUID, RequestAccessSession>()
    private val sessionRepository = mock<RequestAccessSessionRepository>()
    private val sessions = RequestAccessSessionService(sessionRepository)
    private val contactProof = mock<InformationRequestContactProofService>()
    private val readAccess = InformationRequestNoAuthReadAccessService(
        contactProof,
        sessions,
        InformationRequestAccessContextFactory(mock(), RequestAccessSessionStepUpWindow(Duration.ofMinutes(15))),
    )
    private val requestQueries = mock<InformationRequestQueryService>()
    private val auditRecorder = mock<AuditRecorder>()
    private val resource = InformationRequestNoAuthEvidenceResource(
        fixture.uploadService(),
        fixture.collectionService(),
        fixture.queryService(requestQueries, auditRecorder, readAccess),
        readAccess,
    )
    private val link = ShareLink().apply {
        shareId = UUID.randomUUID()
        linkMode = ShareLinkMode.VERIFICATION_BOOTSTRAP
        expiresAt = Timestamp.from(Instant.now().plusSeconds(3600))
    }
    private val party = partyHeldBy(fixture.respondent)
    private var currentParty = party
    private var signInRequired = false
    private var linkRevoked = false

    init {
        whenever(sessionRepository.requestStateForShare(any()))
            .thenReturn(com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState.ISSUED)
        whenever(sessionRepository.lockParentForShare(any()))
            .thenReturn(InformationRequestParentSnapshot(fixture.exchange.status, lockedForUpdate = true))
        whenever(sessionRepository.save(any())).thenAnswer {
            it.getArgument<RequestAccessSession>(0).also { row -> sessionRows[row.id] = row }
        }
        whenever(sessionRepository.update(any())).thenAnswer { it.getArgument<RequestAccessSession>(0) }
        whenever(sessionRepository.findSessionByIdForUpdate(any())).thenAnswer { sessionRows[it.getArgument(0)] }
        whenever(sessionRepository.findById(any())).thenAnswer { sessionRows[it.getArgument(0)] }
        whenever(sessionRepository.recipientSignInRequired(any())).thenAnswer { signInRequired }
        whenever(contactProof.resolveBootstrapLink(LINK_TOKEN)).thenReturn(link to party)
        whenever(contactProof.resolveBootstrapLinkById(link.id)).thenAnswer {
            if (linkRevoked) throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.ACCESS_LINK_REVOKED,
                "This access link has been revoked",
            )
            link to currentParty
        }
        whenever(requestQueries.findById(any(), any())).thenReturn(fixture.request)
    }

    @ParameterizedTest(name = "{0} when {1} while the file is read")
    @CsvSource(
        "DOWNLOAD, $NO_CHANGE",
        "PREVIEW, $NO_CHANGE",
        "DOWNLOAD, $SESSION_REVOKED",
        "PREVIEW, $SESSION_REVOKED",
        "DOWNLOAD, $SESSION_EXPIRED",
        "PREVIEW, $SESSION_EXPIRED",
        "DOWNLOAD, $SIGN_IN_REQUIRED",
        "PREVIEW, $SIGN_IN_REQUIRED",
        "DOWNLOAD, $LINK_REVOKED",
        "PREVIEW, $LINK_REVOKED",
        "DOWNLOAD, $RECIPIENT_REPLACED",
        "PREVIEW, $RECIPIENT_REPLACED",
        "DOWNLOAD, $PROTECTED_VERIFICATION_LAPSED",
        "PREVIEW, $PROTECTED_VERIFICATION_LAPSED",
        "DOWNLOAD, $UNPROTECTED_VERIFICATION_LAPSED",
        "PREVIEW, $UNPROTECTED_VERIFICATION_LAPSED",
    )
    fun `evidence content is answered only while the session that asked for it is still current once the file is read`(
        use: String,
        change: String,
    ) {
        val artifact = uploadedRecord()
        val issued = sessions.issue(link, fixture.respondent, RequestAccessSessionVerificationStrength.EMAIL_OTP, null)
        if (change == PROTECTED_VERIFICATION_LAPSED) evidenceNeedsRecentVerification()
        val directory = Files.createTempDirectory("evidence-session-read")
        val copy = directory.resolve("record.pdf").toFile().apply { writeBytes(RECORD) }
        var reads = 0
        whenever(fixture.documentVersionRecordingService.open(any())).thenAnswer {
            reads += 1
            changeWhileReading(change, issued.session)
            DocumentVersionContent(DocumentVersionReadHandle.temporaryCopy(copy, directory), "record.pdf")
        }

        val response = answer(use, artifact.id, issued.sessionToken)

        val refusal = REFUSALS[change]
        if (refusal == null) {
            assertEquals(Response.Status.OK.statusCode, response.status, response.entity.toString())
            assertArrayEquals(RECORD, streamed(response))
            assertFalse(directory.toFile().exists())
            verify(auditRecorder, times(1)).record(any())
        } else {
            assertEquals(refusal.first, response.status, response.entity.toString())
            assertEquals(refusal.second, (response.entity as ResponseError).reasonCode)
            assertFalse(response.entity is StreamingOutput)
            assertFalse(directory.toFile().exists())
            verify(auditRecorder, never()).record(any())
        }
        assertEquals(1, reads)
    }

    private fun answer(use: String, artifactId: UUID, sessionToken: String): Response {
        val ids = listOf(fixture.request.id, fixture.documentRequirement.id, artifactId, fixture.versions.single().id)
            .map(UUID::toString)
        return if (use == "DOWNLOAD") resource.download(ids[0], ids[1], ids[2], ids[3], LINK_TOKEN, sessionToken)
        else resource.preview(ids[0], ids[1], ids[2], ids[3], LINK_TOKEN, sessionToken)
    }

    private fun changeWhileReading(change: String, session: RequestAccessSession) {
        when (change) {
            SESSION_REVOKED -> session.revokedAt = Timestamp.from(Instant.now())
            SESSION_EXPIRED -> session.expiresAt = Timestamp.from(Instant.now().minusSeconds(1))
            SIGN_IN_REQUIRED -> signInRequired = true
            LINK_REVOKED -> linkRevoked = true
            RECIPIENT_REPLACED -> currentParty = partyHeldBy(PrincipalRef.participant(UUID.randomUUID()))
            PROTECTED_VERIFICATION_LAPSED, UNPROTECTED_VERIFICATION_LAPSED ->
                session.issuedAt = Timestamp.from(Instant.now().minus(Duration.ofHours(1)))
        }
    }

    private fun uploadedRecord() =
        fixture.uploadService().upload(
            UploadInformationRequestEvidenceCommand(
                requestId = fixture.request.id,
                requirementId = fixture.documentRequirement.id,
                access = fixture.access,
                surface = InformationRequestEvidenceSurface.AUTHENTICATED,
                precondition = CommandPrecondition.Unconditioned,
                idempotencyKey = "stored-record-${UUID.randomUUID()}",
                file = fixture.evidenceFile(content = RECORD.decodeToString()),
                attributes = InformationRequestEvidenceAttributes.NONE,
            ),
        ).artifact.artifact

    private fun evidenceNeedsRecentVerification() {
        val evidenceActions =
            setOf(Action.INFORMATION_REQUEST_EVIDENCE_VIEW, Action.INFORMATION_REQUEST_EVIDENCE_MANAGE)
        whenever(fixture.authorizationService.authorize(any(), any(), any(), any())).thenAnswer {
            if (it.getArgument<Action>(1) in evidenceActions && !it.getArgument<AuthorizationContext>(3).mfaSatisfied)
                Decision.Deny(InformationRequestErrorCatalog.CONFIDENTIALITY_DENIED, "Protected evidence")
            else Decision.Allow()
        }
    }

    private fun partyHeldBy(principal: PrincipalRef) = InformationRequestParty().apply {
        informationRequestId = fixture.request.id
        roleKey = InformationRequestShareRoleKey.CONTRIBUTOR
        principalKind = principal.kind
        principalId = principal.id
        shareId = link.shareId
        active = true
    }

    private fun streamed(response: Response): ByteArray =
        ByteArrayOutputStream().also { (response.entity as StreamingOutput).write(it) }.toByteArray()

    private companion object {
        const val LINK_TOKEN = "bootstrap-link"
        const val NO_CHANGE = "nothing changes"
        const val SESSION_REVOKED = "the session is revoked"
        const val SESSION_EXPIRED = "the session expires"
        const val SIGN_IN_REQUIRED = "the Exchange starts requiring recipients to sign in"
        const val LINK_REVOKED = "the access link is revoked"
        const val RECIPIENT_REPLACED = "the party passes to another recipient"
        const val PROTECTED_VERIFICATION_LAPSED = "verification lapses on protected evidence"
        const val UNPROTECTED_VERIFICATION_LAPSED = "verification lapses on unprotected evidence"

        val RECORD = "%PDF-1.4\n%%EOF\n".toByteArray()

        val REFUSALS = mapOf(
            SESSION_REVOKED to (409 to InformationRequestErrorCatalog.ACCESS_SESSION_REVOKED),
            SESSION_EXPIRED to (409 to InformationRequestErrorCatalog.ACCESS_SESSION_EXPIRED),
            SIGN_IN_REQUIRED to (409 to InformationRequestErrorCatalog.RECIPIENT_SIGN_IN_REQUIRED),
            LINK_REVOKED to (409 to InformationRequestErrorCatalog.ACCESS_LINK_REVOKED),
            RECIPIENT_REPLACED to (403 to InformationRequestErrorCatalog.FORBIDDEN),
            PROTECTED_VERIFICATION_LAPSED to (403 to InformationRequestErrorCatalog.FORBIDDEN),
        )
    }
}
