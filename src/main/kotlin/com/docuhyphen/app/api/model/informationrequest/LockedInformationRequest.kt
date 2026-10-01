package com.docuhyphen.app.api.model.informationrequest

import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.InformationRequest

data class LockedInformationRequest(
    val exchange: Exchange,
    val request: InformationRequest,
)
