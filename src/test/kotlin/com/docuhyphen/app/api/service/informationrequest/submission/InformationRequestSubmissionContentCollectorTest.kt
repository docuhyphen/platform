package com.docuhyphen.app.api.service.informationrequest.submission

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceConformance
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceRequirementState
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.submission.InformationRequestEvidenceSubmissionFacts
import com.docuhyphen.app.api.model.informationrequest.submission.InformationRequestEvidenceSubmissionMember
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRevisionRepository
import com.docuhyphen.app.api.repository.informationrequest.occurrence.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateSectionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.fields.FieldValueRevisionQueryService
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceEvaluationService
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestSupportingEvidenceLinkService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*

class InformationRequestSubmissionContentCollectorTest {
    private val version = InformationRequestTemplateVersion().apply { templateDefinitionId = UUID.randomUUID() }
    private val request = InformationRequest().apply {
        exchangeId = UUID.randomUUID()
        templateVersionId = version.id
        ownerType = InformationRequestOwnerType.ORGANIZATION
        ownerOrganizationId = UUID.randomUUID()
        state = InformationRequestState.ISSUED
    }
    private val section = InformationRequestTemplateSection().apply {
        templateVersionId = version.id
        sectionKey = "records"
        title = "Records"
    }
    private val documentTemplate = templateRequirement("supporting-record", InformationRequestRequirementType.DOCUMENT)
    private val assertionTemplate =
        templateRequirement("recorded-assertion", InformationRequestRequirementType.RESPONSE_ATTESTATION)
    private val documentBinding = binding(documentTemplate)
    private val assertionBinding = binding(assertionTemplate)
    private val document = requirement(documentTemplate, documentBinding)
    private val assertion = requirement(assertionTemplate, assertionBinding)

    private val responseStore: InformationRequestResponseStore = mock()
    private val evidenceEvaluationService: InformationRequestEvidenceEvaluationService = mock()
    private val collector: InformationRequestSubmissionContentCollector

    init {
        val templateVersionRepository: InformationRequestTemplateVersionRepository = mock()
        val sectionRepository: InformationRequestTemplateSectionRepository = mock()
        val bindingRepository: InformationRequestTemplateRequirementBindingRepository = mock()
        val templateRequirementRepository: InformationRequestTemplateRequirementRepository = mock()
        val requirementRepository: InformationRequestRequirementRepository = mock()
        val revisionRepository: InformationRequestRequirementRevisionRepository = mock()
        val occurrenceRepository: InformationRequestGroupOccurrenceRepository = mock()
        val supportingEvidenceLinkService: InformationRequestSupportingEvidenceLinkService = mock()
        val stages: InformationRequestSubmissionStages = mock()

        whenever(templateVersionRepository.findById(version.id)).thenReturn(version)
        whenever(sectionRepository.findOrdered(version.id)).thenReturn(listOf(section))
        whenever(stages.stageOrderOf(any(), any())).thenReturn(emptyList())
        whenever(bindingRepository.findOrdered(version.id)).thenReturn(listOf(documentBinding, assertionBinding))
        whenever(templateRequirementRepository.findAllByDefinition(version.templateDefinitionId))
            .thenReturn(listOf(documentTemplate, assertionTemplate))
        whenever(requirementRepository.findForRequest(request.id)).thenReturn(listOf(document, assertion))
        whenever(revisionRepository.findCurrentForRequest(request.id)).thenReturn(
            listOf(
                revision(document),
                revision(assertion)
            )
        )
        whenever(occurrenceRepository.findForRequest(request.id)).thenReturn(emptyList())
        whenever(supportingEvidenceLinkService.linksFrom(any(), any())).thenReturn(emptyList())
        whenever(responseStore.findCurrentForRequest(request.id)).thenReturn(emptyList())
        whenever(evidenceEvaluationService.submissionFacts(any(), anyOrNull())).thenReturn(evidence())

        collector = InformationRequestSubmissionContentCollector(
            templateVersionRepository = templateVersionRepository,
            sectionRepository = sectionRepository,
            bindingRepository = bindingRepository,
            templateRequirementRepository = templateRequirementRepository,
            requirementRepository = requirementRepository,
            revisionRepository = revisionRepository,
            occurrenceRepository = occurrenceRepository,
            responseStore = responseStore,
            fieldValueRevisions = mock<FieldValueRevisionQueryService>(),
            evidenceEvaluationService = evidenceEvaluationService,
            supportingEvidenceLinkService = supportingEvidenceLinkService,
            stages = stages,
            conditionEvaluationService = mock(),
        )
    }

    @Test
    fun `the content hash is stable while nothing in scope changes`() {
        assertEquals(hash(), hash())
    }

    @Test
    fun `a disposition or a note recorded against an assertion changes the content hash`() {
        val before = hash()

        whenever(responseStore.findCurrentForRequest(request.id)).thenReturn(listOf(response(assertion)))
        val withNote = hash()
        whenever(responseStore.findCurrentForRequest(request.id))
            .thenReturn(listOf(response(assertion, InformationRequestResponseDisposition.EXCEPTION_REQUESTED)))
        val withException = hash()

        assertNotEquals(before, withNote)
        assertNotEquals(withNote, withException)
    }

    @Test
    fun `a governing inspection or malware assessment appended to a current evidence version changes the content hash`() {
        val versionId = UUID.randomUUID()
        val inspectionId = UUID.randomUUID()
        whenever(evidenceEvaluationService.submissionFacts(any(), anyOrNull())).thenReturn(
            evidence(
                versionId,
                inspectionId,
                null
            )
        )
        val inspected = hash()

        whenever(evidenceEvaluationService.submissionFacts(any(), anyOrNull()))
            .thenReturn(evidence(versionId, inspectionId, UUID.randomUUID()))
        val scanned = hash()
        whenever(evidenceEvaluationService.submissionFacts(any(), anyOrNull()))
            .thenReturn(evidence(versionId, UUID.randomUUID(), null))
        val reinspected = hash()

        assertNotEquals(inspected, scanned)
        assertNotEquals(inspected, reinspected)
    }

    private fun hash(): String = collector.collect(request, null).contentHash

    private fun evidence(
        versionId: UUID = UUID.randomUUID(),
        inspectionId: UUID? = null,
        malwareId: UUID? = null,
    ) = InformationRequestEvidenceSubmissionFacts(
        state = InformationRequestEvidenceRequirementState.SATISFIED,
        members = listOf(
            InformationRequestEvidenceSubmissionMember(
                artifactId = ARTIFACT_ID,
                versionId = versionId,
                versionNumber = 1,
                documentVersionId = DOCUMENT_VERSION_ID,
                contentHashAlgorithm = "SHA_256",
                contentHash = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                contentLength = 3,
                contentVerification = "VERIFIED",
                conformance = InformationRequestEvidenceConformance.CONFORMING,
                inspectionAssessmentId = inspectionId,
                malwareAssessmentId = malwareId,
            ),
        ),
    )

    private fun response(
        target: InformationRequestRequirement,
        disposition: InformationRequestResponseDisposition = InformationRequestResponseDisposition.NOT_ANSWERED,
    ) = InformationRequestResponse().apply {
        id = RESPONSE_ID
        informationRequestId = request.id
        informationRequestRequirementId = target.id
        requirementRevisionId = UUID.randomUUID()
        occurrencePath = target.occurrencePath
        this.disposition = disposition
        narrative = "Recorded after the review"
        responseRevision = 3
        recordedByPrincipalKind = PrincipalKind.USER
        recordedByPrincipalId = UUID.randomUUID()
    }

    private fun templateRequirement(key: String, type: InformationRequestRequirementType) =
        InformationRequestTemplateRequirement().apply {
            templateDefinitionId = version.templateDefinitionId
            requirementKey = key
            requirementType = type
        }

    private fun binding(templateRequirement: InformationRequestTemplateRequirement) =
        InformationRequestTemplateRequirementBinding().apply {
            templateVersionId = version.id
            templateDefinitionId = version.templateDefinitionId
            templateRequirementId = templateRequirement.id
            templateSectionId = section.id
            prompt = "Provide the recorded item"
        }

    private fun requirement(
        templateRequirement: InformationRequestTemplateRequirement,
        binding: InformationRequestTemplateRequirementBinding,
    ) = InformationRequestRequirement().apply {
        informationRequestId = request.id
        sourceTemplateVersionId = version.id
        sourceTemplateRequirementId = templateRequirement.id
        sourceTemplateBindingId = binding.id
        occurrencePath = "root"
    }

    private fun revision(target: InformationRequestRequirement) = InformationRequestRequirementRevision().apply {
        informationRequestRequirementId = target.id
        informationRequestId = request.id
        sourceTemplateVersionId = version.id
        sourceTemplateRequirementId = target.sourceTemplateRequirementId
        sourceTemplateBindingId = target.sourceTemplateBindingId
        occurrencePath = target.occurrencePath
        configurationHashSha256 = "0".repeat(64)
    }

    private companion object {
        val ARTIFACT_ID: UUID = UUID.randomUUID()
        val DOCUMENT_VERSION_ID: UUID = UUID.randomUUID()
        val RESPONSE_ID: UUID = UUID.randomUUID()
    }
}
