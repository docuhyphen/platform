package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateAcceptedValueRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConditionPredicateRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConditionRuleRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateEvidencePolicyRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateGroupRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionRequest
import com.docuhyphen.app.api.model.entity.InformationRequestContributorRole
import com.docuhyphen.app.api.service.fields.FieldOperator
import com.docuhyphen.app.api.model.entity.InformationRequestConditionHiddenDataPolicy
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAttribute
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAttributeRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceConformancePolicy
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceWaiverPolicy
import com.docuhyphen.app.api.model.entity.InformationRequestRequiredness
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.entity.InformationRequestResponseMode
import com.docuhyphen.app.api.model.entity.InformationRequestReviewPolicy
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateDefinition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateScopeKind
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateStatus
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersion
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.repository.informationrequest.RequestTemplatePostgreSQLResource
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

/**
 * A draft is authored as one document, so writing it has to leave exactly that document behind and
 * reading it back has to return the same thing.
 *
 * The pieces of a configuration live in eight tables with composite keys, freeze triggers, and
 * one-way relations between them, and a partial write would leave a version that renders as
 * something the author never stated. These tests exercise the whole document against the released
 * schema: what a write leaves behind, what a rewrite removes, what survives a rewrite because it is
 * a stable identity rather than a statement about one version, and what the stored rules refuse.
 */
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
        // The second document defines no groups or condition rules, so the first document's groups
        // and rules do not survive either, the same way its supporting evidence relation does not.
        assertEquals(emptyList<String>(), version.groups.map { it.groupKey })
        assertEquals(emptyList<String>(), version.conditionRules.map { it.ruleKey })
        val note = version.sections.single().requirements.single()
        assertEquals("recorded-note", note.requirementKey)
        assertEquals(emptyList<String>(), note.supportingEvidenceRequirementKeys)
        // The second document states no permitted answers, which is the plain provided answer and
        // nothing else rather than every answer.
        assertEquals(
            listOf(InformationRequestResponseDisposition.PROVIDED),
            note.permittedDispositions,
        )
        assertNull(note.evidencePolicy)
        // The requirement identity is not a statement about one version, so dropping the binding
        // that placed it leaves the identity behind for a later version to ask about again.
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
        // The binding is this version's statement about it, so rewriting the document restates it.
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

        // The frozen version is inserted rather than transitioned into. Transitioning one is the
        // publishing service's work and carries its own completeness rules, and neither is what this
        // is about: the question here is only whether configuration can reach a version that has
        // stopped being a draft.
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

    // ── Fixtures ──────────────────────────────────────────────────────────────

    /**
     * One version asking for typed data conditionally, per occurrence, in a compartment, reviewed
     * and supported by a requested document, plus two requested documents where one may stand in
     * for the other. Every optional policy a binding or an evidence policy can state is exercised,
     * so a mismapped column cannot pass unnoticed.
     */
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
                    ),
                ),
            ),
            InformationRequestTemplateSectionRequest(
                sectionKey = "supporting-data",
                title = "Supporting data",
                requirements = listOf(
                    requirement("supporting-record", InformationRequestRequirementType.DOCUMENT).copy(
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

    /**
     * The one stable field every typed requirement in these fixtures collects. It is created once
     * per test class because the reference is a foreign key, and a version that collects the same
     * field twice is refused, which is a rule of its own rather than something these tests exercise.
     */
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
