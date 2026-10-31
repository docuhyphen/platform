package com.docuhyphen.app.api.service.informationrequest.conformance

import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.FieldOperator
import com.docuhyphen.app.api.model.informationrequest.amendment.AmendInformationRequestCommand
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestResponsePatch
import com.docuhyphen.app.api.model.informationrequest.response.PatchInformationRequestResponsesCommand
import com.docuhyphen.app.api.model.informationrequest.response.ResponseNarrativePatch
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateWalkingSkeletonStore
import com.docuhyphen.app.api.service.informationrequest.capability.model.WalkingSkeletonDraft
import com.docuhyphen.app.api.service.informationrequest.conformance.model.AmendedHiddenAnswerFixture
import com.docuhyphen.app.api.service.informationrequest.conformance.model.AmendedHiddenStoredAnswer
import com.docuhyphen.app.api.service.informationrequest.conformance.model.PublishedRequest
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationWriter
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateMaterializer
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestAmendedHiddenAnswerTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

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
    lateinit var authorization: AuthorizationService

    private val published by lazy {
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
        InformationRequestTemplateWalkingSkeletonStore(dataSource, definitions, versions, capabilities, writer)
    }

    @Test
    fun `an answer hidden by a condition returns with its retained content once an amendment makes it unconditional`() {
        val fixture = hiddenFollowUp()

        amendTo(fixture, FollowUpCondition.UNCONDITIONAL, versionNumber = 2)

        val followUp = followUp(fixture)
        assertTrue(followUp.active)
        assertNull(followUp.hiddenByRule)
        assertEquals(RETAINED_REASON, followUp.narrative)
    }

    @Test
    fun `an answer hidden by a condition returns once an amendment moves it to a rule that holds`() {
        val fixture = hiddenFollowUp()

        amendTo(fixture, FollowUpCondition.WHEN_SOURCE_UNAVAILABLE, versionNumber = 2)

        val followUp = followUp(fixture)
        assertTrue(followUp.active)
        assertNull(followUp.hiddenByRule)
        assertEquals(RETAINED_REASON, followUp.narrative)
    }

    @Test
    fun `an answer moved to a rule that does not hold stays hidden under that rule and returns once it holds`() {
        val fixture = hiddenFollowUp()

        amendTo(fixture, FollowUpCondition.WHEN_SOURCE_NOT_APPLICABLE, versionNumber = 2)

        val hidden = followUp(fixture)
        assertFalse(hidden.active)
        assertEquals(FollowUpCondition.WHEN_SOURCE_NOT_APPLICABLE.ruleKey, hidden.hiddenByRule)
        answerSource(fixture, InformationRequestResponseDisposition.NOT_APPLICABLE, "source-not-applicable")
        val returned = followUp(fixture)
        assertTrue(returned.active)
        assertEquals(RETAINED_REASON, returned.narrative)
    }

    @Test
    fun `an answer whose rule an amendment leaves in place stays hidden`() {
        val fixture = hiddenFollowUp()

        amendTo(fixture, FollowUpCondition.WHEN_SOURCE_PROVIDED, versionNumber = 2)

        val followUp = followUp(fixture)
        assertFalse(followUp.active)
        assertEquals(FollowUpCondition.WHEN_SOURCE_PROVIDED.ruleKey, followUp.hiddenByRule)
    }

    private fun hiddenFollowUp(): AmendedHiddenAnswerFixture {
        var schemaVersionId: UUID? = null
        val request = published.issue(
            "hidden-answer-${UUID.randomUUID().toString().take(8)}",
            listOf("record-note"),
            listOf("CONTRIBUTOR", "DECISION_MAKER"),
        ) { schema, _ ->
            schemaVersionId = schema
            configuration(schema, FollowUpCondition.WHEN_SOURCE_PROVIDED)
        }
        val fixture = AmendedHiddenAnswerFixture(request, requireNotNull(schemaVersionId))
        answerSource(fixture, InformationRequestResponseDisposition.PROVIDED, "source-provided")
        patch(fixture, "follow-up-answered") {
            listOf(
                InformationRequestResponsePatch(
                    requirementId = requirementId(fixture, FOLLOW_UP),
                    disposition = InformationRequestResponseDisposition.UNAVAILABLE,
                    narrative = ResponseNarrativePatch.Set(RETAINED_REASON),
                ),
            )
        }
        answerSource(fixture, InformationRequestResponseDisposition.UNAVAILABLE, "source-unavailable")
        val hidden = followUp(fixture)
        assertFalse(hidden.active, "the follow-up answer is hidden before the amendment")
        assertEquals(FollowUpCondition.WHEN_SOURCE_PROVIDED.ruleKey, hidden.hiddenByRule)
        return fixture
    }

    private fun answerSource(
        fixture: AmendedHiddenAnswerFixture,
        disposition: InformationRequestResponseDisposition,
        key: String
    ) {
        patch(fixture, key) {
            listOf(
                InformationRequestResponsePatch(
                    requirementId = requirementId(fixture, SOURCE),
                    disposition = disposition,
                    narrative = if (disposition == InformationRequestResponseDisposition.PROVIDED) ResponseNarrativePatch.Clear
                    else ResponseNarrativePatch.Set("The source record is not held"),
                ),
            )
        }
    }

    private fun amendTo(fixture: AmendedHiddenAnswerFixture, condition: FollowUpCondition, versionNumber: Int) {
        val request = fixture.request
        val draft =
            WalkingSkeletonDraft(request.definitionId, currentVersion(request), request.ownerId, "hidden-answer")
        val next = store.insertNextDraft(draft, versionNumber)
        store.publish(next, configuration(fixture.schemaVersionId, condition))
        QuarkusTransaction.requiringNew().run {
            runtime.build(request.requestId, centralAuthorization = authorization).amendments.amend(
                AmendInformationRequestCommand(
                    requestId = request.requestId,
                    targetTemplateVersionId = next.versionId,
                    reasonCode = "collection-revised",
                    access = request.access("DECISION_MAKER"),
                    precondition = CommandPrecondition.ExpectedRevision(
                        InformationRequestETag.aggregateOf(requests.findById(request.requestId)!!),
                    ),
                    idempotencyKey = "amend-to-$versionNumber",
                ),
            )
        }
    }

    private fun patch(
        fixture: AmendedHiddenAnswerFixture,
        key: String,
        patches: () -> List<InformationRequestResponsePatch>
    ) {
        QuarkusTransaction.requiringNew().run {
            runtime.build(fixture.request.requestId, centralAuthorization = authorization).responses.patch(
                PatchInformationRequestResponsesCommand(
                    requestId = fixture.request.requestId,
                    access = fixture.request.access("CONTRIBUTOR"),
                    precondition = CommandPrecondition.ExpectedRevision(
                        InformationRequestETag.responsesOf(requests.findById(fixture.request.requestId)!!),
                    ),
                    idempotencyKey = key,
                    patches = patches(),
                ),
            )
        }
    }

    private fun requirementId(fixture: AmendedHiddenAnswerFixture, requirementKey: String): UUID =
        dataSource.connection.use { connection ->
            UUID.fromString(
                queryString(
                    connection,
                    """
                SELECT runtime.id::text FROM information_request_requirement runtime
                JOIN information_request_template_requirement authored ON authored.id = runtime.source_template_requirement_id
                WHERE runtime.information_request_id = ? AND authored.requirement_key = ?
                """.trimIndent(),
                    fixture.request.requestId,
                    requirementKey,
                ),
            )
        }

    private fun followUp(fixture: AmendedHiddenAnswerFixture): AmendedHiddenStoredAnswer =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
            SELECT active_in_response, hidden_by_condition_rule_key, narrative
            FROM information_request_response WHERE information_request_requirement_id = ?
            """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, requirementId(fixture, FOLLOW_UP))
                statement.executeQuery().use { rows ->
                    assertTrue(rows.next(), "the follow-up answer is stored")
                    AmendedHiddenStoredAnswer(rows.getBoolean(1), rows.getString(2), rows.getString(3))
                }
            }
        }

    private fun currentVersion(request: PublishedRequest): UUID =
        QuarkusTransaction.requiringNew().call { requests.findById(request.requestId)!!.templateVersionId }

    private fun configuration(schemaVersionId: UUID, condition: FollowUpCondition) =
        InformationRequestTemplateConfigurationRequest(
            schemaVersionId = schemaVersionId,
            conditionRules = listOfNotNull(
                condition.expected?.let { expected ->
                    InformationRequestTemplateConditionRuleRequest(
                        ruleKey = condition.ruleKey,
                        hiddenDataPolicy = InformationRequestConditionHiddenDataPolicy.RETAIN_SECURELY,
                        predicates = listOf(
                            InformationRequestTemplateConditionPredicateRequest(
                                sourceRequirementKey = SOURCE,
                                operator = FieldOperator.EQUALS,
                                expectedDisposition = expected,
                            ),
                        ),
                    )
                },
            ),
            sections = listOf(
                InformationRequestTemplateSectionRequest(
                    sectionKey = "records",
                    title = "Records",
                    requirements = listOf(
                        document(
                            SOURCE,
                            InformationRequestRequiredness.REQUIRED,
                            null,
                            listOf(
                                InformationRequestResponseDisposition.PROVIDED,
                                InformationRequestResponseDisposition.UNAVAILABLE,
                                InformationRequestResponseDisposition.NOT_APPLICABLE,
                            ),
                        ),
                        document(
                            FOLLOW_UP,
                            if (condition.expected == null) InformationRequestRequiredness.REQUIRED
                            else InformationRequestRequiredness.CONDITIONAL,
                            condition.expected?.let { condition.ruleKey },
                            listOf(InformationRequestResponseDisposition.UNAVAILABLE),
                        ),
                    ),
                ),
            ),
        )

    private fun document(
        key: String,
        requiredness: InformationRequestRequiredness,
        ruleKey: String?,
        dispositions: List<InformationRequestResponseDisposition>,
    ) = InformationRequestTemplateRequirementRequest(
        requirementKey = key,
        requirementType = InformationRequestRequirementType.DOCUMENT,
        prompt = "Provide this record or explain its absence",
        requiredness = requiredness,
        conditionalRuleKey = ruleKey,
        contributorRole = InformationRequestContributorRole.CONTRIBUTOR,
        permittedDispositions = dispositions,
        evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(minimumFileCount = 1),
    )

    private enum class FollowUpCondition(val ruleKey: String, val expected: InformationRequestResponseDisposition?) {
        WHEN_SOURCE_PROVIDED("when-source-provided", InformationRequestResponseDisposition.PROVIDED),
        WHEN_SOURCE_UNAVAILABLE("when-source-unavailable", InformationRequestResponseDisposition.UNAVAILABLE),
        WHEN_SOURCE_NOT_APPLICABLE("when-source-not-applicable", InformationRequestResponseDisposition.NOT_APPLICABLE),
        UNCONDITIONAL("unconditional", null),
    }

    private companion object {
        const val SOURCE = "source-record"
        const val FOLLOW_UP = "follow-up-record"
        const val RETAINED_REASON = "The follow-up record was never issued"
    }
}
