package com.docuhyphen.app.api.service.informationrequest.capability

import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.auth.authz.Grant
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.dto.InformationRequestResponseWorkspaceDto
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.party.GrantInformationRequestDelegatedAuthorityCommand
import com.docuhyphen.app.api.model.informationrequest.party.RevokeInformationRequestDelegatedAuthorityCommand
import com.docuhyphen.app.api.model.informationrequest.response.ResponseNarrativePatch
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceArtifactRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.communication.OtpService
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateWalkingSkeletonRuntime.Companion.ALTERNATE_EVIDENCE
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateWalkingSkeletonRuntime.Companion.DELEGATE_NOTE
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateWalkingSkeletonRuntime.Companion.PRIMARY_EVIDENCE
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateWalkingSkeletonRuntime.Companion.RECORDED_SUMMARY
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateWalkingSkeletonRuntime.Companion.RESPONSE_CONFIRMATION
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateWalkingSkeletonRuntime.Companion.ROOT
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateWalkingSkeletonRuntime.Companion.SUBJECT_STATUS
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateWalkingSkeletonRuntime.Companion.SUBMITTER_ATTESTATION
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateWalkingSkeletonRuntime.Companion.SUPPORTING_RECORD
import com.docuhyphen.app.api.service.informationrequest.capability.model.IssuedWalkingSkeleton
import com.docuhyphen.app.api.service.informationrequest.conformance.PublishedRequestSupport
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceUploadService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestContactProofService
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthReadAccessService
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestDelegatedAuthorityService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseWorkspaceService
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationWriter
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateMaterializer
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.security.ForbiddenException
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestTemplateWalkingSkeletonRuntimeTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

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
    lateinit var evidenceUploads: InformationRequestEvidenceUploadService

    @Inject
    lateinit var artifactRepository: InformationRequestEvidenceArtifactRepository

    @Inject
    lateinit var partyRepository: InformationRequestPartyRepository

    @Inject
    lateinit var authorizationService: AuthorizationService

    @Inject
    lateinit var workspaces: InformationRequestResponseWorkspaceService

    @Inject
    lateinit var delegatedAuthorities: InformationRequestDelegatedAuthorityService

    @Inject
    lateinit var contactProof: InformationRequestContactProofService

    @Inject
    lateinit var readAccess: InformationRequestNoAuthReadAccessService

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
    private val skeletons by lazy {
        InformationRequestTemplateWalkingSkeletonRuntime(
            dataSource,
            published,
            runtime,
            requestRepository,
            evidenceUploads,
            artifactRepository
        )
    }

    @Test
    fun `an issued basic fixture materializes its exact requirements for parties who act only within their roles, read their own projections, and reach it through an access link`() {
        val issued = skeletons.issueBasic(listOf(CONTRIBUTOR, ATTESTOR), participants = setOf(CONTRIBUTOR))
        val request = issued.request
        val services = runtime.build(issued.requestId, centralAuthorization = authorizationService)

        assertEquals(
            issued.fixture.expectedRequirementKeys.map { it to ROOT }.toSet(),
            skeletons.materializedRequirements(issued)
        )
        assertEquals(issued.fixture.expectedRequirementKeys.size, skeletons.materializedRequirementCount(issued))
        assertEquals(listOf(CONTRIBUTOR), shareRoles(request.principal(CONTRIBUTOR), issued.requestId))
        assertEquals(listOf(ATTESTOR), shareRoles(request.principal(ATTESTOR), issued.requestId))

        val summary = requirement(skeletons.requirementId(issued, RECORDED_SUMMARY))
        val record = requirement(skeletons.requirementId(issued, SUPPORTING_RECORD))
        val confirmation = requirement(skeletons.requirementId(issued, RESPONSE_CONFIRMATION))
        val whole = ResourceRef.informationRequest(issued.requestId)
        val contributor = request.principal(CONTRIBUTOR)
        val attestor = request.principal(ATTESTOR)
        val outsider = PrincipalRef.user(published.outsider())
        assertDecision(true, contributor, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND, summary)
        assertDecision(true, contributor, Action.INFORMATION_REQUEST_EVIDENCE_UPLOAD, record)
        assertDecision(true, contributor, Action.INFORMATION_REQUEST_SUBMIT, whole)
        assertDecision(false, contributor, Action.INFORMATION_REQUEST_REQUIREMENT_ATTEST, confirmation)
        assertDecision(true, attestor, Action.INFORMATION_REQUEST_REQUIREMENT_ATTEST, confirmation)
        assertDecision(false, attestor, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND, summary)
        assertDecision(false, attestor, Action.INFORMATION_REQUEST_EVIDENCE_UPLOAD, record)
        assertDecision(false, attestor, Action.INFORMATION_REQUEST_SUBMIT, whole)
        assertDecision(true, request.owner.principal, Action.INFORMATION_REQUEST_MANAGE_PARTIES, whole)
        assertDecision(false, request.owner.principal, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND, summary)
        assertDecision(false, outsider, Action.INFORMATION_REQUEST_VIEW, whole)
        assertThrows(ForbiddenException::class.java) {
            skeletons.respond(
                services,
                issued,
                RECORDED_SUMMARY,
                request.access(ATTESTOR),
                "attestor-answer",
                fieldValue = "Recorded by the attestor"
            )
        }

        val managed = QuarkusTransaction.requiringNew().call { workspaces.loadRequest(issued.requestId, request.owner) }
        assertEquals(InformationRequestState.ISSUED, managed.state)
        assertEquals(request.templateVersionId, managed.templateVersionId)
        val answered = QuarkusTransaction.requiringNew()
            .call { workspaces.loadRequest(issued.requestId, request.access(CONTRIBUTOR)) }
        assertEquals(InformationRequestState.ISSUED, answered.state)
        assertEquals(request.templateVersionId, answered.templateVersionId)
        assertEquals(
            setOf(RECORDED_SUMMARY, SUPPORTING_RECORD),
            requirementKeys(workspace(issued.requestId, request.access(CONTRIBUTOR)))
        )
        assertEquals(
            setOf(RESPONSE_CONFIRMATION),
            requirementKeys(workspace(issued.requestId, request.access(ATTESTOR)))
        )
        assertThrows(ForbiddenException::class.java) {
            workspace(
                issued.requestId,
                RequestAccessContext(outsider, AuthorizationContext(sessionRef = "outsider-session"))
            )
        }

        val rawToken = "walking-skeleton-link-${UUID.randomUUID()}"
        skeletons.openAccessLink(issued, CONTRIBUTOR, rawToken, otp.hashOtp(ACCESS_CODE))
        val session = contactProof.verifyChallenge(rawToken, ACCESS_CODE)
        val linked = readAccess.resolve(issued.requestId, rawToken, session.sessionToken)
        assertEquals(contributor, linked.principal)
        assertEquals(setOf(RECORDED_SUMMARY, SUPPORTING_RECORD), requirementKeys(workspace(issued.requestId, linked)))
    }

    @Test
    fun `an issued multi-party fixture materializes its root requirements for role-scoped parties, keeps its subject an identity no principal acts for, and lets a delegate act only while its authority is current`() {
        val issued = skeletons.issueMultiParty(listOf(SUBJECT, CONTRIBUTOR, PREPARER, ATTESTOR, REVIEWER))
        val request = issued.request
        val services = runtime.build(issued.requestId, centralAuthorization = authorizationService)

        assertEquals(setOf(SUBJECT_STATUS, DELEGATE_NOTE), issued.anchoredRequirementKeys)
        val rootKeys = issued.fixture.expectedRequirementKeys - issued.anchoredRequirementKeys
        assertEquals(rootKeys.map { it to ROOT }.toSet(), skeletons.materializedRequirements(issued))
        assertEquals(rootKeys.size, skeletons.materializedRequirementCount(issued))
        listOf(CONTRIBUTOR, PREPARER, ATTESTOR, REVIEWER).forEach { party ->
            assertEquals(
                listOf(party),
                shareRoles(request.principal(party), issued.requestId),
                "$party holds its own request Share"
            )
        }
        val subject = QuarkusTransaction.requiringNew()
            .call { requireNotNull(partyRepository.findById(request.parties.getValue(SUBJECT))) }
        assertEquals(request.subjects.getValue(SUBJECT), subject.subjectIdentityRefId)
        assertEquals(
            emptySet<PrincipalRef>(),
            QuarkusTransaction.requiringNew().call { runtime.requirementContext.principalsActingFor(subject) })

        val primary = skeletons.requirementId(issued, PRIMARY_EVIDENCE)
        val alternate = skeletons.requirementId(issued, ALTERNATE_EVIDENCE)
        val assertion = requirement(skeletons.requirementId(issued, SUBMITTER_ATTESTATION))
        val contributor = request.principal(CONTRIBUTOR)
        val preparer = request.principal(PREPARER)
        val attestor = request.principal(ATTESTOR)
        val reviewer = request.principal(REVIEWER)
        assertDecision(true, contributor, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND, requirement(primary))
        assertDecision(true, contributor, Action.INFORMATION_REQUEST_EVIDENCE_UPLOAD, requirement(alternate))
        assertDecision(true, contributor, Action.INFORMATION_REQUEST_REQUIREMENT_ATTEST, assertion)
        assertDecision(true, attestor, Action.INFORMATION_REQUEST_REQUIREMENT_ATTEST, assertion)
        assertDecision(false, attestor, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND, requirement(primary))
        assertDecision(false, preparer, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND, requirement(primary))
        assertDecision(false, preparer, Action.INFORMATION_REQUEST_REQUIREMENT_VIEW, requirement(alternate))
        assertDecision(true, reviewer, Action.INFORMATION_REQUEST_REQUIREMENT_REVIEW, requirement(primary))
        assertDecision(false, reviewer, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND, requirement(primary))

        assertEquals(
            setOf(PRIMARY_EVIDENCE, ALTERNATE_EVIDENCE, SUBMITTER_ATTESTATION),
            requirementKeys(workspace(issued.requestId, request.access(CONTRIBUTOR)))
        )
        assertEquals(
            setOf(SUBMITTER_ATTESTATION),
            requirementKeys(workspace(issued.requestId, request.access(ATTESTOR)))
        )
        val prepared = workspace(issued.requestId, request.access(PREPARER))
        assertEquals(emptySet<String>(), requirementKeys(prepared))
        assertEquals(emptyList<String>(), prepared.templateVersion.groups.map { it.groupKey })
        assertEquals(
            InformationRequestState.ISSUED,
            QuarkusTransaction.requiringNew()
                .call { workspaces.loadRequest(issued.requestId, request.access(REVIEWER)) }.state
        )

        val delegate = PrincipalRef.user(published.shareHolder(request, CONTRIBUTOR))
        val delegateAccess = RequestAccessContext(delegate, AuthorizationContext(sessionRef = "delegate-session"))
        val outsider = RequestAccessContext(
            PrincipalRef.user(published.outsider()),
            AuthorizationContext(sessionRef = "outsider-session")
        )
        assertDecision(false, delegate, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND, requirement(alternate))
        assertThrows(ForbiddenException::class.java) {
            grant(
                issued,
                CONTRIBUTOR,
                delegate,
                alternate,
                null,
                null,
                outsider,
                "outsider-grant"
            )
        }
        val subjectGrant = assertThrows(InformationRequestLifecycleException::class.java) {
            grant(
                issued,
                SUBJECT,
                delegate,
                null,
                null,
                null,
                request.owner,
                "subject-grant"
            )
        }
        assertEquals(InformationRequestErrorCatalog.DELEGATED_AUTHORITY_PARTY_INELIGIBLE, subjectGrant.reasonCode)

        val now = Instant.now()
        grant(
            issued,
            CONTRIBUTOR,
            delegate,
            alternate,
            now.minus(Duration.ofHours(2)),
            now.minus(Duration.ofHours(1)),
            request.owner,
            "expired-grant"
        )
        assertDecision(false, delegate, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND, requirement(alternate))
        val current = grant(issued, CONTRIBUTOR, delegate, alternate, null, null, request.owner, "current-grant")
        assertDecision(true, delegate, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND, requirement(alternate))
        assertDecision(false, delegate, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND, requirement(primary))
        val provided = skeletons.respond(
            services,
            issued,
            ALTERNATE_EVIDENCE,
            delegateAccess,
            "delegate-provides",
            disposition = InformationRequestResponseDisposition.PROVIDED,
        )
        assertEquals(
            delegate.id,
            provided.responses.single { it.informationRequestRequirementId == alternate }.recordedByPrincipalId
        )

        revoke(issued, current, request.owner, "revoke-current")
        assertDecision(false, delegate, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND, requirement(alternate))
        assertThrows(ForbiddenException::class.java) {
            skeletons.respond(
                services,
                issued,
                ALTERNATE_EVIDENCE,
                delegateAccess,
                "late-delegate",
                narrative = ResponseNarrativePatch.Set("Provided after the authority ended"),
            )
        }
    }

    @Suppress("LongParameterList")
    private fun grant(
        issued: IssuedWalkingSkeleton,
        party: String,
        delegate: PrincipalRef,
        requirementId: UUID?,
        effective: Instant?,
        expires: Instant?,
        access: RequestAccessContext,
        key: String,
    ): UUID {
        val precondition = CommandPrecondition.ExpectedRevision(skeletons.partiesETag(issued))
        return QuarkusTransaction.requiringNew().call {
            delegatedAuthorities.grant(
                GrantInformationRequestDelegatedAuthorityCommand(
                    requestId = issued.requestId,
                    assignedPartyId = issued.request.parties.getValue(party),
                    delegatePrincipal = delegate,
                    requirementId = requirementId,
                    authorityInstrumentRef = "instrument-$key",
                    effectiveAt = effective?.let(Timestamp::from),
                    expiresAt = expires?.let(Timestamp::from),
                    access = access,
                    precondition = precondition,
                    idempotencyKey = key,
                ),
            ).authority.id
        }
    }

    private fun revoke(issued: IssuedWalkingSkeleton, authorityId: UUID, access: RequestAccessContext, key: String) {
        val precondition = CommandPrecondition.ExpectedRevision(skeletons.partiesETag(issued))
        QuarkusTransaction.requiringNew().run {
            delegatedAuthorities.revoke(
                RevokeInformationRequestDelegatedAuthorityCommand(
                    requestId = issued.requestId,
                    authorityId = authorityId,
                    reason = "authority ended",
                    access = access,
                    precondition = precondition,
                    idempotencyKey = key,
                ),
            )
        }
    }

    private fun shareRoles(principal: PrincipalRef, requestId: UUID): List<String> =
        QuarkusTransaction.requiringNew().call {
            authorizationService.grantsOn(
                principal,
                ResourceRef.informationRequest(requestId),
                AuthorizationContext(sessionRef = "grant-session")
            )
                .filter { it.sourceKind == Grant.SourceKind.DIRECT_SHARE }
                .map { it.roleName }
        }

    private fun assertDecision(allowed: Boolean, principal: PrincipalRef, action: Action, resource: ResourceRef) {
        val decision = QuarkusTransaction.requiringNew().call {
            authorizationService.authorize(
                principal,
                action,
                resource,
                AuthorizationContext(sessionRef = "decision-session")
            )
        }
        assertEquals(
            allowed,
            decision !is Decision.Deny,
            "$action on ${resource.type} for ${principal.kind}: $decision"
        )
    }

    private fun requirement(requirementId: UUID): ResourceRef = ResourceRef.informationRequestRequirement(requirementId)

    private fun workspace(requestId: UUID, access: RequestAccessContext): InformationRequestResponseWorkspaceDto =
        QuarkusTransaction.requiringNew().call { workspaces.load(requestId, access) }

    private fun requirementKeys(workspace: InformationRequestResponseWorkspaceDto): Set<String> =
        workspace.templateVersion.sections.flatMap { it.requirements }.map { it.requirementKey }.toSet()

    private companion object {
        const val SUBJECT = "SUBJECT"
        const val CONTRIBUTOR = "CONTRIBUTOR"
        const val PREPARER = "PREPARER"
        const val ATTESTOR = "ATTESTOR"
        const val REVIEWER = "REVIEWER"
        const val ACCESS_CODE = "123456"
    }
}
