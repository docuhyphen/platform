package com.docuhyphen.app.api.service.informationrequest.access.model

import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestAuthorActions

data class AuthorActionPlacement(
    val parentStatus: String,
    val parentDeleted: Boolean,
    val requestState: String,
    val expected: InformationRequestAuthorActions,
)
