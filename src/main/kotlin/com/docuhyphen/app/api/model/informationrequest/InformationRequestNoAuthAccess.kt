package com.docuhyphen.app.api.model.informationrequest

import com.docuhyphen.app.api.service.informationrequest.RequestAccessContext
import java.util.UUID

data class InformationRequestNoAuthAccess(
    val access: RequestAccessContext,
    val requestId: UUID,
)
