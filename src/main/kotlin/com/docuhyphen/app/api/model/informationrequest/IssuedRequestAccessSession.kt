package com.docuhyphen.app.api.model.informationrequest

import com.docuhyphen.app.api.model.entity.RequestAccessSession

class IssuedRequestAccessSession(
    val session: RequestAccessSession,
    val sessionToken: String,
)
