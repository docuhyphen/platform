package com.docuhyphen.app.api.resource.informationrequest.clock

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.clock.handler.InformationRequestClockRequestHandler
import com.docuhyphen.app.api.resource.informationrequest.clock.operations.InformationRequestClockPolicyResourceOperations
import com.docuhyphen.app.api.resource.model.DefineInformationRequestClockPolicyRequest
import com.docuhyphen.app.api.resource.model.InformationRequestClockPolicyDefinitionRequest
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockPolicyService
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestClockPolicyResource @Inject constructor(
    policies: InformationRequestClockPolicyService,
    clocks: InformationRequestClockService,
) : InformationRequestClockPolicyResourceOperations
{
    private val handler = InformationRequestClockRequestHandler(policies, clocks)

    override fun list(): Response
    {
        return try
        {
            handler.listPolicies()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request clock policy list failed", exception)
        }
    }

    override fun get(policyId: String): Response
    {
        return try
        {
            handler.policy(InformationRequestCommandHttp.uuid(policyId, "clock policy id"))
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request clock policy read failed", exception)
        }
    }

    override fun define(request: DefineInformationRequestClockPolicyRequest?): Response
    {
        return try
        {
            handler.definePolicy(request)
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request clock policy definition failed", exception)
        }
    }

    override fun publishVersion(
        policyId: String,
        request: InformationRequestClockPolicyDefinitionRequest?,
    ): Response
    {
        return try
        {
            handler.publishVersion(InformationRequestCommandHttp.uuid(policyId, "clock policy id"), request)
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request clock policy version publication failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestClockPolicyResource::class.java)
    }
}
