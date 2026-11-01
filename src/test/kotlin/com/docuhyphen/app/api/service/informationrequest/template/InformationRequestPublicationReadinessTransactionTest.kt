package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.FieldOperator
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.amendment.AmendInformationRequestCommand
import com.docuhyphen.app.api.model.informationrequest.creation.CreateAdHocInformationRequestCommand
import com.docuhyphen.app.api.model.informationrequest.template.InformationRequestTemplateMutationContext
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.amendment.InformationRequestAmendmentTargetResolver
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateWalkingSkeletonStore
import com.docuhyphen.app.api.service.informationrequest.capability.model.WalkingSkeletonField
import com.docuhyphen.app.api.service.informationrequest.creation.InformationRequestAdHocCreationService
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestPublicationReadinessTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var definitionRepository: InformationRequestTemplateDefinitionRepository

    @Inject
    lateinit var versionRepository: InformationRequestTemplateVersionRepository

    @Inject
    lateinit var capabilityRepository: InformationRequestTemplateVersionCapabilityRepository

    @Inject
    lateinit var configurationWriter: InformationRequestTemplateConfigurationWriter

    @Inject
    lateinit var projectionLoader: InformationRequestTemplateProjectionLoader

    @Inject
    lateinit var schemaCompatibility: InformationRequestTemplateSchemaCompatibility

    @Inject
    lateinit var freezer: InformationRequestTemplateVersionFreezer

    @Inject
    lateinit var commandReceiptService: CommandReceiptService

    @Inject
    lateinit var creationService: InformationRequestAdHocCreationService

    @Inject
    lateinit var amendmentTargets: InformationRequestAmendmentTargetResolver

    private val store by lazy {
        InformationRequestTemplateWalkingSkeletonStore(
            dataSource, definitionRepository, versionRepository, capabilityRepository, configurationWriter,
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["missing-schema", "missing-evidence-policy", "waiver-without-answer", "answer-without-waiver", "subject-contributor", "subject-confirmation"])
    fun `ad hoc readiness refusals name the item and roll back every creation record`(case: String) {
        val owner = store.insertOwner()
        val exchangeId = insertExchange(owner)
        val command = command(owner, exchangeId, configuration(invalidRequirement(case)), "invalid-$case")

        val refusal = assertThrows<InformationRequestTemplateValidationException> {
            creationService.createAdHoc(command)
        }

        assertEquals("requested-records", refusal.sectionKey)
        assertEquals("supporting-record", refusal.requirementKey)
        dataSource.connection.use { connection ->
            assertEquals(
                "0", queryString(
                    connection,
                    "SELECT count(*)::text FROM information_request WHERE exchange_id = ?", exchangeId
                )
            )
            assertEquals(
                "0", queryString(
                    connection,
                    "SELECT count(*)::text FROM information_request_template_definition WHERE scope_user_id = ?", owner
                )
            )
            assertEquals(
                "0", queryString(
                    connection,
                    "SELECT count(*)::text FROM command_receipt WHERE resource_id = ?", exchangeId
                )
            )
            assertEquals(
                "0", queryString(
                    connection,
                    "SELECT count(*)::text FROM information_request_transition t JOIN information_request r ON r.id = t.information_request_id WHERE r.exchange_id = ?",
                    exchangeId
                )
            )
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `valid typed collection and document collection still create private pinned requests`(typed: Boolean) {
        val owner = store.insertOwner()
        val field = store.insertTextField("recorded-note")
        val schemaId = if (typed) store.insertSchemaVersion(owner, listOf(field)) else null
        val requirement = if (typed) InformationRequestTemplateRequirementRequest(
            "recorded-note", InformationRequestRequirementType.FIELD, "Record the current note",
            collectedFieldDefinitionId = field.fieldDefinitionId,
        )
        else documentRequirement().copy(evidencePolicy = InformationRequestTemplateEvidencePolicyRequest())
        val command = command(owner, insertExchange(owner), configuration(requirement, schemaId), "valid-collection")

        val result = creationService.createAdHoc(command)
        val replay = creationService.createAdHoc(command)

        assertEquals(result.request.id, replay.request.id)
        assertEquals(1, result.requirementCount)
        QuarkusTransaction.requiringNew().run {
            val version = requireNotNull(versionRepository.findById(result.request.templateVersionId))
            val definition = requireNotNull(definitionRepository.findById(version.templateDefinitionId))
            assertEquals(InformationRequestTemplateStatus.PUBLISHED, version.status)
            assertEquals(InformationRequestTemplateOriginKind.AD_HOC_REQUEST, definition.originKind)
            assertEquals(result.request.id, definition.originRequestId)
            assertEquals(PrincipalKind.USER, definition.createdByPrincipalKind)
            assertEquals(owner, definition.createdByPrincipalId)
            assertEquals(PrincipalKind.USER, version.createdByPrincipalKind)
            assertEquals(owner, version.createdByPrincipalId)
            assertEquals(PrincipalKind.USER, version.publishedByPrincipalKind)
            assertEquals(owner, version.publishedByPrincipalId)
            assertEquals(schemaId, version.schemaVersionId)
            assertFalse(capabilityRepository.findForVersion(version.id).isEmpty())
        }
        QuarkusTransaction.requiringNew().run {
            val target = amendmentTargets.resolve(
                result.request, AmendInformationRequestCommand(
                    requestId = result.request.id,
                    configuration = configuration(requirement, schemaId),
                    access = access(owner),
                    precondition = CommandPrecondition.ExpectedRevision(result.requestETag),
                    idempotencyKey = "valid-private-amendment",
                )
            )
            assertEquals(2, target.versionNumber)
            assertEquals(InformationRequestTemplateStatus.PUBLISHED, target.status)
            assertEquals(schemaId, target.schemaVersionId)
        }
    }

    @ParameterizedTest
    @EnumSource(value = PrincipalKind::class, names = ["APPLICATION", "SERVICE_ACCOUNT"])
    fun `private amendments preserve original creators and record their own canonical publisher`(kind: PrincipalKind) {
        val owner = store.insertOwner()
        val configured = configuration(
            InformationRequestTemplateRequirementRequest(
                "record-confirmation", InformationRequestRequirementType.RESPONSE_ATTESTATION, "Confirm the records",
            )
        )
        val result = creationService.createAdHoc(command(owner, insertExchange(owner), configured, "create-for-$kind"))
        val actor = PrincipalRef(kind, UUID.randomUUID())
        val amendment = AmendInformationRequestCommand(
            requestId = result.request.id,
            configuration = configured,
            access = RequestAccessContext(actor, AuthorizationContext()),
            precondition = CommandPrecondition.ExpectedRevision(result.requestETag),
            idempotencyKey = "amend-with-$kind",
        )
        val target = QuarkusTransaction.requiringNew().call { amendmentTargets.resolve(result.request, amendment) }

        QuarkusTransaction.requiringNew().run {
            val original = requireNotNull(versionRepository.findById(result.request.templateVersionId))
            val amended = requireNotNull(versionRepository.findById(target.id))
            val definition = requireNotNull(definitionRepository.findById(original.templateDefinitionId))
            assertEquals(PrincipalKind.USER, definition.createdByPrincipalKind)
            assertEquals(owner, definition.createdByPrincipalId)
            assertEquals(PrincipalKind.USER, original.createdByPrincipalKind)
            assertEquals(owner, original.createdByPrincipalId)
            assertEquals(PrincipalKind.USER, original.publishedByPrincipalKind)
            assertEquals(owner, original.publishedByPrincipalId)
            assertEquals(actor.kind, amended.createdByPrincipalKind)
            assertEquals(actor.id, amended.createdByPrincipalId)
            assertEquals(actor.kind, amended.publishedByPrincipalKind)
            assertEquals(actor.id, amended.publishedByPrincipalId)
            assertEquals(InformationRequestTemplateStatus.PUBLISHED, amended.status)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["missing-evidence-policy", "subject-contributor", "subject-confirmation"])
    fun `a private amendment reports configuration refusal without storing a later version`(case: String) {
        val owner = store.insertOwner()
        val result = creationService.createAdHoc(
            command(
                owner, insertExchange(owner), configuration(
                    InformationRequestTemplateRequirementRequest(
                        "record-confirmation",
                        InformationRequestRequirementType.RESPONSE_ATTESTATION,
                        "Confirm the records",
                    ),
                ), "create-for-amendment"
            )
        )
        val amendment = AmendInformationRequestCommand(
            requestId = result.request.id,
            configuration = configuration(invalidRequirement(case)),
            access = access(owner),
            precondition = CommandPrecondition.ExpectedRevision(result.requestETag),
            idempotencyKey = "invalid-amendment",
        )

        val refusal = assertThrows<InformationRequestTemplateValidationException> {
            QuarkusTransaction.requiringNew().run { amendmentTargets.resolve(result.request, amendment) }
        }

        assertEquals("requested-records", refusal.sectionKey)
        assertEquals("supporting-record", refusal.requirementKey)
        QuarkusTransaction.requiringNew().run {
            val current = requireNotNull(versionRepository.findById(result.request.templateVersionId))
            assertEquals(
                listOf(1),
                versionRepository.findForDefinitions(listOf(current.templateDefinitionId)).map { it.versionNumber })
            assertEquals(InformationRequestTemplateStatus.PUBLISHED, current.status)
        }
    }

    @Test
    fun `every publishing path records exactly the capabilities its configuration requires`() {
        val owner = store.insertOwner()
        val requirement = documentRequirement().copy(evidencePolicy = InformationRequestTemplateEvidencePolicyRequest())
        val created = creationService.createAdHoc(
            command(owner, insertExchange(owner), configuration(requirement), "capability-rows"),
        )
        val amended = QuarkusTransaction.requiringNew().call {
            amendmentTargets.resolve(
                created.request, AmendInformationRequestCommand(
                    requestId = created.request.id,
                    configuration = configuration(requirement),
                    access = access(owner),
                    precondition = CommandPrecondition.ExpectedRevision(created.requestETag),
                    idempotencyKey = "capability-rows-amendment",
                )
            )
        }
        val reusable = store.insertDraft(owner, "capability-rows")
        store.publish(reusable, configuration(requirement))

        QuarkusTransaction.requiringNew().run {
            listOf(created.request.templateVersionId, amended.id, reusable.versionId).forEach { versionId ->
                val recorded = capabilityRepository.findForVersion(versionId)
                assertFalse(recorded.isEmpty())
                assertEquals(
                    capabilityRepository.findRequiredCapabilities(versionId),
                    recorded.map { it.capabilityKey })
                assertEquals(
                    recorded.map { it.capabilityKey.contractVersion },
                    recorded.map { it.requiredContractVersion })
                assertEquals(InformationRequestTemplateStatus.PUBLISHED, versionRepository.findById(versionId)?.status)
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["retired", "different-owner", "different-target"])
    fun `reusable publication revalidates a schema changed since draft authoring`(case: String) {
        val owner = store.insertOwner()
        val field = store.insertTextField("recorded-note")
        val schemaId = store.insertSchemaVersion(owner, listOf(field))
        val draft = store.insertDraft(owner, "typed-collection")
        QuarkusTransaction.requiringNew().run {
            configurationWriter.replaceConfiguration(
                requireNotNull(versionRepository.findById(draft.versionId)), configuration(
                    InformationRequestTemplateRequirementRequest(
                        "recorded-note", InformationRequestRequirementType.FIELD, "Record the note",
                        collectedFieldDefinitionId = field.fieldDefinitionId,
                    ),
                    schemaId,
                )
            )
        }
        dataSource.connection.use { connection ->
            val change = when (case) {
                "retired" -> "status = 'RETIRED'"
                "different-owner" -> "scope_kind = 'PERSONAL', scope_user_id = '${store.insertOwner()}'"
                else -> "target_resource_type = 'EXCHANGE'"
            }
            execute(
                connection,
                "UPDATE schema_definition SET $change WHERE id = (SELECT schema_definition_id FROM schema_version WHERE id = ?)",
                schemaId
            )
        }
        val definition =
            QuarkusTransaction.requiringNew().call { requireNotNull(definitionRepository.findById(draft.definitionId)) }
        val authoring = mock<InformationRequestTemplateAuthoringService>()
        whenever(authoring.requireMutationContext(definition.id)).thenReturn(
            InformationRequestTemplateMutationContext(definition, PrincipalRef.user(owner)),
        )
        whenever(authoring.projectTemplate(any())).thenAnswer {
            InformationRequestTemplateDto(
                definition.id, definition.scopeKind, scopeUserId = owner, namespace = definition.namespace,
                templateKey = definition.templateKey, displayName = definition.displayName, status = definition.status,
                draftVersion = projectionLoader.loadVersion(requireNotNull(versionRepository.findById(draft.versionId))),
                createdAt = definition.createdAt, updatedAt = definition.updatedAt,
            )
        }
        val publication =
            InformationRequestTemplatePublicationService(authoring, versionRepository, freezer, commandReceiptService)

        assertThrows<InformationRequestTemplateValidationException> {
            QuarkusTransaction.requiringNew().run {
                publication.publishTemplate(definition.id, CommandPrecondition.Unconditioned, "revalidated-$case")
            }
        }

        QuarkusTransaction.requiringNew().run {
            val version = requireNotNull(versionRepository.findById(draft.versionId))
            assertEquals(InformationRequestTemplateStatus.DRAFT, version.status)
            assertNull(version.publishedAt)
            assertTrue(capabilityRepository.findForVersion(version.id).isEmpty())
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["malformed-literal", "field-type-mismatch"])
    fun `ad hoc creation refuses a condition its pinned Field cannot read and stores nothing`(case: String) {
        val owner = store.insertOwner()
        val field = store.insertTextField("recorded-control")
        val schemaId = store.insertSchemaVersion(owner, listOf(field))
        val exchangeId = insertExchange(owner)
        val literal = if (case == "malformed-literal") JsonArray(listOf(JsonPrimitive("recorded-value")))
        else JsonPrimitive(true)
        val type = if (case == "malformed-literal") FieldValueType.SHORT_TEXT else FieldValueType.BOOLEAN

        val refusal = assertThrows<InformationRequestTemplateValidationException> {
            creationService.createAdHoc(
                command(owner, exchangeId, conditionalConfiguration(field, schemaId, type, literal), "condition-$case"),
            )
        }

        assertEquals(CONDITION_RULE, refusal.conditionRuleKey)
        assertNull(refusal.groupKey)
        dataSource.connection.use { connection ->
            assertEquals(
                "0",
                queryString(
                    connection,
                    "SELECT count(*)::text FROM information_request WHERE exchange_id = ?",
                    exchangeId
                ),
            )
            assertEquals(
                "0",
                queryString(
                    connection,
                    "SELECT count(*)::text FROM information_request_template_definition WHERE scope_user_id = ?",
                    owner,
                ),
            )
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["unreadable-literal", "field-type-mismatch"])
    fun `publication revalidates a stored condition against its literal and the pinned Field`(case: String) {
        val owner = store.insertOwner()
        val field = store.insertTextField("recorded-control")
        val schemaId = store.insertSchemaVersion(owner, listOf(field))
        val draft = store.insertDraft(owner, "conditional-collection")
        QuarkusTransaction.requiringNew().run {
            configurationWriter.replaceConfiguration(
                requireNotNull(versionRepository.findById(draft.versionId)),
                conditionalConfiguration(field, schemaId, FieldValueType.SHORT_TEXT, JsonPrimitive("recorded-value")),
            )
        }
        dataSource.connection.use { connection ->
            execute(
                connection,
                if (case == "unreadable-literal")
                    "UPDATE information_request_template_condition_predicate SET text_value = NULL WHERE template_version_id = ?"
                else
                    "UPDATE information_request_template_condition_predicate SET value_type = 'BOOLEAN', text_value = NULL, bool_value = TRUE WHERE template_version_id = ?",
                draft.versionId,
            )
        }

        val refusal = assertThrows<InformationRequestTemplateValidationException> {
            QuarkusTransaction.requiringNew().run {
                freezer.freeze(
                    requireNotNull(definitionRepository.findById(draft.definitionId)),
                    requireNotNull(versionRepository.findById(draft.versionId)),
                    PrincipalRef.user(owner),
                )
            }
        }

        assertEquals(CONDITION_RULE, refusal.conditionRuleKey)
        assertNull(refusal.groupKey)
        QuarkusTransaction.requiringNew().run {
            assertEquals(
                InformationRequestTemplateStatus.DRAFT,
                requireNotNull(versionRepository.findById(draft.versionId)).status,
            )
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["PDF", "application/pdf; charset=binary"])
    fun `publication refuses a stored file type that is not a bare media type and names its section`(stored: String) {
        val owner = store.insertOwner()
        val draft = store.insertDraft(owner, "document-collection")
        QuarkusTransaction.requiringNew().run {
            configurationWriter.replaceConfiguration(
                requireNotNull(versionRepository.findById(draft.versionId)),
                configuration(
                    documentRequirement().copy(
                        evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(
                            acceptedValues = listOf(
                                InformationRequestTemplateAcceptedValueRequest(
                                    InformationRequestEvidenceAttribute.CONTENT_TYPE,
                                    "application/pdf"
                                ),
                            ),
                        ),
                    ),
                ),
            )
        }
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE information_request_template_evidence_accepted_value SET accepted_value = ? WHERE template_version_id = ?",
                stored,
                draft.versionId,
            )
        }

        val refusal = assertThrows<InformationRequestTemplateValidationException> {
            QuarkusTransaction.requiringNew().run {
                freezer.freeze(
                    requireNotNull(definitionRepository.findById(draft.definitionId)),
                    requireNotNull(versionRepository.findById(draft.versionId)),
                    PrincipalRef.user(owner),
                )
            }
        }

        assertEquals("requested-records", refusal.sectionKey)
        assertEquals("supporting-record", refusal.requirementKey)
        QuarkusTransaction.requiringNew().run {
            assertEquals(
                InformationRequestTemplateStatus.DRAFT,
                requireNotNull(versionRepository.findById(draft.versionId)).status
            )
        }
    }

    @Test
    fun `a draft that collects a Field its Schema Version does not bind is refused when it is written`() {
        val owner = store.insertOwner()
        val bound = store.insertTextField("recorded-note")
        val unbound = store.insertTextField("other-note")
        val schemaId = store.insertSchemaVersion(owner, listOf(bound))
        val draft = store.insertDraft(owner, "unbound-collection")

        val refusal = assertThrows<InformationRequestTemplateValidationException> {
            QuarkusTransaction.requiringNew().run {
                configurationWriter.replaceConfiguration(
                    requireNotNull(versionRepository.findById(draft.versionId)),
                    configuration(
                        InformationRequestTemplateRequirementRequest(
                            "other-note", InformationRequestRequirementType.FIELD, "Record the other note",
                            collectedFieldDefinitionId = unbound.fieldDefinitionId,
                        ),
                        schemaId,
                    ),
                )
            }
        }

        assertEquals("requested-records", refusal.sectionKey)
        assertEquals("other-note", refusal.requirementKey)
        QuarkusTransaction.requiringNew().run {
            assertTrue(projectionLoader.loadVersion(requireNotNull(versionRepository.findById(draft.versionId))).sections.isEmpty())
        }
    }

    @Test
    fun `publication revalidates that every collected Field is bound by the named Schema Version`() {
        val owner = store.insertOwner()
        val bound = store.insertTextField("recorded-note")
        val unbound = store.insertTextField("other-note")
        val schemaId = store.insertSchemaVersion(owner, listOf(bound))
        val draft = store.insertDraft(owner, "stored-unbound-collection")
        QuarkusTransaction.requiringNew().run {
            configurationWriter.replaceConfiguration(
                requireNotNull(versionRepository.findById(draft.versionId)),
                configuration(
                    InformationRequestTemplateRequirementRequest(
                        "recorded-note", InformationRequestRequirementType.FIELD, "Record the note",
                        collectedFieldDefinitionId = bound.fieldDefinitionId,
                    ),
                    schemaId,
                ),
            )
        }
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE information_request_template_requirement_binding SET collected_field_definition_id = ? WHERE template_version_id = ?",
                unbound.fieldDefinitionId,
                draft.versionId,
            )
        }

        val refusal = assertThrows<InformationRequestTemplateValidationException> {
            QuarkusTransaction.requiringNew().run {
                freezer.freeze(
                    requireNotNull(definitionRepository.findById(draft.definitionId)),
                    requireNotNull(versionRepository.findById(draft.versionId)),
                    PrincipalRef.user(owner),
                )
            }
        }

        assertEquals("requested-records", refusal.sectionKey)
        assertEquals("recorded-note", refusal.requirementKey)
        QuarkusTransaction.requiringNew().run {
            assertEquals(
                InformationRequestTemplateStatus.DRAFT,
                requireNotNull(versionRepository.findById(draft.versionId)).status
            )
        }
    }

    @Test
    fun `a draft that collects a Field its Schema Version marks read-only is saved but refused at publication`() {
        val owner = store.insertOwner()
        val field = store.insertTextField("recorded-note")
        val schemaId = store.insertSchemaVersion(owner, listOf(field))
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE schema_field_binding SET is_read_only = TRUE WHERE schema_version_id = ?",
                schemaId
            )
        }
        val draft = store.insertDraft(owner, "read-only-collection")
        QuarkusTransaction.requiringNew().run {
            configurationWriter.replaceConfiguration(
                requireNotNull(versionRepository.findById(draft.versionId)),
                configuration(
                    InformationRequestTemplateRequirementRequest(
                        "recorded-note", InformationRequestRequirementType.FIELD, "Record the note",
                        collectedFieldDefinitionId = field.fieldDefinitionId,
                    ),
                    schemaId,
                ),
            )
        }

        val refusal = assertThrows<InformationRequestTemplateValidationException> {
            QuarkusTransaction.requiringNew().run {
                freezer.freeze(
                    requireNotNull(definitionRepository.findById(draft.definitionId)),
                    requireNotNull(versionRepository.findById(draft.versionId)),
                    PrincipalRef.user(owner),
                )
            }
        }

        assertEquals("requested-records", refusal.sectionKey)
        assertEquals("recorded-note", refusal.requirementKey)
        assertTrue(refusal.message.contains("read-only")) { refusal.message }
        QuarkusTransaction.requiringNew().run {
            assertEquals(
                InformationRequestTemplateStatus.DRAFT,
                requireNotNull(versionRepository.findById(draft.versionId)).status
            )
        }
    }

    @Test
    fun `a draft whose evidence bounds the database would refuse is refused when it is written`() {
        val owner = store.insertOwner()
        val draft = store.insertDraft(owner, "unbounded-evidence")

        val refusal = assertThrows<InformationRequestTemplateValidationException> {
            QuarkusTransaction.requiringNew().run {
                configurationWriter.replaceConfiguration(
                    requireNotNull(versionRepository.findById(draft.versionId)),
                    configuration(
                        documentRequirement().copy(
                            evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(maximumIssueAgeDays = 30),
                        ),
                    ),
                )
            }
        }

        assertEquals("requested-records", refusal.sectionKey)
        assertEquals("supporting-record", refusal.requirementKey)
        QuarkusTransaction.requiringNew().run {
            assertTrue(projectionLoader.loadVersion(requireNotNull(versionRepository.findById(draft.versionId))).sections.isEmpty())
        }
    }

    @Test
    fun `a draft whose condition reads an answer from another stage is refused when it is written`() {
        val owner = store.insertOwner()
        val draft = store.insertDraft(owner, "staged-collection")

        val refusal = assertThrows<InformationRequestTemplateValidationException> {
            QuarkusTransaction.requiringNew().run {
                configurationWriter.replaceConfiguration(
                    requireNotNull(versionRepository.findById(draft.versionId)),
                    crossStageConfiguration(),
                )
            }
        }

        assertEquals("second-record", refusal.requirementKey)
        QuarkusTransaction.requiringNew().run {
            val version = requireNotNull(versionRepository.findById(draft.versionId))
            assertEquals(InformationRequestTemplateStatus.DRAFT, version.status)
            assertTrue(projectionLoader.loadVersion(version).sections.isEmpty())
        }
    }

    @Test
    fun `publication revalidates that a stored staged document relies only on evidence from its own stage`() {
        val owner = store.insertOwner()
        val draft = store.insertDraft(owner, "staged-alternatives")
        QuarkusTransaction.requiringNew().run {
            configurationWriter.replaceConfiguration(
                requireNotNull(versionRepository.findById(draft.versionId)),
                sameStageAlternativeConfiguration(),
            )
        }
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE information_request_template_section SET submission_stage_key = 'second' " +
                        "WHERE template_version_id = ? AND section_key = 'first-alternates'",
                draft.versionId,
            )
        }

        val refusal = assertThrows<InformationRequestTemplateValidationException> {
            QuarkusTransaction.requiringNew().run {
                freezer.freeze(
                    requireNotNull(definitionRepository.findById(draft.definitionId)),
                    requireNotNull(versionRepository.findById(draft.versionId)),
                    PrincipalRef.user(owner),
                )
            }
        }

        assertEquals("first-record", refusal.requirementKey)
        QuarkusTransaction.requiringNew().run {
            assertEquals(
                InformationRequestTemplateStatus.DRAFT,
                requireNotNull(versionRepository.findById(draft.versionId)).status,
            )
        }
    }

    @ParameterizedTest
    @EnumSource(
        value = InformationRequestConditionHiddenDataPolicy::class,
        names = ["CLEAR_WITH_CONFIRMATION", "ARCHIVE_OUTSIDE_ACTIVE_RESPONSE"],
    )
    fun `a draft whose condition clears or archives another contributor role's answer is refused when it is written`(
        policy: InformationRequestConditionHiddenDataPolicy,
    ) {
        val owner = store.insertOwner()
        val field = store.insertTextField("recorded-control")
        val schemaId = store.insertSchemaVersion(owner, listOf(field))
        val draft = store.insertDraft(owner, "cross-party-condition")

        val refusal = assertThrows<InformationRequestTemplateValidationException> {
            QuarkusTransaction.requiringNew().run {
                configurationWriter.replaceConfiguration(
                    requireNotNull(versionRepository.findById(draft.versionId)),
                    crossRoleConfiguration(field, schemaId, policy, InformationRequestContributorRole.PREPARER),
                )
            }
        }

        assertEquals(CONDITION_RULE, refusal.conditionRuleKey)
        assertTrue(refusal.message.contains("supporting-record"))
        QuarkusTransaction.requiringNew().run {
            assertTrue(projectionLoader.loadVersion(requireNotNull(versionRepository.findById(draft.versionId))).sections.isEmpty())
        }
    }

    @Test
    fun `a condition that keeps another contributor role's answer securely is written`() {
        val owner = store.insertOwner()
        val field = store.insertTextField("recorded-control")
        val schemaId = store.insertSchemaVersion(owner, listOf(field))
        val draft = store.insertDraft(owner, "cross-party-retained")

        QuarkusTransaction.requiringNew().run {
            configurationWriter.replaceConfiguration(
                requireNotNull(versionRepository.findById(draft.versionId)),
                crossRoleConfiguration(
                    field,
                    schemaId,
                    InformationRequestConditionHiddenDataPolicy.RETAIN_SECURELY,
                    InformationRequestContributorRole.PREPARER,
                ),
            )
        }

        QuarkusTransaction.requiringNew().run {
            assertEquals(
                1,
                projectionLoader.loadVersion(requireNotNull(versionRepository.findById(draft.versionId))).conditionRules.size
            )
        }
    }

    @Test
    fun `publication revalidates that a stored condition clears only answers of its source's contributor role`() {
        val owner = store.insertOwner()
        val field = store.insertTextField("recorded-control")
        val schemaId = store.insertSchemaVersion(owner, listOf(field))
        val draft = store.insertDraft(owner, "stored-cross-party")
        QuarkusTransaction.requiringNew().run {
            configurationWriter.replaceConfiguration(
                requireNotNull(versionRepository.findById(draft.versionId)),
                crossRoleConfiguration(
                    field,
                    schemaId,
                    InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION,
                    InformationRequestContributorRole.CONTRIBUTOR,
                ),
            )
        }
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                UPDATE information_request_template_requirement_binding binding
                SET contributor_role = 'PREPARER'
                FROM information_request_template_requirement requirement
                WHERE binding.template_requirement_id = requirement.id
                  AND binding.template_version_id = ?
                  AND requirement.requirement_key = 'supporting-record'
                """.trimIndent(),
                draft.versionId,
            )
        }

        val refusal = assertThrows<InformationRequestTemplateValidationException> {
            QuarkusTransaction.requiringNew().run {
                freezer.freeze(
                    requireNotNull(definitionRepository.findById(draft.definitionId)),
                    requireNotNull(versionRepository.findById(draft.versionId)),
                    PrincipalRef.user(owner),
                )
            }
        }

        assertEquals(CONDITION_RULE, refusal.conditionRuleKey)
        assertTrue(refusal.message.contains("supporting-record"))
        QuarkusTransaction.requiringNew().run {
            assertEquals(
                InformationRequestTemplateStatus.DRAFT,
                requireNotNull(versionRepository.findById(draft.versionId)).status
            )
        }
    }

    @Test
    fun `publication revalidates that a stored staged condition reads only answers of its own stage`() {
        val owner = store.insertOwner()
        val draft = store.insertDraft(owner, "stored-cross-stage")
        QuarkusTransaction.requiringNew().run {
            configurationWriter.replaceConfiguration(
                requireNotNull(versionRepository.findById(draft.versionId)),
                sameStageConditionConfiguration(),
            )
        }
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE information_request_template_section SET submission_stage_key = 'second' " +
                        "WHERE template_version_id = ? AND section_key = 'second-records'",
                draft.versionId,
            )
        }

        val refusal = assertThrows<InformationRequestTemplateValidationException> {
            QuarkusTransaction.requiringNew().run {
                freezer.freeze(
                    requireNotNull(definitionRepository.findById(draft.definitionId)),
                    requireNotNull(versionRepository.findById(draft.versionId)),
                    PrincipalRef.user(owner),
                )
            }
        }

        assertEquals("second-record", refusal.requirementKey)
        QuarkusTransaction.requiringNew().run {
            assertEquals(
                InformationRequestTemplateStatus.DRAFT,
                requireNotNull(versionRepository.findById(draft.versionId)).status
            )
        }
    }

    private fun crossRoleConfiguration(
        field: WalkingSkeletonField,
        schemaId: UUID,
        policy: InformationRequestConditionHiddenDataPolicy,
        governedRole: InformationRequestContributorRole,
    ): InformationRequestTemplateConfigurationRequest {
        val base = conditionalConfiguration(field, schemaId, FieldValueType.SHORT_TEXT, JsonPrimitive("recorded-value"))
        return base.copy(
            sections = base.sections.map { section ->
                section.copy(requirements = section.requirements.map { requirement ->
                    if (requirement.requirementKey == "supporting-record") requirement.copy(contributorRole = governedRole)
                    else requirement
                })
            },
            conditionRules = base.conditionRules.map { it.copy(hiddenDataPolicy = policy) },
        )
    }

    private fun sameStageConditionConfiguration(): InformationRequestTemplateConfigurationRequest {
        val crossStage = crossStageConfiguration()
        return crossStage.copy(
            sections = crossStage.sections.map { it.copy(submissionStageKey = "first") },
            conditionRules = crossStage.conditionRules.map {
                it.copy(hiddenDataPolicy = InformationRequestConditionHiddenDataPolicy.ARCHIVE_OUTSIDE_ACTIVE_RESPONSE)
            },
        )
    }

    private fun sameStageAlternativeConfiguration() = InformationRequestTemplateConfigurationRequest(
        submissionMode = InformationRequestSubmissionMode.STAGED,
        sections = listOf(
            InformationRequestTemplateSectionRequest(
                "first-records", "First records", submissionStageKey = "first",
                requirements = listOf(
                    stagedDocument("first-record").copy(
                        requiredness = InformationRequestRequiredness.REQUIRED,
                        substituteRequirementKeys = listOf("alternate-record"),
                    ),
                ),
            ),
            InformationRequestTemplateSectionRequest(
                "first-alternates", "First alternates", submissionStageKey = "first",
                requirements = listOf(stagedDocument("alternate-record")),
            ),
            InformationRequestTemplateSectionRequest(
                "second-records", "Second records", submissionStageKey = "second",
                requirements = listOf(stagedDocument("second-record")),
            ),
        ),
    )

    private fun crossStageConfiguration() = InformationRequestTemplateConfigurationRequest(
        submissionMode = InformationRequestSubmissionMode.STAGED,
        submissionStageOrdering = InformationRequestSubmissionStageOrdering.SEQUENTIAL,
        sections = listOf(
            InformationRequestTemplateSectionRequest(
                "first-records", "First records", submissionStageKey = "first",
                requirements = listOf(stagedDocument("first-record")),
            ),
            InformationRequestTemplateSectionRequest(
                "second-records", "Second records", submissionStageKey = "second",
                requirements = listOf(
                    stagedDocument("second-record").copy(
                        requiredness = InformationRequestRequiredness.CONDITIONAL,
                        conditionalRuleKey = "when-first-given",
                    ),
                ),
            ),
        ),
        conditionRules = listOf(
            InformationRequestTemplateConditionRuleRequest(
                ruleKey = "when-first-given",
                predicates = listOf(
                    InformationRequestTemplateConditionPredicateRequest(
                        sourceRequirementKey = "first-record",
                        operator = FieldOperator.EQUALS,
                        expectedDisposition = InformationRequestResponseDisposition.PROVIDED,
                    ),
                ),
            ),
        ),
    )

    private fun conditionalConfiguration(
        field: WalkingSkeletonField,
        schemaId: UUID,
        type: FieldValueType,
        literal: JsonElement,
    ) = InformationRequestTemplateConfigurationRequest(
        schemaVersionId = schemaId,
        sections = listOf(
            InformationRequestTemplateSectionRequest(
                "recorded-data", "Recorded data",
                requirements = listOf(
                    InformationRequestTemplateRequirementRequest(
                        "recorded-control", InformationRequestRequirementType.FIELD, "Record the control",
                        collectedFieldDefinitionId = field.fieldDefinitionId,
                    ),
                    InformationRequestTemplateRequirementRequest(
                        "supporting-record",
                        InformationRequestRequirementType.DOCUMENT,
                        "Provide the supporting record",
                        permittedDispositions = listOf(InformationRequestResponseDisposition.PROVIDED),
                        requiredness = InformationRequestRequiredness.CONDITIONAL,
                        conditionalRuleKey = CONDITION_RULE,
                        evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(),
                    ),
                ),
            ),
        ),
        conditionRules = listOf(
            InformationRequestTemplateConditionRuleRequest(
                CONDITION_RULE,
                predicates = listOf(
                    InformationRequestTemplateConditionPredicateRequest(
                        fieldDefinitionId = field.fieldDefinitionId,
                        valueType = type,
                        operator = FieldOperator.EQUALS,
                        value = literal,
                    ),
                ),
            ),
        ),
    )

    private fun stagedDocument(key: String) = InformationRequestTemplateRequirementRequest(
        key, InformationRequestRequirementType.DOCUMENT, "Provide the $key",
        permittedDispositions = listOf(InformationRequestResponseDisposition.PROVIDED),
        evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(),
    )

    private fun invalidRequirement(case: String): InformationRequestTemplateRequirementRequest = when (case) {
        "missing-schema" -> InformationRequestTemplateRequirementRequest(
            "supporting-record", InformationRequestRequirementType.FIELD, "Record the current note",
            collectedFieldDefinitionId = store.insertTextField("recorded-note").fieldDefinitionId,
        )

        "waiver-without-answer" -> documentRequirement().copy(
            evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(
                waiverPolicy = InformationRequestEvidenceWaiverPolicy.RESPONDENT_DECLARED,
            )
        )

        "answer-without-waiver" -> documentRequirement().copy(
            evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(),
            permittedDispositions = listOf(
                InformationRequestResponseDisposition.PROVIDED,
                InformationRequestResponseDisposition.WAIVED
            ),
        )

        "subject-contributor" -> documentRequirement().copy(
            contributorRole = InformationRequestContributorRole.SUBJECT,
            evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(),
        )

        "subject-confirmation" -> InformationRequestTemplateRequirementRequest(
            "supporting-record",
            InformationRequestRequirementType.RESPONSE_ATTESTATION,
            "Confirm the recorded information",
            attestationPolicy = InformationRequestTemplateAttestationPolicyRequest(
                requiredRoles = listOf(InformationRequestContributorRole.SUBJECT),
            ),
        )

        else -> documentRequirement()
    }

    private fun documentRequirement() = InformationRequestTemplateRequirementRequest(
        "supporting-record", InformationRequestRequirementType.DOCUMENT, "Provide the supporting record",
        permittedDispositions = listOf(InformationRequestResponseDisposition.PROVIDED),
    )

    private fun configuration(requirement: InformationRequestTemplateRequirementRequest, schemaId: UUID? = null) =
        InformationRequestTemplateConfigurationRequest(
            schemaVersionId = schemaId, sections = listOf(
                InformationRequestTemplateSectionRequest(
                    "requested-records",
                    "Requested records",
                    requirements = listOf(requirement)
                ),
            )
        )

    private fun access(owner: UUID) =
        RequestAccessContext(PrincipalRef.user(owner), AuthorizationContext(sessionRef = "owner-session"))

    private fun command(
        owner: UUID,
        exchangeId: UUID,
        configuration: InformationRequestTemplateConfigurationRequest,
        key: String
    ) =
        CreateAdHocInformationRequestCommand(
            exchangeId,
            "Process collection",
            configuration = configuration,
            access = access(owner),
            idempotencyKey = key
        )

    private fun insertExchange(owner: UUID): UUID {
        val exchangeId = UUID.randomUUID()
        dataSource.connection.use { connection ->
            execute(
                connection, """
                INSERT INTO user_subscription_policy (id, app_user_id, plan_code, subscription_status)
                VALUES (?, ?, 'PERSONAL', 'ACTIVE')
            """.trimIndent(), UUID.randomUUID(), owner
            )
            execute(
                connection, """
                INSERT INTO exchange (id, owner_user_id, initiator_id, is_deleted, require_recipient_sign_in,
                    created_date, last_activity, description, initial_share_message, name, status)
                VALUES (?, ?, ?, FALSE, FALSE, now(), now(), 'Collect records', 'Please respond', 'Process collection', 'ACCEPTED_STARTED')
            """.trimIndent(), exchangeId, owner, owner
            )
            execute(
                connection, """
                INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name,
                    source, status, granted_at)
                VALUES (?, 'EXCHANGE', ?, 'USER', ?, 'OWNER', 'DIRECT', 'ACTIVE', now())
            """.trimIndent(), UUID.randomUUID(), exchangeId, owner
            )
        }
        return exchangeId
    }

    private companion object {
        const val CONDITION_RULE = "control-applies"
    }
}
