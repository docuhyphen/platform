package com.docuhyphen.app.api.resource.informationrequest.model

import java.util.*

data class HiddenTargetRoute(
    val verb: String,
    val route: String,
    val body: String? = null,
    val multipart: Boolean = false,
) {
    fun path(prefix: String, requestId: UUID, target: String, otherId: UUID): String =
        prefix + route.replace("{id}", requestId.toString()).replace("{target}", target)
            .replace("{other}", otherId.toString())

    fun body(target: String, otherId: UUID): String? =
        body?.replace("{target}", target)?.replace("{other}", otherId.toString())
}
