package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.exception.OrganizationContextRequiredException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.dto.ExchangeClearanceStatusDto
import com.docuhyphen.app.api.model.dto.WorkflowInstanceListItemDto
import com.docuhyphen.app.api.service.workflow.WorkflowDefinitionService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class ExchangeWorkflowStatusService @Inject constructor(
    private val exchangeRetrievalService: ExchangeRetrievalService,
    private val workflowDefinitionService: WorkflowDefinitionService,
    private val authTokenContext: AuthTokenContext,
)
{
    fun workflowInstances(exchangeId: String): List<WorkflowInstanceListItemDto>
    {
        val id = UUID.fromString(exchangeId)
        exchangeRetrievalService.getExchange(exchangeId)
        val callerId = authTokenContext.authToken.appUser?.id
        val callerOrgId = authTokenContext.activeOrganizationId
        return if (callerOrgId != null && callerId != null)
            workflowDefinitionService.listInstancesForSubject("EXCHANGE", id, callerOrgId, callerId)
        else
            emptyList()
    }

    fun clearanceStatus(exchangeId: String): ExchangeClearanceStatusDto
    {
        val id = UUID.fromString(exchangeId)
        exchangeRetrievalService.getExchange(exchangeId)
        val callerOrgId = authTokenContext.activeOrganizationId
            ?: throw OrganizationContextRequiredException("Organisation context required")
        return workflowDefinitionService.getExchangeClearanceStatus(id, callerOrgId)
    }
}
