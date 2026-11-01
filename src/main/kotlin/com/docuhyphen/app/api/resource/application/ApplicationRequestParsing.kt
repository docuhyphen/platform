package com.docuhyphen.app.api.resource.application

import java.util.*

internal fun parseApplicationId(raw: String): UUID =
    runCatching { UUID.fromString(raw) }.getOrElse { throw IllegalArgumentException("Invalid id") }
