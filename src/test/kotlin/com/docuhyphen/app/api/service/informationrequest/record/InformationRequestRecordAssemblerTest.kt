package com.docuhyphen.app.api.service.informationrequest.record

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestCompletenessItemState
import com.docuhyphen.app.api.repository.informationrequest.acceptedfact.InformationRequestAcceptedFactRepository
import com.docuhyphen.app.api.repository.informationrequest.acceptedfact.InformationRequestFactRecertificationRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewDecisionRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewFindingRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewRepository
import com.docuhyphen.app.api.repository.informationrequest.submission.InformationRequestSubmissionAttestationRepository
import com.docuhyphen.app.api.repository.informationrequest.submission.InformationRequestSubmissionItemRepository
import com.docuhyphen.app.api.repository.informationrequest.submission.InformationRequestSubmissionPackageRepository
import com.docuhyphen.app.api.service.informationrequest.externalsource.InformationRequestExternalSourceRecordAssembler
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestItemCorrectionService
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*

class InformationRequestRecordAssemblerTest {
    private val partyRepository = mock<InformationRequestPartyRepository>()
    private val packageRepository = mock<InformationRequestSubmissionPackageRepository>()
    private val itemRepository = mock<InformationRequestSubmissionItemRepository>()
    private val externalSources = mock<InformationRequestExternalSourceRecordAssembler>()
    private val itemCorrections = mock<InformationRequestItemCorrectionService>()
    private val attestationRepository = mock<InformationRequestSubmissionAttestationRepository>()
    private val reviewRepository = mock<InformationRequestReviewRepository>()
    private val decisionRepository = mock<InformationRequestReviewDecisionRepository>()
    private val findingRepository = mock<InformationRequestReviewFindingRepository>()
    private val factRepository = mock<InformationRequestAcceptedFactRepository>()
    private val recertificationRepository = mock<InformationRequestFactRecertificationRepository>()
    private val assembler = InformationRequestRecordAssembler(
        transitionRepository = mock(),
        partyRepository = partyRepository,
        packageRepository = packageRepository,
        itemRepository = itemRepository,
        evidenceRepository = mock(),
        attestationRepository = attestationRepository,
        withdrawalRepository = mock(),
        reviewRepository = reviewRepository,
        decisionRepository = decisionRepository,
        findingRepository = findingRepository,
        correctionRepository = mock(),
        correctionItemRepository = mock(),
        factRepository = factRepository,
        revocationRepository = mock(),
        businessDecisionRepository = mock(),
        clockRepository = mock(),
        clockEventRepository = mock(),
        noticeStates = mock(),
        fieldValues = mock(),
        recertificationRepository = recertificationRepository,
        recertificationEvidenceRepository = mock(),
        externalSources = externalSources,
        itemCorrections = itemCorrections,
    )

    init {
        whenever(externalSources.assemble(any(), any())).thenReturn(JsonObject(emptyMap()))
    }

    @Test
    fun `facts and recertifications for withheld Requirements export only identifiers and a redaction marker`() {
        val request = request()
        val requirementId = UUID.randomUUID()
        val fact = InformationRequestAcceptedFact().apply {
            sourceInformationRequestId = request.id
            sourceRequirementId = requirementId
            sourcePackageId = UUID.randomUUID()
            sourceSubmissionItemId = UUID.randomUUID()
            purposeKey = "record.reuse"
            fieldDefinitionId = UUID.randomUUID()
            promotedByPrincipalKind = PrincipalKind.USER
            promotedByPrincipalId = UUID.randomUUID()
        }
        val recertification = InformationRequestFactRecertification().apply {
            informationRequestId = request.id
            informationRequestRequirementId = requirementId
            responseId = UUID.randomUUID()
            factId = fact.id
            purposeKey = "record.reuse"
            policyBasisKey = "policy.reuse"
            valueType = FieldValueType.SHORT_TEXT
            sourceInformationRequestId = request.id
            sourcePackageId = fact.sourcePackageId
            sourceSubmissionItemId = fact.sourceSubmissionItemId
            sourceRequirementId = requirementId
            sourceFieldValueRevisionId = UUID.randomUUID()
            assentedByPrincipalKind = PrincipalKind.USER
            assentedByPrincipalId = UUID.randomUUID()
        }
        whenever(factRepository.findFromRequest(request.id)).thenReturn(listOf(fact))
        whenever(recertificationRepository.findForRequest(request.id)).thenReturn(listOf(recertification))
        val visible = assembler.assemble(request) { true }
        assertEquals(
            "record.reuse",
            visible.getValue("acceptedFacts").jsonArray.single().jsonObject.getValue("purposeKey").jsonPrimitive.content
        )
        val withheld = assembler.assemble(request) { false }
        val exportedFact = withheld.getValue("acceptedFacts").jsonArray.single().jsonObject
        assertEquals(setOf("acceptedFactId", "requirementId", "redacted"), exportedFact.keys)
        assertEquals(JsonPrimitive(true), exportedFact.getValue("redacted"))
        val exportedRecertification = withheld.getValue("recertifications").jsonArray.single().jsonObject
        assertEquals(setOf("recertificationId", "requirementId", "redacted"), exportedRecertification.keys)
        assertEquals(JsonPrimitive(true), exportedRecertification.getValue("redacted"))
    }

    @Test
    fun `the record names the request creator and each party assigner by canonical principal`() {
        val applicationId = UUID.randomUUID()
        val serviceAccountId = UUID.randomUUID()
        val request = request().apply {
            createdByPrincipalKind = PrincipalKind.APPLICATION
            createdByPrincipalId = applicationId
        }
        val party = InformationRequestParty().apply {
            informationRequestId = request.id
            roleKey = InformationRequestShareRoleKey.CONTRIBUTOR
            principalKind = PrincipalKind.PARTICIPANT
            principalId = UUID.randomUUID()
            assignedByPrincipalKind = PrincipalKind.SERVICE_ACCOUNT
            assignedByPrincipalId = serviceAccountId
        }
        whenever(partyRepository.findForRequest(request.id)).thenReturn(listOf(party))

        val record = assembler.assemble(request) { true }

        assertEquals(4, record.getValue("schemaVersion").jsonPrimitive.int)
        val exported = record.getValue("request").jsonObject
        assertEquals("APPLICATION", exported.getValue("createdByPrincipalKind").jsonPrimitive.content)
        assertEquals(applicationId.toString(), exported.getValue("createdByPrincipalId").jsonPrimitive.content)
        assertFalse(exported.containsKey("createdByAppUserId"))
        val exportedParty = record.getValue("parties").jsonArray.single().jsonObject
        assertEquals("SERVICE_ACCOUNT", exportedParty.getValue("assignedByPrincipalKind").jsonPrimitive.content)
        assertEquals(serviceAccountId.toString(), exportedParty.getValue("assignedByPrincipalId").jsonPrimitive.content)
    }

    @Test
    fun `the record lists each privacy correction of a submitted item with its corrected content and reason`() {
        val request = request()
        val submission = submission(request)
        val item = item(submission, UUID.randomUUID(), "Recorded narrative")
        val correction = correction(request, item)
        whenever(packageRepository.findForRequest(request.id)).thenReturn(listOf(submission))
        whenever(itemRepository.findForPackages(listOf(submission.id))).thenReturn(listOf(item))
        whenever(itemCorrections.correctionsFor(request.id)).thenReturn(listOf(correction))

        val record = assembler.assemble(request) { true }

        val exported = record.getValue("itemCorrections").jsonArray.single().jsonObject
        assertEquals(correction.id.toString(), exported.getValue("correctionId").jsonPrimitive.content)
        assertEquals(item.id.toString(), exported.getValue("submissionItemId").jsonPrimitive.content)
        assertEquals(
            item.informationRequestRequirementId.toString(),
            exported.getValue("requirementId").jsonPrimitive.content
        )
        assertEquals(submission.id.toString(), exported.getValue("packageId").jsonPrimitive.content)
        assertEquals(
            correction.privacyRequestId.toString(),
            exported.getValue("privacyRequestId").jsonPrimitive.content
        )
        assertEquals(JsonPrimitive("Corrected record"), exported.getValue("correctedValue"))
        assertEquals("Corrected narrative", exported.getValue("correctedNarrative").jsonPrimitive.content)
        assertEquals("subject.correction", exported.getValue("reasonCode").jsonPrimitive.content)
        assertEquals("USER", exported.getValue("recordedByPrincipalKind").jsonPrimitive.content)
    }

    @Test
    fun `an item and its corrections are withheld for a Requirement the caller may not export`() {
        val request = request()
        val submission = submission(request)
        val disclosedRequirement = UUID.randomUUID()
        val withheldRequirement = UUID.randomUUID()
        val disclosed = item(submission, disclosedRequirement, "Disclosed narrative")
        val withheld = item(submission, withheldRequirement, "Withheld narrative")
        whenever(packageRepository.findForRequest(request.id)).thenReturn(listOf(submission))
        whenever(itemRepository.findForPackages(listOf(submission.id))).thenReturn(listOf(disclosed, withheld))
        whenever(itemCorrections.correctionsFor(request.id)).thenReturn(listOf(correction(request, withheld)))

        val record = assembler.assemble(request) { it != withheldRequirement }

        val items = record.getValue("packages").jsonArray.single().jsonObject.getValue("items").jsonArray
            .map { it.jsonObject }
            .associateBy { it.getValue("requirementId").jsonPrimitive.content }
        val shown = items.getValue(disclosedRequirement.toString())
        assertEquals("Disclosed narrative", shown.getValue("narrative").jsonPrimitive.content)
        assertFalse(shown.containsKey("redacted"))
        val hidden = items.getValue(withheldRequirement.toString())
        assertEquals(true, hidden.getValue("redacted").jsonPrimitive.boolean)
        assertEquals(setOf("itemId", "requirementId", "redacted"), hidden.keys)
        val correction = record.getValue("itemCorrections").jsonArray.single().jsonObject
        assertEquals(true, correction.getValue("redacted").jsonPrimitive.boolean)
        assertEquals(setOf("correctionId", "submissionItemId", "requirementId", "redacted"), correction.keys)
    }

    @Test
    fun `review decisions, findings, and attestations about a withheld Requirement are withheld with it`() {
        val request = request()
        val submission = submission(request)
        val disclosedRequirement = UUID.randomUUID()
        val withheldRequirement = UUID.randomUUID()
        val disclosed = item(submission, disclosedRequirement, "Disclosed narrative")
        val withheld = item(submission, withheldRequirement, "Withheld narrative")
        val review = InformationRequestReview().apply {
            informationRequestId = request.id
            packageId = submission.id
            templateVersionId = request.templateVersionId
            openedByPrincipalKind = PrincipalKind.USER
            openedByPrincipalId = UUID.randomUUID()
        }
        whenever(packageRepository.findForRequest(request.id)).thenReturn(listOf(submission))
        whenever(itemRepository.findForPackages(listOf(submission.id))).thenReturn(listOf(disclosed, withheld))
        whenever(reviewRepository.findForRequest(request.id)).thenReturn(listOf(review))
        whenever(decisionRepository.findForReviews(listOf(review.id))).thenReturn(
            listOf(
                decision(review, disclosed),
                decision(review, withheld)
            )
        )
        whenever(findingRepository.findForReviews(listOf(review.id))).thenReturn(
            listOf(
                finding(review, disclosed),
                finding(review, withheld)
            )
        )
        whenever(attestationRepository.findForRequest(request.id))
            .thenReturn(listOf(attestation(request, disclosedRequirement), attestation(request, withheldRequirement)))

        val record = assembler.assemble(request) { it != withheldRequirement }

        val exportedReview = record.getValue("reviews").jsonArray.single().jsonObject
        val decisions = exportedReview.getValue("decisions").jsonArray.map { it.jsonObject }
            .associateBy { it.getValue("submissionItemId").jsonPrimitive.content }
        assertEquals("REJECTED", decisions.getValue(disclosed.id.toString()).getValue("outcome").jsonPrimitive.content)
        assertEquals(setOf("submissionItemId", "stageKey", "redacted"), decisions.getValue(withheld.id.toString()).keys)
        val findings = exportedReview.getValue("findings").jsonArray.map { it.jsonObject }
            .associateBy { it.getValue("submissionItemId").jsonPrimitive.content }
        assertEquals(
            "record.unreadable",
            findings.getValue(disclosed.id.toString()).getValue("reasonCode").jsonPrimitive.content
        )
        assertEquals(setOf("findingId", "submissionItemId", "redacted"), findings.getValue(withheld.id.toString()).keys)
        val attestations = record.getValue("attestations").jsonArray.map { it.jsonObject }
            .associateBy { it.getValue("requirementId").jsonPrimitive.content }
        assertEquals(
            "ASSENTED",
            attestations.getValue(disclosedRequirement.toString()).getValue("decision").jsonPrimitive.content
        )
        assertEquals(
            setOf("attestationId", "requirementId", "redacted"),
            attestations.getValue(withheldRequirement.toString()).keys
        )
    }

    private fun decision(review: InformationRequestReview, item: InformationRequestSubmissionItem) =
        InformationRequestReviewDecision().apply {
            reviewId = review.id
            informationRequestId = review.informationRequestId
            packageId = review.packageId
            submissionItemId = item.id
            requirementId = item.informationRequestRequirementId
            templateReviewStageId = UUID.randomUUID()
            stageKey = "first-review"
            outcome = InformationRequestReviewOutcome.REJECTED
            decidedByPrincipalKind = PrincipalKind.USER
            decidedByPrincipalId = UUID.randomUUID()
        }

    private fun finding(review: InformationRequestReview, item: InformationRequestSubmissionItem) =
        InformationRequestReviewFinding().apply {
            reviewId = review.id
            informationRequestId = review.informationRequestId
            packageId = review.packageId
            submissionItemId = item.id
            requirementId = item.informationRequestRequirementId
            reasonCode = "record.unreadable"
            narrative = "The record cannot be read"
            severity = InformationRequestFindingSeverity.MAJOR
            visibility = InformationRequestReviewVisibility.RESPONDENT_VISIBLE
            correctionScope = InformationRequestFindingCorrectionScope.EVIDENCE_VERSION
            recordedByPrincipalKind = PrincipalKind.USER
            recordedByPrincipalId = UUID.randomUUID()
        }

    private fun attestation(request: InformationRequest, requirementId: UUID) =
        InformationRequestSubmissionAttestation().apply {
            informationRequestId = request.id
            attestationRequirementId = requirementId
            requirementRevisionId = UUID.randomUUID()
            partyId = UUID.randomUUID()
            partyRole = InformationRequestContributorRole.ATTESTOR
            principalKind = PrincipalKind.USER
            principalId = UUID.randomUUID()
            decision = InformationRequestAttestationDecision.ASSENTED
            authenticationStrength = InformationRequestAuthenticationStrength.ACCOUNT_SIGN_IN
            attestedContentHashSha256 = "d".repeat(64)
            statementHashSha256 = "e".repeat(64)
            policyHashSha256 = "f".repeat(64)
        }

    private fun request() = InformationRequest().apply {
        exchangeId = UUID.randomUUID()
        templateVersionId = UUID.randomUUID()
        ownerType = InformationRequestOwnerType.ORGANIZATION
        ownerOrganizationId = UUID.randomUUID()
    }

    private fun submission(request: InformationRequest) = InformationRequestSubmissionPackage().apply {
        informationRequestId = request.id
        templateVersionId = request.templateVersionId
        contentHashSha256 = "a".repeat(64)
        manifestHashSha256 = "b".repeat(64)
        submittedByPrincipalKind = PrincipalKind.USER
        submittedByPrincipalId = UUID.randomUUID()
    }

    private fun item(submission: InformationRequestSubmissionPackage, requirementId: UUID, recorded: String) =
        InformationRequestSubmissionItem().apply {
            packageId = submission.id
            informationRequestId = submission.informationRequestId
            informationRequestRequirementId = requirementId
            requirementRevisionId = UUID.randomUUID()
            templateBindingId = UUID.randomUUID()
            requirementKey = "recorded-item-$requirementId"
            requirementType = InformationRequestRequirementType.DOCUMENT
            occurrencePath = "root"
            completenessState = InformationRequestCompletenessItemState.COMPLETE
            disposition = InformationRequestResponseDisposition.PROVIDED
            narrative = recorded
            itemHashSha256 = "c".repeat(64)
        }

    private fun correction(request: InformationRequest, item: InformationRequestSubmissionItem) =
        InformationRequestItemCorrection().apply {
            informationRequestId = request.id
            packageId = item.packageId
            submissionItemId = item.id
            privacyRequestId = UUID.randomUUID()
            correctedValueJson = "\"Corrected record\""
            correctedNarrative = "Corrected narrative"
            reasonCode = "subject.correction"
            recordedByPrincipalKind = PrincipalKind.USER
            recordedByPrincipalId = UUID.randomUUID()
        }
}
