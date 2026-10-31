package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateGroupRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionRequest
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.resource.informationrequest.model.HiddenTargetRoute
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.capability.model.WalkingSkeletonField
import com.docuhyphen.app.api.service.informationrequest.conformance.PublishedRequestSupport
import com.docuhyphen.app.api.service.informationrequest.conformance.model.PublishedRequest
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationWriter
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateMaterializer
import com.docuhyphen.app.api.service.user.AppUserService
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.restassured.http.ContentType
import io.restassured.response.Response
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestHiddenGroupHttpTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var appUsers: AppUserService

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
    fun `a respondent cannot tell a group it may not change from a group key that does not exist when adding to it`() {
        val request = issue()

        val hidden = send(request, ADD, NOTE)
        val unknown = send(request, ADD, "unknown-${UUID.randomUUID()}")

        assertRefusedAlike(hidden, unknown)
    }

    @Test
    fun `a respondent cannot tell a group it may not change from a group key that does not exist when ordering it`() {
        val request = issue()

        val hidden = send(request, ORDER, NOTE)
        val unknown = send(request, ORDER, "unknown-${UUID.randomUUID()}")

        assertRefusedAlike(hidden, unknown)
    }

    @Test
    fun `a respondent cannot tell an occurrence it may not change from one that does not exist when removing it`() {
        val request = issue()

        val hidden = send(request, REMOVE, occurrenceId(request, "$NOTE[0]").toString())
        val unknown = send(request, REMOVE, UUID.randomUUID().toString())

        assertRefusedAlike(hidden, unknown)
    }

    @Test
    fun `a respondent cannot tell another group's occurrence from an unknown one when naming the parent of a nested occurrence`() {
        val request = issue()
        val entry = send(request, ADD, ENTRY)
        assertEquals(200, entry.statusCode, entry.asString())

        val foreign = send(request, ADD_NESTED, occurrenceId(request, "$NOTE[0]").toString())
        val unknown = send(request, ADD_NESTED, UUID.randomUUID().toString())

        assertEquals(409, foreign.statusCode, foreign.asString())
        assertEquals(foreign.statusCode, unknown.statusCode, unknown.asString())
        assertEquals(foreign.asString(), unknown.asString())
    }

    @Test
    fun `control a respondent still adds, orders, nests and removes occurrences of its own groups`() {
        val request = issue()

        val added = send(request, ADD, ENTRY)
        assertEquals(200, added.statusCode, added.asString())
        val entryId = occurrenceId(request, "$ENTRY[0]").toString()
        val nested = send(request, ADD_NESTED, entryId)
        assertEquals(200, nested.statusCode, nested.asString())
        val ordered = send(request, ORDER_ONE, ENTRY, entryId)
        assertEquals(200, ordered.statusCode, ordered.asString())
        val removed = send(request, REMOVE, entryId)
        assertEquals(200, removed.statusCode, removed.asString())
    }

    private fun assertRefusedAlike(hidden: Response, unknown: Response) {
        assertEquals(403, hidden.statusCode, hidden.asString())
        assertEquals(hidden.statusCode, unknown.statusCode, unknown.asString())
        assertEquals(hidden.asString(), unknown.asString())
    }

    private fun send(
        request: PublishedRequest,
        route: HiddenTargetRoute,
        target: String,
        other: String = target
    ): Response {
        val spec = InformationRequestSignedInCaller.signIn(appUsers, request.users.getValue(CONTRIBUTOR)).authorized()
            .header("Idempotency-Key", "hidden-group-${UUID.randomUUID()}")
            .header("If-Match", responseETag(request))
            .contentType(ContentType.JSON)
        val body = route.body(target, UUID.randomUUID())?.replace("{occurrence}", other)
        return (if (body == null) spec else spec.body(body))
            .request(route.verb, route.path("", request.requestId, target, UUID.randomUUID()))
    }

    private fun responseETag(request: PublishedRequest): String =
        QuarkusTransaction.requiringNew().call {
            InformationRequestETag.responsesOf(requireNotNull(requests.findById(request.requestId)))
        }

    private fun occurrenceId(request: PublishedRequest, path: String): UUID =
        dataSource.connection.use { connection ->
            UUID.fromString(
                requireNotNull(
                    queryString(
                        connection,
                        """
                        SELECT id::text FROM information_request_group_occurrence
                        WHERE information_request_id = ? AND occurrence_path = ? AND removed_at IS NULL
                        """.trimIndent(),
                        request.requestId,
                        path,
                    ),
                ),
            )
        }

    private fun issue(): PublishedRequest =
        support.issue(
            "hidden-group-${UUID.randomUUID()}",
            listOf(ENTRY_LABEL, DETAIL_LABEL, NOTE_LABEL),
            listOf(CONTRIBUTOR, PREPARER)
        ) { schemaVersionId, fields ->
            InformationRequestTemplateConfigurationRequest(
                schemaVersionId = schemaVersionId,
                groups = listOf(
                    InformationRequestTemplateGroupRequest(groupKey = ENTRY, maxOccurrences = 4),
                    InformationRequestTemplateGroupRequest(
                        groupKey = DETAIL,
                        parentGroupKey = ENTRY,
                        maxOccurrences = 4
                    ),
                    InformationRequestTemplateGroupRequest(groupKey = NOTE, minOccurrences = 1, maxOccurrences = 4),
                ),
                sections = listOf(
                    InformationRequestTemplateSectionRequest(
                        sectionKey = "entries",
                        title = "Entries",
                        requirements = listOf(
                            labelRequirement(ENTRY_LABEL, ENTRY, InformationRequestContributorRole.CONTRIBUTOR, fields),
                            labelRequirement(
                                DETAIL_LABEL,
                                DETAIL,
                                InformationRequestContributorRole.CONTRIBUTOR,
                                fields
                            ),
                            labelRequirement(NOTE_LABEL, NOTE, InformationRequestContributorRole.PREPARER, fields),
                        ),
                    ),
                ),
            )
        }

    private fun labelRequirement(
        key: String,
        anchor: String,
        role: InformationRequestContributorRole,
        fields: Map<String, WalkingSkeletonField>,
    ) = InformationRequestTemplateRequirementRequest(
        requirementKey = key,
        requirementType = InformationRequestRequirementType.FIELD,
        prompt = "Name the item",
        responseMode = InformationRequestResponseMode.PROVIDE,
        requiredness = InformationRequestRequiredness.OPTIONAL,
        contributorRole = role,
        occurrenceAnchorKey = anchor,
        collectedFieldDefinitionId = fields.getValue(key).fieldDefinitionId,
        permittedDispositions = listOf(InformationRequestResponseDisposition.PROVIDED),
    )

    private companion object {
        const val CONTRIBUTOR = "CONTRIBUTOR"
        const val PREPARER = "PREPARER"
        const val ENTRY = "entry"
        const val DETAIL = "entry-detail"
        const val NOTE = "note"
        const val ENTRY_LABEL = "entry-label"
        const val DETAIL_LABEL = "detail-label"
        const val NOTE_LABEL = "note-label"
        const val OCCURRENCES = "/information-requests/{id}/group-occurrences"

        val ADD = HiddenTargetRoute("POST", OCCURRENCES, "{\"groupKey\":\"{target}\"}")
        val ADD_NESTED =
            HiddenTargetRoute("POST", OCCURRENCES, "{\"groupKey\":\"$DETAIL\",\"parentOccurrenceId\":\"{target}\"}")
        val ORDER = HiddenTargetRoute("PATCH", "$OCCURRENCES/order", "{\"groupKey\":\"{target}\",\"occurrenceIds\":[]}")
        val ORDER_ONE = HiddenTargetRoute(
            "PATCH",
            "$OCCURRENCES/order",
            "{\"groupKey\":\"{target}\",\"occurrenceIds\":[\"{occurrence}\"]}"
        )
        val REMOVE = HiddenTargetRoute("DELETE", "$OCCURRENCES/{target}")
    }
}
