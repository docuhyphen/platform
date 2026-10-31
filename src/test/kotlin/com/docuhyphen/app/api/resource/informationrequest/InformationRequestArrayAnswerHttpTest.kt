package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionRequest
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
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
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestArrayAnswerHttpTest {
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
    fun `sending reminders answers the reminder of each request as a JSON array`() {
        val request = issued()
        val owner = InformationRequestSignedInCaller.signIn(appUsers, request.ownerId)

        val response = owner.command("remind-${UUID.randomUUID()}")
            .body("""{"requestIds":["${request.requestId}"]}""")
            .post("/information-request-reminders")

        assertEquals(201, response.statusCode, response.asString())
        assertEquals(listOf(request.requestId.toString()), response.jsonPath().getList<String>("requestId"))
        assertEquals(listOf(1), response.jsonPath().getList<Int>("noticeCount"))
    }

    @Test
    fun `delegated authority references and revocation reasons are bounded before storage`() {
        val request = issued()
        val owner = InformationRequestSignedInCaller.signIn(appUsers, request.ownerId)
        val base = "/information-requests/${request.requestId}"
        fun etag(): String = owner.authorized().get("$base/delegated-authorities").then()
            .statusCode(200).extract().header("ETag")

        fun grant(length: Int) = owner.command("grant-${UUID.randomUUID()}").header("If-Match", etag())
            .body(
                """{"assignedPartyId":"${request.parties.getValue("CONTRIBUTOR")}","delegatePrincipalKind":"USER","delegatePrincipalId":"${
                    request.users.getValue(
                        "DECISION_MAKER"
                    )
                }","authorityInstrumentRef":"${"x".repeat(length)}"}"""
            )
            .post("$base/delegated-authorities")

        val valid = grant(512)
        assertEquals(201, valid.statusCode, valid.asString())
        val oversized = grant(513)
        val authorityId = valid.jsonPath().getString("id")
        val oversizedReason = owner.command("revoke-${UUID.randomUUID()}").header("If-Match", etag())
            .body("""{"reason":"${"r".repeat(513)}"}""")
            .post("$base/delegated-authorities/$authorityId/revocations")
        org.junit.jupiter.api.Assertions.assertAll(
            { assertEquals(400, oversized.statusCode, oversized.asString()) },
            { assertEquals(400, oversizedReason.statusCode, oversizedReason.asString()) },
        )
    }

    @Test
    fun `listing delegated authorities answers a JSON array`() {
        val request = issued()
        val owner = InformationRequestSignedInCaller.signIn(appUsers, request.ownerId)

        val response = owner.authorized().get("/information-requests/${request.requestId}/delegated-authorities")

        assertEquals(200, response.statusCode, response.asString())
        assertEquals(emptyList<Any>(), response.jsonPath().getList<Any>("$"))
    }

    private fun issued(): PublishedRequest =
        support.issue(
            "array-answer-${UUID.randomUUID()}",
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
