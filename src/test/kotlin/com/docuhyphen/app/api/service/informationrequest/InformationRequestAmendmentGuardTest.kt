package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateGroupDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateVersionDto
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestAmendmentChangeKind
import com.docuhyphen.app.api.model.entity.InformationRequestGroupOccurrence
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionPackage
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateStatus
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAmendmentGroupChange
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAmendmentPlan
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAmendmentRequirementChange
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class InformationRequestAmendmentGuardTest
{
    private val request = InformationRequest().apply { templateVersionId = UUID.randomUUID() }
    private val requirementRepository: InformationRequestRequirementRepository = mock()
    private val occurrenceRepository: InformationRequestGroupOccurrenceRepository = mock()
    private val lockService: InformationRequestSubmissionLockService = mock()
    private val guard = InformationRequestAmendmentGuard(requirementRepository, occurrenceRepository, lockService)

    private val lineItems = InformationRequestTemplateGroupDto(UUID.randomUUID(), "line-items", null, 0, 5)
    private val answered = UUID.randomUUID()
    private val runtime = InformationRequestRequirement().apply {
        informationRequestId = request.id
        sourceTemplateRequirementId = answered
        occurrencePath = "root"
    }

    init
    {
        whenever(requirementRepository.findForRequest(request.id)).thenReturn(listOf(runtime))
        whenever(occurrenceRepository.findForRequest(request.id)).thenReturn(emptyList())
        whenever(lockService.activePackages(request.id)).thenReturn(emptyList())
        whenever(lockService.lockedRequirementIds(request.id)).thenReturn(emptySet())
    }

    @Test
    fun `a presentation or meaning change to an open request is accepted`()
    {
        guard.requireAmendable(request, version(), plan(change(answered, InformationRequestAmendmentChangeKind.MEANING_CHANGED)))
    }

    @Test
    fun `another Schema Version is refused`()
    {
        assertRefused(InformationRequestErrorCatalog.AMENDMENT_SCHEMA_CHANGED, plan(schemaChanged = true))
    }

    @Test
    fun `a moved anchor under existing occurrences or a narrowed group is refused`()
    {
        assertRefused(
            InformationRequestErrorCatalog.AMENDMENT_OCCURRENCE_STRUCTURE_CHANGED,
            plan(change(answered, InformationRequestAmendmentChangeKind.MEANING_CHANGED, anchorChanged = true)),
        )

        whenever(occurrenceRepository.findForRequest(request.id)).thenReturn(listOf(occurrence(), occurrence()))
        assertRefused(
            InformationRequestErrorCatalog.AMENDMENT_OCCURRENCE_STRUCTURE_CHANGED,
            plan(groupChanges = listOf(InformationRequestAmendmentGroupChange("line-items", removed = false, parentChanged = false, 1))),
        )
        assertRefused(
            InformationRequestErrorCatalog.AMENDMENT_OCCURRENCE_STRUCTURE_CHANGED,
            plan(groupChanges = listOf(InformationRequestAmendmentGroupChange("line-items", removed = true, parentChanged = false, null))),
        )
        guard.requireAmendable(
            request,
            version(),
            plan(groupChanges = listOf(InformationRequestAmendmentGroupChange("line-items", removed = false, parentChanged = false, 3))),
        )
    }

    @Test
    fun `a change reaching a submitted stage is refused while other stages stay amendable`()
    {
        whenever(lockService.activePackages(request.id)).thenReturn(listOf(submitted("first-stage")))

        assertRefused(
            InformationRequestErrorCatalog.AMENDMENT_SUBMITTED_SCOPE_CHANGED,
            plan(change(UUID.randomUUID(), InformationRequestAmendmentChangeKind.ADDED, toStage = "first-stage")),
        )
        assertRefused(InformationRequestErrorCatalog.AMENDMENT_SUBMITTED_SCOPE_CHANGED, plan(submissionPolicyChanged = true))
        guard.requireAmendable(
            request,
            version(),
            plan(change(UUID.randomUUID(), InformationRequestAmendmentChangeKind.ADDED, toStage = "second-stage")),
        )

        whenever(lockService.lockedRequirementIds(request.id)).thenReturn(setOf(runtime.id))
        assertRefused(
            InformationRequestErrorCatalog.AMENDMENT_SUBMITTED_SCOPE_CHANGED,
            plan(change(answered, InformationRequestAmendmentChangeKind.PRESENTATION_CHANGED, fromStage = "second-stage")),
        )
    }

    private fun assertRefused(reasonCode: String, plan: InformationRequestAmendmentPlan)
    {
        val refusal = assertThrows(InformationRequestLifecycleException::class.java) { guard.requireAmendable(request, version(), plan) }
        assertEquals(reasonCode, refusal.reasonCode)
    }

    private fun plan(
        vararg changes: InformationRequestAmendmentRequirementChange,
        groupChanges: List<InformationRequestAmendmentGroupChange> = emptyList(),
        schemaChanged: Boolean = false,
        submissionPolicyChanged: Boolean = false,
    ) = InformationRequestAmendmentPlan(changes.toList(), groupChanges, schemaChanged, submissionPolicyChanged)

    @Suppress("LongParameterList")
    private fun change(
        templateRequirementId: UUID,
        kind: InformationRequestAmendmentChangeKind,
        anchorChanged: Boolean = false,
        fromStage: String? = null,
        toStage: String? = null,
    ) = InformationRequestAmendmentRequirementChange(
        requirementKey = "recorded-item",
        templateRequirementId = templateRequirementId,
        kind = kind,
        fromBindingId = if (kind == InformationRequestAmendmentChangeKind.ADDED) null else UUID.randomUUID(),
        toBindingId = if (kind == InformationRequestAmendmentChangeKind.REMOVED) null else UUID.randomUUID(),
        fromStageKey = fromStage,
        toStageKey = toStage,
        anchorChanged = anchorChanged,
    )

    private fun occurrence() = InformationRequestGroupOccurrence().apply {
        informationRequestId = request.id
        sourceTemplateGroupId = lineItems.id
        occurrencePath = "line-items[${UUID.randomUUID()}]"
    }

    private fun submitted(stageKey: String) = InformationRequestSubmissionPackage().apply {
        informationRequestId = request.id
        this.stageKey = stageKey
    }

    private fun version() = InformationRequestTemplateVersionDto(
        id = request.templateVersionId,
        templateDefinitionId = UUID.randomUUID(),
        versionNumber = 1,
        status = InformationRequestTemplateStatus.PUBLISHED,
        groups = listOf(lineItems),
        createdAt = Timestamp.from(Instant.now()),
    )
}
