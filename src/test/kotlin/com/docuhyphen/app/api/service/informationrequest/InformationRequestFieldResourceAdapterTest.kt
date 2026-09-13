package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersion
import com.docuhyphen.app.api.model.entity.RequestExecutionGrant
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.auth.authz.ScopeReference
import com.docuhyphen.app.api.service.fields.FieldValidationException
import com.docuhyphen.app.api.service.fields.FieldsAccessContext
import com.docuhyphen.app.api.service.subscription.SubscriptionContext
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class InformationRequestFieldResourceAdapterTest
{
    private val requestId = UUID.randomUUID()
    private val templateVersionId = UUID.randomUUID()
    private val schemaVersionId = UUID.randomUUID()
    private val otherSchemaVersionId = UUID.randomUUID()
    private val organizationId = UUID.randomUUID()
    private val ownerUserId = UUID.randomUUID()
    private val actorId = UUID.randomUUID()
    private val access = FieldsAccessContext(PrincipalRef.user(actorId), AuthorizationContext())

    @Test
    fun `an Information Request reports its resource type and persisted owner scope`()
    {
        val orgAdapter = adapter(request(InformationRequestState.DRAFT))
        assertEquals(ResourceType.INFORMATION_REQUEST.name, orgAdapter.resourceType)
        assertEquals(ScopeReference.Organization(organizationId), orgAdapter.ownerScope(requestId))
        assertEquals(SubscriptionContext.forOrganization(organizationId), orgAdapter.subscriptionContext(requestId))

        val personalAdapter = adapter(
            request(InformationRequestState.DRAFT).apply {
                ownerType = InformationRequestOwnerType.USER
                ownerOrganizationId = null
                ownerUserId = this@InformationRequestFieldResourceAdapterTest.ownerUserId
            },
        )
        assertEquals(ScopeReference.Personal(ownerUserId), personalAdapter.ownerScope(requestId))
        assertEquals(SubscriptionContext.forUser(ownerUserId), personalAdapter.subscriptionContext(requestId))
    }

    @Test
    fun `schema assignment must match the Template Version schema version pinned by the request`()
    {
        val adapter = adapter(request(InformationRequestState.DRAFT))

        assertDoesNotThrow {
            adapter.validateSchemaVersionAssignment(requestId, schemaVersionId)
        }
        val refusal = assertThrows<FieldValidationException> {
            adapter.validateSchemaVersionAssignment(requestId, otherSchemaVersionId)
        }
        assertTrue(refusal.message.orEmpty().contains("Template Version Schema Version"))
    }

    @Test
    fun `schema assignment freezes after issue while values stay editable in response states`()
    {
        assertTrue(adapter(request(InformationRequestState.DRAFT)).schemaAssignmentMutable(requestId))
        assertFalse(adapter(request(InformationRequestState.ISSUED)).schemaAssignmentMutable(requestId))
        assertFalse(adapter(request(InformationRequestState.IN_PROGRESS)).schemaAssignmentMutable(requestId))
        assertFalse(adapter(request(InformationRequestState.CHANGES_REQUESTED)).schemaAssignmentMutable(requestId))

        assertFalse(adapter(request(InformationRequestState.DRAFT)).valuesEditable(requestId))
        assertTrue(adapter(request(InformationRequestState.ISSUED)).valuesEditable(requestId))
        assertTrue(adapter(request(InformationRequestState.IN_PROGRESS)).valuesEditable(requestId))
        assertTrue(adapter(request(InformationRequestState.CHANGES_REQUESTED)).valuesEditable(requestId))
        assertFalse(adapter(request(InformationRequestState.SUBMITTED)).valuesEditable(requestId))
    }

    @Test
    fun `Field writes answer to the frozen execution grant once one has been issued for the request`()
    {
        val executionGrantService = mock<InformationRequestExecutionGrantService>()
        whenever(executionGrantService.findForRequest(requestId)).thenReturn(
            RequestExecutionGrant().apply { requestId = this@InformationRequestFieldResourceAdapterTest.requestId },
        )
        val issuedAdapter = adapter(request(InformationRequestState.ISSUED), executionGrantService = executionGrantService)

        assertTrue(issuedAdapter.mutationEntitlementFrozen(requestId))
    }

    @Test
    fun `Field writes still answer to the live subscription before a request has ever been issued`()
    {
        val executionGrantService = mock<InformationRequestExecutionGrantService>()
        whenever(executionGrantService.findForRequest(requestId)).thenReturn(null)
        val draftAdapter = adapter(request(InformationRequestState.DRAFT), executionGrantService = executionGrantService)

        assertFalse(draftAdapter.mutationEntitlementFrozen(requestId))
    }

    @Test
    fun `central authorization keeps runtime callers closed until request providers are registered`()
    {
        val authorizationService = mock<AuthorizationService>()
        whenever(
            authorizationService.authorize(
                access.principal,
                Action.INFORMATION_REQUEST_VIEW,
                ResourceRef.informationRequest(requestId),
                access.authorization,
            ),
        ).thenReturn(
            Decision.Deny(
                Decision.REASON_RESOURCE_CONTEXT_UNRESOLVED,
                "Authorization context could not be resolved for INFORMATION_REQUEST",
            ),
        )
        val adapter = adapter(request(InformationRequestState.DRAFT), authorizationService)

        assertThrows<ForbiddenException> {
            adapter.authorizeViewFields(requestId, access.principal, access.authorization)
        }
        verify(authorizationService).authorize(
            access.principal,
            Action.INFORMATION_REQUEST_VIEW,
            ResourceRef.informationRequest(requestId),
            access.authorization,
        )
    }

    @Test
    fun `Requirement response access permits request field value writes`()
    {
        val requirementId = UUID.randomUUID()
        val authorizationService = mock<AuthorizationService>()
        val requirementRepository = mock<InformationRequestRequirementRepository>()
        whenever(requirementRepository.findForRequest(requestId)).thenReturn(
            listOf(InformationRequestRequirement().apply { id = requirementId }),
        )
        whenever(
            authorizationService.authorize(
                access.principal,
                Action.INFORMATION_REQUEST_EDIT,
                ResourceRef.informationRequest(requestId),
                access.authorization,
            ),
        ).thenReturn(Decision.Deny("denied", "denied"))
        whenever(
            authorizationService.authorize(
                access.principal,
                Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND,
                ResourceRef.informationRequestRequirement(requirementId),
                access.authorization,
            ),
        ).thenReturn(Decision.Allow())
        val adapter = adapter(
            request(InformationRequestState.ISSUED),
            authorizationService,
            requirementRepository = requirementRepository,
        )

        assertDoesNotThrow {
            adapter.authorizeManageFields(requestId, access.principal, access.authorization)
        }
    }

    private fun adapter(
        request: InformationRequest?,
        authorizationService: AuthorizationService = mock(),
        executionGrantService: InformationRequestExecutionGrantService = mock(),
        requirementRepository: InformationRequestRequirementRepository = mock(),
    ): InformationRequestFieldResourceAdapter
    {
        val requestRepository = mock<InformationRequestRepository>()
        whenever(requestRepository.findById(requestId)).thenReturn(request)

        val versionRepository = mock<InformationRequestTemplateVersionRepository>()
        whenever(versionRepository.findById(templateVersionId)).thenReturn(
            InformationRequestTemplateVersion().apply {
                id = templateVersionId
                this.schemaVersionId = this@InformationRequestFieldResourceAdapterTest.schemaVersionId
            },
        )

        return InformationRequestFieldResourceAdapter(
            requestRepository = requestRepository,
            requirementRepository = requirementRepository,
            templateVersionRepository = versionRepository,
            authorizationService = authorizationService,
            executionGrantService = executionGrantService,
            bindingPolicy = InformationRequestFieldBindingPolicy(
                requestRepository = mock(),
                requirementRepository = mock(),
                templateBindingRepository = mock(),
                organizationMembershipRepository = mock(),
                authorizationService = mock(),
            ),
        )
    }

    private fun request(state: InformationRequestState) = InformationRequest().apply {
        id = requestId
        exchangeId = UUID.randomUUID()
        this.templateVersionId = this@InformationRequestFieldResourceAdapterTest.templateVersionId
        ownerType = InformationRequestOwnerType.ORGANIZATION
        ownerOrganizationId = organizationId
        this.state = state
    }
}
