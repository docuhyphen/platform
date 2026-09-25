package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.ACCESS_LINK_TOKEN_HEADER
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.SESSION_TOKEN_HEADER
import com.docuhyphen.app.api.resource.model.SubmitInformationRequestPackageRequest
import com.docuhyphen.app.api.resource.model.WithdrawInformationRequestPackageRequest
import com.docuhyphen.app.api.service.informationrequest.InformationRequestNoAuthReadAccessService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestSubmissionQueryService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestSubmissionService
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.HttpHeaders.IF_MATCH
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("no-auth/information-requests/{id}/submissions")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class InformationRequestNoAuthSubmissionResource @Inject constructor(
    submissionService: InformationRequestSubmissionService,
    queryService: InformationRequestSubmissionQueryService,
    private val readAccessService: InformationRequestNoAuthReadAccessService,
)
{
    private val endpoint = InformationRequestSubmissionEndpoint(submissionService, queryService)

    @GET
    fun list(
        @PathParam("id") id: String,
        @HeaderParam(ACCESS_LINK_TOKEN_HEADER) accessLinkToken: String?,
        @HeaderParam(SESSION_TOKEN_HEADER) sessionToken: String?,
    ): Response
    {
        return try
        {
            InformationRequestCommandHttp.withNoAuthAccess(readAccessService, id, accessLinkToken, sessionToken) { requestId, access ->
                endpoint.list(requestId, access)
            }
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "No-auth Information Request submission list failed", exception)
        }
    }

    @GET
    @Path("/{packageId}")
    fun detail(
        @PathParam("id") id: String,
        @PathParam("packageId") packageId: String,
        @HeaderParam(ACCESS_LINK_TOKEN_HEADER) accessLinkToken: String?,
        @HeaderParam(SESSION_TOKEN_HEADER) sessionToken: String?,
    ): Response
    {
        return try
        {
            InformationRequestCommandHttp.withNoAuthAccess(readAccessService, id, accessLinkToken, sessionToken) { requestId, access ->
                endpoint.detail(requestId, packageId(packageId), access)
            }
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "No-auth Information Request submission lookup failed", exception)
        }
    }

    @POST
    fun submit(
        @PathParam("id") id: String,
        request: SubmitInformationRequestPackageRequest?,
        @HeaderParam(ACCESS_LINK_TOKEN_HEADER) accessLinkToken: String?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
        @HeaderParam(SESSION_TOKEN_HEADER) sessionToken: String?,
    ): Response
    {
        return try
        {
            InformationRequestCommandHttp.withNoAuthAccess(readAccessService, id, accessLinkToken, sessionToken) { requestId, access ->
                endpoint.submit(requestId, request?.stageKey, access, ifMatch, idempotencyKey)
            }
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "No-auth Information Request submission failed", exception)
        }
    }

    @POST
    @Path("/{packageId}/withdrawal")
    fun withdraw(
        @PathParam("id") id: String,
        @PathParam("packageId") packageId: String,
        request: WithdrawInformationRequestPackageRequest?,
        @HeaderParam(ACCESS_LINK_TOKEN_HEADER) accessLinkToken: String?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
        @HeaderParam(SESSION_TOKEN_HEADER) sessionToken: String?,
    ): Response
    {
        return try
        {
            InformationRequestCommandHttp.withNoAuthAccess(readAccessService, id, accessLinkToken, sessionToken) { requestId, access ->
                endpoint.withdraw(requestId, packageId(packageId), request?.reasonCode, access, ifMatch, idempotencyKey)
            }
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "No-auth Information Request submission withdrawal failed", exception)
        }
    }

    private fun packageId(raw: String) = InformationRequestCommandHttp.uuid(raw, "submission package id")

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestNoAuthSubmissionResource::class.java)
    }
}
