package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateEvidencePolicyRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateReviewStageRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionRequest
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceConformancePolicy
import com.docuhyphen.app.api.model.entity.InformationRequestRequiredness
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestReviewAggregation
import com.docuhyphen.app.api.model.entity.InformationRequestReviewPolicy
import com.docuhyphen.app.api.model.entity.InformationRequestReviewStageOrdering
import com.docuhyphen.app.api.model.entity.InformationRequestReviewTieResolution
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class InformationRequestTemplateReviewPlanValidationTest
{
    private val validator = InformationRequestTemplateConfigurationValidator()

    @Test
    fun `a version that routes review states ordered stages with a coherent aggregation`()
    {
        val normalized = validator.normalize(
            configuration(
                section("records", reviewedDocument("checked-record")),
                section("confirmations", document("plain-record")),
                ordering = InformationRequestReviewStageOrdering.PARALLEL,
                stages = listOf(
                    stage(" First-Check ", sectionKeys = listOf(" Records ")),
                    stage(
                        "final-check",
                        aggregation = InformationRequestReviewAggregation.QUORUM,
                        quorum = 2,
                        minimum = 3,
                        tie = InformationRequestReviewTieResolution.REQUIRE_OVERRIDE,
                        override = true,
                        excludesResponseParties = true,
                        excludesPriorReviewers = true,
                    ),
                ),
            ),
        )

        assertEquals(InformationRequestReviewStageOrdering.PARALLEL, normalized.reviewStageOrdering)
        assertEquals(listOf("first-check", "final-check"), normalized.reviewStages.map { it.stageKey })
        assertEquals(listOf("records"), normalized.reviewStages.first().sectionKeys)
        val final = normalized.reviewStages.last()
        assertEquals(2, final.quorumCount)
        assertEquals(3, final.minimumReviewerCount)
        assertTrue(final.overridePermitted && final.excludesResponseParties && final.excludesPriorReviewers)
    }

    @Test
    fun `a reviewer-routed version that states no stage is left to the default stage`()
    {
        val normalized = validator.normalize(configuration(section("records", deficiencyReviewableDocument("checked-record"))))
        assertTrue(normalized.reviewStages.isEmpty())
        assertEquals(InformationRequestReviewStageOrdering.SEQUENTIAL, normalized.reviewStageOrdering)
    }

    @Test
    fun `a version that routes no work to a reviewer states no review stage`()
    {
        val refusal = assertThrows<InformationRequestTemplateValidationException> {
            validator.normalize(configuration(section("records", document("plain-record")), stages = listOf(stage("first-check"))))
        }
        assertEquals("first-check", refusal.reviewStageKey)
    }

    @Test
    fun `an incoherent review stage is refused naming the stage`()
    {
        fun refusedStage(candidate: InformationRequestTemplateReviewStageRequest, vararg others: InformationRequestTemplateReviewStageRequest) =
            assertThrows<InformationRequestTemplateValidationException> {
                validator.normalize(
                    configuration(section("records", reviewedDocument("checked-record")), stages = listOf(*others, candidate)),
                )
            }

        assertEquals("quorum-check", refusedStage(stage("quorum-check", aggregation = InformationRequestReviewAggregation.QUORUM)).reviewStageKey)
        assertEquals(
            "quorum-check",
            refusedStage(stage("quorum-check", aggregation = InformationRequestReviewAggregation.QUORUM, quorum = 3, minimum = 2)).reviewStageKey,
        )
        assertEquals("all-check", refusedStage(stage("all-check", aggregation = InformationRequestReviewAggregation.ALL, quorum = 1)).reviewStageKey)
        assertEquals(
            "tied-check",
            refusedStage(stage("tied-check", tie = InformationRequestReviewTieResolution.REQUIRE_OVERRIDE)).reviewStageKey,
        )
        assertEquals("empty-check", refusedStage(stage("empty-check", minimum = 0)).reviewStageKey)
        assertEquals("untitled-check", refusedStage(stage("untitled-check", title = "  ")).reviewStageKey)
        assertEquals("first-check", refusedStage(stage("first-check"), stage("first-check")).reviewStageKey)
        assertEquals("elsewhere-check", refusedStage(stage("elsewhere-check", sectionKeys = listOf("missing-section"))).reviewStageKey)
        assertNull(refusedStage(stage("Not A Key")).requirementKey)
    }

    @Test
    fun `every reviewer-routed requirement is covered by some stage`()
    {
        val refusal = assertThrows<InformationRequestTemplateValidationException> {
            validator.normalize(
                configuration(
                    section("records", reviewedDocument("checked-record")),
                    section("confirmations", document("plain-record")),
                    stages = listOf(stage("confirmation-check", sectionKeys = listOf("confirmations"))),
                ),
            )
        }
        assertEquals("checked-record", refusal.requirementKey)
    }

    @Test
    fun `a fact reuse purpose is a lowercase key`()
    {
        val normalized = validator.normalize(configuration(section("records", document("plain-record")), purpose = " Profile.Reuse "))
        assertEquals("profile.reuse", normalized.factReusePurposeKey)
        assertNull(validator.normalize(configuration(section("records", document("plain-record")), purpose = "  ")).factReusePurposeKey)
        assertThrows<InformationRequestTemplateValidationException> {
            validator.normalize(configuration(section("records", document("plain-record")), purpose = "has space"))
        }
    }

    private fun configuration(
        vararg sections: InformationRequestTemplateSectionRequest,
        ordering: InformationRequestReviewStageOrdering = InformationRequestReviewStageOrdering.SEQUENTIAL,
        stages: List<InformationRequestTemplateReviewStageRequest> = emptyList(),
        purpose: String? = null,
    ) = InformationRequestTemplateConfigurationRequest(
        sections = sections.toList(),
        reviewStageOrdering = ordering,
        reviewStages = stages,
        factReusePurposeKey = purpose,
    )

    private fun section(key: String, vararg requirements: InformationRequestTemplateRequirementRequest) =
        InformationRequestTemplateSectionRequest(sectionKey = key, title = "Records", requirements = requirements.toList())

    private fun document(key: String, review: InformationRequestReviewPolicy = InformationRequestReviewPolicy.NOT_REQUIRED) =
        InformationRequestTemplateRequirementRequest(
            requirementKey = key,
            requirementType = InformationRequestRequirementType.DOCUMENT,
            prompt = "Provide the recorded item",
            requiredness = InformationRequestRequiredness.REQUIRED,
            reviewPolicy = review,
            evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(),
        )

    private fun reviewedDocument(key: String) = document(key, InformationRequestReviewPolicy.REQUIRED)

    private fun deficiencyReviewableDocument(key: String) = document(key).copy(
        evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(
            conformancePolicy = InformationRequestEvidenceConformancePolicy.DEFICIENCY_REVIEWABLE,
        ),
    )

    @Suppress("LongParameterList")
    private fun stage(
        key: String,
        title: String = "Review stage",
        aggregation: InformationRequestReviewAggregation = InformationRequestReviewAggregation.ANY,
        quorum: Int? = null,
        minimum: Int = 1,
        tie: InformationRequestReviewTieResolution = InformationRequestReviewTieResolution.MOST_SEVERE_OUTCOME,
        override: Boolean = false,
        excludesResponseParties: Boolean = false,
        excludesPriorReviewers: Boolean = false,
        sectionKeys: List<String> = emptyList(),
    ) = InformationRequestTemplateReviewStageRequest(
        stageKey = key,
        title = title,
        aggregation = aggregation,
        quorumCount = quorum,
        minimumReviewerCount = minimum,
        tieResolution = tie,
        overridePermitted = override,
        excludesResponseParties = excludesResponseParties,
        excludesPriorReviewers = excludesPriorReviewers,
        sectionKeys = sectionKeys,
    )
}
