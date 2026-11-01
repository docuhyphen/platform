package com.docuhyphen.app.api.model.workflow

data class ActionResult(
    val success: Boolean,
    val reason: String? = null,
)
