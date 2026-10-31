package com.docuhyphen.app.api.resource.informationrequest.response

import com.docuhyphen.app.api.model.InformationRequestRecipientPreviewDtoMapper
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.response.operations.InformationRequestRecipientPreviewResourceOperations
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestRecipientPreviewService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestRecipientPreviewResource @Inject constructor(
    private val previews: InformationRequestRecipientPreviewService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
) : InformationRequestRecipientPreviewResourceOperations {
    override fun preview(id: String, partyId: String): Response {
        return try {
            val preview = previews.preview(
                InformationRequestCommandHttp.uuid(id, "information request id"),
                InformationRequestCommandHttp.uuid(partyId, "party id"),
                accessContextFactory.currentAuthenticated(),
            )
            Response.ok(InformationRequestRecipientPreviewDtoMapper.toDto(preview)).build()
        } catch (exception: Exception) {
            InformationRequestCommandHttp.refused(logger, "Information Request recipient preview failed", exception)
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(InformationRequestRecipientPreviewResource::class.java)
    }
}
