package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.BlueprintDocumentDefault
import com.docuhyphen.app.api.model.entity.BlueprintFieldDefault
import com.docuhyphen.app.api.model.entity.BlueprintParticipantDefault
import com.docuhyphen.app.api.model.entity.CommandReceipt
import com.docuhyphen.app.api.model.entity.DocumentLibraryEntry
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.ExchangeShareRoleName
import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestDocumentPlaceholder
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.InformationRequestTransition
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestDocumentPlaceholderRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.blueprint.BlueprintInformationRequestInstantiationSnapshot
import com.docuhyphen.app.api.service.blueprint.BlueprintDocumentInstantiationDefault
import com.docuhyphen.app.api.service.blueprint.BlueprintDefinitionService
import com.docuhyphen.app.api.service.command.CommandReceiptRequest
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandReceiptStore
import com.docuhyphen.app.api.service.fields.BlueprintFieldDefaultsCommand
import com.docuhyphen.app.api.service.fields.SchemaAssignmentService
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class InformationRequestBlueprintInstantiationServiceTest
{
    private val blueprintId = UUID.randomUUID()
    private val exchangeId = UUID.randomUUID()
    private val organizationId = UUID.randomUUID()
    private val actorId = UUID.randomUUID()
    private val templateVersionId = UUID.randomUUID()
    private val fieldDefinitionId = UUID.randomUUID()
    private val libraryDocumentId = UUID.randomUUID()
    private val contributorId = UUID.randomUUID()
    private val access = RequestAccessContext(
        PrincipalRef.user(actorId),
        AuthorizationContext(activeOrgId = organizationId),
    )

    @Test
    fun `blueprint instantiation pins the named version snapshots defaults and replays the command result`()
    {
        val fixture = fixture()
        val command = CreateInformationRequestFromBlueprintCommand(
            blueprintDefinitionId = blueprintId,
            exchangeId = exchangeId,
            access = access,
            idempotencyKey = "create-from-blueprint",
        )

        val first = fixture.service.createFromBlueprint(command)
        val second = fixture.service.createFromBlueprint(command)

        assertEquals(first.request.id, second.request.id)
        assertEquals(first.requestETag, second.requestETag)
        assertEquals(2, first.requirementCount)
        assertEquals(1, fixture.savedRequests.size)
        assertEquals(templateVersionId, first.request.templateVersionId)
        assertEquals(InformationRequestOwnerType.ORGANIZATION, first.request.ownerType)
        assertEquals(organizationId, first.request.ownerOrganizationId)
        assertEquals(actorId, first.request.createdByAppUserId)

        val partyDefaults = argumentCaptor<List<BlueprintInformationRequestPartyDefault>>()
        verify(fixture.partyService).materializeBlueprintDefaultParties(
            eq(first.request),
            partyDefaults.capture(),
            eq(access),
        )
        assertEquals(
            listOf(InformationRequestShareRoleKey.CONTRIBUTOR, InformationRequestShareRoleKey.ATTESTOR),
            partyDefaults.firstValue.map { it.roleKey },
        )
        assertEquals(PrincipalKind.USER, partyDefaults.firstValue.first().principal.kind)
        assertEquals(contributorId, partyDefaults.firstValue.first().principal.id)

        val fieldDefaults = argumentCaptor<BlueprintFieldDefaultsCommand>()
        verify(fixture.schemaAssignmentService).applyBlueprintFieldDefaults(fieldDefaults.capture())
        assertEquals(ResourceType.INFORMATION_REQUEST.name, fieldDefaults.firstValue.resource.resourceType)
        assertEquals(first.request.id, fieldDefaults.firstValue.resource.resourceId)
        assertEquals(fieldDefinitionId, fieldDefaults.firstValue.defaults.single().fieldDefinitionId)
        assertEquals(JsonPrimitive("Initial field value"), fieldDefaults.firstValue.defaults.single().value)

        val placeholder = fixture.savedPlaceholders.single()
        assertEquals(first.request.id, placeholder.informationRequestId)
        assertEquals("Reference file", placeholder.title)
        assertEquals(true, placeholder.required)
        assertEquals(libraryDocumentId, placeholder.libraryDocumentId)
        assertEquals("reference.pdf", placeholder.libraryFileName)
        assertEquals("application/pdf", placeholder.libraryDocumentType)
        assertEquals("content-hash", placeholder.libraryContentHash)

        verify(fixture.authorizationService).authorize(
            access.principal,
            Action.INFORMATION_REQUEST_CREATE,
            ResourceRef.exchange(exchangeId),
            access.authorization,
        )
        verify(fixture.requestRepository).save(any())
        verify(fixture.placeholderRepository).save(any())
    }

    @Test
    fun `retired named versions refuse new blueprint instantiation without creating a request`()
    {
        val fixture = fixture()
        whenever(
            fixture.blueprintService.loadInformationRequestInstantiationSnapshot(
                blueprintId,
                access.principal,
                access.authorization,
            ),
        )
            .thenThrow(
                InformationRequestTemplateVersionUnavailableException(
                    InformationRequestTemplateVersionUnavailableException.RETIRED,
                    "Retired in test",
                ),
            )

        val refusal = assertThrows<InformationRequestTemplateVersionUnavailableException> {
            fixture.service.createFromBlueprint(
                CreateInformationRequestFromBlueprintCommand(
                    blueprintDefinitionId = blueprintId,
                    exchangeId = exchangeId,
                    access = access,
                    idempotencyKey = "create-from-retired-blueprint",
                ),
            )
        }

        assertEquals(InformationRequestTemplateVersionUnavailableException.RETIRED, refusal.code)
        verify(fixture.requestRepository, never()).save(any())
        verify(fixture.placeholderRepository, never()).save(any())
    }

    private data class Fixture(
        val service: InformationRequestBlueprintInstantiationService,
        val blueprintService: BlueprintDefinitionService,
        val requestRepository: InformationRequestRepository,
        val placeholderRepository: InformationRequestDocumentPlaceholderRepository,
        val partyService: InformationRequestPartyService,
        val schemaAssignmentService: SchemaAssignmentService,
        val authorizationService: AuthorizationService,
        val savedRequests: MutableList<InformationRequest>,
        val savedPlaceholders: MutableList<InformationRequestDocumentPlaceholder>,
    )

    private fun fixture(): Fixture
    {
        val exchange = Exchange().apply {
            id = exchangeId
            ownerOrganizationId = organizationId
            status = ExchangeStatus.ACCEPTED_STARTED
            isDeleted = false
        }
        val exchangeRepository = mock<ExchangeRepository>()
        whenever(exchangeRepository.findByIdForUpdate(exchangeId)).thenReturn(exchange)

        val blueprintService = mock<BlueprintDefinitionService>()
        whenever(
            blueprintService.loadInformationRequestInstantiationSnapshot(
                blueprintId,
                access.principal,
                access.authorization,
            ),
        )
            .thenReturn(snapshot())

        val requests = mutableListOf<InformationRequest>()
        val requestRepository = mock<InformationRequestRepository>()
        whenever(requestRepository.save(any())).thenAnswer { invocation ->
            invocation.getArgument<InformationRequest>(0).also { requests += it }
        }
        whenever(requestRepository.findById(any())).thenAnswer { invocation ->
            requests.firstOrNull { it.id == invocation.getArgument<UUID>(0) }
        }

        val placeholders = mutableListOf<InformationRequestDocumentPlaceholder>()
        val placeholderRepository = mock<InformationRequestDocumentPlaceholderRepository>()
        whenever(placeholderRepository.save(any())).thenAnswer { invocation ->
            invocation.getArgument<InformationRequestDocumentPlaceholder>(0).also { placeholders += it }
        }

        val authorizationService = mock<AuthorizationService>()
        whenever(
            authorizationService.authorize(
                access.principal,
                Action.INFORMATION_REQUEST_CREATE,
                ResourceRef.exchange(exchangeId),
                access.authorization,
            ),
        ).thenReturn(Decision.Allow())

        val materializer = mock<InformationRequestTemplateMaterializer>()
        whenever(materializer.materialize(any(), any())).thenReturn(
            InformationRequestMaterializationResult(requirementCount = 2),
        )

        val partyService = mock<InformationRequestPartyService>()
        val schemaAssignmentService = mock<SchemaAssignmentService>()
        val transitionHistory = mock<InformationRequestTransitionHistoryService>()
        whenever(transitionHistory.record(any())).thenAnswer { invocation ->
            val command = invocation.getArgument<InformationRequestTransitionHistoryCommand>(0)
            InformationRequestTransition().apply {
                informationRequestId = command.request.id
                sequenceNumber = 1
                fromState = command.fromState
                toState = command.toState
                mutation = command.mutation
            }
        }

        return Fixture(
            service = InformationRequestBlueprintInstantiationService(
                exchangeRepository = exchangeRepository,
                requestRepository = requestRepository,
                placeholderRepository = placeholderRepository,
                blueprintService = blueprintService,
                partyService = partyService,
                schemaAssignmentService = schemaAssignmentService,
                materializer = materializer,
                authorizationService = authorizationService,
                commandReceiptService = CommandReceiptService(InMemoryBlueprintCommandReceiptStore()),
                transitionHistory = transitionHistory,
            ),
            blueprintService = blueprintService,
            requestRepository = requestRepository,
            placeholderRepository = placeholderRepository,
            partyService = partyService,
            schemaAssignmentService = schemaAssignmentService,
            authorizationService = authorizationService,
            savedRequests = requests,
            savedPlaceholders = placeholders,
        )
    }

    private fun snapshot() = BlueprintInformationRequestInstantiationSnapshot(
        blueprintDefinitionId = blueprintId,
        templateVersionId = templateVersionId,
        participantDefaults = listOf(
            BlueprintParticipantDefault().apply {
                principalKind = "APP_USER"
                principalId = contributorId.toString()
                roleName = ExchangeShareRoleName.PARTICIPANT
            },
            BlueprintParticipantDefault().apply {
                principalKind = "PRINCIPAL_GROUP"
                principalId = UUID.randomUUID().toString()
                roleName = ExchangeShareRoleName.SIGNER
            },
        ),
        documentDefaults = listOf(
            BlueprintDocumentInstantiationDefault(
                default = BlueprintDocumentDefault().apply {
                    title = "Reference file"
                    required = true
                    libraryDocumentId = this@InformationRequestBlueprintInstantiationServiceTest.libraryDocumentId
                },
                libraryEntry = DocumentLibraryEntry().apply {
                    id = libraryDocumentId
                    title = "Reusable reference"
                    description = "Reusable reference metadata"
                    documentType = "application/pdf"
                    fileName = "reference.pdf"
                    fileSizeBytes = 128
                    contentHash = "content-hash"
                },
            ),
        ),
        fieldDefaults = listOf(
            BlueprintFieldDefault().apply {
                fieldDefinitionId = this@InformationRequestBlueprintInstantiationServiceTest.fieldDefinitionId
                valueType = FieldValueType.SHORT_TEXT
                valueJson = "\"Initial field value\""
            },
        ),
    )
}

private class InMemoryBlueprintCommandReceiptStore : CommandReceiptStore
{
    private val receipts = mutableListOf<CommandReceipt>()

    override fun findForCommand(request: CommandReceiptRequest): CommandReceipt? =
        receipts.firstOrNull {
            it.resourceType == request.resource.type &&
                it.resourceId == request.resource.id &&
                it.operationName == request.operation &&
                it.actorKind == request.actor.kind &&
                it.actorId == request.actor.id &&
                it.idempotencyKey == request.idempotencyKey
        }

    override fun insert(receipt: CommandReceipt): CommandReceipt
    {
        receipts += receipt
        return receipt
    }
}
