package com.docuhyphen.app.api.resource.exchange

import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.migration.queryStrings
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
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.conformance.PublishedRequestSupport
import com.docuhyphen.app.api.service.informationrequest.conformance.model.PublishedRequest
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationWriter
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateMaterializer
import com.docuhyphen.app.api.service.user.AppUserService
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusMock
import io.quarkus.test.junit.QuarkusTest
import io.restassured.http.ContentType
import io.restassured.response.Response
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.mock
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class ExchangeStatusTransitionHttpTest {
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
    fun `a viewer cannot return an active Exchange to INITIATED, so its requests stay answerable`() {
        val request = issued()
        val viewer =
            InformationRequestSignedInCaller.signIn(appUsers, exchangeShareHolder(request.exchangeId, "VIEWER"))

        val response = statusChange(viewer, request.exchangeId, "INITIATED")

        assertEquals(400, response.statusCode, response.asString())
        assertEquals("ACCEPTED_STARTED", statusOf(request.exchangeId))
        assertEquals(emptySet<String>(), lifecycleEventKeys(request.exchangeId))
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["ACCEPTED_STARTED", "REJECTED"])
    fun `a viewer cannot write a rejection reason through the generic update, also on a finished Exchange`(status: String) {
        val request = issued()
        if (status == "REJECTED") finish(request, status)
        val viewer =
            InformationRequestSignedInCaller.signIn(appUsers, exchangeShareHolder(request.exchangeId, "VIEWER"))

        val response = viewer.authorized()
            .contentType(ContentType.JSON)
            .body("""{"rejectionReason":"Replaced by a viewer"}""")
            .put("/exchanges/${request.exchangeId}")

        assertEquals(400, response.statusCode, response.asString())
        assertEquals("", rejectionReasonOf(request.exchangeId))
        assertEquals(status, statusOf(request.exchangeId))
    }

    @Test
    fun `a caller who may only view an Exchange is refused when ending it, not answered with a server error`() {
        val request = issued()
        val viewer =
            InformationRequestSignedInCaller.signIn(appUsers, exchangeShareHolder(request.exchangeId, "VIEWER"))

        val response = statusChange(viewer, request.exchangeId, "ENDED")

        assertEquals(403, response.statusCode, response.asString())
        assertEquals("ACCEPTED_STARTED", statusOf(request.exchangeId))
        assertEquals(emptySet<String>(), lifecycleEventKeys(request.exchangeId))
    }

    @Test
    fun `the owner cannot return an active Exchange to INITIATED either`() {
        val request = issued()
        val owner = InformationRequestSignedInCaller.signIn(appUsers, request.ownerId)

        val response = statusChange(owner, request.exchangeId, "INITIATED")

        assertEquals(400, response.statusCode, response.asString())
        assertEquals("ACCEPTED_STARTED", statusOf(request.exchangeId))
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["ENDED", "REJECTED", "RESCINDED"])
    fun `a finished Exchange accepts no further status change through the generic update`(finished: String) {
        QuarkusMock.installMockForType(mock<EmailService>(), EmailService::class.java)
        val request = issued()
        finish(request, finished)
        val owner = InformationRequestSignedInCaller.signIn(appUsers, request.ownerId)

        val ending = statusChange(owner, request.exchangeId, "ENDED")
        val reopening = statusChange(owner, request.exchangeId, "INITIATED")

        assertEquals(409, ending.statusCode, ending.asString())
        assertTrue(ending.asString().contains("cannot change to ENDED"), ending.asString())
        assertEquals(400, reopening.statusCode, reopening.asString())
        assertEquals(finished, statusOf(request.exchangeId))
        assertEquals(emptySet<String>(), lifecycleEventKeys(request.exchangeId))
    }

    @Test
    fun `ending an active Exchange is the one generic status change and it is recorded on the audit ledger`() {
        QuarkusMock.installMockForType(mock<EmailService>(), EmailService::class.java)
        val request = issued()
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE information_request SET gates_exchange_closure = FALSE WHERE id = ?",
                request.requestId
            )
        }
        val owner = InformationRequestSignedInCaller.signIn(appUsers, request.ownerId)

        val ended = owner.authorized()
            .contentType(ContentType.JSON)
            .body("""{"status":"ENDED","cancelRemainingInformationRequests":true}""")
            .put("/exchanges/${request.exchangeId}")

        assertEquals(200, ended.statusCode, ended.asString())
        assertEquals("ENDED", statusOf(request.exchangeId))
        assertEquals(setOf("exchange.lifecycle.ended"), lifecycleEventKeys(request.exchangeId))
        assertTrue(lifecyclePayloads(request.exchangeId).single().contains("\"previousStatus\":\"ACCEPTED_STARTED\""))
    }

    @Test
    fun `requiring recipient sign-in on an Exchange whose recipient verified access keeps the requirement and its validity`() {
        val request = issued()
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE exchange SET no_auth_access_verified_at = now() WHERE id = ?",
                request.exchangeId
            )
        }
        val owner = InformationRequestSignedInCaller.signIn(appUsers, request.ownerId)

        val response = owner.authorized()
            .contentType(ContentType.JSON)
            .body("""{"requireRecipientSignIn":true,"noAuthAccessValidityDays":3}""")
            .put("/exchanges/${request.exchangeId}")

        assertEquals(200, response.statusCode, response.asString())
        dataSource.connection.use { connection ->
            assertEquals(
                "true|3|",
                queryString(
                    connection,
                    """
                    SELECT require_recipient_sign_in::text || '|' || no_auth_access_validity_days || '|' ||
                           COALESCE(no_auth_access_verified_at::text, '')
                    FROM exchange WHERE id = ?
                    """.trimIndent(),
                    request.exchangeId,
                ),
            )
        }
    }

    private fun statusChange(caller: InformationRequestSignedInCaller, exchangeId: UUID, status: String): Response =
        caller.authorized()
            .contentType(ContentType.JSON)
            .body("""{"status":"$status"}""")
            .put("/exchanges/$exchangeId")

    private fun exchangeShareHolder(exchangeId: UUID, role: String): UUID {
        val userId = support.outsider()
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
                VALUES (?, 'EXCHANGE', ?, 'USER', ?, ?, 'DIRECT', 'ACTIVE', now())
                """.trimIndent(),
                UUID.randomUUID(),
                exchangeId,
                userId,
                role,
            )
        }
        return userId
    }

    private fun finish(request: PublishedRequest, status: String) {
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE exchange SET status = ?, end_date = now() WHERE id = ?",
                status,
                request.exchangeId
            )
            execute(
                connection,
                "UPDATE information_request SET state = 'CANCELLED', cancelled_at = now() WHERE id = ?",
                request.requestId,
            )
        }
    }

    private fun rejectionReasonOf(exchangeId: UUID): String? =
        dataSource.connection.use { connection ->
            queryString(connection, "SELECT COALESCE(rejection_reason, '') FROM exchange WHERE id = ?", exchangeId)
        }

    private fun statusOf(exchangeId: UUID): String? =
        dataSource.connection.use { connection ->
            queryString(
                connection,
                "SELECT status FROM exchange WHERE id = ?",
                exchangeId
            )
        }

    private fun lifecycleEventKeys(exchangeId: UUID): Set<String> =
        dataSource.connection.use { connection ->
            queryStrings(
                connection,
                "SELECT event_type_key FROM audit_outbox WHERE target_id = ? AND event_type_key LIKE 'exchange.lifecycle.%'",
                exchangeId.toString(),
            )
        }

    private fun lifecyclePayloads(exchangeId: UUID): Set<String> =
        dataSource.connection.use { connection ->
            queryStrings(
                connection,
                "SELECT payload_json FROM audit_outbox WHERE target_id = ? AND event_type_key LIKE 'exchange.lifecycle.%'",
                exchangeId.toString(),
            )
        }

    private fun issued(): PublishedRequest =
        support.issue(
            "status-transition-${UUID.randomUUID()}",
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
                                requiredness = InformationRequestRequiredness.OPTIONAL,
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
