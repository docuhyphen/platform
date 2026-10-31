package com.docuhyphen.app.api.resource.informationrequest.parent

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
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusMock
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestExchangeDeletionHttpTest {
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
    fun `deleting an Exchange cancels its unfinished requests, so they no longer count as open for the owner`() {
        val request = issued()
        val owner = InformationRequestSignedInCaller.signIn(appUsers, request.ownerId)
        assertEquals(1L, openFor(request))

        val response = owner.authorized().delete("/exchanges/${request.exchangeId}")

        assertEquals(200, response.statusCode, response.asString())
        assertEquals("CANCELLED", stateOf(request))
        assertEquals("PARENT_DELETED", latestTransitionReason(request))
        assertEquals(0L, openFor(request))
    }

    private fun openFor(request: PublishedRequest): Long =
        QuarkusTransaction.requiringNew()
            .call { requests.countOpenForOwner(InformationRequestOwnerType.USER, request.ownerId) }

    private fun stateOf(request: PublishedRequest): String? =
        dataSource.connection.use { connection ->
            queryString(connection, "SELECT state FROM information_request WHERE id = ?", request.requestId)
        }

    private fun latestTransitionReason(request: PublishedRequest): String? =
        dataSource.connection.use { connection ->
            queryString(
                connection,
                """
                SELECT reason_code FROM information_request_transition
                WHERE information_request_id = ? AND mutation = 'CANCEL'
                ORDER BY sequence_number DESC LIMIT 1
                """.trimIndent(),
                request.requestId,
            )
        }

    private fun issued(): PublishedRequest =
        support.issue(
            "exchange-deletion-${UUID.randomUUID()}",
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
