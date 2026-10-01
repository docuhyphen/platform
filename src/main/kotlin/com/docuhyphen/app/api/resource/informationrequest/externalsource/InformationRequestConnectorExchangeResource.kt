package com.docuhyphen.app.api.resource.informationrequest.externalsource

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.InformationRequestExternalSourceDtoMapper
import com.docuhyphen.app.api.model.informationrequest.externalsource.RequestInformationRequestConnectorExchangeCommand
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.externalsource.operations.InformationRequestConnectorExchangeResourceOperations
import com.docuhyphen.app.api.resource.model.RequestInformationRequestConnectorExchangeRequest
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.externalsource.InformationRequestConnectorService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestConnectorExchangeResource @Inject constructor(
    private val exchanges: InformationRequestConnectorService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
) : InformationRequestConnectorExchangeResourceOperations
{
    override fun list(id: String): Response
    {
        return try
        {
            val access = accessContextFactory.currentAuthenticated()
            val listed = exchanges.exchanges(requestId(id), access)
            Response.ok(listed.map { InformationRequestExternalSourceDtoMapper.toDto(it, access.principal) }
                .toTypedArray()).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(
                logger,
                "Information Request connector exchange list failed",
                exception
            )
        }
    }

    override fun request(
        id: String,
        request: RequestInformationRequestConnectorExchangeRequest?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val requestId = requestId(id)
            val body = request
                ?: throw InformationRequestCommandRequestException("A connector exchange names its Requirement and connector")
            val key = InformationRequestCommandHttp.idempotencyKey(idempotencyKey)
            val access = accessContextFactory.currentAuthenticated()
            val exchange = exchanges.request(
                RequestInformationRequestConnectorExchangeCommand(
                    requestId,
                    body.requirementId,
                    body.connectorKey,
                    body.lookupReference,
                    access,
                    key
                ),
            )
            Response.status(Response.Status.CREATED)
                .entity(InformationRequestExternalSourceDtoMapper.toDto(exchange, access.principal)).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(
                logger,
                "Information Request connector exchange request failed",
                exception
            )
        }
    }

    private fun requestId(raw: String) = InformationRequestCommandHttp.uuid(raw, "information request id")

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestConnectorExchangeResource::class.java)
    }
}
