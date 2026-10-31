package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.DocumentEncryptionMode
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceCollectionState
import com.docuhyphen.app.api.model.informationrequest.evidence.*
import com.docuhyphen.app.api.model.informationrequest.lifecycle.ChangeInformationRequestCompletionGateCommand
import com.docuhyphen.app.api.model.informationrequest.party.GrantInformationRequestDelegatedAuthorityCommand
import com.docuhyphen.app.api.model.informationrequest.party.RevokeInformationRequestDelegatedAuthorityCommand
import com.docuhyphen.app.api.model.informationrequest.party.RevokeInformationRequestPartyCommand
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestResponsePatch
import com.docuhyphen.app.api.model.informationrequest.response.PatchInformationRequestResponsesCommand
import com.docuhyphen.app.api.model.informationrequest.response.ResponseNarrativePatch
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceArtifactRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.conformance.ConformanceRequestSupport
import com.docuhyphen.app.api.service.informationrequest.conformance.model.ConformanceRequest
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceCollectionService
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceUploadService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestCompletionGateService
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestDelegatedAuthorityService
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestPartyRevocationService
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestReplayCurrentStateTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requests: InformationRequestRepository

    @Inject
    lateinit var artifacts: InformationRequestEvidenceArtifactRepository

    @Inject
    lateinit var authorization: AuthorizationService

    @Inject
    lateinit var completionGates: InformationRequestCompletionGateService

    @Inject
    lateinit var partyRevocations: InformationRequestPartyRevocationService

    @Inject
    lateinit var delegatedAuthorities: InformationRequestDelegatedAuthorityService

    @Inject
    lateinit var evidenceUploads: InformationRequestEvidenceUploadService

    @Inject
    lateinit var evidenceCollection: InformationRequestEvidenceCollectionService

    private val support by lazy { ConformanceRequestSupport(dataSource, runtime, requests) }

    @Test
    fun `a retried completion gate change answers the request as it stands now with that request's ETag`() {
        val request = managedRequest()
        fun change(value: Boolean, tag: String, key: String) = QuarkusTransaction.requiringNew().call {
            completionGates.change(
                ChangeInformationRequestCompletionGateCommand(
                    requestId = request.requestId,
                    gatesExchangeClosure = value,
                    access = support.owner(request),
                    precondition = CommandPrecondition.ExpectedRevision(tag),
                    idempotencyKey = key,
                ),
            )
        }

        val original = aggregateETag(request)
        val first = change(false, original, "gate-first")
        val second = change(true, first.requestETag, "gate-second")

        val replay = change(false, original, "gate-first")

        assertTrue(replay.request.gatesExchangeClosure)
        assertEquals(second.requestETag, replay.requestETag)
        assertEquals(InformationRequestETag.aggregateOf(replay.request), replay.requestETag)
        assertNotEquals(first.requestETag, replay.requestETag)
    }

    @Test
    fun `a retried party revocation answers the parties ETag as it stands now`() {
        val request = managedRequest()
        fun revoke(partyId: UUID, tag: String, key: String) = QuarkusTransaction.requiringNew().call {
            partyRevocations.revoke(
                RevokeInformationRequestPartyCommand(
                    requestId = request.requestId,
                    partyId = partyId,
                    access = support.owner(request),
                    precondition = CommandPrecondition.ExpectedRevision(tag),
                    idempotencyKey = key,
                ),
            )
        }

        val original = partiesETag(request)
        val first = revoke(request.runtime.contributorPartyId, original, "revoke-contributor")
        val second = revoke(request.runtime.attestorPartyId, first.partiesETag, "revoke-attestor")

        val replay = revoke(request.runtime.contributorPartyId, original, "revoke-contributor")

        assertEquals(request.runtime.contributorPartyId, replay.party.id)
        assertFalse(replay.party.active)
        assertEquals(second.partiesETag, replay.partiesETag)
        assertEquals(partiesETag(request), replay.partiesETag)
    }

    @Test
    fun `a retried response save answers the responses and response ETag as they stand now`() {
        val request = support.fieldRequest()
        val services = runtime.build(request.requestId, centralAuthorization = authorization)
        fun save(narrative: String, tag: String, key: String) = QuarkusTransaction.requiringNew().call {
            services.responses.patch(
                PatchInformationRequestResponsesCommand(
                    requestId = request.requestId,
                    access = support.contributor(request),
                    precondition = CommandPrecondition.ExpectedRevision(tag),
                    idempotencyKey = key,
                    patches = listOf(
                        InformationRequestResponsePatch(
                            requirementId = request.answers.requirementId,
                            narrative = ResponseNarrativePatch.Set(narrative),
                        ),
                    ),
                ),
            )
        }

        val original = support.responseETag(request)
        val first = save("First note", original, "note-first")
        val second = save("Second note", first.responseETag, "note-second")

        val replay = save("First note", original, "note-first")

        assertEquals(second.responseETag, replay.responseETag)
        assertEquals(support.responseETag(request), replay.responseETag)
        assertEquals(listOf("Second note"), replay.responses.map { it.narrative })
    }

    @Test
    fun `a retried upload answers the file and its ETags as they stand after a later replacement`() {
        val request = support.fieldRequest()
        val requirementId = request.runtime.documentRequirementId
        val contributor = support.contributor(request)
        val upload = UploadInformationRequestEvidenceCommand(
            requestId = request.requestId,
            requirementId = requirementId,
            access = contributor,
            surface = InformationRequestEvidenceSurface.AUTHENTICATED,
            precondition = CommandPrecondition.ExpectedRevision(evidenceETag(requirementId)),
            idempotencyKey = "upload-first",
            file = pdf("first-record"),
            attributes = InformationRequestEvidenceAttributes(),
        )
        val first = QuarkusTransaction.requiringNew().call { evidenceUploads.upload(upload) }
        val replaced = QuarkusTransaction.requiringNew().call {
            evidenceUploads.replace(
                ReplaceInformationRequestEvidenceCommand(
                    requestId = request.requestId,
                    requirementId = requirementId,
                    artifactId = first.artifact.artifact.id,
                    access = contributor,
                    surface = InformationRequestEvidenceSurface.AUTHENTICATED,
                    precondition = CommandPrecondition.ExpectedRevision(first.artifactETag),
                    idempotencyKey = "replace-first",
                    file = pdf("second-record"),
                    attributes = InformationRequestEvidenceAttributes(),
                ),
            )
        }

        val replay = QuarkusTransaction.requiringNew().call { evidenceUploads.upload(upload) }

        assertEquals(2, replay.artifact.versions.size)
        assertEquals(replaced.artifactETag, replay.artifactETag)
        assertEquals(replaced.evidenceETag, replay.evidenceETag)
        assertEquals(InformationRequestETag.artifactOf(replay.artifact.artifact), replay.artifactETag)
        assertEquals(evidenceETag(requirementId), replay.evidenceETag)
    }

    @Test
    fun `a retried withdrawal answers the requirement's evidence ETag as it stands after a later upload`() {
        val request = support.fieldRequest()
        val requirementId = request.runtime.documentRequirementId
        val contributor = support.contributor(request)
        fun upload(key: String) = QuarkusTransaction.requiringNew().call {
            evidenceUploads.upload(
                UploadInformationRequestEvidenceCommand(
                    requestId = request.requestId,
                    requirementId = requirementId,
                    access = contributor,
                    surface = InformationRequestEvidenceSurface.AUTHENTICATED,
                    precondition = CommandPrecondition.ExpectedRevision(evidenceETag(requirementId)),
                    idempotencyKey = key,
                    file = pdf(key),
                    attributes = InformationRequestEvidenceAttributes(),
                ),
            )
        }

        val uploaded = upload("upload-to-withdraw")
        val withdrawal = ChangeInformationRequestEvidenceStateCommand(
            requestId = request.requestId,
            requirementId = requirementId,
            artifactId = uploaded.artifact.artifact.id,
            access = contributor,
            precondition = CommandPrecondition.ExpectedRevision(uploaded.artifactETag),
            idempotencyKey = "withdraw-first",
            reason = null,
        )
        val withdrawn = QuarkusTransaction.requiringNew().call { evidenceCollection.withdraw(withdrawal) }
        upload("upload-after-withdrawal")

        val replay = QuarkusTransaction.requiringNew().call { evidenceCollection.withdraw(withdrawal) }

        assertEquals(InformationRequestEvidenceCollectionState.WITHDRAWN, replay.artifact.artifact.collectionState)
        assertEquals(withdrawn.artifactETag, replay.artifactETag)
        assertNotEquals(withdrawn.evidenceETag, replay.evidenceETag)
        assertEquals(evidenceETag(requirementId), replay.evidenceETag)
    }

    @Test
    fun `a retried delegated authority grant answers the authority and parties ETag as they stand after its revocation`() {
        val delegate = PrincipalRef.user(UUID.randomUUID())
        val request = managedRequest { connection, prepared ->
            prepared.runtime.insertUser(delegate.id)
            execute(
                connection,
                """
                INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
                VALUES (?, 'INFORMATION_REQUEST', ?, 'USER', ?, 'CONTRIBUTOR', 'DIRECT', 'ACTIVE', now())
                """.trimIndent(),
                UUID.randomUUID(),
                prepared.requestId,
                delegate.id,
            )
        }
        val grant = GrantInformationRequestDelegatedAuthorityCommand(
            requestId = request.requestId,
            assignedPartyId = request.runtime.contributorPartyId,
            delegatePrincipal = delegate,
            requirementId = request.answers.requirementId,
            authorityInstrumentRef = "instrument-replay",
            effectiveAt = null,
            expiresAt = null,
            access = support.owner(request),
            precondition = CommandPrecondition.ExpectedRevision(partiesETag(request)),
            idempotencyKey = "grant-first",
        )
        val granted = QuarkusTransaction.requiringNew().call { delegatedAuthorities.grant(grant) }
        val revoked = QuarkusTransaction.requiringNew().call {
            delegatedAuthorities.revoke(
                RevokeInformationRequestDelegatedAuthorityCommand(
                    requestId = request.requestId,
                    authorityId = granted.authority.id,
                    reason = "authority ended",
                    access = support.owner(request),
                    precondition = CommandPrecondition.ExpectedRevision(granted.authoritiesETag),
                    idempotencyKey = "revoke-first",
                ),
            )
        }

        val replay = QuarkusTransaction.requiringNew().call { delegatedAuthorities.grant(grant) }

        assertEquals(granted.authority.id, replay.authority.id)
        assertFalse(replay.authority.active)
        assertEquals(revoked.authoritiesETag, replay.authoritiesETag)
        assertEquals(partiesETag(request), replay.authoritiesETag)
    }

    private fun managedRequest(
        prepare: (java.sql.Connection, ConformanceRequest) -> Unit = { _, _ -> },
    ): ConformanceRequest =
        support.fieldRequest(prepare = { connection, prepared ->
            execute(
                connection,
                """
                INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
                VALUES (?, 'EXCHANGE', ?, 'USER', ?, 'OWNER', 'DIRECT', 'ACTIVE', now())
                """.trimIndent(),
                UUID.randomUUID(),
                prepared.runtime.exchangeId,
                prepared.runtime.template.userId,
            )
            prepare(connection, prepared)
        })

    private fun aggregateETag(request: ConformanceRequest): String =
        QuarkusTransaction.requiringNew().call {
            InformationRequestETag.aggregateOf(requireNotNull(requests.findById(request.requestId)))
        }

    private fun partiesETag(request: ConformanceRequest): String =
        QuarkusTransaction.requiringNew().call {
            InformationRequestETag.partiesOf(requireNotNull(requests.findById(request.requestId)))
        }

    private fun evidenceETag(requirementId: UUID): String =
        QuarkusTransaction.requiringNew().call {
            InformationRequestETag.evidenceOf(requirementId, artifacts.findForRequirement(requirementId))
        }

    private fun pdf(title: String): InformationRequestEvidenceFile {
        val target = File.createTempFile("replay-current-state", ".pdf").apply { deleteOnExit() }
        PDDocument().use { document ->
            document.addPage(PDPage())
            document.documentInformation.title = title
            document.save(target)
        }
        return InformationRequestEvidenceFile(target, "$title.pdf", "application/pdf", DocumentEncryptionMode.INTERNAL)
    }
}
