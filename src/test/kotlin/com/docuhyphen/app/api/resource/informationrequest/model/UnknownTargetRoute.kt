package com.docuhyphen.app.api.resource.informationrequest.model

import java.util.*

data class UnknownTargetRoute(
    val verb: String,
    val route: String,
    val body: String? = null,
    val multipart: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
    val refusalStatus: Int = 403,
) {
    fun path(requestId: UUID, otherId: UUID): String =
        route.replace("{id}", requestId.toString()).replace("{other}", otherId.toString())
}
