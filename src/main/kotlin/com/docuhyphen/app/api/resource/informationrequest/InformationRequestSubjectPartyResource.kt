package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.InformationRequestPartyDtoMapper
import com.docuhyphen.app.api.model.informationrequest.AssignInformationRequestSubjectCommand
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSubjectReference
import com.docuhyphen.app.api.resource.command.CommandPreconditionHeader
import com.docuhyphen.app.api.resource.model.AssignInformationRequestSubjectRequest
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestSubjectService
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.HttpHeaders.IF_MATCH
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.CREATED
import org.slf4j.LoggerFactory

@Path("/information-requests/{id}/subjects")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class InformationRequestSubjectPartyResource @Inject constructor(
    private val subjectService: InformationRequestSubjectService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
)
{
    @POST
    fun assign(
        @PathParam("id") id: String,
        request: AssignInformationRequestSubjectRequest,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val reference = request.reference?.let { stated ->
                if (listOf(stated.authority, stated.identifierType, stated.identifierValue).any { it.isBlank() })
                {
                    throw InformationRequestCommandRequestException(
                        "A subject reference states its authority, identifier type, and identifier value",
                    )
                }
                InformationRequestSubjectReference(stated.authority.trim(), stated.identifierType.trim(), stated.identifierValue.trim())
            }
            val result = subjectService.assignSubject(
                AssignInformationRequestSubjectCommand(
                    requestId = InformationRequestCommandHttp.uuid(id, "information request id"),
                    subjectKind = request.subjectKind,
                    reference = reference,
                    access = accessContextFactory.currentAuthenticated(),
                    precondition = CommandPreconditionHeader.required(ifMatch),
                    idempotencyKey = InformationRequestCommandHttp.idempotencyKey(idempotencyKey),
                ),
            )
            Response.status(CREATED)
                .entity(InformationRequestPartyDtoMapper.toDto(result.party, revealIdentity = true))
                .header("ETag", result.partiesETag)
                .build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request subject assignment failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestSubjectPartyResource::class.java)
    }
}
