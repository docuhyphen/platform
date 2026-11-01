package com.docuhyphen.app.api.service.informationrequest.conformance

import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionRequest
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.FieldsAccessContext
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.conformance.model.PublishedRequest
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationWriter
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateMaterializer
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateVersionUnavailableException
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestRetiredSchemaTest {
    @Inject
    lateinit var dataSource: DataSource

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
    fun `a request is not created from a Version whose Schema was retired after publication, with a stable reason`() {
        val issued = issued()
        val draftId = UUID.randomUUID()
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                UPDATE schema_definition SET status = 'RETIRED'
                WHERE id = (SELECT schema_definition_id FROM schema_version
                            WHERE id = (SELECT schema_version_id FROM information_request_template_version WHERE id = ?))
                """.trimIndent(),
                issued.templateVersionId,
            )
            execute(
                connection,
                """
                INSERT INTO information_request
                    (id, exchange_id, template_version_id, owner_type, owner_user_id, state, gates_exchange_closure,
                     aggregate_revision, party_revision, created_at, updated_at)
                VALUES (?, ?, ?, 'USER', ?, 'DRAFT', TRUE, 1, 1, now(), now())
                """.trimIndent(),
                draftId,
                issued.exchangeId,
                issued.templateVersionId,
                issued.ownerId,
            )
        }

        val refusal = assertThrows(InformationRequestTemplateVersionUnavailableException::class.java) {
            QuarkusTransaction.requiringNew().run {
                materializer.materialize(
                    requireNotNull(requests.findById(draftId)),
                    FieldsAccessContext(
                        PrincipalRef.user(issued.ownerId),
                        AuthorizationContext(sessionRef = "owner-session")
                    ),
                )
            }
        }

        assertEquals(InformationRequestTemplateVersionUnavailableException.SCHEMA_UNAVAILABLE, refusal.code)
    }

    private fun issued(): PublishedRequest =
        support.issue(
            "retired-schema-${UUID.randomUUID()}",
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
