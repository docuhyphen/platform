package com.docuhyphen.app.api.resource.informationrequest.acceptedfact

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.acceptedfact.handler.InformationRequestFactRecertificationRequestHandler
import com.docuhyphen.app.api.resource.informationrequest.acceptedfact.operations.InformationRequestFactRecertificationResourceOperations
import com.docuhyphen.app.api.resource.model.RecertifyInformationRequestAcceptedFactRequest
import com.docuhyphen.app.api.service.informationrequest.acceptedfact.InformationRequestFactRecertificationService
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestFactRecertificationResource @Inject constructor(
    recertifications: InformationRequestFactRecertificationService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
) : InformationRequestFactRecertificationResourceOperations
{
    private val handler = InformationRequestFactRecertificationRequestHandler(recertifications)

    override fun recertify(
        id: String,
        factId: String,
        request: RecertifyInformationRequestAcceptedFactRequest?,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            handler.recertify(
                InformationRequestCommandHttp.uuid(id, "information request id"),
                InformationRequestCommandHttp.uuid(factId, "accepted fact id"),
                request,
                accessContextFactory.currentAuthenticated(),
                ifMatch,
                idempotencyKey,
            )
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request accepted fact recertification failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestFactRecertificationResource::class.java)
    }
}
