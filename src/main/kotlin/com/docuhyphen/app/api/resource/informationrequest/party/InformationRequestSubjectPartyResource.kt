package com.docuhyphen.app.api.resource.informationrequest.party

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.InformationRequestPartyDtoMapper
import com.docuhyphen.app.api.model.informationrequest.party.AssignInformationRequestSubjectCommand
import com.docuhyphen.app.api.model.informationrequest.party.InformationRequestSubjectReference
import com.docuhyphen.app.api.resource.command.CommandPreconditionHeader
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.party.operations.InformationRequestSubjectPartyResourceOperations
import com.docuhyphen.app.api.resource.model.AssignInformationRequestSubjectRequest
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestSubjectService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.CREATED
import org.slf4j.LoggerFactory

class InformationRequestSubjectPartyResource @Inject constructor(
    private val subjectService: InformationRequestSubjectService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
) : InformationRequestSubjectPartyResourceOperations
{
    override fun assign(
        id: String,
        request: AssignInformationRequestSubjectRequest,
        ifMatch: String?,
        idempotencyKey: String?,
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
