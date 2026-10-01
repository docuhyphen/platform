package com.docuhyphen.app.api.resource.informationrequest.acceptedfact

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.acceptedfact.handler.InformationRequestAcceptedFactRequestHandler
import com.docuhyphen.app.api.resource.informationrequest.acceptedfact.operations.InformationRequestAcceptedFactResourceOperations
import com.docuhyphen.app.api.resource.model.PromoteInformationRequestAcceptedFactRequest
import com.docuhyphen.app.api.resource.model.RevokeInformationRequestAcceptedFactRequest
import com.docuhyphen.app.api.service.informationrequest.acceptedfact.InformationRequestAcceptedFactQueryService
import com.docuhyphen.app.api.service.informationrequest.acceptedfact.InformationRequestAcceptedFactService
import com.docuhyphen.app.api.service.informationrequest.acceptedfact.InformationRequestBusinessDecisionService
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestAcceptedFactResource @Inject constructor(
    facts: InformationRequestAcceptedFactService,
    factQueries: InformationRequestAcceptedFactQueryService,
    decisions: InformationRequestBusinessDecisionService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
) : InformationRequestAcceptedFactResourceOperations
{
    private val handler = InformationRequestAcceptedFactRequestHandler(facts, factQueries, decisions)

    override fun list(id: String): Response
    {
        return try
        {
            handler.promotedFrom(requestId(id), accessContextFactory.currentAuthenticated())
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request accepted fact list failed", exception)
        }
    }

    override fun promote(
        id: String,
        request: PromoteInformationRequestAcceptedFactRequest?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            handler.promote(requestId(id), request, accessContextFactory.currentAuthenticated(), idempotencyKey)
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(
                logger,
                "Information Request accepted fact promotion failed",
                exception
            )
        }
    }

    override fun revoke(
        id: String,
        factId: String,
        request: RevokeInformationRequestAcceptedFactRequest?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            handler.revoke(
                requestId(id),
                InformationRequestCommandHttp.uuid(factId, "accepted fact id"),
                request,
                accessContextFactory.currentAuthenticated(),
                idempotencyKey,
            )
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(
                logger,
                "Information Request accepted fact revocation failed",
                exception
            )
        }
    }

    private fun requestId(raw: String) = InformationRequestCommandHttp.uuid(raw, "information request id")

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestAcceptedFactResource::class.java)
    }
}
