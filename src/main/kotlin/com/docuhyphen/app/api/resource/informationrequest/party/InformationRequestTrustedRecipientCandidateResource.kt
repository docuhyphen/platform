package com.docuhyphen.app.api.resource.informationrequest.party

import com.docuhyphen.app.api.model.InformationRequestTrustedRecipientCandidateDtoMapper
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.party.operations.InformationRequestTrustedRecipientCandidateResourceOperations
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestTrustedRecipientCandidateQueryService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestTrustedRecipientCandidateResource @Inject constructor(
    private val candidateQueryService: InformationRequestTrustedRecipientCandidateQueryService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
) : InformationRequestTrustedRecipientCandidateResourceOperations {
    override fun list(id: String): Response {
        return try {
            val candidates = candidateQueryService.candidates(
                InformationRequestCommandHttp.uuid(id, "information request id"),
                accessContextFactory.currentAuthenticated(),
            )
            Response.ok(candidates.map(InformationRequestTrustedRecipientCandidateDtoMapper::toDto).toTypedArray())
                .build()
        } catch (exception: Exception) {
            InformationRequestCommandHttp.refused(
                logger,
                "Information Request trusted recipient candidate list failed",
                exception
            )
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(InformationRequestTrustedRecipientCandidateResource::class.java)
    }
}
