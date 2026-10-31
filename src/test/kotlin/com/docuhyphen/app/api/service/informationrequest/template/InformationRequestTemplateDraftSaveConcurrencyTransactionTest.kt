package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateEvidencePolicyRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionRequest
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.audit.AuditCaptureResult
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.auth.AdminActionGuardService
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateWalkingSkeletonStore
import com.docuhyphen.app.api.service.informationrequest.capability.model.WalkingSkeletonDraft
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusMock
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestTemplateDraftSaveConcurrencyTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var definitions: InformationRequestTemplateDefinitionRepository

    @Inject
    lateinit var versions: InformationRequestTemplateVersionRepository

    @Inject
    lateinit var capabilities: InformationRequestTemplateVersionCapabilityRepository

    @Inject
    lateinit var writer: InformationRequestTemplateConfigurationWriter

    @Inject
    lateinit var authoring: InformationRequestTemplateAuthoringService

    @Inject
    lateinit var publication: InformationRequestTemplatePublicationService

    @Inject
    lateinit var lifecycle: InformationRequestTemplateLifecycleService

    private val store by lazy {
        InformationRequestTemplateWalkingSkeletonStore(dataSource, definitions, versions, capabilities, writer)
    }

    @Test
    fun `a draft save keeps a Template status another administrator changed while the save ran`() {
        val draft = authoredDraft()
        authoring.replaceDraftConfiguration(draft.definitionId, configuration(), CommandPrecondition.Unconditioned)
        publication.publishTemplate(draft.definitionId, CommandPrecondition.Unconditioned, "publish-first")
        lifecycle.createDraftVersion(draft.definitionId, 1, "second-draft")
        val concurrent = mutableListOf<CompletableFuture<Void>>()
        val compatibility = mock<InformationRequestTemplateSchemaCompatibility>()
        doAnswer {
            val change = CompletableFuture.runAsync { markDraftOnly(draft.definitionId) }
            concurrent += change
            runCatching { change.get(2, TimeUnit.SECONDS) }
            null
        }.whenever(compatibility).requireUsable(any(), anyOrNull())
        QuarkusMock.installMockForType(compatibility, InformationRequestTemplateSchemaCompatibility::class.java)

        authoring.replaceDraftConfiguration(draft.definitionId, configuration(), CommandPrecondition.Unconditioned)
        concurrent.single().get(10, TimeUnit.SECONDS)

        assertEquals(
            InformationRequestTemplateStatus.DRAFT,
            QuarkusTransaction.requiringNew().call { definitions.findById(draft.definitionId)!!.status },
        )
    }

    private fun markDraftOnly(definitionId: UUID) {
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE information_request_template_definition SET status = 'DRAFT' WHERE id = ?",
                definitionId
            )
        }
    }

    private fun authoredDraft(): WalkingSkeletonDraft {
        val owner = store.insertOwner()
        dataSource.connection.use { connection ->
            execute(
                connection,
                "INSERT INTO user_subscription_policy (id, app_user_id, plan_code, subscription_status) VALUES (?, ?, 'PERSONAL', 'ACTIVE')",
                UUID.randomUUID(),
                owner,
            )
        }
        val context = mock<AuthTokenContext>()
        whenever(context.authToken).thenReturn(AuthToken().apply { appUser = AppUser().apply { id = owner } })
        QuarkusMock.installMockForType(context, AuthTokenContext::class.java)
        QuarkusMock.installMockForType(mock<AdminActionGuardService>(), AdminActionGuardService::class.java)
        val recorder = mock<AuditRecorder>()
        whenever(recorder.record(any())).thenAnswer { invocation ->
            val event = invocation.getArgument<AuditEventDraft>(0)
            AuditCaptureResult.Captured(UUID.randomUUID(), event.eventId)
        }
        QuarkusMock.installMockForType(recorder, AuditRecorder::class.java)
        return store.insertDraft(owner, "concurrent-collection")
    }

    private fun configuration() = InformationRequestTemplateConfigurationRequest(
        sections = listOf(
            InformationRequestTemplateSectionRequest(
                "requested-records",
                "Requested records",
                requirements = listOf(
                    InformationRequestTemplateRequirementRequest(
                        "supporting-record",
                        InformationRequestRequirementType.DOCUMENT,
                        "Provide the supporting record",
                        permittedDispositions = listOf(InformationRequestResponseDisposition.PROVIDED),
                        evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(),
                    ),
                ),
            ),
        ),
    )
}
