package com.docuhyphen.app.api.resource.informationrequest.acceptedfact

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.acceptedfact.handler.InformationRequestFactRecertificationRequestHandler
import com.docuhyphen.app.api.resource.informationrequest.acceptedfact.operations.InformationRequestNoAuthFactRecertificationResourceOperations
import com.docuhyphen.app.api.resource.model.RecertifyInformationRequestAcceptedFactRequest
import com.docuhyphen.app.api.service.informationrequest.acceptedfact.InformationRequestFactRecertificationService
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthReadAccessService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestNoAuthFactRecertificationResource @Inject constructor(
    recertifications: InformationRequestFactRecertificationService,
    private val readAccessService: InformationRequestNoAuthReadAccessService,
) : InformationRequestNoAuthFactRecertificationResourceOperations
{
    private val handler = InformationRequestFactRecertificationRequestHandler(recertifications)

    override fun recertify(
        id: String,
        factId: String,
        request: RecertifyInformationRequestAcceptedFactRequest?,
        accessLinkToken: String?,
        sessionToken: String?,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            InformationRequestCommandHttp.withNoAuthAccess(
                readAccessService,
                id,
                accessLinkToken,
                sessionToken
            ) { requestId, access ->
                handler.recertify(
                    requestId,
                    InformationRequestCommandHttp.uuid(factId, "accepted fact id"),
                    request,
                    access,
                    ifMatch,
                    idempotencyKey,
                )
            }
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(
                logger,
                "No-auth Information Request accepted fact recertification failed",
                exception
            )
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestNoAuthFactRecertificationResource::class.java)
    }
}
