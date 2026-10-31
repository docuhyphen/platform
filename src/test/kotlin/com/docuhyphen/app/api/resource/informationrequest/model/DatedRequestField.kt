package com.docuhyphen.app.api.resource.informationrequest.model

data class DatedRequestField(
    val route: String,
    val fixedFields: String,
    val dateName: String,
    val ifMatch: String? = null,
) {
    fun path(requestId: java.util.UUID): String = "/information-requests/$requestId/$route"

    fun body(date: String?): String = if (date == null) "{$fixedFields}" else "{$fixedFields,\"$dateName\":$date}"
}
