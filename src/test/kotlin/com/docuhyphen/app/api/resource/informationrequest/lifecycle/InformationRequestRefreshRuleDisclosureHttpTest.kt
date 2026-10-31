package com.docuhyphen.app.api.resource.informationrequest.lifecycle

import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateEvidencePolicyRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionRequest
import com.docuhyphen.app.api.model.entity.InformationRequestContributorRole
import com.docuhyphen.app.api.model.entity.InformationRequestRequiredness
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
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
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestRefreshRuleDisclosureHttpTest {
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
    fun `the follow-up history names a refresh rule only to people who may see its Requirement`() {
        val request = issued()
        refreshRule(request, "own-record")
        refreshRule(request, "prepared-record")
        val contributor = InformationRequestSignedInCaller.signIn(appUsers, request.users.getValue("CONTRIBUTOR"))
        val owner = InformationRequestSignedInCaller.signIn(appUsers, request.ownerId)

        val asContributor = contributor.authorized().get("/information-requests/${request.requestId}/successors")
        val asOwner = owner.authorized().get("/information-requests/${request.requestId}/successors")

        assertEquals(200, asContributor.statusCode, asContributor.asString())
        assertEquals(listOf("own-record"), asContributor.jsonPath().getList<String>("refreshRules.requirementKey"))
        assertEquals(
            listOf("own-record", "prepared-record"),
            asOwner.jsonPath().getList<String>("refreshRules.requirementKey")
        )
    }

    private fun refreshRule(request: PublishedRequest, key: String) {
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO information_request_refresh_rule
                    (id, information_request_id, requirement_key, lead_days, created_by_principal_kind, created_by_principal_id, created_at)
                VALUES (?, ?, ?, 30, 'USER', ?, now())
                """.trimIndent(),
                UUID.randomUUID(),
                request.requestId,
                key,
                request.ownerId,
            )
        }
    }

    private fun issued(): PublishedRequest =
        support.issue(
            "refresh-disclosure-${UUID.randomUUID()}",
            emptyList(),
            listOf("CONTRIBUTOR", "PREPARER", "DECISION_MAKER")
        ) { _, _ ->
            InformationRequestTemplateConfigurationRequest(
                sections = listOf(
                    InformationRequestTemplateSectionRequest(
                        sectionKey = "records",
                        title = "Records",
                        requirements = listOf(
                            document("own-record", InformationRequestContributorRole.CONTRIBUTOR),
                            document("prepared-record", InformationRequestContributorRole.PREPARER),
                        ),
                    ),
                ),
            )
        }

    private fun document(key: String, role: InformationRequestContributorRole) =
        InformationRequestTemplateRequirementRequest(
            requirementKey = key,
            requirementType = InformationRequestRequirementType.DOCUMENT,
            prompt = "Provide the record",
            requiredness = InformationRequestRequiredness.OPTIONAL,
            contributorRole = role,
            permittedDispositions = listOf(InformationRequestResponseDisposition.PROVIDED),
            evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(
                minimumFileCount = 1,
                maximumFileCount = 2
            ),
        )
}
