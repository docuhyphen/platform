package com.docuhyphen.app.api.service.informationrequest.conformance

import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.evidence.*
import com.docuhyphen.app.api.model.informationrequest.submission.SubmitInformationRequestPackageCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestSignedInCaller
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.conformance.model.PublishedRequest
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceQueryService
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceUploadService
import com.docuhyphen.app.api.service.informationrequest.review.InformationRequestReviewQueryService
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
class InformationRequestReviewerSubmittedEvidenceTest {
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
    lateinit var reviewQueries: InformationRequestReviewQueryService

    @Inject
    lateinit var reviewRepository: InformationRequestReviewRepository

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

    @Test
    fun `a reviewer the review shows an item to downloads and previews its submitted file`() {
        val submitted = submitted(reviewed = true)
        val reviewer = submitted.request.access(REVIEWER)
        assertTrue(shown(submitted, reviewer), "The review shows the reviewer what was submitted")

        val download = open(submitted, reviewer, InformationRequestEvidenceContentUse.DOWNLOAD)
        val preview = open(submitted, reviewer, InformationRequestEvidenceContentUse.PREVIEW)

        assertArrayEquals(submitted.bytes, download.first)
        assertFalse(download.second)
        assertArrayEquals(submitted.bytes, preview.first)
        assertTrue(preview.second)
    }

    @Test
    fun `a signed-in reviewer reads a submitted file over HTTP`() {
        val submitted = submitted(reviewed = true)
        val reviewer = InformationRequestSignedInCaller.signIn(appUsers, submitted.request.users.getValue(REVIEWER))

        val response = reviewer.authorized().get(
            "/information-requests/${submitted.request.requestId}/submissions/${submitted.packageId}/evidence/${submitted.versionId}/content",
        )

        assertEquals(200, response.statusCode, response.asString())
        assertArrayEquals(submitted.bytes, response.asByteArray())
    }

    @Test
    fun `a protected item's file opens to a reviewer only while the reviewer is recently verified`() {
        val submitted = submitted(reviewed = true, protected = true)
        val unverified = submitted.request.access(REVIEWER)
        val verified = verified(unverified)

        assertFalse(shown(submitted, unverified))
        assertThrows(ForbiddenException::class.java) {
            open(
                submitted,
                unverified,
                InformationRequestEvidenceContentUse.DOWNLOAD
            )
        }
        assertTrue(shown(submitted, verified))
        assertArrayEquals(
            submitted.bytes,
            open(submitted, verified, InformationRequestEvidenceContentUse.DOWNLOAD).first
        )
    }

    @Test
    fun `the Requirement's own file routes still refuse a reviewer`() {
        val submitted = submitted(reviewed = true)

        assertThrows(ForbiddenException::class.java) {
            collection.openContent(
                submitted.request.requestId, submitted.requirementId, submitted.artifactId, submitted.versionId,
                submitted.request.access(REVIEWER), InformationRequestEvidenceContentUse.DOWNLOAD,
            ).close()
        }
    }

    @Test
    fun `a reviewer is refused the files of a submission no review covers`() {
        val submitted = submitted(reviewed = false)

        assertTrue(
            QuarkusTransaction.requiringNew().call { reviewRepository.findForRequest(submitted.request.requestId) }
                .isEmpty()
        )
        assertThrows(ForbiddenException::class.java) {
            open(submitted, submitted.request.access(REVIEWER), InformationRequestEvidenceContentUse.DOWNLOAD)
        }
    }

    @Test
    fun `an answering co-party who does not review stays refused a reviewed file`() {
        val submitted = submitted(reviewed = true)

        assertThrows(ForbiddenException::class.java) {
            open(submitted, submitted.request.access(PREPARER), InformationRequestEvidenceContentUse.DOWNLOAD)
        }
    }

    @Test
    fun `the providing contributor and the request manager still open the file`() {
        val submitted = submitted(reviewed = true)

        assertArrayEquals(
            submitted.bytes,
            open(submitted, submitted.request.access(CONTRIBUTOR), InformationRequestEvidenceContentUse.DOWNLOAD).first
        )
        assertArrayEquals(
            submitted.bytes,
            open(
                submitted,
                submitted.request.access(DECISION_MAKER),
                InformationRequestEvidenceContentUse.DOWNLOAD
            ).first
        )
    }

    private class Submitted(
        val request: PublishedRequest,
        val requirementId: UUID,
        val artifactId: UUID,
        val versionId: UUID,
        val packageId: UUID,
        val bytes: ByteArray,
    )

    private fun submitted(reviewed: Boolean, protected: Boolean = false): Submitted {
        val request = support.issue(
            "reviewed-evidence-${UUID.randomUUID().toString().take(8)}", emptyList(),
            listOf(CONTRIBUTOR, PREPARER, REVIEWER, DECISION_MAKER),
        ) { _, _ ->
            InformationRequestTemplateConfigurationRequest(
                sections = listOf(
                    InformationRequestTemplateSectionRequest(
                        "records", "Records",
                        requirements = listOf(
                            InformationRequestTemplateRequirementRequest(
                                "provided-record", InformationRequestRequirementType.DOCUMENT, "Provide the record",
                                requiredness = InformationRequestRequiredness.REQUIRED,
                                reviewPolicy = if (reviewed) InformationRequestReviewPolicy.REQUIRED else InformationRequestReviewPolicy.NOT_REQUIRED,
                                confidentialityCompartmentKey = if (protected) "restricted-record" else null,
                                permittedDispositions = listOf(InformationRequestResponseDisposition.PROVIDED),
                                evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(
                                    minimumFileCount = 1,
                                    maximumFileCount = 1
                                ),
                            )
                        ),
                    )
                ),
                reviewStages = if (reviewed) listOf(
                    InformationRequestTemplateReviewStageRequest(
                        "check",
                        "Check",
                        sectionKeys = listOf("records")
                    )
                )
                else emptyList(),
            )
        }
        val requirement = support.requirement(request, "provided-record")
        val contributor = if (protected) verified(request.access(CONTRIBUTOR)) else request.access(CONTRIBUTOR)
        val target = File.createTempFile("reviewed-evidence", ".pdf").apply { deleteOnExit() }
        PDDocument().use { pdf ->
            pdf.addPage(PDPage())
            pdf.documentInformation.title = UUID.randomUUID().toString()
            pdf.save(target)
        }
        val uploaded = QuarkusTransaction.requiringNew().call {
            uploads.upload(
                UploadInformationRequestEvidenceCommand(
                    request.requestId, requirement.id, contributor, InformationRequestEvidenceSurface.AUTHENTICATED,
                    CommandPrecondition.ExpectedRevision(
                        InformationRequestETag.evidenceOf(
                            requirement.id,
                            emptyList()
                        )
                    ),
                    UUID.randomUUID().toString(),
                    InformationRequestEvidenceFile(
                        target,
                        "provided-record.pdf",
                        "application/pdf",
                        DocumentEncryptionMode.INTERNAL
                    ),
                    InformationRequestEvidenceAttributes.NONE,
                )
            )
        }
        val packageId = QuarkusTransaction.requiringNew().call {
            val content = collector.collect(requireNotNull(requests.findById(request.requestId)), null)
            submissions.submit(
                SubmitInformationRequestPackageCommand(
                    request.requestId, null, contributor,
                    CommandPrecondition.ExpectedRevision(
                        InformationRequestETag.submissionOf(
                            null,
                            content.contentHash
                        )
                    ),
                    UUID.randomUUID().toString(),
                )
            )
        }.submission.submissionPackage.id
        return Submitted(
            request, requirement.id, uploaded.artifact.artifact.id, uploaded.artifact.versions.single().version.id,
            packageId, target.readBytes(),
        )
    }

    private fun verified(access: RequestAccessContext): RequestAccessContext =
        access.copy(authorization = access.authorization.copy(mfaSatisfied = true))

    private fun shown(submitted: Submitted, access: RequestAccessContext): Boolean =
        QuarkusTransaction.requiringNew().call {
            val reviewId = reviewRepository.findForRequest(submitted.request.requestId)
                .single { it.packageId == submitted.packageId }.id
            val readable = reviewQueries.review(submitted.request.requestId, reviewId, access)
            readable.snapshot.submission.items.single().id in readable.contentVisibleItemIds
        }

    private fun open(
        submitted: Submitted,
        access: RequestAccessContext,
        use: InformationRequestEvidenceContentUse,
    ): Pair<ByteArray, Boolean> =
        retained.openContent(submitted.request.requestId, submitted.packageId, submitted.versionId, access, use)
            .use { it.file.readBytes() to it.inline }

    private companion object {
        const val CONTRIBUTOR = "CONTRIBUTOR"
        const val PREPARER = "PREPARER"
        const val REVIEWER = "REVIEWER"
        const val DECISION_MAKER = "DECISION_MAKER"
    }
}
