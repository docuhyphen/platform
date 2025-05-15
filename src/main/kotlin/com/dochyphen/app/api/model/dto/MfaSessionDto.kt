package com.dochyphen.app.api.model.dto

import com.dochyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
class MfaSessionDto {

    @Serializable(with = UUIDSerializer::class)
    var id: UUID? = null

    var mfaTokenHashed: String? = null

    var mfaToken: String? = null

    constructor()
}