package com.docuhyphen.app.api.resource.informationrequest.noauth

import com.docuhyphen.app.api.model.entity.InformationRequestAttestationDecision
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestNoAuthRequestResource
import com.docuhyphen.app.api.resource.informationrequest.acceptedfact.InformationRequestNoAuthAcceptedFactOfferResource
import com.docuhyphen.app.api.resource.informationrequest.acceptedfact.InformationRequestNoAuthFactRecertificationResource
import com.docuhyphen.app.api.resource.informationrequest.acceptedfact.operations.InformationRequestNoAuthAcceptedFactOfferResourceOperations
import com.docuhyphen.app.api.resource.informationrequest.acceptedfact.operations.InformationRequestNoAuthFactRecertificationResourceOperations
import com.docuhyphen.app.api.resource.informationrequest.amendment.InformationRequestNoAuthAmendmentResource
import com.docuhyphen.app.api.resource.informationrequest.amendment.operations.InformationRequestNoAuthAmendmentResourceOperations
import com.docuhyphen.app.api.resource.informationrequest.attestation.InformationRequestNoAuthAttestationResource
import com.docuhyphen.app.api.resource.informationrequest.attestation.operations.InformationRequestNoAuthAttestationResourceOperations
import com.docuhyphen.app.api.resource.informationrequest.evidence.InformationRequestNoAuthEvidenceResource
import com.docuhyphen.app.api.resource.informationrequest.evidence.operations.InformationRequestNoAuthEvidenceResourceOperations
import com.docuhyphen.app.api.resource.informationrequest.lifecycle.InformationRequestNoAuthCarryForwardResource
import com.docuhyphen.app.api.resource.informationrequest.lifecycle.operations.InformationRequestNoAuthCarryForwardResourceOperations
import com.docuhyphen.app.api.resource.informationrequest.operations.InformationRequestNoAuthRequestResourceOperations
import com.docuhyphen.app.api.resource.informationrequest.review.InformationRequestNoAuthReviewResource
import com.docuhyphen.app.api.resource.informationrequest.review.InformationRequestNoAuthReviewResultResource
import com.docuhyphen.app.api.resource.informationrequest.review.operations.InformationRequestNoAuthReviewResourceOperations
import com.docuhyphen.app.api.resource.informationrequest.review.operations.InformationRequestNoAuthReviewResultResourceOperations
import com.docuhyphen.app.api.resource.informationrequest.submission.InformationRequestNoAuthSubmissionPreviewResource
import com.docuhyphen.app.api.resource.informationrequest.submission.InformationRequestNoAuthSubmissionResource
import com.docuhyphen.app.api.resource.informationrequest.submission.operations.InformationRequestNoAuthSubmissionPreviewResourceOperations
import com.docuhyphen.app.api.resource.informationrequest.submission.operations.InformationRequestNoAuthSubmissionResourceOperations
import com.docuhyphen.app.api.resource.model.*
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.acceptedfact.InformationRequestAcceptedFactQueryService
import com.docuhyphen.app.api.service.informationrequest.acceptedfact.InformationRequestAcceptedFactService
import com.docuhyphen.app.api.service.informationrequest.acceptedfact.InformationRequestBusinessDecisionService
import com.docuhyphen.app.api.service.informationrequest.acceptedfact.InformationRequestFactRecertificationService
import com.docuhyphen.app.api.service.informationrequest.amendment.InformationRequestAmendmentQueryService
import com.docuhyphen.app.api.service.informationrequest.amendment.InformationRequestAmendmentService
import com.docuhyphen.app.api.service.informationrequest.attestation.InformationRequestSubmissionAttestationService
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceCollectionService
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceQueryService
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceUploadService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLineageQueryService
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthSessionFixture
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestGroupOccurrenceService
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestPartyQueryService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseDraftService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseWorkspaceService
import com.docuhyphen.app.api.service.informationrequest.review.*
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionQueryService
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionService
import jakarta.ws.rs.HttpMethod
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.core.Response
import org.jboss.resteasy.reactive.multipart.FileUpload
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.*
import java.lang.reflect.Method
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

class InformationRequestNoAuthRequestBindingContractTest {
    private val fixture = InformationRequestNoAuthSessionFixture()
    private val issued = fixture.issue()
    private val otherRequestId = UUID.randomUUID()

    private val partyQueries: InformationRequestPartyQueryService = mock()
    private val responseDrafts: InformationRequestResponseDraftService = mock()
    private val occurrences: InformationRequestGroupOccurrenceService = mock()
    private val workspaces: InformationRequestResponseWorkspaceService = mock()
    private val evidenceUploads: InformationRequestEvidenceUploadService = mock()
    private val evidenceCollection: InformationRequestEvidenceCollectionService = mock()
    private val evidenceQueries: InformationRequestEvidenceQueryService = mock()
    private val submissions: InformationRequestSubmissionService = mock()
    private val submissionQueries: InformationRequestSubmissionQueryService = mock()
    private val attestations: InformationRequestSubmissionAttestationService = mock()
    private val reviewAssignments: InformationRequestReviewAssignmentService = mock()
    private val reviewDecisions: InformationRequestReviewDecisionService = mock()
    private val reviewFindings: InformationRequestReviewFindingService = mock()
    private val reviewComments: InformationRequestReviewCommentService = mock()
    private val reviewCycles: InformationRequestReviewCycleService = mock()
    private val reviewQueries: InformationRequestReviewQueryService = mock()
    private val lineage: InformationRequestLineageQueryService = mock()
    private val facts: InformationRequestAcceptedFactService = mock()
    private val factQueries: InformationRequestAcceptedFactQueryService = mock()
    private val businessDecisions: InformationRequestBusinessDecisionService = mock()
    private val recertifications: InformationRequestFactRecertificationService = mock()
    private val amendments: InformationRequestAmendmentService = mock()
    private val amendmentQueries: InformationRequestAmendmentQueryService = mock()

    private val requestResource =
        InformationRequestNoAuthRequestResource(fixture.service, partyQueries, responseDrafts, occurrences, workspaces)
    private val evidenceResource =
        InformationRequestNoAuthEvidenceResource(evidenceUploads, evidenceCollection, evidenceQueries, fixture.service)
    private val submissionResource =
        InformationRequestNoAuthSubmissionResource(submissions, submissionQueries, fixture.service)
    private val previewResource =
        InformationRequestNoAuthSubmissionPreviewResource(submissions, submissionQueries, fixture.service)
    private val attestationResource = InformationRequestNoAuthAttestationResource(attestations, fixture.service)
    private val reviewResource = InformationRequestNoAuthReviewResource(
        reviewAssignments,
        reviewDecisions,
        reviewFindings,
        reviewComments,
        reviewCycles,
        reviewQueries,
        fixture.service,
    )
    private val reviewResultResource = InformationRequestNoAuthReviewResultResource(
        reviewAssignments,
        reviewDecisions,
        reviewFindings,
        reviewComments,
        reviewCycles,
        reviewQueries,
        fixture.service,
    )
    private val carryForwardResource = InformationRequestNoAuthCarryForwardResource(lineage, fixture.service)
    private val offerResource =
        InformationRequestNoAuthAcceptedFactOfferResource(facts, factQueries, businessDecisions, fixture.service)
    private val recertificationResource =
        InformationRequestNoAuthFactRecertificationResource(recertifications, fixture.service)
    private val amendmentResource =
        InformationRequestNoAuthAmendmentResource(amendments, amendmentQueries, fixture.service)

    @Test
    fun `every no-auth request route answers not found for a session verified on another request without reaching its service or spending a use`() {
        val responses = routes(otherRequestId.toString()).mapValues { (_, call) -> call() }

        assertAll(
            responses.map { (route, response) -> Executable { assertNotFound(route, response) } } + listOf(
                Executable { verifyNoInteractions(*downstreamServices()) },
                Executable { assertEquals(0, issued.session.useCount) },
                Executable { assertNull(issued.session.lastUsedAt) },
                Executable { verify(fixture.repository, never()).update(any()) },
            ),
        )
    }

    @Test
    fun `the same link and session still open the request they were issued for`() {
        whenever(lineage.carryForwards(eq(fixture.requestId), any())).thenReturn(emptyList())

        val response = carryForwardResource.list(fixture.requestId.toString(), LINK_TOKEN, issued.sessionToken)

        assertEquals(Response.Status.OK.statusCode, response.status)
        val access = argumentCaptor<RequestAccessContext>()
            .also { verify(lineage).carryForwards(eq(fixture.requestId), it.capture()) }
            .firstValue
        assertEquals(fixture.participant, access.principal)
        assertEquals(issued.session.id.toString(), access.authorization.sessionRef)
        assertEquals(1, issued.session.useCount)
    }

    @Test
    fun `the binding contract exercises every route declared under the no-auth request path`() {
        assertEquals(declaredRequestRoutes(), routes(otherRequestId.toString()).keys)
    }

    @ParameterizedTest
    @ValueSource(strings = ["requirementId", "artifactId", "versionId", "occurrenceId", "packageId", "reviewId", "factId"])
    fun `malformed nested identifiers are refused without consuming the verified session`(identifierName: String) {
        val commands = routes(fixture.requestId.toString(), identifierName)
        val operations = listOf(
            InformationRequestNoAuthRequestResourceOperations::class.java,
            InformationRequestNoAuthEvidenceResourceOperations::class.java,
            InformationRequestNoAuthSubmissionResourceOperations::class.java,
            InformationRequestNoAuthAttestationResourceOperations::class.java,
            InformationRequestNoAuthReviewResourceOperations::class.java,
            InformationRequestNoAuthFactRecertificationResourceOperations::class.java,
        )
        val affectedRoutes = operations.flatMap { type ->
            type.declaredMethods.filter { method ->
                method.parameterAnnotations.any { annotations ->
                    annotations.filterIsInstance<PathParam>().any { it.value == identifierName }
                }
            }.map { "${type.simpleName}.${it.name}" }
        }
        assertEquals(false, affectedRoutes.isEmpty())
        val responses = affectedRoutes.associateWith { commands.getValue(it)() }

        assertAll(
            responses.map { (route, response) -> Executable { assertEquals(400, response.status, route) } } + listOf(
                Executable { verifyNoInteractions(*downstreamServices()) },
                Executable { assertEquals(0, issued.session.useCount) },
                Executable { assertNull(issued.session.lastUsedAt) },
                Executable { verify(fixture.repository, never()).update(any()) },
            ),
        )
    }

    private fun assertNotFound(route: String, response: Response) {
        assertEquals(Response.Status.NOT_FOUND.statusCode, response.status, route)
        assertEquals(InformationRequestErrorCatalog.NOT_FOUND, (response.entity as? ResponseError)?.reasonCode, route)
    }

    private fun downstreamServices(): Array<Any> = arrayOf(
        partyQueries,
        responseDrafts,
        occurrences,
        workspaces,
        evidenceUploads,
        evidenceCollection,
        evidenceQueries,
        submissions,
        submissionQueries,
        attestations,
        reviewAssignments,
        reviewDecisions,
        reviewFindings,
        reviewComments,
        reviewCycles,
        reviewQueries,
        lineage,
        facts,
        factQueries,
        businessDecisions,
        recertifications,
        amendments,
        amendmentQueries,
    )

    private fun routes(id: String, malformedIdentifier: String? = null): Map<String, () -> Response> {
        val session = issued.sessionToken
        fun identifier(name: String): String =
            if (name == malformedIdentifier) "invalid-id" else UUID.randomUUID().toString()

        val requirementId = identifier("requirementId")
        val artifactId = identifier("artifactId")
        val versionId = identifier("versionId")
        val occurrenceId = identifier("occurrenceId")
        val packageId = identifier("packageId")
        val reviewId = identifier("reviewId")
        val factId = identifier("factId")
        val file: FileUpload = mock()
        val request = InformationRequestNoAuthRequestResourceOperations::class.java
        val evidence = InformationRequestNoAuthEvidenceResourceOperations::class.java
        val submission = InformationRequestNoAuthSubmissionResourceOperations::class.java
        val review = InformationRequestNoAuthReviewResourceOperations::class.java
        return mapOf(
            route(request, "get") { requestResource.get(id, LINK_TOKEN, session) },
            route(request, "parties") { requestResource.parties(id, LINK_TOKEN, session) },
            route(request, "responseWorkspace") { requestResource.responseWorkspace(id, LINK_TOKEN, session) },
            route(request, "patchResponses") {
                requestResource.patchResponses(
                    id,
                    PatchInformationRequestResponsesRequest(
                        listOf(
                            InformationRequestResponsePatchRequest(
                                UUID.randomUUID(),
                                InformationRequestResponseDisposition.PROVIDED
                            )
                        ),
                    ),
                    LINK_TOKEN,
                    IF_MATCH,
                    COMMAND_KEY,
                    session,
                )
            },
            route(request, "addGroupOccurrence") {
                requestResource.addGroupOccurrence(
                    id,
                    CreateInformationRequestGroupOccurrenceRequest("entries"),
                    LINK_TOKEN,
                    IF_MATCH,
                    COMMAND_KEY,
                    session,
                )
            },
            route(request, "removeGroupOccurrence") {
                requestResource.removeGroupOccurrence(id, occurrenceId, LINK_TOKEN, IF_MATCH, COMMAND_KEY, session)
            },
            route(request, "reorderGroupOccurrences") {
                requestResource.reorderGroupOccurrences(
                    id,
                    ReorderInformationRequestGroupOccurrencesRequest(
                        "entries",
                        occurrenceIds = listOf(UUID.randomUUID())
                    ),
                    LINK_TOKEN,
                    IF_MATCH,
                    COMMAND_KEY,
                    session,
                )
            },
            route(evidence, "list") { evidenceResource.list(id, requirementId, LINK_TOKEN, session) },
            route(evidence, "artifact") {
                evidenceResource.artifact(
                    id,
                    requirementId,
                    artifactId,
                    LINK_TOKEN,
                    session
                )
            },
            route(evidence, "upload") {
                evidenceResource.upload(
                    id, requirementId, file, null, null, null, null, null, null, null, null, null, null,
                    IF_MATCH, COMMAND_KEY, LINK_TOKEN, session,
                )
            },
            route(evidence, "replace") {
                evidenceResource.replace(
                    id, requirementId, artifactId, file, null, null, null, null, null, null, null, null, null, null,
                    IF_MATCH, COMMAND_KEY, LINK_TOKEN, session,
                )
            },
            route(evidence, "withdraw") {
                evidenceResource.withdraw(
                    id, requirementId, artifactId, InformationRequestEvidenceStateChangeRequest("superseded"),
                    IF_MATCH, COMMAND_KEY, LINK_TOKEN, session,
                )
            },
            route(evidence, "remove") {
                evidenceResource.remove(
                    id,
                    requirementId,
                    artifactId,
                    InformationRequestEvidenceStateChangeRequest("superseded"),
                    IF_MATCH,
                    COMMAND_KEY,
                    LINK_TOKEN,
                    session
                )
            },
            route(evidence, "download") {
                evidenceResource.download(
                    id,
                    requirementId,
                    artifactId,
                    versionId,
                    LINK_TOKEN,
                    session
                )
            },
            route(evidence, "preview") {
                evidenceResource.preview(
                    id,
                    requirementId,
                    artifactId,
                    versionId,
                    LINK_TOKEN,
                    session
                )
            },
            route(submission, "list") { submissionResource.list(id, LINK_TOKEN, session) },
            route(submission, "detail") { submissionResource.detail(id, packageId, LINK_TOKEN, session) },
            route(submission, "submit") {
                submissionResource.submit(
                    id,
                    SubmitInformationRequestPackageRequest(),
                    LINK_TOKEN,
                    IF_MATCH,
                    COMMAND_KEY,
                    session
                )
            },
            route(submission, "withdraw") {
                submissionResource.withdraw(
                    id, packageId, WithdrawInformationRequestPackageRequest("reason.withdrawn"),
                    LINK_TOKEN, IF_MATCH, COMMAND_KEY, session,
                )
            },
            route(InformationRequestNoAuthSubmissionPreviewResourceOperations::class.java, "preview") {
                previewResource.preview(id, null, LINK_TOKEN, session)
            },
            route(InformationRequestNoAuthAttestationResourceOperations::class.java, "record") {
                attestationResource.record(
                    id,
                    requirementId,
                    RecordInformationRequestAttestationRequest(InformationRequestAttestationDecision.ASSENTED),
                    LINK_TOKEN,
                    IF_MATCH,
                    COMMAND_KEY,
                    session,
                )
            },
            route(review, "comment") {
                reviewResource.comment(
                    id, reviewId,
                    RecordInformationRequestReviewCommentRequest(
                        submissionItemId = UUID.randomUUID(),
                        body = "Please clarify"
                    ),
                    LINK_TOKEN, session, COMMAND_KEY,
                )
            },
            route(review, "appeal") {
                reviewResource.appeal(
                    id, reviewId, ReopenInformationRequestReviewRequest("The record was complete"),
                    LINK_TOKEN, session, IF_MATCH, COMMAND_KEY,
                )
            },
            route(InformationRequestNoAuthReviewResultResourceOperations::class.java, "results") {
                reviewResultResource.results(id, LINK_TOKEN, session)
            },
            route(InformationRequestNoAuthCarryForwardResourceOperations::class.java, "list") {
                carryForwardResource.list(id, LINK_TOKEN, session)
            },
            route(InformationRequestNoAuthAcceptedFactOfferResourceOperations::class.java, "offers") {
                offerResource.offers(id, LINK_TOKEN, session)
            },
            route(InformationRequestNoAuthFactRecertificationResourceOperations::class.java, "recertify") {
                recertificationResource.recertify(
                    id, factId, RecertifyInformationRequestAcceptedFactRequest(UUID.randomUUID(), true),
                    LINK_TOKEN, session, IF_MATCH, COMMAND_KEY,
                )
            },
            route(InformationRequestNoAuthAmendmentResourceOperations::class.java, "list") {
                amendmentResource.list(id, LINK_TOKEN, session)
            },
        )
    }

    private fun route(operations: Class<*>, method: String, call: () -> Response): Pair<String, () -> Response> =
        "${operations.simpleName}.$method" to call

    private fun declaredRequestRoutes(): Set<String> {
        val classes =
            Paths.get(InformationRequestNoAuthRequestResourceOperations::class.java.protectionDomain.codeSource.location.toURI())
        val operations = Files.walk(classes.resolve(RESOURCE_PACKAGE.replace('.', '/'))).use { files ->
            files.map { classes.relativize(it).joinToString(".").removeSuffix(".class") }
                .filter { it.endsWith("ResourceOperations") }
                .map { Class.forName(it) }
                .toList()
        }
        return operations
            .filter {
                it.getAnnotation(Path::class.java)?.value?.trimStart('/')?.startsWith(NO_AUTH_REQUEST_PATH) == true
            }
            .flatMap { type -> type.declaredMethods.filter(::isRoute).map { "${type.simpleName}.${it.name}" } }
            .toSet()
    }

    private fun isRoute(method: Method): Boolean =
        method.annotations.any { it.annotationClass.java.isAnnotationPresent(HttpMethod::class.java) }

    private companion object {
        const val LINK_TOKEN = InformationRequestNoAuthSessionFixture.BOOTSTRAP_TOKEN
        const val IF_MATCH = "\"request-etag\""
        const val COMMAND_KEY = "binding-key"
        const val RESOURCE_PACKAGE = "com.docuhyphen.app.api.resource"
        const val NO_AUTH_REQUEST_PATH = "no-auth/information-requests/{id}"
    }
}
