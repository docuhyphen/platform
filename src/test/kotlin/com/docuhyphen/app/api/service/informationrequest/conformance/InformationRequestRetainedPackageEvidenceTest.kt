package com.docuhyphen.app.api.service.informationrequest.conformance

import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.amendment.AmendInformationRequestCommand
import com.docuhyphen.app.api.model.informationrequest.evidence.*
import com.docuhyphen.app.api.model.informationrequest.occurrence.RemoveInformationRequestGroupOccurrenceCommand
import com.docuhyphen.app.api.model.informationrequest.submission.SubmitInformationRequestPackageCommand
import com.docuhyphen.app.api.model.informationrequest.submission.WithdrawInformationRequestPackageCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.occurrence.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestSignedInCaller
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.amendment.InformationRequestAmendmentService
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateWalkingSkeletonStore
import com.docuhyphen.app.api.service.informationrequest.capability.model.WalkingSkeletonDraft
import com.docuhyphen.app.api.service.informationrequest.conformance.model.PublishedRequest
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceQueryService
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceUploadService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestGroupOccurrenceService
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionContentCollector
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionEvidenceQueryService
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionService
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationWriter
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateMaterializer
import com.docuhyphen.app.api.service.user.AppUserService
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.security.ForbiddenException
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
class InformationRequestRetainedPackageEvidenceTest {
    @Inject
    lateinit var dataSource: DataSource

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

    @Inject
    lateinit var occurrences: InformationRequestGroupOccurrenceRepository

    @Inject
    lateinit var groups: InformationRequestGroupOccurrenceService

    @Inject
    lateinit var uploads: InformationRequestEvidenceUploadService

    @Inject
    lateinit var collection: InformationRequestEvidenceQueryService

    @Inject
    lateinit var retained: InformationRequestSubmissionEvidenceQueryService

    @Inject
    lateinit var submissions: InformationRequestSubmissionService

    @Inject
    lateinit var collector: InformationRequestSubmissionContentCollector

    @Inject
    lateinit var amendments: InformationRequestAmendmentService

    @Inject
    lateinit var appUsers: AppUserService

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
    private val store by lazy {
        InformationRequestTemplateWalkingSkeletonStore(
            dataSource,
            definitions,
            versions,
            capabilities,
            writer
        )
    }

    @Test
    fun `a request manager downloads a submitted file through its package`() {
        val provided = provided()
        val packageId = submit(provided)

        assertOpens(
            provided,
            packageId,
            provided.request.access(DECISION_MAKER),
            InformationRequestEvidenceContentUse.DOWNLOAD
        )
    }

    @Test
    fun `a withdrawn package keeps opening its exact file while its occurrence stays`() {
        val provided = provided()
        val packageId = submit(provided)
        withdraw(provided, packageId)

        assertOpens(
            provided,
            packageId,
            provided.request.access(DECISION_MAKER),
            InformationRequestEvidenceContentUse.DOWNLOAD
        )
    }

    @Test
    fun `a withdrawn package downloads its exact file after its occurrence is removed`() {
        val provided = provided()
        val packageId = submit(provided)
        withdraw(provided, packageId)
        removeOccurrence(provided)

        assertOpens(
            provided,
            packageId,
            provided.request.access(DECISION_MAKER),
            InformationRequestEvidenceContentUse.DOWNLOAD
        )
    }

    @Test
    fun `a withdrawn package previews its exact file after its occurrence is removed`() {
        val provided = provided()
        val packageId = submit(provided)
        withdraw(provided, packageId)
        removeOccurrence(provided)

        assertOpens(
            provided,
            packageId,
            provided.request.access(DECISION_MAKER),
            InformationRequestEvidenceContentUse.PREVIEW
        )
    }

    @Test
    fun `the contributor who provided a retained file opens it after its occurrence is removed`() {
        val provided = provided()
        val packageId = submit(provided)
        withdraw(provided, packageId)
        removeOccurrence(provided)

        assertOpens(
            provided,
            packageId,
            provided.request.access(CONTRIBUTOR),
            InformationRequestEvidenceContentUse.DOWNLOAD
        )
    }

    @Test
    fun `a withdrawn package opens its exact file after an amendment removes its Requirement`() {
        val request = issue("retained-evidence-amended", amendable = true)
        val requirementId = support.requirement(request, "provided-record").id
        val (uploaded, bytes) = uploadWithBytes(request, requirementId, "provided-record.pdf")
        val provided = Provided(
            request,
            requirementId,
            uploaded.artifact.artifact.id,
            uploaded.artifact.versions.single().version.id,
            bytes
        )
        val packageId = submit(provided)
        withdraw(provided, packageId)

        removeProvidedRecord(request)

        assertOpens(provided, packageId, request.access(DECISION_MAKER), InformationRequestEvidenceContentUse.DOWNLOAD)
        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            collection.openContent(
                request.requestId, requirementId, provided.artifactId, provided.versionId,
                request.access(DECISION_MAKER), InformationRequestEvidenceContentUse.DOWNLOAD,
            ).close()
        }
        assertEquals(InformationRequestErrorCatalog.NOT_FOUND, refusal.reasonCode)
    }

    @Test
    fun `a signed-in request manager reads a retained file over HTTP after its occurrence is removed`() {
        val provided = provided()
        val packageId = submit(provided)
        withdraw(provided, packageId)
        removeOccurrence(provided)
        val manager = InformationRequestSignedInCaller.signIn(appUsers, provided.request.users.getValue(DECISION_MAKER))
        val path =
            "/information-requests/${provided.request.requestId}/submissions/$packageId/evidence/${provided.versionId}"

        val download = manager.authorized().get("$path/content")
        val preview = manager.authorized().get("$path/preview")
        val detail =
            manager.authorized().get("/information-requests/${provided.request.requestId}/submissions/$packageId")

        assertEquals(200, download.statusCode, download.asString())
        assertArrayEquals(provided.bytes, download.asByteArray())
        assertTrue(download.header("Content-Disposition").startsWith("attachment; "))
        assertEquals("nosniff", download.header("X-Content-Type-Options"))
        assertEquals(200, preview.statusCode, preview.asString())
        assertTrue(preview.header("Content-Disposition").startsWith("inline; "))
        assertEquals(200, detail.statusCode, detail.asString())
        assertEquals(packageId.toString(), detail.jsonPath().getString("id"))
    }

    @Test
    fun `the retained file route answers its refusals as JSON over HTTP`() {
        val provided = provided()
        val packageId = submit(provided)
        val manager = InformationRequestSignedInCaller.signIn(appUsers, provided.request.users.getValue(DECISION_MAKER))
        val outsider = InformationRequestSignedInCaller.signIn(appUsers, support.outsider())
        val path = "/information-requests/${provided.request.requestId}/submissions/$packageId/evidence"

        val unknown = manager.authorized().get("$path/${UUID.randomUUID()}/content")
        val refused = outsider.authorized().get("$path/${provided.versionId}/content")

        assertEquals(404, unknown.statusCode, unknown.asString())
        assertEquals(InformationRequestErrorCatalog.NOT_FOUND, unknown.jsonPath().getString("reasonCode"))
        assertEquals(403, refused.statusCode, refused.asString())
        assertTrue(!refused.jsonPath().getString("errorMessage").isNullOrBlank(), refused.asString())
    }

    @Test
    fun `the active collection still refuses the file of a removed occurrence`() {
        val provided = provided()
        withdraw(provided, submit(provided))
        removeOccurrence(provided)

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            collection.openContent(
                provided.request.requestId, provided.requirementId, provided.artifactId, provided.versionId,
                provided.request.access(DECISION_MAKER), InformationRequestEvidenceContentUse.DOWNLOAD,
            ).close()
        }
        assertEquals(InformationRequestErrorCatalog.GROUP_OCCURRENCE_REMOVED, refusal.reasonCode)
    }

    @Test
    fun `a package opens only a file it holds`() {
        val provided = provided()
        val packageId = submit(provided)
        withdraw(provided, packageId)
        val later = upload(provided.request, provided.requirementId, "later-record.pdf")

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            open(
                provided.request.requestId,
                packageId,
                later.artifact.versions.single().version.id,
                provided.request.access(DECISION_MAKER)
            )
        }
        assertEquals(InformationRequestErrorCatalog.NOT_FOUND, refusal.reasonCode)
    }

    @Test
    fun `another request's package opens nothing from this request`() {
        val provided = provided()
        val packageId = submit(provided)
        val other = issue("retained-evidence-other")

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            open(other.requestId, packageId, provided.versionId, other.access(DECISION_MAKER))
        }
        assertEquals(InformationRequestErrorCatalog.NOT_FOUND, refusal.reasonCode)
    }

    @Test
    fun `a caller who may not open the item's files is refused its retained file`() {
        val provided = provided()
        val packageId = submit(provided)
        withdraw(provided, packageId)
        removeOccurrence(provided)
        val outsider = RequestAccessContext(
            PrincipalRef.user(support.outsider()),
            AuthorizationContext(sessionRef = "outsider-session")
        )

        assertThrows(ForbiddenException::class.java) {
            open(provided.request.requestId, packageId, provided.versionId, provided.request.access(PREPARER))
        }
        assertThrows(ForbiddenException::class.java) {
            open(provided.request.requestId, packageId, provided.versionId, outsider)
        }
    }

    @Test
    fun `a caller who may not open a package's file cannot tell it from a file the package does not hold`() {
        val provided = provided()
        val packageId = submit(provided)
        val preparer = provided.request.access(PREPARER)

        val hidden = assertThrows(ForbiddenException::class.java) {
            open(provided.request.requestId, packageId, provided.versionId, preparer)
        }
        val unknown = assertThrows(ForbiddenException::class.java) {
            open(provided.request.requestId, packageId, UUID.randomUUID(), preparer)
        }

        assertEquals(hidden.message, unknown.message)
    }

    @Test
    fun `over HTTP a caller who may not open a package's file gets the same refusal for it and for a file the package does not hold`() {
        val provided = provided()
        val packageId = submit(provided)
        val preparer = InformationRequestSignedInCaller.signIn(appUsers, provided.request.users.getValue(PREPARER))
        val path = "/information-requests/${provided.request.requestId}/submissions/$packageId/evidence"

        listOf("content", "preview").forEach { use ->
            val hidden = preparer.authorized().get("$path/${provided.versionId}/$use")
            val unknown = preparer.authorized().get("$path/${UUID.randomUUID()}/$use")

            assertEquals(403, hidden.statusCode, hidden.asString())
            assertEquals(hidden.statusCode, unknown.statusCode, unknown.asString())
            assertEquals(hidden.asString(), unknown.asString())
        }
    }

    private class Provided(
        val request: PublishedRequest,
        val requirementId: UUID,
        val artifactId: UUID,
        val versionId: UUID,
        val bytes: ByteArray,
    )

    private fun provided(): Provided {
        val request = issue("retained-evidence")
        support.addOccurrence(
            groups,
            request,
            request.access(CONTRIBUTOR),
            "entries",
            null,
            UUID.randomUUID().toString()
        )
        val requirementId = support.requirement(request, "provided-record", "entries[0]").id
        val (uploaded, bytes) = uploadWithBytes(request, requirementId, "provided-record.pdf")
        return Provided(
            request,
            requirementId,
            uploaded.artifact.artifact.id,
            uploaded.artifact.versions.single().version.id,
            bytes,
        )
    }

    private fun issue(templateKey: String, amendable: Boolean = false): PublishedRequest =
        support.issue(
            "$templateKey-${UUID.randomUUID().toString().take(8)}",
            emptyList(),
            listOf(CONTRIBUTOR, PREPARER, DECISION_MAKER)
        ) { _, _ ->
            if (amendable) configuration(
                anchor = null,
                withProvidedRecord = true
            ) else configuration(anchor = "entries", withProvidedRecord = true)
        }

    private fun configuration(anchor: String?, withProvidedRecord: Boolean) =
        InformationRequestTemplateConfigurationRequest(
            submissionMode = InformationRequestSubmissionMode.STAGED,
            groups = if (anchor == null) emptyList()
            else listOf(InformationRequestTemplateGroupRequest(anchor, minOccurrences = 0, maxOccurrences = 3)),
            sections = listOf(
                InformationRequestTemplateSectionRequest(
                    "records", "Records", submissionStageKey = FIRST_STAGE,
                    requirements = listOfNotNull(
                        if (withProvidedRecord) InformationRequestTemplateRequirementRequest(
                            "provided-record", InformationRequestRequirementType.DOCUMENT, "Provide the record",
                            occurrenceAnchorKey = anchor,
                            requiredness = InformationRequestRequiredness.REQUIRED,
                            permittedDispositions = listOf(InformationRequestResponseDisposition.PROVIDED),
                            evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(
                                minimumFileCount = 1,
                                maximumFileCount = 2
                            ),
                        ) else null,
                        if (anchor == null) InformationRequestTemplateRequirementRequest(
                            "kept-record", InformationRequestRequirementType.DOCUMENT, "Provide any further record",
                            requiredness = InformationRequestRequiredness.OPTIONAL,
                            permittedDispositions = listOf(InformationRequestResponseDisposition.PROVIDED),
                            evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(maximumFileCount = 1),
                        ) else null,
                    ),
                ),
                InformationRequestTemplateSectionRequest(
                    "confirmation", "Confirmation", submissionStageKey = "later",
                    requirements = listOf(
                        InformationRequestTemplateRequirementRequest(
                            "confirm-records",
                            InformationRequestRequirementType.RESPONSE_ATTESTATION,
                            "Confirm the records",
                            requiredness = InformationRequestRequiredness.REQUIRED,
                            permittedDispositions = listOf(InformationRequestResponseDisposition.PROVIDED),
                        )
                    ),
                ),
            ),
        )

    private fun removeProvidedRecord(request: PublishedRequest) {
        val next = store.insertNextDraft(
            WalkingSkeletonDraft(
                request.definitionId,
                request.templateVersionId,
                request.ownerId,
                "retained-evidence-amended"
            ),
            2,
        )
        store.publish(next, configuration(anchor = null, withProvidedRecord = false))
        QuarkusTransaction.requiringNew().run {
            amendments.amend(
                AmendInformationRequestCommand(
                    requestId = request.requestId,
                    targetTemplateVersionId = next.versionId,
                    reasonCode = "scope-reduced",
                    access = request.access(DECISION_MAKER),
                    precondition = CommandPrecondition.ExpectedRevision(
                        InformationRequestETag.aggregateOf(requireNotNull(requests.findById(request.requestId))),
                    ),
                    idempotencyKey = UUID.randomUUID().toString(),
                )
            )
        }
        assertEquals(
            next.versionId,
            QuarkusTransaction.requiringNew().call { requests.findById(request.requestId)!!.templateVersionId })
    }

    private fun upload(
        request: PublishedRequest,
        requirementId: UUID,
        fileName: String
    ): InformationRequestEvidenceCommandResult =
        uploadWithBytes(request, requirementId, fileName).first

    private fun uploadWithBytes(
        request: PublishedRequest,
        requirementId: UUID,
        fileName: String,
    ): Pair<InformationRequestEvidenceCommandResult, ByteArray> {
        val target = File.createTempFile("retained-evidence", ".pdf").apply { deleteOnExit() }
        PDDocument().use { pdf ->
            pdf.addPage(PDPage())
            pdf.documentInformation.title = UUID.randomUUID().toString()
            pdf.save(target)
        }
        val bytes = target.readBytes()
        val current = QuarkusTransaction.requiringNew()
            .call { collection.list(request.requestId, requirementId, request.access(CONTRIBUTOR)) }
        val uploaded = QuarkusTransaction.requiringNew().call {
            uploads.upload(
                UploadInformationRequestEvidenceCommand(
                    request.requestId,
                    requirementId,
                    request.access(CONTRIBUTOR),
                    InformationRequestEvidenceSurface.AUTHENTICATED,
                    CommandPrecondition.ExpectedRevision(current.evidenceETag),
                    UUID.randomUUID().toString(),
                    InformationRequestEvidenceFile(
                        target,
                        fileName,
                        "application/pdf",
                        DocumentEncryptionMode.INTERNAL
                    ),
                    InformationRequestEvidenceAttributes.NONE,
                )
            )
        }
        return uploaded to bytes
    }

    private fun submit(provided: Provided): UUID = QuarkusTransaction.requiringNew().call {
        val request = provided.request
        val content = collector.collect(requireNotNull(requests.findById(request.requestId)), FIRST_STAGE)
        submissions.submit(
            SubmitInformationRequestPackageCommand(
                request.requestId, FIRST_STAGE, request.access(CONTRIBUTOR),
                CommandPrecondition.ExpectedRevision(
                    InformationRequestETag.submissionOf(
                        FIRST_STAGE,
                        content.contentHash
                    )
                ),
                UUID.randomUUID().toString(),
            )
        )
    }.submission.submissionPackage.id

    private fun withdraw(provided: Provided, packageId: UUID) {
        QuarkusTransaction.requiringNew().run {
            submissions.withdraw(
                WithdrawInformationRequestPackageCommand(
                    provided.request.requestId,
                    packageId,
                    "records-revised",
                    provided.request.access(CONTRIBUTOR),
                    CommandPrecondition.ExpectedRevision(support.responseETag(provided.request)),
                    UUID.randomUUID().toString(),
                )
            )
        }
    }

    private fun removeOccurrence(provided: Provided) {
        val request = provided.request
        val occurrenceId =
            QuarkusTransaction.requiringNew().call { occurrences.findForRequest(request.requestId).single().id }
        QuarkusTransaction.requiringNew().run {
            groups.remove(
                RemoveInformationRequestGroupOccurrenceCommand(
                    request.requestId,
                    request.access(CONTRIBUTOR),
                    CommandPrecondition.ExpectedRevision(support.responseETag(request)),
                    UUID.randomUUID().toString(),
                    occurrenceId,
                )
            )
        }
        assertTrue(QuarkusTransaction.requiringNew().call { occurrences.findForRequest(request.requestId) }.isEmpty())
    }

    private fun open(
        requestId: UUID,
        packageId: UUID,
        versionId: UUID,
        access: RequestAccessContext,
        use: InformationRequestEvidenceContentUse = InformationRequestEvidenceContentUse.DOWNLOAD,
    ) = retained.openContent(requestId, packageId, versionId, access, use).use { it.file.readBytes() to it }

    private fun assertOpens(
        provided: Provided,
        packageId: UUID,
        access: RequestAccessContext,
        use: InformationRequestEvidenceContentUse
    ) {
        val (bytes, content) = open(provided.request.requestId, packageId, provided.versionId, access, use)

        assertArrayEquals(provided.bytes, bytes)
        assertEquals("application/pdf", content.mediaType)
        assertEquals("provided-record.pdf", content.fileName)
        assertEquals(use == InformationRequestEvidenceContentUse.PREVIEW, content.inline)
    }

    private companion object {
        const val CONTRIBUTOR = "CONTRIBUTOR"
        const val PREPARER = "PREPARER"
        const val DECISION_MAKER = "DECISION_MAKER"
        const val FIRST_STAGE = "first"
    }
}
