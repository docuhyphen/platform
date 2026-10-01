package com.docuhyphen.app.api.model.informationrequest.noauth

import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import java.util.UUID

data class InformationRequestNoAuthAccess(
    val access: RequestAccessContext,
    val requestId: UUID,
)
