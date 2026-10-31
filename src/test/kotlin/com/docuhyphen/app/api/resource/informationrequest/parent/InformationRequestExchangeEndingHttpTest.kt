package com.docuhyphen.app.api.resource.informationrequest.parent

import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryString
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestExchangeEndingHttpTest {
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

    @BeforeEach
    fun quietEmail() {
        QuarkusMock.installMockForType(mock<EmailService>(), EmailService::class.java)
    }

    @Test
    fun `an Exchange Editor who may not see its requests learns neither their ids nor their number when ending is refused`() {
        val request = issued()
        val editor = InformationRequestSignedInCaller.signIn(appUsers, exchangeEditor(request))
        val owner = InformationRequestSignedInCaller.signIn(appUsers, request.ownerId)

        val editorRefusal = end(editor, request, cancelRemaining = false)
        val ownerRefusal = end(owner, request, cancelRemaining = false)

        assertEquals(409, editorRefusal.statusCode, editorRefusal.asString())
        assertEquals(
            "INFORMATION_REQUEST_COMPLETION_GATES_UNSATISFIED",
            editorRefusal.jsonPath().getString("reasonCode")
        )
        assertEquals(emptyList<String>(), editorRefusal.jsonPath().getList<String>("informationRequestIds"))
        assertEquals(
            listOf(request.requestId.toString()),
            ownerRefusal.jsonPath().getList<String>("informationRequestIds")
        )
    }

    @Test
    fun `an Exchange Editor cannot cancel requests it may not manage by ending the Exchange`() {
        val request = issued()
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE information_request SET gates_exchange_closure = FALSE WHERE id = ?",
                request.requestId
            )
        }
        val editor = InformationRequestSignedInCaller.signIn(appUsers, exchangeEditor(request))

        val refusal = end(editor, request, cancelRemaining = true)

        assertEquals(409, refusal.statusCode, refusal.asString())
        assertEquals(
            "INFORMATION_REQUEST_REMAINING_REQUESTS_NOT_CANCELLABLE",
            refusal.jsonPath().getString("reasonCode")
        )
        assertEquals(emptyList<String>(), refusal.jsonPath().getList<String>("informationRequestIds"))
        dataSource.connection.use { connection ->
            assertEquals(
                "ISSUED",
                queryString(connection, "SELECT state FROM information_request WHERE id = ?", request.requestId)
            )
            assertEquals(
                "ACCEPTED_STARTED",
                queryString(connection, "SELECT status FROM exchange WHERE id = ?", request.exchangeId)
            )
        }
    }

    private fun end(
        caller: InformationRequestSignedInCaller,
        request: PublishedRequest,
        cancelRemaining: Boolean
    ): Response =
        caller.authorized()
            .contentType(ContentType.JSON)
            .body("""{"status":"ENDED","cancelRemainingInformationRequests":$cancelRemaining}""")
            .put("/exchanges/${request.exchangeId}")

    private fun exchangeEditor(request: PublishedRequest): UUID {
        val userId = support.outsider()
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
                VALUES (?, 'EXCHANGE', ?, 'USER', ?, 'EDITOR', 'DIRECT', 'ACTIVE', now())
                """.trimIndent(),
                UUID.randomUUID(),
                request.exchangeId,
                userId,
            )
        }
        return userId
    }

    private fun issued(): PublishedRequest =
        support.issue(
            "exchange-ending-${UUID.randomUUID()}",
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
