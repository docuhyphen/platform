package com.docuhyphen.app.api.resource.informationrequest.party

import com.docuhyphen.app.api.migration.queryInt
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
import io.restassured.http.ContentType
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestSubjectRaceHttpTest {
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
    fun `two simultaneous subject assignments under one Idempotency-Key leave one subject and one party`() {
        val request = issued()
        val owner = InformationRequestSignedInCaller.signIn(appUsers, request.ownerId)
        val partiesETag = owner.authorized().get("/information-requests/${request.requestId}/parties").header("ETag")
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2) { task -> Thread(task, "subject-race") }
        val responses = try {
            val calls = (1..2).map {
                pool.submit(Callable {
                    start.await(10, TimeUnit.SECONDS)
                    owner.authorized()
                        .contentType(ContentType.JSON)
                        .header("If-Match", partiesETag)
                        .header("Idempotency-Key", "subject-race-${request.requestId}")
                        .body("""{"subjectKind":"RECORD"}""")
                        .post("/information-requests/${request.requestId}/subjects")
                })
            }
            start.countDown()
            calls.map { it.get(60, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }

        assertEquals(listOf(201, 201), responses.map { it.statusCode }, responses.joinToString { it.asString() })
        assertEquals(1, responses.map { it.jsonPath().getString("id") }.toSet().size)
        dataSource.connection.use { connection ->
            assertEquals(
                1,
                queryInt(
                    connection,
                    "SELECT count(*) FROM information_request_party WHERE information_request_id = ? AND role_key = 'SUBJECT'",
                    request.requestId,
                ),
            )
        }
    }

    private fun issued(): PublishedRequest =
        support.issue(
            "subject-race-${UUID.randomUUID()}",
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
