package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.dto.InformationRequestSubmissionAttestationResultDto
import com.docuhyphen.app.api.model.dto.InformationRequestSubmissionPackageDto
import com.docuhyphen.app.api.model.dto.InformationRequestSubmissionPreviewDto
import com.docuhyphen.app.api.model.dto.InformationRequestSubmissionRefusalDto
import com.docuhyphen.app.api.model.dto.InformationRequestSubmissionResultDto
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestAttestationDecision
import com.docuhyphen.app.api.model.entity.InformationRequestAuthenticationStrength
import com.docuhyphen.app.api.model.entity.InformationRequestContributorRole
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionAttestation
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionMode
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionPackage
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionStageOrdering
import com.docuhyphen.app.api.model.informationrequest.InformationRequestNoAuthAccess
import com.docuhyphen.app.api.model.informationrequest.InformationRequestReadableSubmissionPackage
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSubmissionAttestationResult
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSubmissionItemProblem
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSubmissionPackageView
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSubmissionPreview
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSubmissionProblemCode
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSubmissionReadiness
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSubmissionResult
import com.docuhyphen.app.api.model.informationrequest.RecordInformationRequestSubmissionAttestationCommand
import com.docuhyphen.app.api.model.informationrequest.SubmitInformationRequestPackageCommand
import com.docuhyphen.app.api.model.informationrequest.WithdrawInformationRequestPackageCommand
import com.docuhyphen.app.api.resource.model.RecordInformationRequestAttestationRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.SubmitInformationRequestPackageRequest
import com.docuhyphen.app.api.resource.model.WithdrawInformationRequestPackageRequest
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestNoAuthReadAccessService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestState
import com.docuhyphen.app.api.service.informationrequest.InformationRequestSubmissionAttestationService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestSubmissionIncompleteException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestSubmissionQueryService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestSubmissionService
import com.docuhyphen.app.api.service.informationrequest.RequestAccessContext
import io.quarkus.security.ForbiddenException
import jakarta.ws.rs.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class InformationRequestSubmissionResourceContractTest
{
    private val requestId = UUID.randomUUID()
    private val requirementId = UUID.randomUUID()
    private val principal = PrincipalRef.user(UUID.randomUUID())
    private val access = RequestAccessContext(principal, AuthorizationContext(sessionRef = "user-session"))
    private val noAuthAccess = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext(sessionRef = "link"))

    private val submissionService: InformationRequestSubmissionService = mock()
    private val attestationService: InformationRequestSubmissionAttestationService = mock()
    private val queryService: InformationRequestSubmissionQueryService = mock()
    private val accessContextFactory: InformationRequestAccessContextFactory = mock()
    private val readAccessService: InformationRequestNoAuthReadAccessService = mock()

    private val submissionPackage = InformationRequestSubmissionPackage().apply {
        informationRequestId = requestId
        packageNumber = 1
        stageKey = "stage-one"
        templateVersionId = UUID.randomUUID()
        contentHashSha256 = "a".repeat(64)
        manifestHashSha256 = "b".repeat(64)
        submittedByPrincipalKind = principal.kind
        submittedByPrincipalId = principal.id
    }
    private val view = InformationRequestSubmissionPackageView(submissionPackage, emptyList(), emptyList(), emptyList(), emptyList(), null)
    private val readable = InformationRequestReadableSubmissionPackage(view, emptySet(), emptyMap())
    private val request = InformationRequest().apply {
        id = requestId
        exchangeId = UUID.randomUUID()
        templateVersionId = submissionPackage.templateVersionId
        ownerType = InformationRequestOwnerType.ORGANIZATION
        state = InformationRequestState.IN_PROGRESS
    }
    private val result = InformationRequestSubmissionResult(request, view, "\"request:4\"", "\"responses:4\"")

    private val resource = InformationRequestSubmissionResource(submissionService, queryService, accessContextFactory)
    private val previewResource = InformationRequestSubmissionPreviewResource(submissionService, queryService, accessContextFactory)
    private val attestationResource = InformationRequestAttestationResource(attestationService, accessContextFactory)
    private val noAuthResource = InformationRequestNoAuthSubmissionResource(submissionService, queryService, readAccessService)
    private val noAuthPreviewResource = InformationRequestNoAuthSubmissionPreviewResource(submissionService, queryService, readAccessService)
    private val noAuthAttestationResource = InformationRequestNoAuthAttestationResource(attestationService, readAccessService)

    init
    {
        whenever(accessContextFactory.currentAuthenticated()).thenReturn(access)
        whenever(queryService.readable(any(), any())).thenReturn(readable)
        whenever(readAccessService.resolve(eq("link-token"), anyOrNull())).thenReturn(InformationRequestNoAuthAccess(noAuthAccess, requestId))
    }

    @Test
    fun `submission resources are subordinate to the request on both surfaces`()
    {
        assertEquals("/information-requests/{id}/submissions", pathOf(InformationRequestSubmissionResource::class.java))
        assertEquals("/information-requests/{id}/submission-preview", pathOf(InformationRequestSubmissionPreviewResource::class.java))
        assertEquals(
            "/information-requests/{id}/requirements/{requirementId}/attestations",
            pathOf(InformationRequestAttestationResource::class.java),
        )
        assertEquals("no-auth/information-requests/{id}/submissions", pathOf(InformationRequestNoAuthSubmissionResource::class.java))
        assertEquals(
            "no-auth/information-requests/{id}/submission-preview",
            pathOf(InformationRequestNoAuthSubmissionPreviewResource::class.java),
        )
        assertEquals(
            "no-auth/information-requests/{id}/requirements/{requirementId}/attestations",
            pathOf(InformationRequestNoAuthAttestationResource::class.java),
        )
    }

    @Test
    fun `a submission delegates its scope, precondition, and key and answers the created package`()
    {
        whenever(submissionService.submit(any())).thenReturn(result)

        val response = resource.submit(requestId.toString(), SubmitInformationRequestPackageRequest(" stage-one "), "\"submission:stage-one:abc\"", " submit-key ")

        assertEquals(201, response.status)
        assertEquals(result.responseETag, response.getHeaderString("ETag"))
        val body = response.entity as InformationRequestSubmissionResultDto
        assertEquals(InformationRequestState.IN_PROGRESS, body.requestState)
        assertEquals(result.requestETag, body.requestETag)
        assertEquals(submissionPackage.id, body.submission.id)
        assertEquals(true, body.submission.submittedByCaller)
        val command = argumentCaptor<SubmitInformationRequestPackageCommand>().also { verify(submissionService).submit(it.capture()) }.firstValue
        assertEquals(requestId, command.requestId)
        assertEquals("stage-one", command.stageKey)
        assertEquals(access, command.access)
        assertEquals(CommandPrecondition.ExpectedRevision("\"submission:stage-one:abc\""), command.precondition)
        assertEquals("submit-key", command.idempotencyKey)
    }

    @Test
    fun `a whole-package submission may omit its body and a submission without a key never reaches the service`()
    {
        whenever(submissionService.submit(any())).thenReturn(result)

        assertEquals(201, resource.submit(requestId.toString(), null, "\"submission:whole:abc\"", "key").status)
        val command = argumentCaptor<SubmitInformationRequestPackageCommand>().also { verify(submissionService).submit(it.capture()) }.firstValue
        assertEquals(null, command.stageKey)

        val missingKey = resource.submit(requestId.toString(), null, "\"submission:whole:abc\"", " ")
        assertEquals(400, missingKey.status)
        assertEquals(400, resource.submit("not-a-uuid", null, "\"x\"", "key").status)
        verify(submissionService).submit(any())
    }

    @Test
    fun `an incomplete scope is refused as unprocessable with only the problems the caller may see`()
    {
        val problem = InformationRequestSubmissionItemProblem(requirementId, "item-a", "", InformationRequestSubmissionProblemCode.ATTESTATION_MISSING)
        whenever(submissionService.submit(any())).thenThrow(
            InformationRequestSubmissionIncompleteException(InformationRequestSubmissionReadiness(listOf(problem), 2)),
        )

        val response = resource.submit(requestId.toString(), null, "\"submission:whole:abc\"", "key")

        assertEquals(422, response.status)
        val body = response.entity as InformationRequestSubmissionRefusalDto
        assertEquals(InformationRequestErrorCatalog.SUBMISSION_INCOMPLETE, body.reasonCode)
        assertEquals(listOf(requirementId), body.problems.map { it.requirementId })
        assertEquals(InformationRequestSubmissionProblemCode.ATTESTATION_MISSING, body.problems.single().code)
        assertEquals(2, body.undisclosedProblemCount)
    }

    @Test
    fun `refusals map to stable statuses`()
    {
        whenever(submissionService.submit(any()))
            .thenThrow(CommandPreconditionException.required(null))
            .thenThrow(InformationRequestLifecycleException(InformationRequestErrorCatalog.SUBMISSION_ALREADY_SUBMITTED, "already"))
            .thenThrow(InformationRequestLifecycleException(InformationRequestErrorCatalog.NOT_FOUND, "missing"))
            .thenThrow(ForbiddenException("denied"))

        assertEquals(428, resource.submit(requestId.toString(), null, null, "key").status)
        val conflict = resource.submit(requestId.toString(), null, "\"x\"", "key")
        assertEquals(409, conflict.status)
        assertEquals(InformationRequestErrorCatalog.SUBMISSION_ALREADY_SUBMITTED, (conflict.entity as ResponseError).reasonCode)
        assertEquals(404, resource.submit(requestId.toString(), null, "\"x\"", "key").status)
        assertEquals(403, resource.submit(requestId.toString(), null, "\"x\"", "key").status)
    }

    @Test
    fun `a withdrawal delegates the package and reason and answers the withdrawn package`()
    {
        whenever(submissionService.withdraw(any())).thenReturn(result)

        val response = resource.withdraw(
            requestId.toString(),
            submissionPackage.id.toString(),
            WithdrawInformationRequestPackageRequest(" CORRECTION "),
            "\"responses:3\"",
            "withdraw-key",
        )

        assertEquals(200, response.status)
        assertEquals(result.responseETag, response.getHeaderString("ETag"))
        val command = argumentCaptor<WithdrawInformationRequestPackageCommand>().also { verify(submissionService).withdraw(it.capture()) }.firstValue
        assertEquals(submissionPackage.id, command.packageId)
        assertEquals("CORRECTION", command.reasonCode)
        assertEquals(CommandPrecondition.ExpectedRevision("\"responses:3\""), command.precondition)
        assertEquals(400, resource.withdraw(requestId.toString(), "bad", null, "\"x\"", "key").status)
    }

    @Test
    fun `packages and one package are read through the recipient-safe query`()
    {
        whenever(queryService.packages(requestId, access)).thenReturn(listOf(readable))
        whenever(queryService.packageDetail(requestId, submissionPackage.id, access)).thenReturn(readable)

        val list = resource.list(requestId.toString())
        assertEquals(200, list.status)
        @Suppress("UNCHECKED_CAST")
        assertEquals(listOf(submissionPackage.id), (list.entity as Array<InformationRequestSubmissionPackageDto>).map { it.id })

        val detail = resource.detail(requestId.toString(), submissionPackage.id.toString())
        assertEquals(200, detail.status)
        assertEquals(1, (detail.entity as InformationRequestSubmissionPackageDto).packageNumber)
    }

    @Test
    fun `review-before-submit answers the scope with its submission ETag`()
    {
        whenever(queryService.preview(requestId, "stage-two", access)).thenReturn(preview("stage-two"))

        val response = previewResource.preview(requestId.toString(), "stage-two")

        assertEquals(200, response.status)
        assertEquals("\"submission:stage-two:abc\"", response.getHeaderString("ETag"))
        val body = response.entity as InformationRequestSubmissionPreviewDto
        assertEquals("stage-two", body.stageKey)
        assertEquals(false, body.ready)
        assertEquals(1, body.undisclosedProblemCount)
    }

    @Test
    fun `an attestation delegates the decision on the exact Requirement and answers the recorded assertion`()
    {
        whenever(attestationService.record(any())).thenReturn(
            InformationRequestSubmissionAttestationResult(attestation(), null, "\"submission:whole:abc\""),
        )

        val response = attestationResource.record(
            requestId.toString(),
            requirementId.toString(),
            RecordInformationRequestAttestationRequest(
                decision = InformationRequestAttestationDecision.ASSENTED,
                externalSignatureReference = " ref-1 ",
            ),
            "\"submission:whole:abc\"",
            "attest-key",
        )

        assertEquals(201, response.status)
        assertEquals("\"submission:whole:abc\"", response.getHeaderString("ETag"))
        val body = response.entity as InformationRequestSubmissionAttestationResultDto
        assertEquals(true, body.attestation.attestedByCaller)
        val command = argumentCaptor<RecordInformationRequestSubmissionAttestationCommand>()
            .also { verify(attestationService).record(it.capture()) }.firstValue
        assertEquals(requirementId, command.requirementId)
        assertEquals(InformationRequestAttestationDecision.ASSENTED, command.decision)
        assertEquals("ref-1", command.externalSignatureReference)
        assertEquals("attest-key", command.idempotencyKey)
    }

    @Test
    fun `the no-auth surface resolves its link access and refuses another request's link`()
    {
        whenever(submissionService.submit(any())).thenReturn(result)
        whenever(queryService.preview(requestId, null, noAuthAccess)).thenReturn(preview(null))

        val submitted = noAuthResource.submit(requestId.toString(), null, "link-token", "\"submission:whole:abc\"", "key", null)
        assertEquals(201, submitted.status)
        val command = argumentCaptor<SubmitInformationRequestPackageCommand>().also { verify(submissionService).submit(it.capture()) }.firstValue
        assertEquals(noAuthAccess, command.access)
        assertEquals(200, noAuthPreviewResource.preview(requestId.toString(), null, "link-token", null).status)

        whenever(readAccessService.resolve(eq("other-token"), anyOrNull()))
            .thenReturn(InformationRequestNoAuthAccess(noAuthAccess, UUID.randomUUID()))
        assertEquals(404, noAuthResource.list(requestId.toString(), "other-token", null).status)
        assertEquals(400, noAuthResource.list(requestId.toString(), null, null).status)
        val attested = noAuthAttestationResource.record(
            requestId.toString(),
            requirementId.toString(),
            RecordInformationRequestAttestationRequest(InformationRequestAttestationDecision.ASSENTED),
            "other-token",
            "\"x\"",
            "key",
            null,
        )
        assertEquals(404, attested.status)
        verify(attestationService, never()).record(any())
    }

    private fun preview(stageKey: String?) = InformationRequestSubmissionPreview(
        requestId = requestId,
        stageKey = stageKey,
        submissionMode = if (stageKey == null) InformationRequestSubmissionMode.WHOLE_PACKAGE else InformationRequestSubmissionMode.STAGED,
        submissionStageOrdering = InformationRequestSubmissionStageOrdering.ANY_ORDER,
        submissionETag = "\"submission:${stageKey ?: "whole"}:abc\"",
        readiness = InformationRequestSubmissionReadiness(emptyList(), 1),
        attestations = emptyList(),
        stages = emptyList(),
        canSubmit = false,
        packages = listOf(readable),
    )

    private fun attestation() = InformationRequestSubmissionAttestation().apply {
        informationRequestId = requestId
        attestationRequirementId = requirementId
        requirementRevisionId = UUID.randomUUID()
        partyId = UUID.randomUUID()
        partyRole = InformationRequestContributorRole.SUBJECT
        principalKind = principal.kind
        principalId = principal.id
        decision = InformationRequestAttestationDecision.ASSENTED
        authenticationStrength = InformationRequestAuthenticationStrength.ACCOUNT_SIGN_IN
        attestedContentHashSha256 = "c".repeat(64)
        statementHashSha256 = "d".repeat(64)
        policyHashSha256 = "e".repeat(64)
        attestedAt = Timestamp.from(Instant.now())
    }

    private fun pathOf(type: Class<*>): String = type.getAnnotation(Path::class.java).value
}
