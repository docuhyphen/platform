package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.InformationRequestTemplateConfigurationMapper
import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.FieldOperator
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.RequestTemplatePostgreSQLResource
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.sql.Timestamp
import java.time.Instant
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(RequestTemplatePostgreSQLResource::class)
class InformationRequestTemplateConfigurationWriterContractTest
{
    @Inject
    lateinit var writer: InformationRequestTemplateConfigurationWriter

    @Inject
    lateinit var projectionLoader: InformationRequestTemplateProjectionLoader

    @Inject
    lateinit var definitionRepository: InformationRequestTemplateDefinitionRepository

    @Inject
    lateinit var versionRepository: InformationRequestTemplateVersionRepository

    @Inject
    lateinit var requirementRepository: InformationRequestTemplateRequirementRepository

    @Inject
    lateinit var dataSource: DataSource

    @Test
    fun `a whole configuration document survives the write and reads back as it was authored`()
    {
        val draft = draftTemplate()

        QuarkusTransaction.requiringNew().run { writer.replaceConfiguration(draft, richDocument()) }

        val version = QuarkusTransaction.requiringNew().call {
            projectionLoader.loadVersion(versionRepository.findById(draft.id)!!)
        }

        assertEquals(listOf("collected-data", "supporting-data"), version.sections.map { it.sectionKey })
        assertEquals("Collected data", version.sections.first().title)
        assertEquals("What the process records", version.sections.first().helpText)
        assertNull(version.schemaVersionId)

        val groupsByKey = version.groups.associateBy { it.groupKey }
        assertEquals(setOf("per-recorded-item", "per-recorded-detail"), groupsByKey.keys)
        val rootGroup = groupsByKey.getValue("per-recorded-item")
        assertNull(rootGroup.parentGroupKey)
        assertEquals(0, rootGroup.minOccurrences)
        assertEquals(5, rootGroup.maxOccurrences)
        val nestedGroup = groupsByKey.getValue("per-recorded-detail")
        assertEquals("per-recorded-item", nestedGroup.parentGroupKey)
        assertEquals(1, nestedGroup.minOccurrences)
        assertNull(nestedGroup.maxOccurrences)

        val rule = version.conditionRules.single()
        assertEquals("when-note-applies", rule.ruleKey)
        assertEquals(1, rule.expressionVersion)
        assertEquals(
            InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION,
            rule.hiddenDataPolicy,
        )
        val predicate = rule.predicates.single()
        assertEquals("supporting-record", predicate.sourceRequirementKey)
        assertNull(predicate.fieldDefinitionId)
        assertEquals(FieldOperator.EQUALS, predicate.operator)
        assertEquals(InformationRequestResponseDisposition.WAIVED, predicate.expectedDisposition)

        val collected = version.sections.first().requirements
        assertEquals(listOf("recorded-note", "recorded-assertion"), collected.map { it.requirementKey })

        val note = collected.first()
        assertEquals(InformationRequestRequirementType.FIELD, note.requirementType)
        assertEquals(collectedField, note.collectedFieldDefinitionId)
        assertEquals("State the recorded note", note.prompt)
        assertEquals(InformationRequestResponseMode.PROVIDE_ONCE, note.responseMode)
        assertEquals(InformationRequestRequiredness.CONDITIONAL, note.requiredness)
        assertEquals("when-note-applies", note.conditionalRuleKey)
        assertEquals("per-recorded-item", note.occurrenceAnchorKey)
        assertEquals("restricted-set", note.confidentialityCompartmentKey)
        assertEquals(InformationRequestContributorRole.PREPARER, note.contributorRole)
        assertEquals(InformationRequestReviewPolicy.REQUIRED, note.reviewPolicy)
        assertEquals(
            listOf(
                InformationRequestResponseDisposition.PROVIDED,
                InformationRequestResponseDisposition.NOT_APPLICABLE,
            ),
            note.permittedDispositions,
        )
        assertEquals(listOf("supporting-record"), note.supportingEvidenceRequirementKeys)
        assertNull(note.evidencePolicy)

        val supporting = version.sections.last().requirements
        assertEquals(listOf("supporting-record", "alternate-record"), supporting.map { it.requirementKey })

        val record = supporting.first()
        assertEquals(listOf("alternate-record"), record.substituteRequirementKeys)
        val policy = requireNotNull(record.evidencePolicy) { "A requested document states its policy" }
        assertEquals(2, policy.minimumFileCount)
        assertEquals(6, policy.maximumFileCount)
        assertEquals(1_000_000L, policy.maximumFileSizeBytes)
        assertEquals(4_000_000L, policy.maximumTotalSizeBytes)
        assertEquals(InformationRequestEvidenceAttributeRequirement.REQUIRED, policy.issuerRequirement)
        assertEquals(90, policy.maximumIssueAgeDays)
        assertTrue(policy.coverageContinuityRequired)
        assertEquals(InformationRequestEvidenceWaiverPolicy.REVIEW_APPROVAL_REQUIRED, policy.waiverPolicy)
        assertEquals(
            InformationRequestEvidenceConformancePolicy.DEFICIENCY_REVIEWABLE,
            policy.conformancePolicy,
        )
        assertEquals(
            listOf(
                InformationRequestEvidenceAttribute.CONTENT_TYPE to "application/pdf",
                InformationRequestEvidenceAttribute.ISSUER to "recording-body",
            ),
            policy.acceptedValues.map { it.attribute to it.acceptedValue }.sortedBy { it.first.name },
        )
    }

    @Test
    fun `groups, rules and related documents read back in key order so equal content always hashes alike`() {
        val first = draftTemplate()
        val second = draftTemplate()
        QuarkusTransaction.requiringNew().run {
            writer.replaceConfiguration(first, orderedDocument(reversed = false))
            writer.replaceConfiguration(second, orderedDocument(reversed = true))
        }

        val (one, two) = QuarkusTransaction.requiringNew().call {
            listOf(first, second).map { projectionLoader.loadVersion(versionRepository.findById(it.id)!!) }
        }

        listOf(one, two).forEach { version ->
            assertEquals(listOf("alpha-group", "zeta-group"), version.groups.map { it.groupKey })
            assertEquals(listOf("alpha-rule", "zeta-rule"), version.conditionRules.map { it.ruleKey })
            val primary = version.sections.single().requirements.single { it.requirementKey == "primary-record" }
            assertEquals(listOf("alpha-record", "zeta-record"), primary.substituteRequirementKeys)
            val note = version.sections.single().requirements.single { it.requirementKey == "recorded-note" }
            assertEquals(listOf("alpha-record", "zeta-record"), note.supportingEvidenceRequirementKeys)
        }
        assertEquals(one.contentHashSha256, two.contentHashSha256)
    }

    @Test
    fun `a rewrite leaves only what the second document states`()
    {
        val draft = draftTemplate()
        QuarkusTransaction.requiringNew().run { writer.replaceConfiguration(draft, richDocument()) }

        QuarkusTransaction.requiringNew().run {
            writer.replaceConfiguration(
                draft,
                InformationRequestTemplateConfigurationRequest(
                    sections = listOf(
                        InformationRequestTemplateSectionRequest(
                            sectionKey = "collected-data",
                            title = "Collected data",
                            requirements = listOf(
                                requirement("recorded-note", InformationRequestRequirementType.FIELD),
                            ),
                        ),
                    ),
                ),
            )
        }

        val version = QuarkusTransaction.requiringNew().call {
            projectionLoader.loadVersion(versionRepository.findById(draft.id)!!)
        }

        assertEquals(listOf("collected-data"), version.sections.map { it.sectionKey })
        assertEquals(emptyList<String>(), version.groups.map { it.groupKey })
        assertEquals(emptyList<String>(), version.conditionRules.map { it.ruleKey })
        val note = version.sections.single().requirements.single()
        assertEquals("recorded-note", note.requirementKey)
        assertEquals(emptyList<String>(), note.supportingEvidenceRequirementKeys)
        assertEquals(
            listOf(InformationRequestResponseDisposition.PROVIDED),
            note.permittedDispositions,
        )
        assertNull(note.evidencePolicy)
        assertEquals(
            listOf("alternate-record", "recorded-assertion", "recorded-note", "supporting-record"),
            QuarkusTransaction.requiringNew()
                .call { requirementRepository.findAllByDefinition(draft.templateDefinitionId) }
                .map { it.requirementKey }
                .sorted(),
        )
    }

    @Test
    fun `a stable requirement keeps its identity across a rewrite that still asks for it`()
    {
        val draft = draftTemplate()
        QuarkusTransaction.requiringNew().run { writer.replaceConfiguration(draft, richDocument()) }

        val firstIdentity = QuarkusTransaction.requiringNew().call {
            projectionLoader.loadVersion(versionRepository.findById(draft.id)!!)
        }.sections.first().requirements.first()

        QuarkusTransaction.requiringNew().run { writer.replaceConfiguration(draft, richDocument()) }

        val secondIdentity = QuarkusTransaction.requiringNew().call {
            projectionLoader.loadVersion(versionRepository.findById(draft.id)!!)
        }.sections.first().requirements.first()

        assertEquals(firstIdentity.requirementKey, secondIdentity.requirementKey)
        assertEquals(firstIdentity.templateRequirementId, secondIdentity.templateRequirementId)
        assertNotEquals(firstIdentity.id, secondIdentity.id)
    }

    @Test
    fun `a requirement cannot change what kind of thing it asks for`()
    {
        val draft = draftTemplate()
        QuarkusTransaction.requiringNew().run { writer.replaceConfiguration(draft, richDocument()) }

        val refusal = assertThrows<InformationRequestTemplateValidationException> {
            QuarkusTransaction.requiringNew().run {
                writer.replaceConfiguration(
                    draft,
                    InformationRequestTemplateConfigurationRequest(
                        sections = listOf(
                            InformationRequestTemplateSectionRequest(
                                sectionKey = "collected-data",
                                title = "Collected data",
                                requirements = listOf(
                                    requirement("recorded-note", InformationRequestRequirementType.DOCUMENT)
                                        .copy(evidencePolicy = InformationRequestTemplateEvidencePolicyRequest()),
                                ),
                            ),
                        ),
                    ),
                )
            }
        }

        assertTrue(
            refusal.message.contains("recorded-note") && refusal.message.contains("FIELD"),
            "The refusal names the requirement and what it already asks for: ${refusal.message}",
        )
    }

    @Test
    fun `the configuration of a frozen version cannot be rewritten`()
    {
        val draft = draftTemplate()
        QuarkusTransaction.requiringNew().run { writer.replaceConfiguration(draft, richDocument()) }

        val frozen = QuarkusTransaction.requiringNew().call {
            versionRepository.save(
                InformationRequestTemplateVersion().apply {
                    templateDefinitionId = draft.templateDefinitionId
                    versionNumber = 2
                    status = InformationRequestTemplateStatus.PUBLISHED
                    publishedAt = Timestamp.from(Instant.now())
                },
            )
        }

        assertThrows<Exception> {
            QuarkusTransaction.requiringNew().run {
                writer.replaceConfiguration(frozen, richDocument())
            }
        }

        val stillAuthored = QuarkusTransaction.requiringNew().call {
            projectionLoader.loadVersion(versionRepository.findById(draft.id)!!)
        }
        assertEquals(listOf("collected-data", "supporting-data"), stillAuthored.sections.map { it.sectionKey })
    }

    @Test
    fun `stages and attestation policies survive the write and read back as authored`()
    {
        val draft = draftTemplate()
        val staged = InformationRequestTemplateConfigurationRequest(
            submissionMode = InformationRequestSubmissionMode.STAGED,
            submissionStageOrdering = InformationRequestSubmissionStageOrdering.SEQUENTIAL,
            sections = listOf(
                InformationRequestTemplateSectionRequest(
                    sectionKey = "supporting-data",
                    title = "Supporting data",
                    submissionStageKey = "record-stage",
                    requirements = listOf(
                        requirement("supporting-record", InformationRequestRequirementType.DOCUMENT).copy(
                            evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(),
                        ),
                    ),
                ),
                InformationRequestTemplateSectionRequest(
                    sectionKey = "confirmations",
                    title = "Confirmations",
                    submissionStageKey = "confirmation-stage",
                    requirements = listOf(
                        requirement("recorded-assertion", InformationRequestRequirementType.RESPONSE_ATTESTATION).copy(
                            attestationPolicy = InformationRequestTemplateAttestationPolicyRequest(
                                requiredRoles = listOf(
                                    InformationRequestContributorRole.PREPARER,
                                    InformationRequestContributorRole.ATTESTOR,
                                ),
                                ordering = InformationRequestAttestationOrdering.ROLE_SEQUENCE,
                                minimumAssentCount = 3,
                                minimumAuthenticationStrength = InformationRequestAuthenticationStrength.MULTI_FACTOR,
                                validityHours = 24,
                                externalSignatureReference = InformationRequestExternalSignatureReferencePolicy.REQUIRED,
                            ),
                        ),
                        requirement("plain-assertion", InformationRequestRequirementType.RESPONSE_ATTESTATION),
                    ),
                ),
            ),
        )

        QuarkusTransaction.requiringNew().run { writer.replaceConfiguration(draft, staged) }
        val version = QuarkusTransaction.requiringNew().call {
            projectionLoader.loadVersion(versionRepository.findById(draft.id)!!)
        }

        assertEquals(InformationRequestSubmissionMode.STAGED, version.submissionMode)
        assertEquals(InformationRequestSubmissionStageOrdering.SEQUENTIAL, version.submissionStageOrdering)
        assertEquals(listOf("record-stage", "confirmation-stage"), version.sections.map { it.submissionStageKey })
        val assertions = version.sections.last().requirements.associateBy { it.requirementKey }
        val stated = assertions.getValue("recorded-assertion").attestationPolicy!!
        assertEquals(
            listOf(InformationRequestContributorRole.PREPARER, InformationRequestContributorRole.ATTESTOR),
            stated.requiredRoles,
        )
        assertEquals(InformationRequestAttestationOrdering.ROLE_SEQUENCE, stated.ordering)
        assertEquals(3, stated.minimumAssentCount)
        assertEquals(InformationRequestAuthenticationStrength.MULTI_FACTOR, stated.minimumAuthenticationStrength)
        assertEquals(24, stated.validityHours)
        assertEquals(InformationRequestExternalSignatureReferencePolicy.REQUIRED, stated.externalSignatureReference)
        val defaulted = assertions.getValue("plain-assertion").attestationPolicy!!
        assertEquals(listOf(InformationRequestContributorRole.CONTRIBUTOR), defaulted.requiredRoles)
        assertEquals(1, defaulted.minimumAssentCount)
        assertNull(version.sections.first().requirements.single().attestationPolicy)

        val copied = InformationRequestTemplateConfigurationMapper.toRequest(version)
        assertEquals(staged.submissionMode, copied.submissionMode)
        assertEquals(stated.requiredRoles, copied.sections.last().requirements.first().attestationPolicy!!.requiredRoles)

        QuarkusTransaction.requiringNew().run {
            writer.replaceConfiguration(
                versionRepository.findById(draft.id)!!,
                InformationRequestTemplateConfigurationRequest(
                    sections = listOf(
                        InformationRequestTemplateSectionRequest(
                            sectionKey = "supporting-data",
                            title = "Supporting data",
                            requirements = listOf(
                                requirement("supporting-record", InformationRequestRequirementType.DOCUMENT).copy(
                                    evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(),
                                ),
                            ),
                        ),
                    ),
                ),
            )
        }
        val rewritten = QuarkusTransaction.requiringNew().call {
            projectionLoader.loadVersion(versionRepository.findById(draft.id)!!)
        }
        assertEquals(InformationRequestSubmissionMode.WHOLE_PACKAGE, rewritten.submissionMode)
        assertNull(rewritten.sections.single().submissionStageKey)
    }

    @Test
    fun `review stages, their ordering, and the reuse purpose survive the write and read back as authored`()
    {
        val draft = draftTemplate()
        val reviewed = InformationRequestTemplateConfigurationRequest(
            reviewStageOrdering = InformationRequestReviewStageOrdering.PARALLEL,
            factReusePurposeKey = "profile.reuse",
            sections = listOf(
                InformationRequestTemplateSectionRequest(
                    sectionKey = "supporting-data",
                    title = "Supporting data",
                    requirements = listOf(
                        requirement("supporting-record", InformationRequestRequirementType.DOCUMENT).copy(
                            reviewPolicy = InformationRequestReviewPolicy.REQUIRED,
                            evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(),
                        ),
                    ),
                ),
                InformationRequestTemplateSectionRequest(
                    sectionKey = "other-data",
                    title = "Other data",
                    requirements = listOf(
                        requirement("other-record", InformationRequestRequirementType.DOCUMENT).copy(
                            evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(),
                        ),
                    ),
                ),
            ),
            reviewStages = listOf(
                InformationRequestTemplateReviewStageRequest(
                    stageKey = "first-check",
                    title = "First check",
                    sectionKeys = listOf("supporting-data"),
                ),
                InformationRequestTemplateReviewStageRequest(
                    stageKey = "final-check",
                    title = "Final check",
                    aggregation = InformationRequestReviewAggregation.QUORUM,
                    quorumCount = 2,
                    minimumReviewerCount = 3,
                    tieResolution = InformationRequestReviewTieResolution.REQUIRE_OVERRIDE,
                    overridePermitted = true,
                    excludesResponseParties = true,
                    excludesPriorReviewers = true,
                ),
            ),
        )

        QuarkusTransaction.requiringNew().run { writer.replaceConfiguration(draft, reviewed) }
        val version = QuarkusTransaction.requiringNew().call {
            projectionLoader.loadVersion(versionRepository.findById(draft.id)!!)
        }

        assertEquals(InformationRequestReviewStageOrdering.PARALLEL, version.reviewStageOrdering)
        assertEquals("profile.reuse", version.factReusePurposeKey)
        assertEquals(listOf("first-check", "final-check"), version.reviewStages.map { it.stageKey })
        assertEquals(listOf("supporting-data"), version.reviewStages.first().sectionKeys)
        val final = version.reviewStages.last()
        assertEquals(InformationRequestReviewAggregation.QUORUM, final.aggregation)
        assertEquals(2, final.quorumCount)
        assertEquals(3, final.minimumReviewerCount)
        assertEquals(InformationRequestReviewTieResolution.REQUIRE_OVERRIDE, final.tieResolution)
        assertTrue(final.overridePermitted && final.excludesResponseParties && final.excludesPriorReviewers)
        assertTrue(final.sectionKeys.isEmpty())

        val copied = InformationRequestTemplateConfigurationMapper.toRequest(version)
        assertEquals(reviewed.reviewStages, copied.reviewStages)
        assertEquals(reviewed.reviewStageOrdering, copied.reviewStageOrdering)
        assertEquals(reviewed.factReusePurposeKey, copied.factReusePurposeKey)

        QuarkusTransaction.requiringNew().run {
            writer.replaceConfiguration(
                versionRepository.findById(draft.id)!!,
                reviewed.copy(reviewStages = emptyList(), factReusePurposeKey = null),
            )
        }
        val rewritten = QuarkusTransaction.requiringNew().call {
            projectionLoader.loadVersion(versionRepository.findById(draft.id)!!)
        }
        assertTrue(rewritten.reviewStages.isEmpty())
        assertNull(rewritten.factReusePurposeKey)
    }

    @Test
    fun `a bound the stored policy refuses stops the whole write rather than half of it`()
    {
        val draft = draftTemplate()

        assertThrows<Exception> {
            QuarkusTransaction.requiringNew().run {
                writer.replaceConfiguration(
                    draft,
                    InformationRequestTemplateConfigurationRequest(
                        sections = listOf(
                            InformationRequestTemplateSectionRequest(
                                sectionKey = "supporting-data",
                                title = "Supporting data",
                                requirements = listOf(
                                    requirement("supporting-record", InformationRequestRequirementType.DOCUMENT)
                                        .copy(
                                            evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(
                                                minimumFileCount = 4,
                                                maximumFileCount = 2,
                                            ),
                                        ),
                                ),
                            ),
                        ),
                    ),
                )
            }
        }

        val version = QuarkusTransaction.requiringNew().call {
            projectionLoader.loadVersion(versionRepository.findById(draft.id)!!)
        }
        assertEquals(emptyList<String>(), version.sections.map { it.sectionKey })
    }

    private fun richDocument() = InformationRequestTemplateConfigurationRequest(
        conditionRules = listOf(
            InformationRequestTemplateConditionRuleRequest(
                ruleKey = "when-note-applies",
                expressionVersion = 1,
                hiddenDataPolicy = InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION,
                predicates = listOf(
                    InformationRequestTemplateConditionPredicateRequest(
                        sourceRequirementKey = "supporting-record",
                        operator = FieldOperator.EQUALS,
                        expectedDisposition = InformationRequestResponseDisposition.WAIVED,
                    ),
                ),
            ),
        ),
        groups = listOf(
            InformationRequestTemplateGroupRequest(
                groupKey = "per-recorded-item",
                maxOccurrences = 5,
            ),
            InformationRequestTemplateGroupRequest(
                groupKey = "per-recorded-detail",
                parentGroupKey = "per-recorded-item",
                minOccurrences = 1,
            ),
        ),
        sections = listOf(
            InformationRequestTemplateSectionRequest(
                sectionKey = "collected-data",
                title = "Collected data",
                helpText = "What the process records",
                requirements = listOf(
                    requirement("recorded-note", InformationRequestRequirementType.FIELD).copy(
                        responseMode = InformationRequestResponseMode.PROVIDE_ONCE,
                        requiredness = InformationRequestRequiredness.CONDITIONAL,
                        contributorRole = InformationRequestContributorRole.PREPARER,
                        reviewPolicy = InformationRequestReviewPolicy.REQUIRED,
                        confidentialityCompartmentKey = "restricted-set",
                        conditionalRuleKey = "when-note-applies",
                        occurrenceAnchorKey = "per-recorded-item",
                        permittedDispositions = listOf(
                            InformationRequestResponseDisposition.NOT_APPLICABLE,
                            InformationRequestResponseDisposition.PROVIDED,
                        ),
                        supportingEvidenceRequirementKeys = listOf("supporting-record"),
                    ),
                    requirement(
                        "recorded-assertion",
                        InformationRequestRequirementType.RESPONSE_ATTESTATION,
                    ).copy(occurrenceAnchorKey = "per-recorded-detail"),
                ),
            ),
            InformationRequestTemplateSectionRequest(
                sectionKey = "supporting-data",
                title = "Supporting data",
                requirements = listOf(
                    requirement("supporting-record", InformationRequestRequirementType.DOCUMENT).copy(
                        contributorRole = InformationRequestContributorRole.PREPARER,
                        permittedDispositions = listOf(InformationRequestResponseDisposition.WAIVED),
                        evidencePolicy = strictPolicy(),
                        substituteRequirementKeys = listOf("alternate-record"),
                    ),
                    requirement("alternate-record", InformationRequestRequirementType.DOCUMENT).copy(
                        evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(),
                    ),
                ),
            ),
        ),
    )

    private fun orderedDocument(reversed: Boolean): InformationRequestTemplateConfigurationRequest {
        fun ordered(vararg items: String): List<String> = if (reversed) items.reversed() else items.toList()
        fun document(key: String) = requirement(key, InformationRequestRequirementType.DOCUMENT).copy(
            evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(),
        )
        return InformationRequestTemplateConfigurationRequest(
            conditionRules = ordered("zeta-rule", "alpha-rule").map { key ->
                InformationRequestTemplateConditionRuleRequest(
                    ruleKey = key,
                    predicates = listOf(
                        InformationRequestTemplateConditionPredicateRequest(
                            sourceRequirementKey = "primary-record",
                            operator = FieldOperator.EQUALS,
                            expectedDisposition = InformationRequestResponseDisposition.PROVIDED,
                        ),
                    ),
                )
            },
            groups = ordered("zeta-group", "alpha-group").map { InformationRequestTemplateGroupRequest(groupKey = it) },
            sections = listOf(
                InformationRequestTemplateSectionRequest(
                    sectionKey = "collected-data",
                    title = "Collected data",
                    requirements = listOf(
                        document("primary-record").copy(
                            substituteRequirementKeys = ordered(
                                "zeta-record",
                                "alpha-record"
                            )
                        ),
                        document("zeta-record"),
                        document("alpha-record"),
                        requirement("recorded-note", InformationRequestRequirementType.FIELD).copy(
                            supportingEvidenceRequirementKeys = ordered("zeta-record", "alpha-record"),
                        ),
                        document("zeta-entry").copy(
                            requiredness = InformationRequestRequiredness.CONDITIONAL,
                            conditionalRuleKey = "zeta-rule",
                            occurrenceAnchorKey = "zeta-group",
                        ),
                        document("alpha-entry").copy(
                            requiredness = InformationRequestRequiredness.CONDITIONAL,
                            conditionalRuleKey = "alpha-rule",
                            occurrenceAnchorKey = "alpha-group",
                        ),
                    ),
                ),
            ),
        )
    }

    private fun strictPolicy() = InformationRequestTemplateEvidencePolicyRequest(
        minimumFileCount = 2,
        maximumFileCount = 6,
        maximumFileSizeBytes = 1_000_000,
        maximumTotalSizeBytes = 4_000_000,
        minimumPageCount = 1,
        maximumPageCount = 40,
        issuerRequirement = InformationRequestEvidenceAttributeRequirement.REQUIRED,
        jurisdictionRequirement = InformationRequestEvidenceAttributeRequirement.OPTIONAL,
        languageRequirement = InformationRequestEvidenceAttributeRequirement.REQUIRED,
        issueDateRequirement = InformationRequestEvidenceAttributeRequirement.REQUIRED,
        expiryDateRequirement = InformationRequestEvidenceAttributeRequirement.REQUIRED,
        coveragePeriodRequirement = InformationRequestEvidenceAttributeRequirement.REQUIRED,
        certificationRequirement = InformationRequestEvidenceAttributeRequirement.REQUIRED,
        signatureRequirement = InformationRequestEvidenceAttributeRequirement.OPTIONAL,
        maximumIssueAgeDays = 90,
        minimumRemainingValidityDays = 0,
        minimumCoverageDays = 90,
        coverageContinuityRequired = true,
        waiverPolicy = InformationRequestEvidenceWaiverPolicy.REVIEW_APPROVAL_REQUIRED,
        conformancePolicy = InformationRequestEvidenceConformancePolicy.DEFICIENCY_REVIEWABLE,
        acceptedValues = listOf(
            InformationRequestTemplateAcceptedValueRequest(
                InformationRequestEvidenceAttribute.CONTENT_TYPE,
                "application/pdf",
            ),
            InformationRequestTemplateAcceptedValueRequest(
                InformationRequestEvidenceAttribute.ISSUER,
                "recording-body",
            ),
        ),
    )

    private fun requirement(key: String, type: InformationRequestRequirementType) =
        InformationRequestTemplateRequirementRequest(
            requirementKey = key,
            requirementType = type,
            prompt = "State the recorded note",
            collectedFieldDefinitionId =
                if (type == InformationRequestRequirementType.FIELD) collectedField else null,
        )

    private val collectedField: UUID by lazy { insertFieldDefinition() }

    private fun insertFieldDefinition(): UUID
    {
        val id = UUID.randomUUID()
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO field_definition
                    (id, scope_kind, namespace, field_key, status, created_at, updated_at)
                VALUES (?, 'PLATFORM', 'process', ?, 'PUBLISHED', ?, ?)
                """.trimIndent(),
            ).use { statement ->
                val now = Timestamp.from(Instant.now())
                statement.setObject(1, id)
                statement.setString(2, "recorded-note-${id.toString().take(8)}")
                statement.setTimestamp(3, now)
                statement.setTimestamp(4, now)
                statement.executeUpdate()
            }
        }
        return id
    }

    private fun draftTemplate(): InformationRequestTemplateVersion
    {
        val owner = insertOwner()
        val definition = QuarkusTransaction.requiringNew().call {
            definitionRepository.save(
                InformationRequestTemplateDefinition().apply {
                    scopeKind = InformationRequestTemplateScopeKind.PERSONAL
                    scopeUserId = owner
                    namespace = "process-${UUID.randomUUID().toString().take(8)}"
                    templateKey = "collection-pattern"
                    displayName = "Collection pattern"
                },
            )
        }
        return QuarkusTransaction.requiringNew().call {
            versionRepository.save(
                InformationRequestTemplateVersion().apply {
                    templateDefinitionId = definition.id
                    versionNumber = 1
                },
            )
        }
    }

    private fun insertOwner(): UUID
    {
        val id = UUID.randomUUID()
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO app_user
                    (id, is_active, created_date, email, email_verification_completed, is_temporary,
                     sign_in_attempts, exchange_version, multifactor_authentication_type,
                     is_password_temporary, email_mfa_fallback_enabled)
                VALUES (?, TRUE, ?, ?, TRUE, FALSE, 0, 0, 'EMAIL', FALSE, FALSE)
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, id)
                statement.setTimestamp(2, Timestamp.from(Instant.now()))
                statement.setString(3, "writer-owner-${id.toString().take(8)}@process.test")
                statement.executeUpdate()
            }
        }
        return id
    }
}
