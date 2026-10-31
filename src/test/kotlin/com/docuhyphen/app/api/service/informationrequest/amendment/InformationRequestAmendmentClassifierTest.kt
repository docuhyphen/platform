package com.docuhyphen.app.api.service.informationrequest.amendment

import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.FieldOperator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.Timestamp
import java.time.Instant
import java.util.*

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
    fun `a condition named by a requirement that is not asked only when it holds changes nothing`() {
        val rules = listOf(rule("when-recorded"), rule("when-confirmed"))
        val from = version(
            listOf(requirement(recordedItem, "recorded-item", condition = "when-recorded")),
            conditionRules = rules,
        )
        val unconditioned = version(listOf(requirement(recordedItem, "recorded-item")), conditionRules = rules)
        val renamed = version(
            listOf(requirement(recordedItem, "recorded-item", condition = "when-confirmed")),
            conditionRules = rules,
        )

        assertTrue(InformationRequestAmendmentClassifier.classify(from, unconditioned).changes.isEmpty())
        assertTrue(InformationRequestAmendmentClassifier.classify(from, renamed).changes.isEmpty())
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

    @Test
    fun `a changed enclosing group bound changes the meaning of every Requirement nested beneath it`() {
        val requirements = listOf(
            requirement(recordedItem, "recorded-item", anchor = "details"),
            requirement(lineItem, "line-item", anchor = "notes"),
        )

        fun groups(entriesMin: Int = 1, sectionsMax: Int? = 5) = listOf(
            group("sections", max = sectionsMax),
            group("entries", parent = "sections", min = entriesMin),
            group("details", parent = "entries", min = 1),
            group("notes"),
        )

        val from = version(requirements, groups = groups())

        listOf(groups(entriesMin = 2), groups(sectionsMax = 8)).forEach { changed ->
            val plan = InformationRequestAmendmentClassifier.classify(from, version(requirements, groups = changed))

            assertEquals(
                listOf("recorded-item" to InformationRequestAmendmentChangeKind.MEANING_CHANGED),
                plan.changes.map { it.requirementKey to it.kind },
            )
            assertEquals(false, plan.changes.single().anchorChanged)
        }
        assertTrue(
            InformationRequestAmendmentClassifier.classify(
                from,
                version(requirements, groups = groups())
            ).changes.isEmpty()
        )
    }

    @Test
    fun `a target Version with a Field Requirement is reported as collecting Field answers`() {
        val from = version(listOf(requirement(recordedItem, "recorded-item")))
        val to = version(
            listOf(
                requirement(recordedItem, "recorded-item"),
                requirement(lineItem, "line-item").copy(requirementType = InformationRequestRequirementType.FIELD),
            ),
        )

        assertEquals(false, InformationRequestAmendmentClassifier.classify(from, from).targetCollectsFields)
        assertEquals(true, InformationRequestAmendmentClassifier.classify(from, to).targetCollectsFields)
    }

    @Test
    fun `adding a Requirement, changing what one asks, or allowing more occurrences expands the request`() {
        val from = version(listOf(requirement(recordedItem, "recorded-item")), groups = listOf(group("line-items")))

        val added = version(
            listOf(requirement(recordedItem, "recorded-item"), requirement(lineItem, "line-item")),
            groups = listOf(group("line-items")),
        )
        val tightened = version(
            listOf(requirement(recordedItem, "recorded-item", requiredness = InformationRequestRequiredness.OPTIONAL)),
            groups = listOf(group("line-items")),
        )
        val morePermitted =
            version(listOf(requirement(recordedItem, "recorded-item")), groups = listOf(group("line-items", max = 8)))
        val unbounded = version(
            listOf(requirement(recordedItem, "recorded-item")),
            groups = listOf(group("line-items", max = null))
        )
        val moreRequired =
            version(listOf(requirement(recordedItem, "recorded-item")), groups = listOf(group("line-items", min = 2)))

        listOf(added, tightened, morePermitted, unbounded, moreRequired).forEach { to ->
            assertTrue(InformationRequestAmendmentClassifier.classify(from, to).expanding)
        }
    }

    @Test
    fun `rewording, removing a Requirement, or allowing fewer occurrences does not expand the request`() {
        val from = version(
            listOf(requirement(recordedItem, "recorded-item"), requirement(confirmation, "confirmation")),
            groups = listOf(group("line-items")),
        )

        val reworded = version(
            listOf(
                requirement(recordedItem, "recorded-item", prompt = "Upload the recorded item"),
                requirement(confirmation, "confirmation"),
            ),
            groups = listOf(group("line-items")),
        )
        val removed = version(listOf(requirement(recordedItem, "recorded-item")), groups = listOf(group("line-items")))
        val fewer = version(
            listOf(requirement(recordedItem, "recorded-item"), requirement(confirmation, "confirmation")),
            groups = listOf(group("line-items", max = 2)),
        )

        listOf(reworded, removed, fewer).forEach { to ->
            assertEquals(false, InformationRequestAmendmentClassifier.classify(from, to).expanding)
        }
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
        condition: String? = null,
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
        conditionalRuleKey = condition,
    )

    private fun rule(key: String) = InformationRequestTemplateConditionRuleDto(
        id = UUID.randomUUID(),
        ruleKey = key,
        expressionVersion = 1,
        predicates = listOf(
            InformationRequestTemplateConditionPredicateDto(
                id = UUID.randomUUID(),
                sourceRequirementKey = "confirmation",
                operator = FieldOperator.IS_NOT_EMPTY,
            ),
        ),
    )

    private fun group(key: String, parent: String? = null, min: Int = 0, max: Int? = 5) =
        InformationRequestTemplateGroupDto(UUID.randomUUID(), key, parent, minOccurrences = min, maxOccurrences = max)

    private fun version(
        requirements: List<InformationRequestTemplateRequirementDto>,
        stageKey: String? = null,
        groups: List<InformationRequestTemplateGroupDto> = emptyList(),
        schemaVersionId: UUID? = null,
        submissionMode: InformationRequestSubmissionMode = InformationRequestSubmissionMode.WHOLE_PACKAGE,
        conditionRules: List<InformationRequestTemplateConditionRuleDto> = emptyList(),
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
        conditionRules = conditionRules,
        createdAt = Timestamp.from(Instant.now()),
    )
}
