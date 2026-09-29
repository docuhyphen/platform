package com.docuhyphen.app.api.service.informationrequest.conformance

import com.docuhyphen.app.api.migration.queryStrings
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateAcceptedValueRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateReviewStageRequest
import com.docuhyphen.app.api.model.entity.InformationRequestFindingCorrectionScope
import com.docuhyphen.app.api.model.entity.InformationRequestFindingSeverity
import com.docuhyphen.app.api.model.entity.InformationRequestReviewAggregation
import com.docuhyphen.app.api.model.entity.InformationRequestReviewOutcome
import com.docuhyphen.app.api.model.entity.InformationRequestReviewPolicy
import com.docuhyphen.app.api.model.entity.InformationRequestReviewState
import com.docuhyphen.app.api.model.informationrequest.SubmitInformationRequestPackageCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestCorrectionItemRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestCorrectionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestReviewRepository
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConditionPredicateRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConditionRuleRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateEvidencePolicyRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateGroupRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionRequest
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.InformationRequestContributorRole
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAttribute
import com.docuhyphen.app.api.model.entity.InformationRequestRequiredness
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.entity.InformationRequestResponseMode
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSubmissionProblemCode
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.fields.FieldOperator
import com.docuhyphen.app.api.service.fields.FieldValueEntry
import com.docuhyphen.app.api.service.fields.FieldsPrecondition
import com.docuhyphen.app.api.service.informationrequest.AddInformationRequestGroupOccurrenceCommand
import com.docuhyphen.app.api.service.informationrequest.InformationRequestConditionEvaluationService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestConditionEvaluationState
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestGroupOccurrenceService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestResponsePatch
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeServices
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.InformationRequestStructuredResponseValidationContext
import com.docuhyphen.app.api.service.informationrequest.InformationRequestStructuredResponseValidationIssue
import com.docuhyphen.app.api.service.informationrequest.InformationRequestStructuredResponseValidationKind
import com.docuhyphen.app.api.service.informationrequest.InformationRequestStructuredResponseValidationService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestStructuredResponseValidator
import com.docuhyphen.app.api.service.informationrequest.InformationRequestTemplateConfigurationWriter
import com.docuhyphen.app.api.service.informationrequest.InformationRequestTemplateMaterializer
import com.docuhyphen.app.api.service.informationrequest.PatchInformationRequestResponsesCommand
import com.docuhyphen.app.api.service.informationrequest.RemoveInformationRequestGroupOccurrenceCommand
import com.docuhyphen.app.api.service.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.service.informationrequest.ResponseFieldValuesPatch
import com.docuhyphen.app.api.service.informationrequest.WalkingSkeletonField
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class RepeatableConditionalRequestConformanceTest
{
    @Inject lateinit var dataSource: DataSource
    @Inject lateinit var runtime: InformationRequestRuntimeTestServices
    @Inject lateinit var requestRepository: InformationRequestRepository
    @Inject lateinit var requirementRepository: InformationRequestRequirementRepository
    @Inject lateinit var definitionRepository: InformationRequestTemplateDefinitionRepository
    @Inject lateinit var versionRepository: InformationRequestTemplateVersionRepository
    @Inject lateinit var capabilityRepository: InformationRequestTemplateVersionCapabilityRepository
    @Inject lateinit var configurationWriter: InformationRequestTemplateConfigurationWriter
    @Inject lateinit var materializer: InformationRequestTemplateMaterializer
    @Inject lateinit var occurrences: InformationRequestGroupOccurrenceService
    @Inject lateinit var conditions: InformationRequestConditionEvaluationService
    @Inject lateinit var authorizationService: AuthorizationService
    @Inject lateinit var reviewRepository: InformationRequestReviewRepository
    @Inject lateinit var correctionRepository: InformationRequestCorrectionRepository
    @Inject lateinit var correctionItemRepository: InformationRequestCorrectionItemRepository

    private val reviews by lazy { ConformanceReviewSupport(runtime, reviewRepository) }

    private val published by lazy {
        PublishedRequestSupport(
            dataSource, requestRepository, requirementRepository, definitionRepository, versionRepository, capabilityRepository,
            configurationWriter, materializer,
        )
    }

    @Test
    fun `nested occurrences keep stable paths, independent values, per-occurrence conditions, cross-occurrence validation, and occurrence-scoped evidence`()
    {
        val scenario = scenario()
        val services = runtime.build(
            scenario.requestId,
            centralAuthorization = authorizationService,
            responseValidation = InformationRequestStructuredResponseValidationService(listOf(DistinctEntryLabels(scenario.label.fieldContractId))),
        )

        val first = addOccurrence(scenario, ENTRY, null, "add-first")
        val second = addOccurrence(scenario, ENTRY, null, "add-second")
        assertNotEquals(first.path, second.path)
        val nested = addOccurrence(scenario, DETAIL, first.id, "add-nested")
        assertTrue(nested.path.startsWith(first.path), "${nested.path} is nested under ${first.path}")
        val secondNested = addOccurrence(scenario, DETAIL, second.id, "add-second-nested")

        assertEquals(InformationRequestConditionEvaluationState.UNKNOWN, detailCondition(scenario, nested.path))
        assertEquals(InformationRequestConditionEvaluationState.UNKNOWN, detailCondition(scenario, secondNested.path))

        val duplicate = assertThrows(InformationRequestLifecycleException::class.java) {
            patchLabels(services, scenario, mapOf(first.path to "same", second.path to "same"), "duplicate-labels")
        }
        assertEquals(InformationRequestErrorCatalog.STRUCTURED_RESPONSE_VALIDATION_FAILED, duplicate.reasonCode)
        assertEquals(0, storedLabels(scenario).size)

        patchLabels(services, scenario, mapOf(first.path to DETAILED, second.path to "brief"), "distinct-labels")
        assertEquals(setOf("$DETAILED@${first.path}", "brief@${second.path}"), storedLabels(scenario))
        assertEquals(InformationRequestConditionEvaluationState.TRUE, detailCondition(scenario, nested.path))
        assertEquals(InformationRequestConditionEvaluationState.FALSE, detailCondition(scenario, secondNested.path))

        val recordsNeedingEvidence = missingRecords(services, scenario)
        assertEquals(setOf(first.path, second.path), recordsNeedingEvidence)
        provideRecord(services, scenario, first.path)
        assertEquals(setOf(second.path), missingRecords(services, scenario))

        QuarkusTransaction.requiringNew().run {
            occurrences.remove(
                RemoveInformationRequestGroupOccurrenceCommand(
                    requestId = scenario.requestId,
                    access = scenario.contributor,
                    precondition = CommandPrecondition.ExpectedRevision(responseETag(scenario)),
                    idempotencyKey = "remove-second",
                    occurrenceId = second.id,
                ),
            )
        }
        val third = addOccurrence(scenario, ENTRY, null, "add-third")
        assertFalse(third.path in setOf(first.path, second.path), "a removed occurrence path is never reused: ${third.path}")
        assertEquals(setOf(third.path), missingRecords(services, scenario))
        assertEquals(InformationRequestConditionEvaluationState.TRUE, detailCondition(scenario, nested.path))
        assertTrue("$DETAILED@${first.path}" in storedLabels(scenario))
    }

    @Test
    fun `a finding on one occurrence returns only that occurrence for correction`()
    {
        val scenario = scenario(reviewed = true)
        val services = runtime.build(scenario.requestId, centralAuthorization = authorizationService)
        val first = addOccurrence(scenario, ENTRY, null, "reviewed-first")
        val second = addOccurrence(scenario, ENTRY, null, "reviewed-second")
        patchLabels(services, scenario, mapOf(first.path to "brief", second.path to "short"), "reviewed-labels")
        provideRecord(services, scenario, first.path)
        provideRecord(services, scenario, second.path)
        val submitted = QuarkusTransaction.requiringNew().call {
            val stored = requireNotNull(requestRepository.findById(scenario.requestId))
            services.submissions.submit(
                SubmitInformationRequestPackageCommand(
                    requestId = scenario.requestId,
                    access = scenario.contributor,
                    precondition = CommandPrecondition.ExpectedRevision(
                        InformationRequestETag.submissionOf(null, runtime.contentCollector.collect(stored, null).contentHash),
                    ),
                    idempotencyKey = "reviewed-submit",
                ),
            ).submission.submissionPackage.id
        }
        val review = reviews.reviews(scenario.requestId).single()
        val assigned = reviews.assign(
            services, scenario.requestId, review.id, ENTRY_REVIEW, scenario.published.parties.getValue("REVIEWER"), scenario.published.owner,
            "reviewed-assign",
        )
        val firstLabel = requirement(scenario, LABEL_KEY, first.path).id
        val firstItem = reviews.item(scenario.requestId, submitted, firstLabel)
        val finding = reviews.finding(
            services, scenario.requestId, review.id, firstItem, InformationRequestFindingSeverity.MAJOR,
            InformationRequestFindingCorrectionScope.RESPONSE, scenario.published.access("REVIEWER"), "reviewed-finding",
        )
        assertEquals(
            first.path,
            QuarkusTransaction.requiringNew().call {
                runtime.packageReader.view(scenario.requestId, submitted).items.single { it.id == finding.submissionItemId }.occurrencePath
            },
        )
        val returned = reviews.decide(
            services, scenario.requestId, assigned,
            reviews.undecided(review.id, ENTRY_REVIEW).associateWith { item ->
                if (item == firstItem) InformationRequestReviewOutcome.CHANGES_REQUIRED else InformationRequestReviewOutcome.SATISFIED
            },
            scenario.published.access("REVIEWER"), "reviewed-decide",
        )
        assertEquals(InformationRequestReviewState.CHANGES_REQUESTED, returned.review.state)
        QuarkusTransaction.requiringNew().run {
            val correction = correctionRepository.findForRequest(scenario.requestId).single()
            assertEquals(listOf(firstLabel), correctionItemRepository.findForCorrections(listOf(correction.id)).map { it.requirementId })
        }
        val refused = assertThrows(InformationRequestLifecycleException::class.java) {
            patchLabels(services, scenario, mapOf(second.path to "changed"), "reviewed-outside")
        }
        assertEquals(InformationRequestErrorCatalog.CORRECTION_SCOPE_DENIED, refused.reasonCode)
        patchLabels(services, scenario, mapOf(first.path to "corrected"), "reviewed-inside")
        assertTrue("corrected@${first.path}" in storedLabels(scenario))
        assertTrue("short@${second.path}" in storedLabels(scenario))
    }

    private fun addOccurrence(scenario: Scenario, groupKey: String, parent: UUID?, key: String): Occurrence =
        QuarkusTransaction.requiringNew().call {
            val result = occurrences.add(
                AddInformationRequestGroupOccurrenceCommand(
                    requestId = scenario.requestId,
                    access = scenario.contributor,
                    precondition = CommandPrecondition.ExpectedRevision(responseETag(scenario)),
                    idempotencyKey = key,
                    groupKey = groupKey,
                    parentOccurrenceId = parent,
                ),
            )
            val added = result.occurrences.single { it.removedAt == null && it.id !in scenario.seen }
            scenario.seen += added.id
            Occurrence(added.id, added.occurrencePath)
        }

    private fun patchLabels(services: InformationRequestRuntimeServices, scenario: Scenario, labels: Map<String, String>, key: String) =
        QuarkusTransaction.requiringNew().call {
            services.responses.patch(
                PatchInformationRequestResponsesCommand(
                    requestId = scenario.requestId,
                    access = scenario.contributor,
                    precondition = CommandPrecondition.ExpectedRevision(responseETag(scenario)),
                    idempotencyKey = key,
                    patches = labels.map { (path, label) ->
                        InformationRequestResponsePatch(
                            requirementId = requirement(scenario, LABEL_KEY, path).id,
                            disposition = InformationRequestResponseDisposition.PROVIDED,
                            fieldValues = ResponseFieldValuesPatch(
                                entries = listOf(FieldValueEntry(scenario.label.fieldContractId, JsonPrimitive(label))),
                                precondition = FieldsPrecondition.Unconditioned,
                            ),
                        )
                    },
                ),
            )
        }

    private fun provideRecord(services: InformationRequestRuntimeServices, scenario: Scenario, path: String)
    {
        val record = requirement(scenario, RECORD_KEY, path)
        QuarkusTransaction.requiringNew().call {
            services.responses.patch(
                PatchInformationRequestResponsesCommand(
                    requestId = scenario.requestId,
                    access = scenario.contributor,
                    precondition = CommandPrecondition.ExpectedRevision(responseETag(scenario)),
                    idempotencyKey = "provide-record-$path",
                    patches = listOf(InformationRequestResponsePatch(requirementId = record.id, disposition = InformationRequestResponseDisposition.PROVIDED)),
                ),
            )
        }
        dataSource.connection.use { connection -> ConformanceEvidenceSql.insertConformingFile(connection, scenario.requestId, record.id, scenario.contributorId) }
    }

    private fun missingRecords(services: InformationRequestRuntimeServices, scenario: Scenario): Set<String> =
        QuarkusTransaction.requiringNew().call {
            services.submissionQueries.preview(scenario.requestId, null, scenario.contributor).readiness.problems
                .filter { it.requirementKey == RECORD_KEY && it.code == InformationRequestSubmissionProblemCode.REQUIREMENT_INCOMPLETE }
                .map { it.occurrencePath }
                .toSet()
        }

    private fun detailCondition(scenario: Scenario, path: String): InformationRequestConditionEvaluationState =
        QuarkusTransaction.requiringNew().call {
            conditions.evaluate(scenario.requestId).single { it.ruleKey == DETAIL_RULE && it.occurrencePath == path }.state
        }

    private fun storedLabels(scenario: Scenario): Set<String> =
        dataSource.connection.use { connection ->
            queryStrings(
                connection,
                """
                SELECT value.text_value || '@' || value_set.occurrence_path
                FROM field_value value
                JOIN field_value_set value_set ON value_set.id = value.field_value_set_id
                WHERE value.resource_type = 'INFORMATION_REQUEST' AND value.resource_id = ? AND value.field_contract_id = ?
                """.trimIndent(),
                scenario.requestId,
                scenario.label.fieldContractId,
            )
        }

    private fun requirement(scenario: Scenario, key: String, path: String): InformationRequestRequirement =
        QuarkusTransaction.requiringNew().call {
            val templateRequirementIds = dataSource.connection.use { connection ->
                queryStrings(
                    connection,
                    "SELECT id::text FROM information_request_template_requirement WHERE template_definition_id = ? AND requirement_key = ?",
                    scenario.definitionId,
                    key,
                )
            }.map(UUID::fromString).toSet()
            requirementRepository.findForRequest(scenario.requestId)
                .single { it.sourceTemplateRequirementId in templateRequirementIds && it.occurrencePath == path }
        }

    private fun responseETag(scenario: Scenario): String =
        QuarkusTransaction.requiringNew().call { InformationRequestETag.responsesOf(requireNotNull(requestRepository.findById(scenario.requestId))) }

    private fun scenario(reviewed: Boolean = false): Scenario
    {
        val parties = if (reviewed) listOf("CONTRIBUTOR", "REVIEWER") else listOf("CONTRIBUTOR")
        val issued = published.issue("repeatable-conditional-request", listOf(LABEL_KEY, "entry-detail"), parties) { schemaVersionId, fields ->
            configuration(schemaVersionId, fields.getValue(LABEL_KEY), fields.getValue("entry-detail"), reviewed)
        }
        return Scenario(
            requestId = issued.requestId,
            definitionId = issued.definitionId,
            contributorId = issued.users.getValue("CONTRIBUTOR"),
            contributor = issued.access("CONTRIBUTOR"),
            label = issued.fields.getValue(LABEL_KEY),
            published = issued,
        )
    }

    private fun configuration(schemaVersionId: UUID, label: WalkingSkeletonField, detail: WalkingSkeletonField, reviewed: Boolean) =
        InformationRequestTemplateConfigurationRequest(
            schemaVersionId = schemaVersionId,
            reviewStages = if (!reviewed) emptyList() else listOf(
                InformationRequestTemplateReviewStageRequest(
                    stageKey = ENTRY_REVIEW,
                    title = "Entry review",
                    aggregation = InformationRequestReviewAggregation.ANY,
                    minimumReviewerCount = 1,
                ),
            ),
            groups = listOf(
                InformationRequestTemplateGroupRequest(groupKey = ENTRY, maxOccurrences = 3),
                InformationRequestTemplateGroupRequest(groupKey = DETAIL, parentGroupKey = ENTRY, maxOccurrences = 2),
            ),
            conditionRules = listOf(
                InformationRequestTemplateConditionRuleRequest(
                    ruleKey = DETAIL_RULE,
                    predicates = listOf(
                        InformationRequestTemplateConditionPredicateRequest(
                            fieldDefinitionId = label.fieldDefinitionId,
                            valueType = FieldValueType.SHORT_TEXT,
                            operator = FieldOperator.EQUALS,
                            value = JsonPrimitive(DETAILED),
                        ),
                    ),
                ),
            ),
            sections = listOf(
                InformationRequestTemplateSectionRequest(
                    sectionKey = "entries",
                    title = "Entries",
                    requirements = listOf(
                        InformationRequestTemplateRequirementRequest(
                            requirementKey = LABEL_KEY,
                            requirementType = InformationRequestRequirementType.FIELD,
                            prompt = "Name the entry",
                            responseMode = InformationRequestResponseMode.PROVIDE,
                            requiredness = InformationRequestRequiredness.REQUIRED,
                            contributorRole = InformationRequestContributorRole.CONTRIBUTOR,
                            occurrenceAnchorKey = ENTRY,
                            reviewPolicy = if (reviewed) InformationRequestReviewPolicy.REQUIRED else InformationRequestReviewPolicy.NOT_REQUIRED,
                            collectedFieldDefinitionId = label.fieldDefinitionId,
                            permittedDispositions = listOf(InformationRequestResponseDisposition.PROVIDED),
                        ),
                        InformationRequestTemplateRequirementRequest(
                            requirementKey = "entry-detail",
                            requirementType = InformationRequestRequirementType.FIELD,
                            prompt = "Describe the entry detail",
                            responseMode = InformationRequestResponseMode.PROVIDE,
                            requiredness = InformationRequestRequiredness.CONDITIONAL,
                            contributorRole = InformationRequestContributorRole.CONTRIBUTOR,
                            conditionalRuleKey = DETAIL_RULE,
                            occurrenceAnchorKey = DETAIL,
                            collectedFieldDefinitionId = detail.fieldDefinitionId,
                            permittedDispositions = listOf(InformationRequestResponseDisposition.PROVIDED),
                        ),
                        InformationRequestTemplateRequirementRequest(
                            requirementKey = RECORD_KEY,
                            requirementType = InformationRequestRequirementType.DOCUMENT,
                            prompt = "Provide the entry record",
                            responseMode = InformationRequestResponseMode.PROVIDE,
                            requiredness = InformationRequestRequiredness.REQUIRED,
                            contributorRole = InformationRequestContributorRole.CONTRIBUTOR,
                            occurrenceAnchorKey = ENTRY,
                            permittedDispositions = listOf(InformationRequestResponseDisposition.PROVIDED),
                            evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(
                                minimumFileCount = 1,
                                maximumFileCount = 2,
                                maximumFileSizeBytes = 1_000_000,
                                maximumTotalSizeBytes = 2_000_000,
                                acceptedValues = listOf(
                                    InformationRequestTemplateAcceptedValueRequest(InformationRequestEvidenceAttribute.CONTENT_TYPE, "application/pdf"),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

    private class DistinctEntryLabels(private val labelContractId: UUID) : InformationRequestStructuredResponseValidator
    {
        override val kinds = setOf(InformationRequestStructuredResponseValidationKind.DUPLICATE)

        override fun validate(context: InformationRequestStructuredResponseValidationContext): List<InformationRequestStructuredResponseValidationIssue> =
            context.patches
                .flatMap { patch -> patch.fieldValues?.entries.orEmpty().filter { it.fieldContractId == labelContractId }.map { it.value to patch.requirementId } }
                .groupBy({ it.first }, { it.second })
                .filterValues { it.size > 1 }
                .map { (value, requirements) ->
                    InformationRequestStructuredResponseValidationIssue(
                        kind = InformationRequestStructuredResponseValidationKind.DUPLICATE,
                        message = "Entry label $value is used by ${requirements.size} entries",
                        requirementId = requirements.first(),
                        fieldContractId = labelContractId,
                    )
                }
    }

    private data class Occurrence(val id: UUID, val path: String)

    private data class Scenario(
        val requestId: UUID,
        val definitionId: UUID,
        val contributorId: UUID,
        val contributor: RequestAccessContext,
        val label: WalkingSkeletonField,
        val published: PublishedRequest,
        val seen: MutableSet<UUID> = mutableSetOf(),
    )

    private companion object
    {
        const val ENTRY = "entry"
        const val DETAIL = "detail"
        const val DETAIL_RULE = "when-entry-is-detailed"
        const val DETAILED = "detailed"
        const val LABEL_KEY = "entry-label"
        const val RECORD_KEY = "entry-record"
        const val ENTRY_REVIEW = "entry-review"
    }
}
