package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.document.DocumentVersionContentHashAlgorithm
import com.docuhyphen.app.api.model.document.DocumentVersionContentVerification
import com.docuhyphen.app.api.model.entity.DocumentVersion
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceArtifact
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAssessment
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAssessmentKind
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAttribute
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceCollectionState
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceVersion
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateBindingSubstitute
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateEvidenceAcceptedValue
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateEvidencePolicy
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.informationrequest.InformationRequestDocumentVersionEvidenceSource
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceAttributes
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceAttributesMapper
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceMalwareOutcome
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceRequirementState
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceStanding
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceVersionSourceMapper
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestEvidenceArtifactRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestEvidenceAssessmentRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestEvidenceVersionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateBindingSubstituteRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateEvidenceAcceptedValueRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateEvidencePolicyRepository
import com.docuhyphen.app.api.service.document.DocumentVersionRecordingService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class InformationRequestEvidenceEvaluationServiceTest
{
    private val today = LocalDate.of(2026, 9, 25)
    private val clock = Clock.fixed(Instant.parse("2026-09-25T10:00:00Z"), ZoneOffset.UTC)
    private val requestId = UUID.randomUUID()

    private val policyRepository: InformationRequestTemplateEvidencePolicyRepository = mock()
    private val acceptedValueRepository: InformationRequestTemplateEvidenceAcceptedValueRepository = mock()
    private val requirementRepository: InformationRequestRequirementRepository = mock()
    private val substituteRepository: InformationRequestTemplateBindingSubstituteRepository = mock()
    private val artifactRepository: InformationRequestEvidenceArtifactRepository = mock()
    private val versionRepository: InformationRequestEvidenceVersionRepository = mock()
    private val assessmentRepository: InformationRequestEvidenceAssessmentRepository = mock()
    private val recordingService: DocumentVersionRecordingService = mock()
    private val deploymentPolicy: InformationRequestEvidenceDeploymentPolicy = mock()

    private val requirements = mutableListOf<InformationRequestRequirement>()
    private val artifacts = mutableListOf<InformationRequestEvidenceArtifact>()
    private val versions = mutableListOf<InformationRequestEvidenceVersion>()
    private val assessments = mutableListOf<InformationRequestEvidenceAssessment>()
    private val contents = mutableMapOf<UUID, DocumentVersion>()

    private val service = InformationRequestEvidenceEvaluationService(
        policyLoader = InformationRequestEvidencePolicyLoader(policyRepository, acceptedValueRepository),
        requirementRepository = requirementRepository,
        substituteRepository = substituteRepository,
        artifactRepository = artifactRepository,
        versionRepository = versionRepository,
        assessmentRepository = assessmentRepository,
        documentVersionRecordingService = recordingService,
        deploymentPolicy = deploymentPolicy,
        clock = clock,
    )

    init
    {
        whenever(requirementRepository.findForRequest(requestId)).thenAnswer { requirements.toList() }
        whenever(substituteRepository.findForBinding(any())).thenReturn(emptyList())
        whenever(acceptedValueRepository.findForPolicy(any())).thenReturn(emptyList())
        whenever(artifactRepository.findForRequirement(any())).thenAnswer { invocation ->
            artifacts.filter { it.informationRequestRequirementId == invocation.arguments[0] }
        }
        whenever(versionRepository.findForArtifact(any())).thenAnswer { invocation ->
            versions.filter { it.evidenceArtifactId == invocation.arguments[0] }.sortedBy { it.versionNumber }
        }
        whenever(assessmentRepository.findForVersions(any())).thenAnswer { invocation ->
            val versionIds = invocation.arguments[0] as Collection<*>
            assessments.filter { it.evidenceVersionId in versionIds }.sortedBy { it.assessedAt }
        }
        whenever(recordingService.findVersion(any())).thenAnswer { invocation -> contents[invocation.arguments[0]] }
        whenever(deploymentPolicy.malwareScanRequired()).thenReturn(true)
    }

    @Test
    fun `a Requirement whose binding states no evidence policy is not evaluated`()
    {
        val requirement = requirement("root")

        assertNull(service.evaluate(requirement))
    }

    @Test
    fun `an active artifact's latest version is current and every other version keeps a standing that never counts`()
    {
        val requirement = requirement("root", policy())
        val replaced = artifact(requirement)
        val first = fileVersion(replaced, 1)
        val second = fileVersion(replaced, 2)
        val withdrawn = fileVersion(artifact(requirement, InformationRequestEvidenceCollectionState.WITHDRAWN), 1)
        val removed = fileVersion(artifact(requirement, InformationRequestEvidenceCollectionState.REMOVED), 1)

        val standings = service.facts(requirement).associate { it.versionId to it.standing }

        assertEquals(InformationRequestEvidenceStanding.SUPERSEDED, standings[first.id])
        assertEquals(InformationRequestEvidenceStanding.CURRENT, standings[second.id])
        assertEquals(InformationRequestEvidenceStanding.WITHDRAWN, standings[withdrawn.id])
        assertEquals(InformationRequestEvidenceStanding.REMOVED, standings[removed.id])
    }

    @Test
    fun `a version is described by its stored content, captured attributes, latest inspection, and governing scan`()
    {
        val requirement = requirement("root", policy())
        val version = fileVersion(
            artifact(requirement),
            1,
            attributes = InformationRequestEvidenceAttributes(issuer = "Process Registry", expiresOn = today.plusDays(90)),
        )
        inspect(version, "INSPECTED", pages = 4, minutes = 0)
        scan(version, "CLEAN", productionEligible = true, minutes = 1)
        scan(version, "MALWARE_DETECTED", productionEligible = true, minutes = 2)
        scan(version, "ERROR", productionEligible = false, minutes = 3)

        val facts = service.facts(requirement).single()

        assertTrue(facts.fileBacked)
        assertEquals(DocumentVersionContentVerification.VERIFIED, facts.contentVerification)
        assertEquals(3L, facts.contentLength)
        assertEquals("application/pdf", facts.declaredMediaType)
        assertEquals("Process Registry", facts.attributes.issuer)
        assertEquals(4, facts.inspection?.pageCount)
        assertEquals(InformationRequestEvidenceMalwareOutcome.MALWARE_DETECTED, facts.malware?.outcome)
    }

    @Test
    fun `a Requirement with a conforming current file is satisfied and an unscanned one stays pending`()
    {
        val requirement = requirement("root", policy())
        val version = fileVersion(artifact(requirement), 1)
        inspect(version, "INSPECTED", pages = 1, minutes = 0)

        assertEquals(InformationRequestEvidenceRequirementState.PENDING_ASSESSMENT, service.evaluate(requirement)?.state)

        scan(version, "CLEAN", productionEligible = true, minutes = 1)
        assertEquals(InformationRequestEvidenceRequirementState.SATISFIED, service.evaluate(requirement)?.state)
    }

    @Test
    fun `where this deployment does not require a malware scan, an inspected file satisfies its Requirement`()
    {
        whenever(deploymentPolicy.malwareScanRequired()).thenReturn(false)
        val requirement = requirement("root", policy())
        inspect(fileVersion(artifact(requirement), 1), "INSPECTED", pages = 1, minutes = 0)

        assertEquals(InformationRequestEvidenceRequirementState.SATISFIED, service.evaluate(requirement)?.state)
    }

    @Test
    fun `accepted values are loaded with the policy`()
    {
        val policy = policy()
        val requirement = requirement("root", policy)
        whenever(acceptedValueRepository.findForPolicy(policy.id)).thenReturn(
            listOf(
                InformationRequestTemplateEvidenceAcceptedValue().apply {
                    evidencePolicyId = policy.id
                    templateVersionId = policy.templateVersionId
                    attribute = InformationRequestEvidenceAttribute.CONTENT_TYPE
                    acceptedValue = "image/png"
                },
            ),
        )
        val version = fileVersion(artifact(requirement), 1)
        inspect(version, "INSPECTED", pages = 1, minutes = 0)
        scan(version, "CLEAN", productionEligible = true, minutes = 1)

        assertEquals(InformationRequestEvidenceRequirementState.DEFICIENT, service.evaluate(requirement)?.state)
    }

    @Test
    fun `a satisfied substitute at the same occurrence, else at the root, satisfies the Requirement`()
    {
        val substitutePolicy = policy()
        val rootSubstitute = requirement("root", substitutePolicy)
        val occurrence = requirement("reported-item[1]", policy())
        whenever(substituteRepository.findForBinding(occurrence.sourceTemplateBindingId)).thenReturn(
            listOf(
                InformationRequestTemplateBindingSubstitute().apply {
                    templateBindingId = occurrence.sourceTemplateBindingId
                    substituteTemplateBindingId = rootSubstitute.sourceTemplateBindingId
                    templateVersionId = substitutePolicy.templateVersionId
                },
            ),
        )

        assertEquals(InformationRequestEvidenceRequirementState.NOT_PROVIDED, service.evaluate(occurrence)?.state)

        val version = fileVersion(artifact(rootSubstitute), 1)
        inspect(version, "INSPECTED", pages = 1, minutes = 0)
        scan(version, "CLEAN", productionEligible = true, minutes = 1)

        val evaluation = service.evaluate(occurrence)
        assertEquals(InformationRequestEvidenceRequirementState.SATISFIED, evaluation?.state)
        assertTrue(evaluation?.satisfiedBySubstitute == true)
        assertFalse(service.evaluate(rootSubstitute)?.satisfiedBySubstitute == true)
    }

    @Test
    fun `validity is judged as of the configured clock's date`()
    {
        val requirement = requirement("root", policy())
        val version = fileVersion(artifact(requirement), 1, attributes = InformationRequestEvidenceAttributes(expiresOn = today.minusDays(1)))
        inspect(version, "INSPECTED", pages = 1, minutes = 0)
        scan(version, "CLEAN", productionEligible = true, minutes = 1)

        assertEquals(InformationRequestEvidenceRequirementState.DEFICIENT, service.evaluate(requirement)?.state)
    }

    private fun policy() = InformationRequestTemplateEvidencePolicy().apply {
        templateBindingId = UUID.randomUUID()
        templateVersionId = UUID.randomUUID()
        minimumFileCount = 1
    }

    private fun requirement(path: String, policy: InformationRequestTemplateEvidencePolicy? = null): InformationRequestRequirement
    {
        val requirement = InformationRequestRequirement().apply {
            informationRequestId = requestId
            sourceTemplateVersionId = UUID.randomUUID()
            sourceTemplateRequirementId = UUID.randomUUID()
            sourceTemplateBindingId = policy?.templateBindingId ?: UUID.randomUUID()
            occurrencePath = path
        }
        whenever(policyRepository.findForBinding(requirement.sourceTemplateBindingId)).thenReturn(policy)
        requirements += requirement
        return requirement
    }

    private fun artifact(
        requirement: InformationRequestRequirement,
        state: InformationRequestEvidenceCollectionState = InformationRequestEvidenceCollectionState.ACTIVE,
    ) = InformationRequestEvidenceArtifact().apply {
        informationRequestId = requestId
        informationRequestRequirementId = requirement.id
        artifactKey = "evidence-${artifacts.size + 1}"
        createdByPrincipalKind = PrincipalKind.PARTICIPANT
        createdByPrincipalId = UUID.randomUUID()
        collectionState = state
    }.also { artifacts += it }

    private fun fileVersion(
        artifact: InformationRequestEvidenceArtifact,
        number: Int,
        attributes: InformationRequestEvidenceAttributes = InformationRequestEvidenceAttributes.NONE,
    ): InformationRequestEvidenceVersion
    {
        val content = DocumentVersion().apply {
            contentLength = 3
            contentHashAlgorithm = DocumentVersionContentHashAlgorithm.SHA_256
            contentHash = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
            contentVerification = DocumentVersionContentVerification.VERIFIED
        }
        contents[content.id] = content
        return InformationRequestEvidenceVersion().apply {
            evidenceArtifactId = artifact.id
            informationRequestId = requestId
            versionNumber = number
            createdByPrincipalKind = PrincipalKind.PARTICIPANT
            createdByPrincipalId = UUID.randomUUID()
            declaredFileName = "record.pdf"
            declaredMediaType = "application/pdf"
            InformationRequestEvidenceVersionSourceMapper.write(this, InformationRequestDocumentVersionEvidenceSource(content.id))
            InformationRequestEvidenceAttributesMapper.write(this, attributes)
        }.also { versions += it }
    }

    private fun inspect(version: InformationRequestEvidenceVersion, outcomeName: String, pages: Int?, minutes: Long)
    {
        assessments += assessment(version, InformationRequestEvidenceAssessmentKind.CONTENT_INSPECTION, outcomeName, minutes).apply {
            detectedMediaType = "application/pdf"
            pageCount = pages
        }
    }

    private fun scan(version: InformationRequestEvidenceVersion, outcomeName: String, productionEligible: Boolean, minutes: Long)
    {
        assessments += assessment(version, InformationRequestEvidenceAssessmentKind.MALWARE_SCAN, outcomeName, minutes).apply {
            this.productionEligible = productionEligible
        }
    }

    private fun assessment(
        version: InformationRequestEvidenceVersion,
        kind: InformationRequestEvidenceAssessmentKind,
        outcomeName: String,
        minutes: Long,
    ) = InformationRequestEvidenceAssessment().apply {
        informationRequestId = requestId
        evidenceVersionId = version.id
        assessmentKind = kind
        outcome = outcomeName
        contentHashAlgorithm = DocumentVersionContentHashAlgorithm.SHA_256
        contentHash = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        contentLength = 3
        assessedAt = Timestamp.from(Instant.parse("2026-09-25T08:00:00Z").plusSeconds(minutes * 60))
        createdAt = assessedAt
    }
}
