package com.docuhyphen.app.api.service.informationrequest.capability

import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.informationrequest.condition.InformationRequestConditionEvaluationState
import com.docuhyphen.app.api.model.informationrequest.response.ResponseNarrativePatch
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceArtifactRepository
import com.docuhyphen.app.api.repository.informationrequest.response.InformationRequestResponseRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateWalkingSkeletonRuntime.Companion.ALTERNATE_EVIDENCE
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateWalkingSkeletonRuntime.Companion.PRIMARY_EVIDENCE
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateWalkingSkeletonRuntime.Companion.RECORDED_SUMMARY
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateWalkingSkeletonRuntime.Companion.REPORTED_ITEM
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateWalkingSkeletonRuntime.Companion.RESPONSE_CONFIRMATION
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateWalkingSkeletonRuntime.Companion.ROOT
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateWalkingSkeletonRuntime.Companion.SUBJECT_RULE
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateWalkingSkeletonRuntime.Companion.SUPPORTING_RECORD
import com.docuhyphen.app.api.service.informationrequest.capability.model.IssuedWalkingSkeleton
import com.docuhyphen.app.api.service.informationrequest.capability.model.WalkingSkeletonProgress
import com.docuhyphen.app.api.service.informationrequest.condition.InformationRequestConditionEvaluationService
import com.docuhyphen.app.api.service.informationrequest.conformance.PublishedRequestSupport
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceUploadService
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestGroupOccurrenceService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestCompletenessProgressService
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
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestTemplateWalkingSkeletonRuntimeResponseTest {
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
    lateinit var responseRepository: InformationRequestResponseRepository

    @Inject
    lateinit var occurrences: InformationRequestGroupOccurrenceService

    @Inject
    lateinit var conditions: InformationRequestConditionEvaluationService

    @Inject
    lateinit var progressService: InformationRequestCompletenessProgressService

    @Inject
    lateinit var authorizationService: AuthorizationService

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
    fun `a basic fixture draft saves sparsely and its completeness counts each collected field, conforming document, and assent`() {
        val issued = skeletons.issueBasic(listOf(CONTRIBUTOR, ATTESTOR))
        val services = runtime.build(issued.requestId, centralAuthorization = authorizationService)
        val contributor = issued.request.access(CONTRIBUTOR)
        val summary = skeletons.requirementId(issued, RECORDED_SUMMARY)

        assertEquals(
            mapOf(RECORDED_SUMMARY to issued.request.fields.getValue(RECORDED_SUMMARY).fieldDefinitionId),
            issued.fixture.configuration.sections.flatMap { it.requirements }
                .filter { it.requirementType == InformationRequestRequirementType.FIELD }
                .associate { it.requirementKey to it.collectedFieldDefinitionId },
        )
        assertEquals(emptyList<String>(), issued.fixture.configuration.groups.map { it.groupKey })
        assertEquals(
            emptyList<Triple<String, String, InformationRequestConditionEvaluationState>>(),
            conditionStates(issued)
        )
        assertEquals(WalkingSkeletonProgress(0, 3, 0), progress(issued))

        skeletons.respond(
            services,
            issued,
            RECORDED_SUMMARY,
            contributor,
            "sparse-draft",
            narrative = ResponseNarrativePatch.Set("Draft note only")
        )
        assertEquals(InformationRequestResponseDisposition.NOT_ANSWERED, disposition(issued, summary))
        assertEquals(WalkingSkeletonProgress(0, 3, 0), progress(issued))

        skeletons.respond(
            services,
            issued,
            RECORDED_SUMMARY,
            contributor,
            "collected-summary",
            fieldValue = "Recorded summary"
        )
        assertEquals(InformationRequestResponseDisposition.NOT_ANSWERED, disposition(issued, summary))
        assertEquals(WalkingSkeletonProgress(1, 3, 33), progress(issued))

        skeletons.upload(issued, SUPPORTING_RECORD, contributor, "supporting-record-file")
        assertEquals(WalkingSkeletonProgress(2, 3, 66), progress(issued))

        skeletons.assent(services, issued, RESPONSE_CONFIRMATION, issued.request.access(ATTESTOR), "confirm-response")
        assertEquals(WalkingSkeletonProgress(3, 3, 100), progress(issued))
    }

    @Test
    fun `a multi-party fixture saves drafts against its real condition and progress while no responding party can add the reported item its subject answers`() {
        val issued = skeletons.issueMultiParty(listOf(SUBJECT, CONTRIBUTOR, PREPARER, ATTESTOR))
        val services = runtime.build(issued.requestId, centralAuthorization = authorizationService)
        val contributor = issued.request.access(CONTRIBUTOR)
        val rootOnly = listOf(Triple(SUBJECT_RULE, ROOT, InformationRequestConditionEvaluationState.UNKNOWN))

        assertEquals(rootOnly, conditionStates(issued))
        assertEquals(WalkingSkeletonProgress(0, 3, 0), progress(issued))

        listOf(PREPARER, CONTRIBUTOR).forEach { party ->
            val steppedUp =
                issued.request.access(party).let { it.copy(authorization = it.authorization.copy(mfaSatisfied = true)) }
            assertThrows(
                ForbiddenException::class.java,
                {
                    published.addOccurrence(
                        occurrences,
                        issued.request,
                        steppedUp,
                        REPORTED_ITEM,
                        null,
                        "add-item-$party"
                    )
                },
                party
            )
        }
        assertEquals(
            issued.fixture.expectedRequirementKeys.size - issued.anchoredRequirementKeys.size,
            skeletons.materializedRequirementCount(issued)
        )

        skeletons.respond(
            services,
            issued,
            PRIMARY_EVIDENCE,
            contributor,
            "primary-draft",
            narrative = ResponseNarrativePatch.Set("Records are being gathered")
        )
        assertEquals(
            InformationRequestResponseDisposition.NOT_ANSWERED,
            disposition(issued, skeletons.requirementId(issued, PRIMARY_EVIDENCE))
        )
        assertEquals(WalkingSkeletonProgress(0, 3, 0), progress(issued))

        skeletons.upload(issued, ALTERNATE_EVIDENCE, contributor, "alternate-record-file")
        skeletons.respond(
            services,
            issued,
            ALTERNATE_EVIDENCE,
            contributor,
            "alternate-provided",
            disposition = InformationRequestResponseDisposition.PROVIDED
        )
        assertEquals(WalkingSkeletonProgress(2, 3, 66), progress(issued))
        assertEquals(rootOnly, conditionStates(issued))
    }

    private fun conditionStates(issued: IssuedWalkingSkeleton): List<Triple<String, String, InformationRequestConditionEvaluationState>> =
        QuarkusTransaction.requiringNew().call { conditions.evaluate(issued.requestId) }
            .map { Triple(it.ruleKey, it.occurrencePath, it.state) }

    private fun progress(issued: IssuedWalkingSkeleton): WalkingSkeletonProgress =
        QuarkusTransaction.requiringNew().call { progressService.evaluate(issued.requestId) }
            .let { WalkingSkeletonProgress(it.completedCount, it.totalCount, it.percentComplete) }

    private fun disposition(issued: IssuedWalkingSkeleton, requirementId: UUID): InformationRequestResponseDisposition =
        QuarkusTransaction.requiringNew().call {
            responseRepository.findCurrentForRequest(issued.requestId)
                .single { it.informationRequestRequirementId == requirementId }.disposition
        }

    private companion object {
        const val SUBJECT = "SUBJECT"
        const val CONTRIBUTOR = "CONTRIBUTOR"
        const val PREPARER = "PREPARER"
        const val ATTESTOR = "ATTESTOR"
    }
}
