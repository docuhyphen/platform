package com.docuhyphen.app.api.service.informationrequest.conformance

import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.dto.InformationRequestImportedValueReconciliationDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionRequest
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.FieldValueEntry
import com.docuhyphen.app.api.model.fields.FieldsPrecondition
import com.docuhyphen.app.api.model.informationrequest.amendment.AmendInformationRequestCommand
import com.docuhyphen.app.api.model.informationrequest.externalsource.*
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestResponsePatch
import com.docuhyphen.app.api.model.informationrequest.response.PatchInformationRequestResponsesCommand
import com.docuhyphen.app.api.model.informationrequest.response.ResponseFieldValuesPatch
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.resource.informationrequest.externalsource.InformationRequestImportedValueReconciliationResource
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.amendment.InformationRequestAmendmentService
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateWalkingSkeletonStore
import com.docuhyphen.app.api.service.informationrequest.capability.model.WalkingSkeletonDraft
import com.docuhyphen.app.api.service.informationrequest.capability.model.WalkingSkeletonField
import com.docuhyphen.app.api.service.informationrequest.conformance.model.PublishedRequest
import com.docuhyphen.app.api.service.informationrequest.externalsource.InformationRequestImportedValueDecisionService
import com.docuhyphen.app.api.service.informationrequest.externalsource.InformationRequestImportedValueIntakeService
import com.docuhyphen.app.api.service.informationrequest.externalsource.InformationRequestImportedValueReconciliationService
import com.docuhyphen.app.api.service.informationrequest.externalsource.InformationRequestImportedValueRecords
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseDraftService
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationWriter
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateMaterializer
import com.docuhyphen.app.api.service.subscription.PlanCode
import com.docuhyphen.app.api.service.subscription.SubscriptionPolicyService
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestReboundExternalValueTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var requests: InformationRequestRepository

    @Inject
    lateinit var requirements: InformationRequestRequirementRepository

    @Inject
    lateinit var definitions: InformationRequestTemplateDefinitionRepository

    @Inject
    lateinit var versions: InformationRequestTemplateVersionRepository

    @Inject
    lateinit var capabilities: InformationRequestTemplateVersionCapabilityRepository

    @Inject
    lateinit var writer: InformationRequestTemplateConfigurationWriter

    @Inject
    lateinit var materializer: InformationRequestTemplateMaterializer

    @Inject
    lateinit var amendments: InformationRequestAmendmentService

    @Inject
    lateinit var responses: InformationRequestResponseDraftService

    @Inject
    lateinit var intake: InformationRequestImportedValueIntakeService

    @Inject
    lateinit var decisions: InformationRequestImportedValueDecisionService

    @Inject
    lateinit var reconciliation: InformationRequestImportedValueReconciliationService

    @Inject
    lateinit var records: InformationRequestImportedValueRecords

    @Inject
    lateinit var subscriptions: SubscriptionPolicyService

    private val support by lazy {
        PublishedRequestSupport(
            dataSource,
            requests,
            requirements,
            definitions,
            versions,
            capabilities,
            writer,
            materializer
        )
    }
    private val store by lazy {
        InformationRequestTemplateWalkingSkeletonStore(
            dataSource,
            definitions,
            versions,
            capabilities,
            writer
        )
    }

    @Test
    fun `a value recorded for the earlier Field is kept apart while values for current Fields reconcile`() {
        val fixture = issue()
        val earlier = propose(fixture, SUMMARY, FieldValueType.SHORT_TEXT, JsonPrimitive("Original source text"))
        amend(fixture, collected = SUMMARY_COUNT)
        answer(fixture, SUMMARY, SUMMARY_COUNT, JsonPrimitive(7))
        answer(fixture, OTHER, OTHER_TEXT, JsonPrimitive("Independent value"))
        val independent = propose(fixture, OTHER, FieldValueType.SHORT_TEXT, JsonPrimitive("Independent value"))
        val current = propose(fixture, SUMMARY, FieldValueType.INTEGER, JsonPrimitive(8))

        val results =
            assertDoesNotThrow<List<InformationRequestImportedValueReconciliation>> { reconcile(fixture, "mixed") }

        assertEquals(
            mapOf(
                earlier to InformationRequestReconciliationOutcome.FIELD_CHANGED,
                independent to InformationRequestReconciliationOutcome.MATCHES,
                current to InformationRequestReconciliationOutcome.DIFFERS,
            ),
            results.associate { it.importedValueId to it.outcome },
        )
        assertNull(results.single { it.importedValueId == earlier }.discrepancyId)
        assertEquals(1, discrepancies(fixture))
        val kept = QuarkusTransaction.requiringNew().call {
            records.values(fixture.request.requestId, fixture.request.access(REVIEWER))
        }.single { it.value.id == earlier }
        assertEquals(FieldValueType.SHORT_TEXT, kept.value.valueType)
        assertEquals("\"Original source text\"", kept.value.canonicalValue)
        assertEquals("source-system", kept.value.sourceReference)
        assertEquals("source-record-reference", kept.value.provenanceReference)
        assertTrue(kept.discrepancies.isEmpty())
        assertEquals("7", answerOf(fixture, SUMMARY_COUNT))
    }

    @Test
    fun `the reconciliation REST adapter answers after a Field rebinding with an earlier value`() {
        val fixture = issue()
        val earlier = propose(fixture, SUMMARY, FieldValueType.SHORT_TEXT, JsonPrimitive("Original source text"))
        amend(fixture, collected = SUMMARY_COUNT)
        answer(fixture, SUMMARY, SUMMARY_COUNT, JsonPrimitive(7))
        val accessContexts = mock<InformationRequestAccessContextFactory>()
        whenever(accessContexts.currentAuthenticated()).thenReturn(fixture.request.access(REVIEWER))

        val response = InformationRequestImportedValueReconciliationResource(reconciliation, accessContexts)
            .reconcile(fixture.request.requestId.toString(), "rebound-field-http")

        assertEquals(200, response.status)
        @Suppress("UNCHECKED_CAST")
        val body = response.entity as Array<InformationRequestImportedValueReconciliationDto>
        assertEquals(
            listOf(earlier to InformationRequestReconciliationOutcome.FIELD_CHANGED),
            body.map { it.importedValueId to it.outcome },
        )
    }

    @Test
    fun `an earlier value rejected before the Field changed keeps its decision and stays out of reconciliation`() {
        val fixture = issue()
        val earlier = propose(fixture, SUMMARY, FieldValueType.SHORT_TEXT, JsonPrimitive("Original source text"))
        QuarkusTransaction.requiringNew().run {
            decisions.decide(
                DecideInformationRequestImportedValueCommand(
                    fixture.request.requestId, earlier, InformationRequestImportedValueDecisionKind.REJECTED,
                    "earlier-source-not-used", fixture.request.access(REVIEWER), UUID.randomUUID().toString(),
                ),
            )
        }
        amend(fixture, collected = SUMMARY_COUNT)
        answer(fixture, SUMMARY, SUMMARY_COUNT, JsonPrimitive(7))
        val current = propose(fixture, SUMMARY, FieldValueType.INTEGER, JsonPrimitive(7))

        assertEquals(
            listOf(current to InformationRequestReconciliationOutcome.MATCHES),
            outcomes(reconcile(fixture, "after-rejection")),
        )
        val kept = QuarkusTransaction.requiringNew().call {
            records.values(fixture.request.requestId, fixture.request.access(REVIEWER))
        }.single { it.value.id == earlier }
        assertEquals(InformationRequestImportedValueDecisionKind.REJECTED, kept.decision?.decision)
    }

    @Test
    fun `a value recorded for the newly collected Field reconciles normally`() {
        val fixture = issue()
        amend(fixture, collected = SUMMARY_COUNT)
        answer(fixture, SUMMARY, SUMMARY_COUNT, JsonPrimitive(7))
        val current = propose(fixture, SUMMARY, FieldValueType.INTEGER, JsonPrimitive(7))

        assertEquals(
            listOf(current to InformationRequestReconciliationOutcome.MATCHES),
            outcomes(reconcile(fixture, "new-field")),
        )
    }

    @Test
    fun `a reconciliation recorded before the Field changed replays its stored outcomes afterwards`() {
        val fixture = issue()
        answer(fixture, SUMMARY, SUMMARY_TEXT, JsonPrimitive("Original source text"))
        val earlier = propose(fixture, SUMMARY, FieldValueType.SHORT_TEXT, JsonPrimitive("Original source text"))
        val recorded = outcomes(reconcile(fixture, "before-rebinding"))

        amend(fixture, collected = SUMMARY_COUNT)

        assertEquals(listOf(earlier to InformationRequestReconciliationOutcome.MATCHES), recorded)
        assertEquals(recorded, outcomes(reconcile(fixture, "before-rebinding")))
        assertEquals(
            listOf(earlier to InformationRequestReconciliationOutcome.FIELD_CHANGED),
            outcomes(reconcile(fixture, "after-rebinding")),
        )
    }

    @Test
    fun `an amendment that keeps the collected Field keeps comparing values recorded before it`() {
        val fixture = issue()
        val earlier = propose(fixture, SUMMARY, FieldValueType.SHORT_TEXT, JsonPrimitive("Original source text"))
        val recordedRevision = recordedRevisionOf(earlier)

        amend(fixture, collected = SUMMARY_TEXT, prompt = "Provide the latest record value")
        answer(fixture, SUMMARY, SUMMARY_TEXT, JsonPrimitive("Original source text"))

        assertNotEquals(recordedRevision, currentRevisionOf(fixture, SUMMARY))
        assertEquals(
            listOf(earlier to InformationRequestReconciliationOutcome.MATCHES),
            outcomes(reconcile(fixture, "unchanged-field")),
        )
    }

    private class Fixture(val request: PublishedRequest, val schemaVersionId: UUID)

    private fun issue(): Fixture {
        var schemaVersionId: UUID? = null
        val request = support.issue(
            "rebound-external-value-${UUID.randomUUID().toString().take(8)}",
            listOf(SUMMARY_TEXT, SUMMARY_COUNT, OTHER_TEXT),
            listOf(CONTRIBUTOR, REVIEWER, DECISION_MAKER),
            fieldTypes = mapOf(SUMMARY_COUNT to FieldValueType.INTEGER),
        ) { schema, fields ->
            schemaVersionId = schema
            configuration(schema, fields, SUMMARY_TEXT, "Provide the current record value")
        }
        QuarkusTransaction.requiringNew().run { subscriptions.ensureUserPolicy(request.ownerId, PlanCode.PERSONAL) }
        return Fixture(request, requireNotNull(schemaVersionId))
    }

    private fun configuration(
        schemaVersionId: UUID,
        fields: Map<String, WalkingSkeletonField>,
        collected: String,
        prompt: String,
    ) = InformationRequestTemplateConfigurationRequest(
        schemaVersionId = schemaVersionId,
        sections = listOf(
            InformationRequestTemplateSectionRequest(
                "answers", "Answers",
                requirements = listOf(
                    InformationRequestTemplateRequirementRequest(
                        SUMMARY, InformationRequestRequirementType.FIELD, prompt,
                        collectedFieldDefinitionId = fields.getValue(collected).fieldDefinitionId,
                        permittedDispositions = listOf(InformationRequestResponseDisposition.PROVIDED),
                    ),
                    InformationRequestTemplateRequirementRequest(
                        OTHER, InformationRequestRequirementType.FIELD, "Provide the independent value",
                        collectedFieldDefinitionId = fields.getValue(OTHER_TEXT).fieldDefinitionId,
                        permittedDispositions = listOf(InformationRequestResponseDisposition.PROVIDED),
                    ),
                ),
            ),
        ),
    )

    private fun amend(fixture: Fixture, collected: String, prompt: String = "Provide the current record value") {
        val request = fixture.request
        val before = support.requirement(request, SUMMARY).id
        val draft = store.insertNextDraft(
            WalkingSkeletonDraft(
                request.definitionId,
                request.templateVersionId,
                request.ownerId,
                "rebound-external-value"
            ),
            2,
        )
        store.publish(draft, configuration(fixture.schemaVersionId, request.fields, collected, prompt))
        QuarkusTransaction.requiringNew().run {
            amendments.amend(
                AmendInformationRequestCommand(
                    requestId = request.requestId,
                    targetTemplateVersionId = draft.versionId,
                    reasonCode = "record-scope-revised",
                    access = request.access(DECISION_MAKER),
                    precondition = CommandPrecondition.ExpectedRevision(
                        InformationRequestETag.aggregateOf(requireNotNull(requests.findById(request.requestId))),
                    ),
                    idempotencyKey = UUID.randomUUID().toString(),
                ),
            )
        }
        assertEquals(before, support.requirement(request, SUMMARY).id)
    }

    private fun answer(fixture: Fixture, requirementKey: String, fieldKey: String, value: JsonElement) {
        val requirementId = support.requirement(fixture.request, requirementKey).id
        QuarkusTransaction.requiringNew().run {
            responses.patch(
                PatchInformationRequestResponsesCommand(
                    requestId = fixture.request.requestId,
                    access = fixture.request.access(CONTRIBUTOR),
                    precondition = CommandPrecondition.ExpectedRevision(
                        InformationRequestETag.responsesOf(requireNotNull(requests.findById(fixture.request.requestId))),
                    ),
                    idempotencyKey = UUID.randomUUID().toString(),
                    patches = listOf(
                        InformationRequestResponsePatch(
                            requirementId = requirementId,
                            disposition = InformationRequestResponseDisposition.PROVIDED,
                            fieldValues = ResponseFieldValuesPatch(
                                listOf(
                                    FieldValueEntry(
                                        fixture.request.fields.getValue(fieldKey).fieldContractId,
                                        value
                                    )
                                ),
                                FieldsPrecondition.Unconditioned,
                            ),
                        ),
                    ),
                ),
            )
        }
    }

    private fun propose(fixture: Fixture, requirementKey: String, type: FieldValueType, value: JsonElement): UUID =
        QuarkusTransaction.requiringNew().call {
            intake.propose(
                ProposeInformationRequestImportedValueCommand(
                    fixture.request.requestId,
                    support.requirement(fixture.request, requirementKey).id,
                    "recorded-value",
                    type,
                    value,
                    "source-system",
                    InformationRequestSourceConfidence.ASSERTED,
                    null,
                    null,
                    "source-record-reference",
                    fixture.request.owner,
                    UUID.randomUUID().toString(),
                ),
            ).value.id
        }

    private fun reconcile(
        fixture: Fixture,
        idempotencyKey: String
    ): List<InformationRequestImportedValueReconciliation> =
        QuarkusTransaction.requiringNew().call {
            reconciliation.reconcile(
                ReconcileInformationRequestImportedValuesCommand(
                    fixture.request.requestId, fixture.request.access(REVIEWER), idempotencyKey,
                ),
            )
        }

    private fun outcomes(results: List<InformationRequestImportedValueReconciliation>) =
        results.map { it.importedValueId to it.outcome }

    private fun discrepancies(fixture: Fixture): Int = dataSource.connection.use { connection ->
        queryInt(
            connection,
            "SELECT COUNT(*) FROM information_request_imported_value_discrepancy WHERE information_request_id = ?",
            fixture.request.requestId,
        )
    }

    private fun answerOf(fixture: Fixture, fieldKey: String): String? = dataSource.connection.use { connection ->
        queryString(
            connection,
            """
            SELECT COALESCE(text_value, TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM number_value::text)))
            FROM field_value
            WHERE resource_type = 'INFORMATION_REQUEST' AND resource_id = ? AND field_contract_id = ?
            """.trimIndent(),
            fixture.request.requestId,
            fixture.request.fields.getValue(fieldKey).fieldContractId,
        )
    }

    private fun recordedRevisionOf(importedValueId: UUID): String? = dataSource.connection.use { connection ->
        queryString(
            connection,
            "SELECT requirement_revision_id::text FROM information_request_imported_value WHERE id = ?",
            importedValueId,
        )
    }

    private fun currentRevisionOf(fixture: Fixture, requirementKey: String): String? =
        dataSource.connection.use { connection ->
            queryString(
                connection,
                "SELECT current_revision_id::text FROM information_request_requirement_current WHERE information_request_requirement_id = ?",
                support.requirement(fixture.request, requirementKey).id,
            )
        }

    private companion object {
        const val SUMMARY = "summary"
        const val OTHER = "other"
        const val SUMMARY_TEXT = "summary-text"
        const val SUMMARY_COUNT = "summary-count"
        const val OTHER_TEXT = "other-text"
        const val CONTRIBUTOR = "CONTRIBUTOR"
        const val REVIEWER = "REVIEWER"
        const val DECISION_MAKER = "DECISION_MAKER"
    }
}
