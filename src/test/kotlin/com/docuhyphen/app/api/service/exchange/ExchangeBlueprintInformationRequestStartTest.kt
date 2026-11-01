package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.dto.BlueprintDocumentConfig
import com.docuhyphen.app.api.model.dto.UpdateBlueprintRequest
import com.docuhyphen.app.api.model.entity.AuthToken
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.resource.model.ExchangeInitiationDto
import com.docuhyphen.app.api.resource.model.ExchangeParticipantRequest
import com.docuhyphen.app.api.resource.model.ExchangeRequestDocumentRequest
import com.docuhyphen.app.api.resource.model.RegisteredUserRecipientSelectionRequest
import com.docuhyphen.app.api.service.auth.AdminActionGuardService
import com.docuhyphen.app.api.model.auth.AdminApprovalContext
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.blueprint.BlueprintDefinitionService
import com.docuhyphen.app.api.service.documentlibrary.DocumentLibraryService
import com.docuhyphen.app.api.service.exchange.model.BlueprintStartOwner
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateWalkingSkeletonStore
import com.docuhyphen.app.api.service.informationrequest.parent.InformationRequestExchangeSummaryService
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationWriter
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateVersionUnavailableException
import com.docuhyphen.app.api.service.subscription.SubscriptionDenialReason
import com.docuhyphen.app.api.service.user.AppUserService
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusMock
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class ExchangeBlueprintInformationRequestStartTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var definitionRepository: InformationRequestTemplateDefinitionRepository

    @Inject
    lateinit var versionRepository: InformationRequestTemplateVersionRepository

    @Inject
    lateinit var capabilityRepository: InformationRequestTemplateVersionCapabilityRepository

    @Inject
    lateinit var configurationWriter: InformationRequestTemplateConfigurationWriter

    @Inject
    lateinit var appUserService: AppUserService

    @Inject
    lateinit var initiationService: ExchangeInitiationService

    @Inject
    lateinit var blueprintService: BlueprintDefinitionService

    @Inject
    lateinit var summaries: InformationRequestExchangeSummaryService

    private val fixtures by lazy {
        ExchangeBlueprintStartFixtures(
            dataSource,
            InformationRequestTemplateWalkingSkeletonStore(
                dataSource, definitionRepository, versionRepository, capabilityRepository, configurationWriter,
            ),
            definitionRepository,
            versionRepository,
        )
    }

    @Test
    fun `an Organization Blueprint whose request collects the documents starts the Exchange with one draft request`() {
        val owner = fixtures.organizationOwner()
        val template = fixtures.organizationTemplate(owner)
        val blueprintId = fixtures.blueprint(owner, template, organizationScoped = true, fieldDefault = "Preset note")

        val exchange = start(owner, blueprintId, documents = emptyList())

        val request = fixtures.requestsFor(exchange.id).single()
        assertEquals(0, fixtures.documentCount(exchange.id))
        assertEquals("DRAFT", request.state)
        assertEquals(template.versionId, request.templateVersionId)
        assertEquals(owner.organizationId, request.ownerOrganizationId)
        assertEquals(setOf(owner.userId.toString()), fixtures.decisionMakers(request.id))
        assertEquals("Preset note", fixtures.requestTextValue(request.id, template))
        assertEquals(listOf(request.id), listedFor(owner, exchange))
    }

    @Test
    fun `a Blueprint start makes parties only of the participants the Exchange has and never of its initiator`() {
        val owner = fixtures.personalOwner()
        val template = fixtures.personalTemplate(owner)
        val kept = fixtures.registeredUser()
        val removed = fixtures.registeredUser()
        val blueprintId = fixtures.blueprint(
            owner,
            template,
            participants = listOf(kept to "PARTICIPANT", removed to "PARTICIPANT", owner.userId to "OWNER"),
        )

        val exchange = start(owner, blueprintId, documents = emptyList(), participants = listOf(kept))

        val request = fixtures.requestsFor(exchange.id).single()
        assertEquals(setOf(kept.toString()), fixtures.partyUsers(request.id, "CONTRIBUTOR"))
        assertEquals(setOf(owner.userId.toString()), fixtures.decisionMakers(request.id))
    }

    @Test
    fun `a Personal Blueprint starts its request in the personal workspace`() {
        val owner = fixtures.personalOwner()
        val template = fixtures.personalTemplate(owner)
        val blueprintId = fixtures.blueprint(owner, template)

        val exchange = start(owner, blueprintId, documents = emptyList())

        val request = fixtures.requestsFor(exchange.id).single()
        assertEquals("DRAFT", request.state)
        assertEquals(owner.userId, request.ownerUserId)
        assertEquals(setOf(owner.userId.toString()), fixtures.decisionMakers(request.id))
        assertEquals(listOf(request.id), listedFor(owner, exchange))
    }

    @Test
    fun `an Exchange whose Blueprint starts no request still needs a document and creates no request`() {
        val owner = fixtures.personalOwner()
        val blueprintId = fixtures.blueprint(owner, template = null)

        val withoutBlueprint = assertThrows<IllegalArgumentException> { start(owner, null, documents = emptyList()) }
        val withoutVersion =
            assertThrows<IllegalArgumentException> { start(owner, blueprintId, documents = emptyList()) }
        val documented = start(owner, blueprintId, documents = listOf(document()))

        assertEquals("Add at least one document", withoutBlueprint.message)
        assertEquals("Add at least one document", withoutVersion.message)
        assertEquals(1, fixtures.documentCount(documented.id))
        assertEquals(0, fixtures.personalRequestsOf(owner.userId))
    }

    @Test
    fun `a retired Version refuses the start and leaves no Exchange and no request`() {
        val owner = fixtures.personalOwner()
        val template = fixtures.personalTemplate(owner)
        val blueprintId = fixtures.blueprint(owner, template)
        fixtures.retire(template, owner.userId)
        val name = uniqueName()

        val refusal = assertThrows<InformationRequestTemplateVersionUnavailableException> {
            start(owner, blueprintId, documents = listOf(document()), name = name)
        }

        assertEquals(InformationRequestTemplateVersionUnavailableException.RETIRED, refusal.code)
        assertEquals(0, fixtures.exchangesNamed(name))
        assertEquals(0, fixtures.personalRequestsOf(owner.userId))
    }

    @Test
    fun `a missing Information Requests entitlement refuses the start and leaves no Exchange and no request`() {
        val owner = fixtures.personalOwner()
        val template = fixtures.personalTemplate(owner)
        val blueprintId = fixtures.blueprint(owner, template)
        fixtures.withdrawInformationRequests(owner)
        val name = uniqueName()

        val refusal = assertThrows<SubscriptionDenialException> {
            start(owner, blueprintId, documents = emptyList(), name = name)
        }

        assertEquals(SubscriptionDenialReason.FEATURE_NOT_INCLUDED, refusal.denial.reason)
        assertEquals(0, fixtures.exchangesNamed(name))
        assertEquals(0, fixtures.personalRequestsOf(owner.userId))
    }

    @Test
    fun `exhausted request capacity refuses the start and leaves no Exchange and no request`() {
        val owner = fixtures.personalOwner()
        val template = fixtures.personalTemplate(owner)
        val blueprintId = fixtures.blueprint(owner, template)
        fixtures.fillOpenRequestCapacity(owner, template, openRequests = 25)
        val name = uniqueName()

        val refusal = assertThrows<SubscriptionDenialException> {
            start(owner, blueprintId, documents = emptyList(), name = name)
        }

        assertEquals(SubscriptionDenialReason.PLAN_LIMIT_REACHED, refusal.denial.reason)
        assertEquals(0, fixtures.exchangesNamed(name))
        assertEquals(25, fixtures.personalRequestsOf(owner.userId))
    }

    @Test
    fun `exhausted request capacity refuses the start before any library document is copied`() {
        val owner = fixtures.personalOwner()
        val template = fixtures.personalTemplate(owner)
        val blueprintId = fixtures.blueprint(owner, template)
        fixtures.fillOpenRequestCapacity(owner, template, openRequests = 25)
        val library = mock<DocumentLibraryService>()
        QuarkusMock.installMockForType(library, DocumentLibraryService::class.java)
        val name = uniqueName()

        val refusal = assertThrows<SubscriptionDenialException> {
            start(owner, blueprintId, documents = listOf(libraryDocument()), name = name)
        }

        assertEquals(SubscriptionDenialReason.PLAN_LIMIT_REACHED, refusal.denial.reason)
        verify(library, never()).resolveLibraryFileForBlueprintDocument(any())
        assertEquals(0, fixtures.exchangesNamed(name))
    }

    @Test
    fun `a Blueprint start copies its library documents once its request is created`() {
        val owner = fixtures.personalOwner()
        val template = fixtures.personalTemplate(owner)
        val blueprintId = fixtures.blueprint(owner, template)
        val library = mock<DocumentLibraryService>()
        QuarkusMock.installMockForType(library, DocumentLibraryService::class.java)
        val document = libraryDocument()

        val exchange = start(owner, blueprintId, documents = listOf(document))

        assertEquals(1, fixtures.requestsFor(exchange.id).size)
        verify(library).resolveLibraryFileForBlueprintDocument(requireNotNull(document.libraryDocumentId))
    }

    @Test
    fun `a Personal Blueprint is refused for an organization's Exchange before anything is written`() {
        val owner = fixtures.organizationOwner()
        val template = fixtures.personalTemplate(owner)
        val blueprintId = fixtures.blueprint(owner, template)
        val name = uniqueName()

        val refusal = assertThrows<InformationRequestTemplateVersionUnavailableException> {
            start(owner, blueprintId, documents = emptyList(), name = name)
        }

        assertEquals(InformationRequestTemplateVersionUnavailableException.OWNER_MISMATCH, refusal.code)
        assertEquals(0, fixtures.exchangesNamed(name))
        assertEquals(0, fixtures.requestsOwnedBy(owner))
        assertEquals(0, fixtures.personalRequestsOf(owner.userId))
    }

    @Test
    fun `the Blueprint stays editable after an Exchange starts its request`() {
        val owner = fixtures.personalOwner()
        val template = fixtures.personalTemplate(owner)
        val blueprintId = fixtures.blueprint(owner, template, documentTitles = listOf("Signed record"))
        val exchange = start(owner, blueprintId, documents = emptyList())
        val request = fixtures.requestsFor(exchange.id).single()
        QuarkusMock.installMockForType(mock<AdminActionGuardService>(), AdminActionGuardService::class.java)

        blueprintService.updateBlueprint(
            blueprintId,
            UpdateBlueprintRequest(exchangeDocuments = listOf(BlueprintDocumentConfig(title = "Revised record"))),
            AdminApprovalContext(),
        )

        assertEquals(setOf("Revised record"), fixtures.blueprintDocumentTitles(blueprintId))
        assertEquals(setOf("Signed record"), fixtures.placeholderTitles(request.id))
    }

    private fun start(
        owner: BlueprintStartOwner,
        blueprintId: UUID?,
        documents: List<ExchangeRequestDocumentRequest>,
        name: String = uniqueName(),
        participants: List<UUID> = emptyList(),
    ): Exchange {
        actAs(owner)
        return initiationService.initiateExchange(
            ExchangeInitiationDto(
                name = name,
                description = "Collect process records",
                initialShareMessage = "Please respond",
                primaryRecipient = RegisteredUserRecipientSelectionRequest(owner.recipientId.toString()),
                exchangeDocuments = documents,
                blueprintDefinitionId = blueprintId,
                participants = participants.map { ExchangeParticipantRequest(RegisteredUserRecipientSelectionRequest(it.toString())) },
            ),
        )
    }

    private fun actAs(owner: BlueprintStartOwner) {
        val appUser = QuarkusTransaction.requiringNew().call { appUserService.getById(owner.userId) }
            ?: error("The starting user was not stored")
        val authTokenContext = mock<AuthTokenContext>()
        whenever(authTokenContext.authToken).thenReturn(AuthToken().apply { this.appUser = appUser })
        whenever(authTokenContext.activeOrganizationId).thenReturn(owner.organizationId)
        QuarkusMock.installMockForType(authTokenContext, AuthTokenContext::class.java)
    }

    private fun listedFor(owner: BlueprintStartOwner, exchange: Exchange): List<UUID> =
        QuarkusTransaction.requiringNew().call {
            summaries.listForExchange(
                exchange.id,
                RequestAccessContext(PrincipalRef.user(owner.userId), AuthorizationContext()),
            ).requests.map { it.request.id }
        }

    private fun document(): ExchangeRequestDocumentRequest = ExchangeRequestDocumentRequest().apply {
        title = "Requested record"
        required = true
    }

    private fun libraryDocument(): ExchangeRequestDocumentRequest = ExchangeRequestDocumentRequest().apply {
        title = "Library record"
        libraryDocumentId = UUID.randomUUID()
    }

    private fun uniqueName(): String = "Blueprint start ${UUID.randomUUID()}"
}
