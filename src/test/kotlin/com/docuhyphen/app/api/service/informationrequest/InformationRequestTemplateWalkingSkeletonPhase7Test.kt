package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateVersionDto
import com.docuhyphen.app.api.model.entity.InformationRequestAmendmentChangeKind
import com.docuhyphen.app.api.model.entity.InformationRequestAttestationDecision
import com.docuhyphen.app.api.model.entity.InformationRequestAttestationOrdering
import com.docuhyphen.app.api.model.entity.InformationRequestAuthenticationStrength
import com.docuhyphen.app.api.model.entity.InformationRequestCarryForwardDecision
import com.docuhyphen.app.api.model.entity.InformationRequestContributorRole
import com.docuhyphen.app.api.model.entity.InformationRequestRecurrence
import com.docuhyphen.app.api.model.entity.InformationRequestRecurrenceUnit
import com.docuhyphen.app.api.model.entity.InformationRequestRequiredness
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionAttestation
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionItem
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionMode
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionStageOrdering
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAttestationPolicy
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAttestationPolicyEvaluator
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAttestationState
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAttestingParty
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSuccessorOccurrence
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.repository.informationrequest.RequestTemplatePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestCompletenessItemState
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(RequestTemplatePostgreSQLResource::class)
class InformationRequestTemplateWalkingSkeletonPhase7Test
{
    @Inject lateinit var dataSource: DataSource
    @Inject lateinit var definitionRepository: InformationRequestTemplateDefinitionRepository
    @Inject lateinit var versionRepository: InformationRequestTemplateVersionRepository
    @Inject lateinit var capabilityRepository: InformationRequestTemplateVersionCapabilityRepository
    @Inject lateinit var configurationWriter: InformationRequestTemplateConfigurationWriter
    @Inject lateinit var projectionLoader: InformationRequestTemplateProjectionLoader
    @Inject lateinit var capabilityGate: InformationRequestTemplateCapabilityGate
    @Inject lateinit var followUpService: InformationRequestFollowUpService

    private val store by lazy {
        InformationRequestTemplateWalkingSkeletonStore(dataSource, definitionRepository, versionRepository, capabilityRepository, configurationWriter)
    }

    @Test
    fun `the basic fixture is fully served while the staged stress fixture waits for review before issuance`()
    {
        val basic = publishBasic()
        val stress = publishStress()

        assertEquals(emptyList<InformationRequestCapabilityRequirement>(), capabilityGate.unservedRequirements(basic.versionId))
        assertEquals(
            listOf(InformationRequestCapability.RESPONSE_REVIEW),
            capabilityGate.unservedRequirements(stress.versionId).map { it.capability },
        )
    }

    @Test
    fun `the stress fixture publishes sequential stages and a multi-party assertion that counts assents in role order`()
    {
        val projected = projection(publishStress().versionId)

        assertEquals(InformationRequestSubmissionMode.STAGED, projected.submissionMode)
        assertEquals(InformationRequestSubmissionStageOrdering.SEQUENTIAL, projected.submissionStageOrdering)
        assertEquals(listOf("response-stage", "evidence-stage", "confirmation-stage"), projected.sections.map { it.submissionStageKey })
        val stored = requireNotNull(projected.sections.flatMap { it.requirements }.single { it.requirementKey == "submitter-attestation" }.attestationPolicy)
        assertEquals(listOf(InformationRequestContributorRole.SUBJECT, InformationRequestContributorRole.ATTESTOR), stored.requiredRoles)
        assertEquals(InformationRequestAttestationOrdering.ROLE_SEQUENCE, stored.ordering)

        val policy = InformationRequestAttestationPolicy(
            bindingId = UUID.randomUUID(),
            requiredRoles = stored.requiredRoles,
            ordering = stored.ordering,
            minimumAssentCount = stored.minimumAssentCount,
            minimumAuthenticationStrength = stored.minimumAuthenticationStrength,
            validityHours = stored.validityHours,
            externalSignatureReference = stored.externalSignatureReference,
            policyHash = "0".repeat(64),
        )
        val subject = InformationRequestAttestingParty(UUID.randomUUID(), InformationRequestContributorRole.SUBJECT)
        val attestor = InformationRequestAttestingParty(UUID.randomUUID(), InformationRequestContributorRole.ATTESTOR)
        val outOfOrder = listOf(assent(attestor, 1), assent(subject, 2))
        assertEquals(InformationRequestAttestationState.PENDING, evaluate(policy, outOfOrder, subject, attestor).state)
        val weak = assent(attestor, 3, InformationRequestAuthenticationStrength.VERIFIED_CONTACT)
        assertEquals(InformationRequestAttestationState.PENDING, evaluate(policy, outOfOrder + weak, subject, attestor).state)
        assertEquals(
            InformationRequestAttestationState.SATISFIED,
            evaluate(policy, outOfOrder + assent(attestor, 4), subject, attestor).state,
        )
    }

    @Test
    fun `a reworded basic Version amends compatibly and a changed one needs every answer to it reconfirmed`()
    {
        val basic = publishBasic()
        val reworded = publishNext(basic, 2) { if (it.requirementKey == "recorded-summary") it.copy(prompt = "Enter the recorded summary") else it }
        val relaxed = publishNext(basic, 3) {
            if (it.requirementKey == "supporting-record") it.copy(requiredness = InformationRequestRequiredness.OPTIONAL) else it
        }
        val original = projection(basic.versionId)

        assertEquals(
            listOf("recorded-summary" to InformationRequestAmendmentChangeKind.PRESENTATION_CHANGED),
            InformationRequestAmendmentClassifier.classify(original, projection(reworded.versionId)).changes.map { it.requirementKey to it.kind },
        )
        assertEquals(
            listOf("supporting-record" to InformationRequestAmendmentChangeKind.MEANING_CHANGED),
            InformationRequestAmendmentClassifier.classify(original, projection(relaxed.versionId)).changes.map { it.requirementKey to it.kind },
        )
    }

    @Test
    fun `a supplement of the basic fixture offers the recorded summary and collects evidence and assent afresh`()
    {
        val requirements = basicConfiguration().sections.flatMap { it.requirements }
        val occurrences = requirements.map { InformationRequestSuccessorOccurrence(UUID.randomUUID(), it.requirementKey, it.requirementType, "root") }
        val items = requirements.map { requirement ->
            InformationRequestSubmissionItem().apply {
                requirementKey = requirement.requirementKey
                requirementType = requirement.requirementType
                occurrencePath = "root"
                disposition = InformationRequestResponseDisposition.PROVIDED
                completenessState = InformationRequestCompletenessItemState.COMPLETE
            }
        }

        val decisions = InformationRequestCarryForwardPlanner.plan(occurrences, items)
            .associate { planned -> occurrences.single { it.requirementId == planned.requirementId }.requirementKey to planned.decision }

        assertEquals(
            mapOf(
                "recorded-summary" to InformationRequestCarryForwardDecision.OFFERED,
                "supporting-record" to InformationRequestCarryForwardDecision.INVALIDATED,
                "response-confirmation" to InformationRequestCarryForwardDecision.INVALIDATED,
            ),
            decisions,
        )
    }

    @Test
    fun `a monthly recurrence of the basic fixture falls due one calendar month after its previous occurrence`()
    {
        val recurrence = InformationRequestRecurrence().apply {
            originRequestId = UUID.randomUUID()
            intervalUnit = InformationRequestRecurrenceUnit.MONTH
            intervalCount = 1
            firstDueAt = Timestamp.from(Instant.parse("2026-01-31T09:00:00Z"))
        }

        assertEquals(Instant.parse("2026-01-31T09:00:00Z"), followUpService.dueAt(recurrence, 1))
        assertEquals(Instant.parse("2026-02-28T09:00:00Z"), followUpService.dueAt(recurrence, 2))
        assertEquals(Instant.parse("2027-01-31T09:00:00Z"), followUpService.dueAt(recurrence, 13))
    }

    private var schemaVersionId: UUID? = null
    private var recordedSummaryField: UUID? = null

    private fun basicConfiguration(): InformationRequestTemplateConfigurationRequest =
        InformationRequestTemplateWalkingSkeletonFixtures.basicFieldDocumentResponseAttestationRequest(
            schemaVersionId ?: UUID.randomUUID(),
            recordedSummaryField ?: UUID.randomUUID(),
        ).configuration

    private fun publishBasic(): WalkingSkeletonDraft
    {
        val field = store.insertTextField("basic-recorded-summary")
        val actorId = store.insertOwner()
        schemaVersionId = store.insertSchemaVersion(actorId, listOf(field))
        recordedSummaryField = field.fieldDefinitionId
        val fixture = InformationRequestTemplateWalkingSkeletonFixtures.basicFieldDocumentResponseAttestationRequest(
            requireNotNull(schemaVersionId),
            field.fieldDefinitionId,
        )
        return store.insertDraft(actorId, fixture.templateKey).also { store.publish(it, fixture.configuration) }
    }

    private fun publishStress(): WalkingSkeletonDraft
    {
        val subjectField = store.insertTextField("stress-subject-status")
        val delegateField = store.insertTextField("stress-delegate-note")
        val actorId = store.insertOwner()
        val schema = store.insertSchemaVersion(actorId, listOf(subjectField, delegateField))
        val fixture = InformationRequestTemplateWalkingSkeletonFixtures.multiPartyStagedEvidenceRequest(
            schema,
            subjectField.fieldDefinitionId,
            delegateField.fieldDefinitionId,
        )
        return store.insertDraft(actorId, fixture.templateKey).also { store.publish(it, fixture.configuration) }
    }

    private fun publishNext(
        published: WalkingSkeletonDraft,
        versionNumber: Int,
        change: (InformationRequestTemplateRequirementRequest) -> InformationRequestTemplateRequirementRequest,
    ): WalkingSkeletonDraft
    {
        val configuration = basicConfiguration().let { base ->
            base.copy(sections = base.sections.map { section -> section.copy(requirements = section.requirements.map(change)) })
        }
        return store.insertNextDraft(published, versionNumber).also { store.publish(it, configuration) }
    }

    private fun projection(versionId: UUID): InformationRequestTemplateVersionDto =
        QuarkusTransaction.requiringNew().call { projectionLoader.loadVersion(versionRepository.findById(versionId)!!) }

    private fun evaluate(
        policy: InformationRequestAttestationPolicy,
        attestations: List<InformationRequestSubmissionAttestation>,
        vararg parties: InformationRequestAttestingParty,
    ) = InformationRequestAttestationPolicyEvaluator.evaluate(policy, CONTENT_HASH, attestations, parties.toList(), Instant.now())

    private fun assent(
        party: InformationRequestAttestingParty,
        sequence: Int,
        strength: InformationRequestAuthenticationStrength = InformationRequestAuthenticationStrength.ACCOUNT_SIGN_IN,
    ) = InformationRequestSubmissionAttestation().apply {
        partyId = party.partyId
        partyRole = party.role
        decision = InformationRequestAttestationDecision.ASSENTED
        authenticationStrength = strength
        attestedContentHashSha256 = CONTENT_HASH
        externalSignatureReference = null
        sequenceNumber = sequence
    }

    private companion object
    {
        val CONTENT_HASH = "a".repeat(64)
    }
}
