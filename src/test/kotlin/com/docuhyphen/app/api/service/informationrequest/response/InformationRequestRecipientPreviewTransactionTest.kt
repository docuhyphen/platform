package com.docuhyphen.app.api.service.informationrequest.response

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.FieldOperator
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestSignedInCaller
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.conformance.PublishedRequestSupport
import com.docuhyphen.app.api.service.informationrequest.conformance.model.PublishedRequest
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationWriter
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateMaterializer
import com.docuhyphen.app.api.service.user.AppUserService
import io.quarkus.security.ForbiddenException
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestRecipientPreviewTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var appUsers: AppUserService

    @Inject
    lateinit var gate: InformationRequestMutationGate

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
    lateinit var workspaces: InformationRequestResponseWorkspaceService

    @Inject
    lateinit var previews: InformationRequestRecipientPreviewService

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

    @Test
    fun `the owner who decides but answers nothing previews exactly what the contributor is shown`() {
        val request = request()
        val contributorView = workspaces.load(request.requestId, request.access(CONTRIBUTOR)).templateVersion

        val preview = previews.preview(request.requestId, request.parties.getValue(CONTRIBUTOR), request.owner)

        assertTrue(gate.permitsRequest(request.owner, Action.INFORMATION_REQUEST_MANAGE_PARTIES, request.requestId))
        assertEquals(setOf(RECORD_IDENTIFIER, RECORD_DETAIL), keys(preview.templateVersion))
        assertEquals(contributorView, preview.templateVersion)
        assertEquals(listOf(DETAIL_RULE), preview.templateVersion.conditionRules.map { it.ruleKey })
        assertEquals(request.parties.getValue(CONTRIBUTOR), preview.partyId)
        assertEquals(InformationRequestShareRoleKey.CONTRIBUTOR, preview.roleKey)
        assertTrue(keys(workspaces.load(request.requestId, request.owner).templateVersion).isEmpty())
    }

    @Test
    fun `the owner previews a draft request before issuing it`() {
        val request = request()
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE information_request SET state = 'DRAFT', issued_at = NULL WHERE id = ?",
                request.requestId
            )
        }

        val preview = previews.preview(request.requestId, request.parties.getValue(CONTRIBUTOR), request.owner)

        assertEquals(setOf(RECORD_IDENTIFIER, RECORD_DETAIL), keys(preview.templateVersion))
        assertEquals(listOf(DETAIL_RULE), preview.templateVersion.conditionRules.map { it.ruleKey })
    }

    @Test
    fun `a preview follows the nominated party's own Requirements and conditions`() {
        val request = request()
        val preparerView = workspaces.load(request.requestId, request.access(PREPARER)).templateVersion

        val preview = previews.preview(request.requestId, request.parties.getValue(PREPARER), request.owner)

        assertEquals(setOf(PREPARER_SUMMARY), keys(preview.templateVersion))
        assertEquals(preparerView, preview.templateVersion)
        assertTrue(preview.templateVersion.conditionRules.isEmpty())
        assertEquals(InformationRequestShareRoleKey.PREPARER, preview.roleKey)
    }

    @Test
    fun `an undisclosed Requirement appears in no party's preview`() {
        val request = request()

        listOf(CONTRIBUTOR, PREPARER).forEach { party ->
            val preview = previews.preview(request.requestId, request.parties.getValue(party), request.owner)
            assertFalse(WITHHELD_NOTE in keys(preview.templateVersion), party)
            assertTrue(keys(preview.templateVersion).isNotEmpty(), party)
        }
    }

    @Test
    fun `a party who answers nothing on the request cannot be previewed`() {
        val request = request()

        listOf(DECISION_MAKER, REVIEWER).forEach { party ->
            assertThrows<InformationRequestCommandRequestException>(party) {
                previews.preview(request.requestId, request.parties.getValue(party), request.owner)
            }
        }
    }

    @Test
    fun `only a caller who manages the request may preview a party`() {
        val request = request()

        assertFalse(
            gate.permitsRequest(
                request.access(CONTRIBUTOR),
                Action.INFORMATION_REQUEST_MANAGE_PARTIES,
                request.requestId
            )
        )
        assertThrows<ForbiddenException> {
            previews.preview(request.requestId, request.parties.getValue(PREPARER), request.access(CONTRIBUTOR))
        }
    }

    @Test
    fun `a party of another request is not found`() {
        val request = request()
        val other = request()

        val refusal = assertThrows<InformationRequestLifecycleException> {
            previews.preview(request.requestId, other.parties.getValue(CONTRIBUTOR), request.owner)
        }

        assertEquals(InformationRequestErrorCatalog.NOT_FOUND, refusal.reasonCode)
    }

    @Test
    fun `the preview is read over HTTP by the owner and refused to a party who does not manage the request`() {
        val request = request()
        val path =
            "/information-requests/${request.requestId}/parties/${request.parties.getValue(CONTRIBUTOR)}/recipient-preview"

        val read = InformationRequestSignedInCaller.signIn(appUsers, request.ownerId).authorized().get(path)
        val refused =
            InformationRequestSignedInCaller.signIn(appUsers, request.users.getValue(CONTRIBUTOR)).authorized()
                .get(path)

        assertEquals(200, read.statusCode, read.asString())
        assertEquals(
            setOf(RECORD_IDENTIFIER, RECORD_DETAIL),
            read.jsonPath().getList<String>("templateVersion.sections.requirements.flatten().requirementKey").toSet(),
        )
        assertEquals("CONTRIBUTOR", read.jsonPath().getString("roleKey"))
        assertEquals(request.parties.getValue(CONTRIBUTOR).toString(), read.jsonPath().getString("partyId"))
        assertEquals(403, refused.statusCode, refused.asString())
    }

    private fun keys(templateVersion: InformationRequestTemplateVersionDto): Set<String> =
        templateVersion.sections.flatMap { it.requirements }.map { it.requirementKey }.toSet()

    private fun request(): PublishedRequest =
        support.issue(
            "preview-${UUID.randomUUID()}",
            listOf(RECORD_IDENTIFIER, RECORD_DETAIL, WITHHELD_NOTE, PREPARER_SUMMARY),
            listOf(CONTRIBUTOR, PREPARER, DECISION_MAKER, REVIEWER),
        ) { schema, fields ->
            fun field(
                key: String,
                role: InformationRequestContributorRole,
                mode: InformationRequestResponseMode = InformationRequestResponseMode.PROVIDE
            ) =
                InformationRequestTemplateRequirementRequest(
                    requirementKey = key,
                    requirementType = InformationRequestRequirementType.FIELD,
                    prompt = "Provide $key",
                    responseMode = mode,
                    requiredness = InformationRequestRequiredness.OPTIONAL,
                    contributorRole = role,
                    collectedFieldDefinitionId = fields.getValue(key).fieldDefinitionId,
                    permittedDispositions = if (mode == InformationRequestResponseMode.NOT_DISCLOSED) emptyList()
                    else listOf(InformationRequestResponseDisposition.PROVIDED),
                )
            InformationRequestTemplateConfigurationRequest(
                schemaVersionId = schema,
                sections = listOf(
                    InformationRequestTemplateSectionRequest(
                        sectionKey = "records",
                        title = "Records",
                        requirements = listOf(
                            field(RECORD_IDENTIFIER, InformationRequestContributorRole.CONTRIBUTOR),
                            field(RECORD_DETAIL, InformationRequestContributorRole.CONTRIBUTOR).copy(
                                requiredness = InformationRequestRequiredness.CONDITIONAL,
                                conditionalRuleKey = DETAIL_RULE,
                            ),
                            field(
                                WITHHELD_NOTE,
                                InformationRequestContributorRole.CONTRIBUTOR,
                                InformationRequestResponseMode.NOT_DISCLOSED
                            ),
                        ),
                    ),
                    InformationRequestTemplateSectionRequest(
                        sectionKey = "summaries",
                        title = "Summaries",
                        requirements = listOf(field(PREPARER_SUMMARY, InformationRequestContributorRole.PREPARER)),
                    ),
                ),
                conditionRules = listOf(
                    InformationRequestTemplateConditionRuleRequest(
                        ruleKey = DETAIL_RULE,
                        predicates = listOf(
                            InformationRequestTemplateConditionPredicateRequest(
                                fieldDefinitionId = fields.getValue(RECORD_IDENTIFIER).fieldDefinitionId,
                                valueType = FieldValueType.SHORT_TEXT,
                                operator = FieldOperator.EQUALS,
                                value = JsonPrimitive("detailed"),
                            ),
                        ),
                    ),
                ),
            )
        }.also { request ->
            dataSource.connection.use { connection ->
                val partyId = request.parties.getValue(DECISION_MAKER)
                execute(
                    connection,
                    "UPDATE share SET principal_id = ? WHERE id = (SELECT share_id FROM information_request_party WHERE id = ?)",
                    request.ownerId,
                    partyId,
                )
                execute(
                    connection,
                    "UPDATE information_request_party SET principal_id = ? WHERE id = ?",
                    request.ownerId,
                    partyId
                )
            }
        }

    private companion object {
        const val CONTRIBUTOR = "CONTRIBUTOR"
        const val PREPARER = "PREPARER"
        const val DECISION_MAKER = "DECISION_MAKER"
        const val REVIEWER = "REVIEWER"
        const val RECORD_IDENTIFIER = "record-identifier"
        const val RECORD_DETAIL = "record-detail"
        const val WITHHELD_NOTE = "withheld-note"
        const val PREPARER_SUMMARY = "preparer-summary"
        const val DETAIL_RULE = "detail-rule"
    }
}
