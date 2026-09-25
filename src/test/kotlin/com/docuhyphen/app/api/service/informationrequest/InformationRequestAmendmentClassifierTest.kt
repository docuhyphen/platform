package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateGroupDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateVersionDto
import com.docuhyphen.app.api.model.entity.InformationRequestAmendmentChangeKind
import com.docuhyphen.app.api.model.entity.InformationRequestContributorRole
import com.docuhyphen.app.api.model.entity.InformationRequestRequiredness
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.entity.InformationRequestResponseMode
import com.docuhyphen.app.api.model.entity.InformationRequestReviewPolicy
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionMode
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class InformationRequestAmendmentClassifierTest
{
    private val recordedItem = UUID.randomUUID()
    private val confirmation = UUID.randomUUID()
    private val lineItem = UUID.randomUUID()

    @Test
    fun `a Version that only rewords or reorders what it asks changes presentation, never meaning`()
    {
        val from = version(listOf(requirement(recordedItem, "recorded-item"), requirement(confirmation, "confirmation")))
        val to = version(
            listOf(
                requirement(confirmation, "confirmation"),
                requirement(recordedItem, "recorded-item", prompt = "Upload the recorded item", dispositions = reversedDispositions),
            ),
        )

        val plan = InformationRequestAmendmentClassifier.classify(from, to)

        assertEquals(
            listOf(
                "confirmation" to InformationRequestAmendmentChangeKind.PRESENTATION_CHANGED,
                "recorded-item" to InformationRequestAmendmentChangeKind.PRESENTATION_CHANGED,
            ),
            plan.changes.map { it.requirementKey to it.kind },
        )
        assertEquals(false, plan.schemaChanged)
        assertEquals(false, plan.submissionPolicyChanged)
    }

    @Test
    fun `an unchanged Version records no change`()
    {
        val from = version(listOf(requirement(recordedItem, "recorded-item")))
        val to = version(listOf(requirement(recordedItem, "recorded-item")))

        assertTrue(InformationRequestAmendmentClassifier.classify(from, to).changes.isEmpty())
    }

    @Test
    fun `a change to what is asked, or its stage, changes meaning and additions and removals are named`()
    {
        val from = version(
            listOf(requirement(recordedItem, "recorded-item"), requirement(confirmation, "confirmation")),
            stageKey = "first-stage",
        )
        val to = version(
            listOf(
                requirement(recordedItem, "recorded-item", requiredness = InformationRequestRequiredness.OPTIONAL),
                requirement(lineItem, "line-item"),
            ),
            stageKey = "second-stage",
        )

        val plan = InformationRequestAmendmentClassifier.classify(from, to)

        assertEquals(
            listOf(
                "confirmation" to InformationRequestAmendmentChangeKind.REMOVED,
                "line-item" to InformationRequestAmendmentChangeKind.ADDED,
                "recorded-item" to InformationRequestAmendmentChangeKind.MEANING_CHANGED,
            ),
            plan.changes.map { it.requirementKey to it.kind },
        )
        val removed = plan.changes.single { it.kind == InformationRequestAmendmentChangeKind.REMOVED }
        assertEquals(null, removed.toBindingId)
        assertEquals("first-stage", removed.fromStageKey)
        val added = plan.changes.single { it.kind == InformationRequestAmendmentChangeKind.ADDED }
        assertEquals(null, added.fromBindingId)
        assertEquals("second-stage", added.toStageKey)
    }

    @Test
    fun `a moved occurrence anchor, a removed group, and a schema or submission policy change are reported`()
    {
        val from = version(
            listOf(requirement(recordedItem, "recorded-item", anchor = "line-items")),
            groups = listOf(group("line-items"), group("notes")),
        )
        val to = version(
            listOf(requirement(recordedItem, "recorded-item", anchor = "entries")),
            groups = listOf(group("entries"), group("notes", parent = "entries")),
            schemaVersionId = UUID.randomUUID(),
            submissionMode = InformationRequestSubmissionMode.STAGED,
        )

        val plan = InformationRequestAmendmentClassifier.classify(from, to)

        assertEquals(true, plan.changes.single().anchorChanged)
        assertEquals(InformationRequestAmendmentChangeKind.MEANING_CHANGED, plan.changes.single().kind)
        assertEquals(
            listOf(Triple("line-items", true, false), Triple("notes", false, true)),
            plan.groupChanges.map { Triple(it.groupKey, it.removed, it.parentChanged) },
        )
        assertEquals(true, plan.schemaChanged)
        assertEquals(true, plan.submissionPolicyChanged)
    }

    private val reversedDispositions = listOf(
        InformationRequestResponseDisposition.NOT_APPLICABLE,
        InformationRequestResponseDisposition.PROVIDED,
    )

    @Suppress("LongParameterList")
    private fun requirement(
        templateRequirementId: UUID,
        key: String,
        prompt: String = "Provide the $key",
        requiredness: InformationRequestRequiredness = InformationRequestRequiredness.REQUIRED,
        anchor: String? = null,
        dispositions: List<InformationRequestResponseDisposition> = reversedDispositions.reversed(),
    ) = InformationRequestTemplateRequirementDto(
        id = UUID.randomUUID(),
        templateRequirementId = templateRequirementId,
        requirementKey = key,
        requirementType = InformationRequestRequirementType.DOCUMENT,
        prompt = prompt,
        responseMode = InformationRequestResponseMode.PROVIDE,
        requiredness = requiredness,
        contributorRole = InformationRequestContributorRole.CONTRIBUTOR,
        reviewPolicy = InformationRequestReviewPolicy.NOT_REQUIRED,
        occurrenceAnchorKey = anchor,
        permittedDispositions = dispositions,
    )

    private fun group(key: String, parent: String? = null) =
        InformationRequestTemplateGroupDto(UUID.randomUUID(), key, parent, minOccurrences = 0, maxOccurrences = 5)

    private fun version(
        requirements: List<InformationRequestTemplateRequirementDto>,
        stageKey: String? = null,
        groups: List<InformationRequestTemplateGroupDto> = emptyList(),
        schemaVersionId: UUID? = null,
        submissionMode: InformationRequestSubmissionMode = InformationRequestSubmissionMode.WHOLE_PACKAGE,
    ) = InformationRequestTemplateVersionDto(
        id = UUID.randomUUID(),
        templateDefinitionId = UUID(0, 1),
        versionNumber = 1,
        status = InformationRequestTemplateStatus.PUBLISHED,
        schemaVersionId = schemaVersionId,
        submissionMode = submissionMode,
        sections = listOf(
            InformationRequestTemplateSectionDto(
                id = UUID.randomUUID(),
                sectionKey = "records",
                title = "Records",
                submissionStageKey = stageKey,
                requirements = requirements,
            ),
        ),
        groups = groups,
        createdAt = Timestamp.from(Instant.now()),
    )
}
