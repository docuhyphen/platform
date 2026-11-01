package com.docuhyphen.app.api.model.auth.idp

data class DecodedIdToken(
    val header: Map<String, Any?>,
    val claims: Map<String, Any?>,
)
