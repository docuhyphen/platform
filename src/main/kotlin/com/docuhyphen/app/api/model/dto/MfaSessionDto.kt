package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
class MfaSessionDto {

    @Serializable(with = UUIDSerializer::class)
    var id: UUID? = null

    var mfaTokenHashed: String? = null

    var mfaToken: String? = null

    var mfaType: String? = null

    var emailFallbackEnabled: Boolean = false

    constructor()
}
