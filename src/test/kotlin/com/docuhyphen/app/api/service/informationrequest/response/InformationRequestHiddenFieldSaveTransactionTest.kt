package com.docuhyphen.app.api.service.informationrequest.response

import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.FieldOperator
import com.docuhyphen.app.api.model.fields.FieldValueEntry
import com.docuhyphen.app.api.model.fields.FieldsPrecondition
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestResponsePatch
import com.docuhyphen.app.api.model.informationrequest.response.PatchInformationRequestResponsesCommand
import com.docuhyphen.app.api.model.informationrequest.response.ResponseFieldValuesPatch
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.conformance.PublishedRequestSupport
import com.docuhyphen.app.api.service.informationrequest.conformance.model.PublishedRequest
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationWriter
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateMaterializer
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.security.ForbiddenException
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestHiddenFieldSaveTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

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
    lateinit var drafts: InformationRequestResponseDraftService

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
    fun `a respondent saves an unrelated answer while another party's empty conditional Field stays hidden`() {
        val request = hiddenFieldRequest("hidden-other-party", InformationRequestContributorRole.PREPARER, null)

        save(request, CONTRIBUTOR, OPEN_KEY, "does not apply", verified = false, key = "unrelated-save")

        assertEquals("does not apply", answerOf(request, OPEN_KEY))
    }

    @Test
    fun `an unverified respondent saves an unrelated answer while an empty protected conditional Field stays hidden`() {
        val request =
            hiddenFieldRequest("hidden-protected", InformationRequestContributorRole.CONTRIBUTOR, "restricted-detail")

        save(request, CONTRIBUTOR, OPEN_KEY, "does not apply", verified = false, key = "unverified-save")

        assertEquals("does not apply", answerOf(request, OPEN_KEY))
    }

    @Test
    fun `a respondent's answer hides another party's answered Field without clearing it, and the Field returns with its answer`() {
        val request = hiddenFieldRequest("hidden-answered", InformationRequestContributorRole.PREPARER, null)
        save(request, CONTRIBUTOR, OPEN_KEY, "applies", verified = false, key = "show-follow-on")
        save(request, PREPARER, HIDDEN_KEY, "follow-on answer", verified = false, key = "answer-follow-on")

        save(request, CONTRIBUTOR, OPEN_KEY, "does not apply", verified = false, key = "hide-follow-on")

        assertEquals("does not apply", answerOf(request, OPEN_KEY))
        assertEquals("follow-on answer", answerOf(request, HIDDEN_KEY))
        assertEquals("RETAIN_SECURELY|false", hiddenEnvelopeOf(request, HIDDEN_KEY))

        save(request, CONTRIBUTOR, OPEN_KEY, "applies", verified = false, key = "show-follow-on-again")

        assertEquals("follow-on answer", answerOf(request, HIDDEN_KEY))
        assertEquals("|true", hiddenEnvelopeOf(request, HIDDEN_KEY))
    }

    @Test
    fun `a respondent confirming the clear of another party's Field is refused like an unknown Requirement and nothing changes`() {
        val request = hiddenFieldRequest("hidden-confirmed", InformationRequestContributorRole.PREPARER, null)
        save(request, CONTRIBUTOR, OPEN_KEY, "applies", verified = false, key = "show-confirmed")
        save(request, PREPARER, HIDDEN_KEY, "follow-on answer", verified = false, key = "answer-confirmed")

        val refusal = assertThrows(ForbiddenException::class.java) {
            save(
                request,
                CONTRIBUTOR,
                OPEN_KEY,
                "does not apply",
                verified = false,
                key = "confirm-other-party-clear",
                confirmed = setOf(published.requirement(request, HIDDEN_KEY).id),
            )
        }
        val unknown = assertThrows(ForbiddenException::class.java) {
            save(
                request,
                CONTRIBUTOR,
                OPEN_KEY,
                "does not apply",
                verified = false,
                key = "confirm-unknown-clear",
                confirmed = setOf(java.util.UUID.randomUUID()),
            )
        }

        assertEquals(refusal.message, unknown.message)
        assertEquals("applies", answerOf(request, OPEN_KEY))
        assertEquals("follow-on answer", answerOf(request, HIDDEN_KEY))
    }

    @Test
    fun `an unverified respondent's answer hides a protected Field it may not clear without clearing it`() {
        val request =
            hiddenFieldRequest(
                "hidden-protected-answered",
                InformationRequestContributorRole.CONTRIBUTOR,
                "restricted-detail"
            )
        save(request, CONTRIBUTOR, OPEN_KEY, "applies", verified = true, key = "show-protected")
        save(request, CONTRIBUTOR, HIDDEN_KEY, "protected answer", verified = true, key = "answer-protected")

        save(request, CONTRIBUTOR, OPEN_KEY, "does not apply", verified = false, key = "hide-protected")

        assertEquals("protected answer", answerOf(request, HIDDEN_KEY))
        assertEquals("RETAIN_SECURELY|false", hiddenEnvelopeOf(request, HIDDEN_KEY))
    }

    private fun hiddenFieldRequest(
        templateKey: String,
        hiddenRole: InformationRequestContributorRole,
        compartment: String?
    ): PublishedRequest {
        val crossParty = hiddenRole != InformationRequestContributorRole.CONTRIBUTOR
        val request = publishedHiddenFieldRequest(
            templateKey,
            hiddenRole,
            compartment,
            if (crossParty) InformationRequestConditionHiddenDataPolicy.RETAIN_SECURELY
            else InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION,
        )
        if (crossParty) keepPublishedPolicy(
            request,
            InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION
        )
        return request
    }

    private fun keepPublishedPolicy(request: PublishedRequest, policy: InformationRequestConditionHiddenDataPolicy) {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            connection.createStatement().use {
                it.execute("ALTER TABLE information_request_template_condition_rule DISABLE TRIGGER request_template_condition_rule_guard_write")
            }
            connection.prepareStatement(
                "UPDATE information_request_template_condition_rule SET hidden_data_policy = ? WHERE template_version_id = ?",
            ).use { statement ->
                statement.setString(1, policy.name)
                statement.setObject(2, request.templateVersionId)
                statement.executeUpdate()
            }
            connection.createStatement().use {
                it.execute("ALTER TABLE information_request_template_condition_rule ENABLE TRIGGER request_template_condition_rule_guard_write")
            }
            connection.commit()
            connection.autoCommit = true
        }
    }

    private fun publishedHiddenFieldRequest(
        templateKey: String,
        hiddenRole: InformationRequestContributorRole,
        compartment: String?,
        policy: InformationRequestConditionHiddenDataPolicy,
    ): PublishedRequest =
        published.issue(
            templateKey,
            listOf(OPEN_KEY, HIDDEN_KEY),
            listOf(CONTRIBUTOR, PREPARER)
        ) { schemaVersionId, fields ->
            InformationRequestTemplateConfigurationRequest(
                schemaVersionId = schemaVersionId,
                conditionRules = listOf(
                    InformationRequestTemplateConditionRuleRequest(
                        ruleKey = "when-detail-applies",
                        hiddenDataPolicy = policy,
                        predicates = listOf(
                            InformationRequestTemplateConditionPredicateRequest(
                                fieldDefinitionId = fields.getValue(OPEN_KEY).fieldDefinitionId,
                                valueType = FieldValueType.SHORT_TEXT,
                                operator = FieldOperator.EQUALS,
                                value = JsonPrimitive("applies"),
                            ),
                        ),
                    ),
                ),
                sections = listOf(
                    InformationRequestTemplateSectionRequest(
                        sectionKey = "responses",
                        title = "Responses",
                        requirements = listOf(
                            InformationRequestTemplateRequirementRequest(
                                requirementKey = OPEN_KEY,
                                requirementType = InformationRequestRequirementType.FIELD,
                                prompt = "Describe the item",
                                requiredness = InformationRequestRequiredness.REQUIRED,
                                contributorRole = InformationRequestContributorRole.CONTRIBUTOR,
                                collectedFieldDefinitionId = fields.getValue(OPEN_KEY).fieldDefinitionId,
                                permittedDispositions = listOf(InformationRequestResponseDisposition.PROVIDED),
                            ),
                            InformationRequestTemplateRequirementRequest(
                                requirementKey = HIDDEN_KEY,
                                requirementType = InformationRequestRequirementType.FIELD,
                                prompt = "Describe the follow-on item",
                                requiredness = InformationRequestRequiredness.CONDITIONAL,
                                contributorRole = hiddenRole,
                                conditionalRuleKey = "when-detail-applies",
                                confidentialityCompartmentKey = compartment,
                                collectedFieldDefinitionId = fields.getValue(HIDDEN_KEY).fieldDefinitionId,
                                permittedDispositions = listOf(InformationRequestResponseDisposition.PROVIDED),
                            ),
                        ),
                    ),
                ),
            )
        }

    @Suppress("LongParameterList")
    private fun save(
        request: PublishedRequest,
        party: String,
        requirementKey: String,
        value: String,
        verified: Boolean,
        key: String,
        confirmed: Set<java.util.UUID> = emptySet(),
    ) =
        QuarkusTransaction.requiringNew().call {
            drafts.patch(
                PatchInformationRequestResponsesCommand(
                    requestId = request.requestId,
                    access = RequestAccessContext(
                        request.principal(party),
                        AuthorizationContext(sessionRef = "hidden-field-session", mfaSatisfied = verified)
                    ),
                    precondition = CommandPrecondition.ExpectedRevision(published.responseETag(request)),
                    idempotencyKey = key,
                    patches = listOf(
                        InformationRequestResponsePatch(
                            requirementId = published.requirement(request, requirementKey).id,
                            disposition = InformationRequestResponseDisposition.PROVIDED,
                            fieldValues = ResponseFieldValuesPatch(
                                entries = listOf(
                                    FieldValueEntry(
                                        request.fields.getValue(requirementKey).fieldContractId,
                                        JsonPrimitive(value)
                                    )
                                ),
                                precondition = FieldsPrecondition.Unconditioned,
                            ),
                        ),
                    ),
                    confirmedHiddenResponseClears = confirmed,
                ),
            )
        }

    private fun answerOf(request: PublishedRequest, requirementKey: String): String? =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT text_value FROM field_value WHERE resource_type = 'INFORMATION_REQUEST' AND resource_id = ? AND field_contract_id = ?",
            ).use { statement ->
                statement.setObject(1, request.requestId)
                statement.setObject(2, request.fields.getValue(requirementKey).fieldContractId)
                statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
            }
        }

    private fun hiddenEnvelopeOf(request: PublishedRequest, requirementKey: String): String? =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT COALESCE(hidden_data_policy, '') || '|' || active_in_response FROM information_request_response WHERE information_request_requirement_id = ?",
            ).use { statement ->
                statement.setObject(1, published.requirement(request, requirementKey).id)
                statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
            }
        }

    private companion object {
        const val CONTRIBUTOR = "CONTRIBUTOR"
        const val PREPARER = "PREPARER"
        const val OPEN_KEY = "open-detail"
        const val HIDDEN_KEY = "follow-on-detail"
    }
}
