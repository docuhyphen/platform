package com.docuhyphen.app.api.resource.informationrequest.party

import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionRequest
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestSignedInCaller
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.conformance.PublishedRequestSupport
import com.docuhyphen.app.api.service.informationrequest.conformance.model.PublishedRequest
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationWriter
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateMaterializer
import com.docuhyphen.app.api.service.user.AppUserService
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.restassured.path.json.JsonPath
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestAssignedWorkHttpTest {
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
    fun `a signed-in party who holds no Share on the Exchange finds the request among the work assigned to them`() {
        val request = issued()
        val contributor = InformationRequestSignedInCaller.signIn(appUsers, request.users.getValue("CONTRIBUTOR"))

        val exchange = contributor.authorized().get("/exchanges/${request.exchangeId}")
        val assigned = contributor.authorized().get("/information-request-assignments")

        assertEquals(404, exchange.statusCode, exchange.asString())
        assertEquals(200, assigned.statusCode, assigned.asString())
        val entries = assigned.jsonPath()
        assertEquals(listOf(request.requestId.toString()), entries.getList<String>("request.id"))
        assertEquals("RESPOND", entries.getString("[0].request.nextAction"))
        assertEquals(listOf("CONTRIBUTOR"), entries.getList<String>("[0].request.callerRoles"))
        assertEquals("Process collection", entries.getString("[0].exchangeName"))
    }

    @Test
    fun `a member of a group party finds the group's request, and a revoked party no longer lists it`() {
        val request = issued()
        val member = support.outsider()
        val partyId = groupParty(request, member)
        val caller = InformationRequestSignedInCaller.signIn(appUsers, member)

        val listed = caller.authorized().get("/information-request-assignments").jsonPath()
        revoke(partyId)
        val afterRevocation = caller.authorized().get("/information-request-assignments").jsonPath()

        assertEquals(listOf(request.requestId.toString()), listed.getList<String>("request.id"))
        assertEquals(emptyList<String>(), afterRevocation.getList<String>("request.id"))
    }

    @Test
    fun `a finished request leaves the assigned work and the menu flag of its party`() {
        val request = issued()
        val contributor = InformationRequestSignedInCaller.signIn(appUsers, request.users.getValue("CONTRIBUTOR"))
        val flaggedWhileOpen = contributor.authorized().get("/information-request-capabilities")
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE information_request SET state = 'CANCELLED', cancelled_at = now() WHERE id = ?",
                request.requestId
            )
        }

        val assigned = contributor.authorized().get("/information-request-assignments")
        val capabilities = contributor.authorized().get("/information-request-capabilities")

        assertEquals(true, flaggedWhileOpen.jsonPath().getBoolean("assignedWork"), flaggedWhileOpen.asString())
        assertEquals(200, assigned.statusCode, assigned.asString())
        assertEquals(emptyList<String>(), assigned.jsonPath().getList<String>("request.id"))
        assertEquals(false, capabilities.jsonPath().getBoolean("assignedWork"), capabilities.asString())
    }

    @Test
    fun `someone with no party role has no assigned work`() {
        issued()
        val outsider = InformationRequestSignedInCaller.signIn(appUsers, support.outsider())

        val assigned = outsider.authorized().get("/information-request-assignments")

        assertEquals(200, assigned.statusCode, assigned.asString())
        assertEquals(emptyList<String>(), JsonPath(assigned.asString()).getList<String>("request.id"))
    }

    private fun groupParty(request: PublishedRequest, member: UUID): UUID {
        val groupId = UUID.randomUUID()
        val shareId = UUID.randomUUID()
        val partyId = UUID.randomUUID()
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO principal_group (id, name, scope, owner_app_user_id, externally_published, is_active, created_date)
                VALUES (?, 'Process contributors', 'PERSONAL', ?, FALSE, TRUE, now())
                """.trimIndent(),
                groupId,
                request.ownerId,
            )
            execute(
                connection,
                """
                INSERT INTO principal_group_member (id, principal_group_id, principal_kind, principal_id, group_role, added_at, is_active)
                VALUES (?, ?, 'USER', ?, 'MEMBER', now(), TRUE)
                """.trimIndent(),
                UUID.randomUUID(),
                groupId,
                member,
            )
            execute(
                connection,
                """
                INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
                VALUES (?, 'INFORMATION_REQUEST', ?, 'PRINCIPAL_GROUP', ?, 'PREPARER', 'DIRECT', 'ACTIVE', now())
                """.trimIndent(),
                shareId,
                request.requestId,
                groupId,
            )
            execute(
                connection,
                """
                INSERT INTO information_request_party
                    (id, information_request_id, role_key, principal_kind, principal_id, share_id, active, party_revision,
                     assigned_at, created_at, updated_at)
                VALUES (?, ?, 'PREPARER', 'PRINCIPAL_GROUP', ?, ?, TRUE, 1, now(), now(), now())
                """.trimIndent(),
                partyId,
                request.requestId,
                groupId,
                shareId,
            )
        }
        return partyId
    }

    private fun revoke(partyId: UUID) {
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                UPDATE share SET status = 'REVOKED', revoked_at = now()
                WHERE id = (SELECT share_id FROM information_request_party WHERE id = ?)
                """.trimIndent(),
                partyId,
            )
            execute(
                connection,
                "UPDATE information_request_party SET active = FALSE, revoked_at = now(), updated_at = now() WHERE id = ?",
                partyId,
            )
        }
    }

    private fun issued(): PublishedRequest =
        support.issue(
            "assigned-work-${UUID.randomUUID()}",
            listOf("record-identifier"),
            listOf("CONTRIBUTOR", "DECISION_MAKER")
        ) { schema, fields ->
            InformationRequestTemplateConfigurationRequest(
                schemaVersionId = schema,
                sections = listOf(
                    InformationRequestTemplateSectionRequest(
                        sectionKey = "records",
                        title = "Records",
                        requirements = listOf(
                            InformationRequestTemplateRequirementRequest(
                                requirementKey = "record-identifier",
                                requirementType = InformationRequestRequirementType.FIELD,
                                prompt = "Provide a record identifier",
                                responseMode = InformationRequestResponseMode.PROVIDE,
                                requiredness = InformationRequestRequiredness.REQUIRED,
                                contributorRole = InformationRequestContributorRole.CONTRIBUTOR,
                                collectedFieldDefinitionId = fields.getValue("record-identifier").fieldDefinitionId,
                                permittedDispositions = listOf(InformationRequestResponseDisposition.PROVIDED),
                            ),
                        ),
                    ),
                ),
            )
        }
}
