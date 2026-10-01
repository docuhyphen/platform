package com.docuhyphen.app.api.resource.informationrequest.clock

import com.docuhyphen.app.api.model.informationrequest.clock.InformationRequestClockChange
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.clock.handler.InformationRequestClockRequestHandler
import com.docuhyphen.app.api.resource.informationrequest.clock.operations.InformationRequestClockResourceOperations
import com.docuhyphen.app.api.resource.model.ChangeInformationRequestClockRequest
import com.docuhyphen.app.api.resource.model.StartInformationRequestClockRequest
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockPolicyService
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestClockResource @Inject constructor(
    policies: InformationRequestClockPolicyService,
    clocks: InformationRequestClockService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
) : InformationRequestClockResourceOperations
{
    private val handler = InformationRequestClockRequestHandler(policies, clocks)

    override fun list(id: String): Response
    {
        return try
        {
            handler.clocks(requestId(id), accessContextFactory.currentAuthenticated())
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request clock list failed", exception)
        }
    }

    override fun start(
        id: String,
        request: StartInformationRequestClockRequest?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            handler.start(requestId(id), request, accessContextFactory.currentAuthenticated(), idempotencyKey)
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request clock start failed", exception)
        }
    }

    override fun pause(
        id: String,
        clockId: String,
        request: ChangeInformationRequestClockRequest?,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            change(id, clockId, InformationRequestClockChange.PAUSE, request, ifMatch, idempotencyKey)
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request clock pause failed", exception)
        }
    }

    override fun resume(
        id: String,
        clockId: String,
        request: ChangeInformationRequestClockRequest?,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            change(id, clockId, InformationRequestClockChange.RESUME, request, ifMatch, idempotencyKey)
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request clock resumption failed", exception)
        }
    }

    override fun extend(
        id: String,
        clockId: String,
        request: ChangeInformationRequestClockRequest?,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            change(id, clockId, InformationRequestClockChange.EXTEND, request, ifMatch, idempotencyKey)
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request clock extension failed", exception)
        }
    }

    @Suppress("LongParameterList")
    private fun change(
        id: String,
        clockId: String,
        change: InformationRequestClockChange,
        request: ChangeInformationRequestClockRequest?,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response =
        handler.change(
            requestId(id),
            InformationRequestCommandHttp.uuid(clockId, "clock id"),
            change,
            request,
            accessContextFactory.currentAuthenticated(),
            ifMatch,
            idempotencyKey,
        )

    private fun requestId(raw: String) = InformationRequestCommandHttp.uuid(raw, "information request id")

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestClockResource::class.java)
    }
}
