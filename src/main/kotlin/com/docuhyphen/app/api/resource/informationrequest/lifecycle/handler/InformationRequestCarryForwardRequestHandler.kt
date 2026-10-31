package com.docuhyphen.app.api.resource.informationrequest.lifecycle.handler

import com.docuhyphen.app.api.model.InformationRequestLineageDtoMapper
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLineageQueryService
import jakarta.ws.rs.core.Response
import java.util.*

class InformationRequestCarryForwardRequestHandler(
    private val lineageQueries: InformationRequestLineageQueryService,
) {
    fun list(requestId: UUID, access: RequestAccessContext): Response =
        Response.ok(
            lineageQueries.carryForwards(requestId, access).map(InformationRequestLineageDtoMapper::toDto)
                .toTypedArray()
        )
            .build()
}
