package com.docuhyphen.app.api.service.informationrequest.response

import com.docuhyphen.app.api.model.entity.InformationRequestResponse
import java.util.*

interface InformationRequestResponseStore {
    fun findCurrentForRequest(requestId: UUID): List<InformationRequestResponse>

    fun findAllForRequest(requestId: UUID): List<InformationRequestResponse>

    fun findCurrentForUpdate(requestId: UUID, requirementId: UUID): InformationRequestResponse?

    fun save(response: InformationRequestResponse): InformationRequestResponse

    fun update(response: InformationRequestResponse): InformationRequestResponse
}
