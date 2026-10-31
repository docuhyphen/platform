package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateEvidencePolicyRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionRequest
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.audit.AuditCaptureFailedException
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestTemplateAuditFailureTransactionTest {
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
    private val templateAuditsFail = AtomicBoolean(false)

    @Test
    fun `a required audit failure rolls back a draft configuration change`() {
        val draft = authoredDraft()
        templateAuditsFail.set(true)

        assertThrows<AuditCaptureFailedException> {
            authoring.replaceDraftConfiguration(draft.definitionId, configuration(), CommandPrecondition.Unconditioned)
        }

        assertEquals(0L, QuarkusTransaction.requiringNew().call { versions.findById(draft.versionId)!!.revision })
        assertEquals(0, configuredRequirements(draft))
    }

    @Test
    fun `a required audit failure rolls back a publication`() {
        val draft = authoredDraft()
        authoring.replaceDraftConfiguration(draft.definitionId, configuration(), CommandPrecondition.Unconditioned)
        templateAuditsFail.set(true)

        assertThrows<AuditCaptureFailedException> {
            publication.publishTemplate(draft.definitionId, CommandPrecondition.Unconditioned, "publish-during-outage")
        }

        QuarkusTransaction.requiringNew().run {
            val version = versions.findById(draft.versionId)!!
            assertEquals(InformationRequestTemplateStatus.DRAFT, version.status)
            assertNull(version.publishedAt)
            assertEquals(0, capabilities.findForVersion(version.id).size)
        }
        assertEquals(0, receipts(draft, "publish-during-outage"))
    }

    @Test
    fun `a required audit failure rolls back a retirement`() {
        val draft = authoredDraft()
        authoring.replaceDraftConfiguration(draft.definitionId, configuration(), CommandPrecondition.Unconditioned)
        publication.publishTemplate(draft.definitionId, CommandPrecondition.Unconditioned, "publish-before-outage")
        templateAuditsFail.set(true)

        assertThrows<AuditCaptureFailedException> {
            lifecycle.retireVersion(draft.definitionId, 1, "retire-during-outage")
        }

        QuarkusTransaction.requiringNew().run {
            val version = versions.findById(draft.versionId)!!
            assertEquals(InformationRequestTemplateStatus.PUBLISHED, version.status)
            assertNull(version.retiredAt)
        }
        assertEquals(0, receipts(draft, "retire-during-outage"))
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
            val draft = invocation.getArgument<AuditEventDraft>(0)
            if (templateAuditsFail.get() && draft.eventTypeKey.startsWith(TEMPLATE_EVENTS)) {
                throw AuditCaptureFailedException("Synthetic required audit outage", null)
            }
            AuditCaptureResult.Captured(UUID.randomUUID(), draft.eventId)
        }
        QuarkusMock.installMockForType(recorder, AuditRecorder::class.java)
        return store.insertDraft(owner, "audited-collection")
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

    private fun configuredRequirements(draft: WalkingSkeletonDraft): Int =
        dataSource.connection.use { connection ->
            queryInt(
                connection,
                "SELECT COUNT(*) FROM information_request_template_requirement_binding WHERE template_version_id = ?",
                draft.versionId,
            )
        }

    private fun receipts(draft: WalkingSkeletonDraft, key: String): Int =
        dataSource.connection.use { connection ->
            queryInt(
                connection,
                "SELECT COUNT(*) FROM command_receipt WHERE resource_id = ? AND idempotency_key = ?",
                draft.definitionId,
                key,
            )
        }

    private companion object {
        const val TEMPLATE_EVENTS = "information_request.template."
    }
}
