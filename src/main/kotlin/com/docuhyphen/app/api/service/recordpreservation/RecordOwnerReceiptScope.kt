package com.docuhyphen.app.api.service.recordpreservation

import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import java.util.*

object RecordOwnerReceiptScope {
    fun of(type: ResourceType, owner: RecordOwnerRef): ResourceRef =
        ResourceRef(
            type,
            UUID.nameUUIDFromBytes(
                "${type.name}|${owner.kind.name}|${owner.id?.toString().orEmpty()}".toByteArray(
                    Charsets.UTF_8
                )
            ),
        )
}
