package com.docuhyphen.app.api.resource.exchange

import com.docuhyphen.app.api.model.entity.ExchangeShareRoleName
import com.docuhyphen.app.api.resource.model.*
import com.docuhyphen.app.api.service.exchange.*
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.*

class ExchangePrimaryRecipientReplacementResourceTest
{
    private val accessManagementService = mock<ExchangeAccessManagementService>()
    private val resource = ExchangeResource(
        exchangeDocumentService = mock<ExchangeDocumentService>(),
        exchangeInitiationService = mock<ExchangeInitiationService>(),
        exchangeRetrievalService = mock<ExchangeRetrievalService>(),
        exchangeUpdateService = mock<ExchangeUpdateService>(),
        exchangeParticipantService = mock<ExchangeParticipantService>(),
        shareQueryService = mock<ShareQueryService>(),
        sessionAccessManagementService = accessManagementService,
        exchangeDetailsViewService = mock<ExchangeDetailsViewService>(),
        exchangeWorkflowStatusService = mock<ExchangeWorkflowStatusService>(),
    )
    private val exchangeId = UUID.randomUUID()

    @Test
    fun `trusted selections are delegated to the replacement service`()
    {
        val selections = listOf<ExchangeRecipientSelectionRequest>(
            TrustedPersonRecipientSelectionRequest(UUID.randomUUID().toString()),
            TrustedGroupRecipientSelectionRequest(
                organizationId = UUID.randomUUID().toString(),
                groupId = UUID.randomUUID().toString(),
            ),
        )
        selections.forEach { selection ->
            whenever(accessManagementService.replacePrimaryRecipient(exchangeId, selection))
                .thenReturn(emptyList())
        }

        assertAll(
            selections.map { selection ->
                {
                    val response = resource.replacePrimaryRecipient(
                        exchangeId.toString(),
                        ReplacePrimaryRecipientRequest(selection),
                    )
                    assertEquals(Response.Status.OK.statusCode, response.status)
                    verify(accessManagementService).replacePrimaryRecipient(exchangeId, selection)
                }
            },
        )
    }

    @Test
    fun `unsupported selections return bad request without a successful mutation`()
    {
        val selections = listOf<ExchangeRecipientSelectionRequest>(
            RegisteredUserRecipientSelectionRequest(UUID.randomUUID().toString()),
            ExternalEmailRecipientSelectionRequest("person@acme.example", "A", "B"),
            InternalGroupRecipientSelectionRequest(UUID.randomUUID().toString()),
            PersonalGroupRecipientSelectionRequest(UUID.randomUUID().toString()),
        )
        selections.forEach { selection ->
            whenever(accessManagementService.replacePrimaryRecipient(exchangeId, selection))
                .thenThrow(IllegalArgumentException("Trusted recipient required"))
        }

        assertAll(
            selections.map { selection ->
                {
                    val response = resource.replacePrimaryRecipient(
                        exchangeId.toString(),
                        ReplacePrimaryRecipientRequest(selection),
                    )
                    assertEquals(Response.Status.BAD_REQUEST.statusCode, response.status)
                    verify(accessManagementService).replacePrimaryRecipient(exchangeId, selection)
                }
            },
        )
    }

    @Test
    fun `trusted participant invitation is delegated to the access service`()
    {
        val selection = TrustedPersonRecipientSelectionRequest(UUID.randomUUID().toString())
        whenever(
            accessManagementService.inviteTrustedParticipant(
                exchangeId = exchangeId,
                selection = selection,
                roleName = ExchangeShareRoleName.VIEWER,
                constraintsJson = null,
                expiresAtEpochMillis = null,
            ),
        ).thenReturn(emptyList())

        val response = resource.inviteTrustedParticipant(
            exchangeId.toString(),
            InviteTrustedParticipantRequest(
                selection = selection,
                roleName = ExchangeShareRoleName.VIEWER,
            ),
        )

        assertEquals(Response.Status.OK.statusCode, response.status)
        verify(accessManagementService).inviteTrustedParticipant(
            exchangeId = exchangeId,
            selection = selection,
            roleName = ExchangeShareRoleName.VIEWER,
            constraintsJson = null,
            expiresAtEpochMillis = null,
        )
    }
}
