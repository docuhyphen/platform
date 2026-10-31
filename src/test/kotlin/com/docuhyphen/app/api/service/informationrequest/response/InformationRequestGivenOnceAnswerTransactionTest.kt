package com.docuhyphen.app.api.service.informationrequest.response

import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.FieldOperator
import com.docuhyphen.app.api.model.fields.FieldValueEntry
import com.docuhyphen.app.api.model.fields.FieldsPrecondition
import com.docuhyphen.app.api.model.informationrequest.occurrence.RemoveInformationRequestGroupOccurrenceCommand
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestResponsePatch
import com.docuhyphen.app.api.model.informationrequest.response.PatchInformationRequestResponsesCommand
import com.docuhyphen.app.api.model.informationrequest.response.ResponseFieldValuesPatch
import com.docuhyphen.app.api.model.informationrequest.submission.SubmitInformationRequestPackageCommand
import com.docuhyphen.app.api.model.informationrequest.submission.WithdrawInformationRequestPackageCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestItemRefusalException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.conformance.PublishedRequestSupport
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestGroupOccurrenceService
import com.docuhyphen.app.api.service.informationrequest.response.model.GivenOnceAnswerScenario
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationWriter
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateMaterializer
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestGivenOnceAnswerTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var requirementRepository: InformationRequestRequirementRepository

    @Inject
    lateinit var definitionRepository: InformationRequestTemplateDefinitionRepository

    @Inject
    lateinit var versionRepository: InformationRequestTemplateVersionRepository

    @Inject
    lateinit var capabilityRepository: InformationRequestTemplateVersionCapabilityRepository

    @Inject
    lateinit var configurationWriter: InformationRequestTemplateConfigurationWriter

    @Inject
    lateinit var materializer: InformationRequestTemplateMaterializer

    @Inject
    lateinit var occurrences: InformationRequestGroupOccurrenceService

    @Inject
    lateinit var authorizationService: AuthorizationService

    private val published by lazy {
        PublishedRequestSupport(
            dataSource,
            requestRepository,
            requirementRepository,
            definitionRepository,
            versionRepository,
            capabilityRepository,
            configurationWriter,
            materializer,
        )
    }

    @Test
    fun `changing another answer cannot clear a Field answer given once after its submission is withdrawn`() {
        val scenario = conditional(InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION)
        answer(scenario, CONTROLLING, ROOT, APPLIES, "show-dependent")
        answer(scenario, DEPENDENT, ROOT, FIXED, "answer-dependent")
        withdraw(scenario, submit(scenario))

        val dependent = published.requirement(scenario.request, DEPENDENT).id
        val refusal = assertThrows(InformationRequestItemRefusalException::class.java) {
            answer(scenario, CONTROLLING, ROOT, "no longer applies", "hide-dependent", setOf(dependent))
        }

        assertEquals(InformationRequestErrorCatalog.SUBMISSION_LOCKED, refusal.reasonCode)
        assertEquals(dependent, refusal.problems.single().requirementId)
        assertEquals(FIXED, valueOf(scenario, DEPENDENT))
        assertEquals(APPLIES, valueOf(scenario, CONTROLLING))
    }

    @Test
    fun `changing another answer keeps a Field answer given once hidden instead of archiving it after its submission is withdrawn`() {
        val scenario = conditional(InformationRequestConditionHiddenDataPolicy.ARCHIVE_OUTSIDE_ACTIVE_RESPONSE)
        answer(scenario, CONTROLLING, ROOT, APPLIES, "show-dependent")
        answer(scenario, DEPENDENT, ROOT, FIXED, "answer-dependent")
        withdraw(scenario, submit(scenario))

        answer(scenario, CONTROLLING, ROOT, "no longer applies", "hide-dependent")

        assertEquals("no longer applies", valueOf(scenario, CONTROLLING))
        assertEquals(FIXED, valueOf(scenario, DEPENDENT))
        assertEquals("RETAIN_SECURELY|false", hiddenStateOf(scenario, DEPENDENT))
    }

    @Test
    fun `an entry holding an answer given once is not removed after its submission is withdrawn`() {
        val scenario = repeated(InformationRequestResponseMode.PROVIDE_ONCE)
        val first =
            published.addOccurrence(occurrences, scenario.request, scenario.contributor, ENTRY, null, "add-first")
        val second =
            published.addOccurrence(occurrences, scenario.request, scenario.contributor, ENTRY, null, "add-second")
        answer(scenario, LABEL, first.occurrencePath, "first entry", "label-first")
        answer(scenario, LABEL, second.occurrencePath, "second entry", "label-second")
        withdraw(scenario, submit(scenario))

        val refusal = assertThrows(InformationRequestItemRefusalException::class.java) {
            remove(scenario, second.id, "remove-second")
        }

        assertEquals(InformationRequestErrorCatalog.SUBMISSION_LOCKED, refusal.reasonCode)
        assertEquals(
            published.requirement(scenario.request, LABEL, second.occurrencePath).id,
            refusal.problems.single().requirementId,
        )
        assertNull(removedAt(second.id))
    }

    @Test
    fun `an entry whose answers may change can be removed after its submission is withdrawn`() {
        val scenario = repeated(InformationRequestResponseMode.PROVIDE)
        val first =
            published.addOccurrence(occurrences, scenario.request, scenario.contributor, ENTRY, null, "add-first")
        val second =
            published.addOccurrence(occurrences, scenario.request, scenario.contributor, ENTRY, null, "add-second")
        answer(scenario, LABEL, first.occurrencePath, "first entry", "label-first")
        answer(scenario, LABEL, second.occurrencePath, "second entry", "label-second")
        withdraw(scenario, submit(scenario))

        remove(scenario, second.id, "remove-second")

        assertNotNull(removedAt(second.id))
    }

    private fun conditional(hiddenDataPolicy: InformationRequestConditionHiddenDataPolicy): GivenOnceAnswerScenario =
        scenario("given-once-condition", listOf(CONTROLLING, DEPENDENT)) { schemaVersionId, fields ->
            InformationRequestTemplateConfigurationRequest(
                schemaVersionId = schemaVersionId,
                reviewStages = listOf(REVIEW_STAGE),
                conditionRules = listOf(
                    InformationRequestTemplateConditionRuleRequest(
                        ruleKey = RULE,
                        hiddenDataPolicy = hiddenDataPolicy,
                        predicates = listOf(
                            InformationRequestTemplateConditionPredicateRequest(
                                fieldDefinitionId = fields.getValue(CONTROLLING),
                                valueType = FieldValueType.SHORT_TEXT,
                                operator = FieldOperator.EQUALS,
                                value = JsonPrimitive(APPLIES),
                            ),
                        ),
                    ),
                ),
                sections = listOf(
                    InformationRequestTemplateSectionRequest(
                        sectionKey = "answers",
                        title = "Answers",
                        requirements = listOf(
                            fieldRequirement(
                                CONTROLLING,
                                fields.getValue(CONTROLLING),
                                InformationRequestResponseMode.PROVIDE
                            ),
                            fieldRequirement(
                                DEPENDENT,
                                fields.getValue(DEPENDENT),
                                InformationRequestResponseMode.PROVIDE_ONCE
                            )
                                .copy(
                                    requiredness = InformationRequestRequiredness.CONDITIONAL,
                                    conditionalRuleKey = RULE
                                ),
                        ),
                    ),
                ),
            )
        }

    private fun repeated(mode: InformationRequestResponseMode): GivenOnceAnswerScenario =
        scenario("given-once-entries", listOf(LABEL)) { schemaVersionId, fields ->
            InformationRequestTemplateConfigurationRequest(
                schemaVersionId = schemaVersionId,
                reviewStages = listOf(REVIEW_STAGE),
                groups = listOf(InformationRequestTemplateGroupRequest(groupKey = ENTRY, maxOccurrences = 3)),
                sections = listOf(
                    InformationRequestTemplateSectionRequest(
                        sectionKey = "entries",
                        title = "Entries",
                        requirements = listOf(
                            fieldRequirement(LABEL, fields.getValue(LABEL), mode).copy(
                                occurrenceAnchorKey = ENTRY
                            )
                        ),
                    ),
                ),
            )
        }

    private fun scenario(
        templateKey: String,
        fieldKeys: List<String>,
        configuration: (UUID, Map<String, UUID>) -> InformationRequestTemplateConfigurationRequest,
    ): GivenOnceAnswerScenario {
        val request = published.issue(templateKey, fieldKeys, listOf(CONTRIBUTOR)) { schemaVersionId, fields ->
            configuration(schemaVersionId, fields.mapValues { it.value.fieldDefinitionId })
        }
        return GivenOnceAnswerScenario(
            request,
            runtime.build(request.requestId, centralAuthorization = authorizationService)
        )
    }

    private fun fieldRequirement(key: String, fieldDefinitionId: UUID, mode: InformationRequestResponseMode) =
        InformationRequestTemplateRequirementRequest(
            requirementKey = key,
            requirementType = InformationRequestRequirementType.FIELD,
            prompt = "State the $key",
            responseMode = mode,
            requiredness = InformationRequestRequiredness.REQUIRED,
            contributorRole = InformationRequestContributorRole.CONTRIBUTOR,
            reviewPolicy = InformationRequestReviewPolicy.REQUIRED,
            collectedFieldDefinitionId = fieldDefinitionId,
            permittedDispositions = listOf(InformationRequestResponseDisposition.PROVIDED),
        )

    private fun answer(
        scenario: GivenOnceAnswerScenario,
        key: String,
        path: String,
        value: String,
        idempotencyKey: String,
        confirmedClears: Set<UUID> = emptySet(),
    ) = QuarkusTransaction.requiringNew().call {
        scenario.services.responses.patch(
            PatchInformationRequestResponsesCommand(
                requestId = scenario.request.requestId,
                access = scenario.contributor,
                precondition = CommandPrecondition.ExpectedRevision(published.responseETag(scenario.request)),
                idempotencyKey = idempotencyKey,
                patches = listOf(
                    InformationRequestResponsePatch(
                        requirementId = published.requirement(scenario.request, key, path).id,
                        disposition = InformationRequestResponseDisposition.PROVIDED,
                        fieldValues = ResponseFieldValuesPatch(
                            entries = listOf(
                                FieldValueEntry(
                                    scenario.request.fields.getValue(key).fieldContractId,
                                    JsonPrimitive(value)
                                )
                            ),
                            precondition = FieldsPrecondition.Unconditioned,
                        ),
                    ),
                ),
                confirmedHiddenResponseClears = confirmedClears,
            ),
        )
    }

    private fun submit(scenario: GivenOnceAnswerScenario): UUID =
        QuarkusTransaction.requiringNew().call {
            val stored = requireNotNull(requestRepository.findById(scenario.request.requestId))
            scenario.services.submissions.submit(
                SubmitInformationRequestPackageCommand(
                    requestId = scenario.request.requestId,
                    access = scenario.contributor,
                    precondition = CommandPrecondition.ExpectedRevision(
                        InformationRequestETag.submissionOf(
                            null,
                            runtime.contentCollector.collect(stored, null).contentHash
                        ),
                    ),
                    idempotencyKey = "submit-${UUID.randomUUID()}",
                ),
            ).submission.submissionPackage.id
        }

    private fun withdraw(scenario: GivenOnceAnswerScenario, packageId: UUID) {
        QuarkusTransaction.requiringNew().run {
            scenario.services.submissions.withdraw(
                WithdrawInformationRequestPackageCommand(
                    requestId = scenario.request.requestId,
                    packageId = packageId,
                    reasonCode = "answer-revisited",
                    access = scenario.contributor,
                    precondition = CommandPrecondition.ExpectedRevision(published.responseETag(scenario.request)),
                    idempotencyKey = "withdraw-${UUID.randomUUID()}",
                ),
            )
        }
    }

    private fun remove(scenario: GivenOnceAnswerScenario, occurrenceId: UUID, key: String) =
        QuarkusTransaction.requiringNew().call {
            occurrences.remove(
                RemoveInformationRequestGroupOccurrenceCommand(
                    requestId = scenario.request.requestId,
                    access = scenario.contributor,
                    precondition = CommandPrecondition.ExpectedRevision(published.responseETag(scenario.request)),
                    idempotencyKey = key,
                    occurrenceId = occurrenceId,
                ),
            )
        }

    private fun valueOf(scenario: GivenOnceAnswerScenario, key: String): String? =
        dataSource.connection.use {
            queryString(
                it,
                "SELECT text_value FROM field_value WHERE resource_type = 'INFORMATION_REQUEST' AND resource_id = ? AND field_contract_id = ?",
                scenario.request.requestId,
                scenario.request.fields.getValue(key).fieldContractId,
            )
        }

    private fun hiddenStateOf(scenario: GivenOnceAnswerScenario, key: String): String? =
        dataSource.connection.use {
            queryString(
                it,
                """
                SELECT COALESCE(hidden_data_policy, '') || '|' || active_in_response
                FROM information_request_response
                WHERE information_request_requirement_id = ?
                ORDER BY response_revision DESC
                LIMIT 1
                """.trimIndent(),
                published.requirement(scenario.request, key).id,
            )
        }

    private fun removedAt(occurrenceId: UUID): String? =
        dataSource.connection.use {
            queryString(
                it,
                "SELECT removed_at::text FROM information_request_group_occurrence WHERE id = ?",
                occurrenceId
            )
        }

    private val GivenOnceAnswerScenario.contributor
        get() = request.access(CONTRIBUTOR)

    private companion object {
        const val CONTRIBUTOR = "CONTRIBUTOR"
        const val CONTROLLING = "controlling-answer"
        const val DEPENDENT = "dependent-answer"
        const val LABEL = "entry-label"
        const val ENTRY = "entry"
        const val RULE = "when-dependent-applies"
        const val ROOT = "root"
        const val APPLIES = "applies"
        const val FIXED = "fixed answer"
        val REVIEW_STAGE =
            InformationRequestTemplateReviewStageRequest(stageKey = "answer-review", title = "Answer review")
    }
}
