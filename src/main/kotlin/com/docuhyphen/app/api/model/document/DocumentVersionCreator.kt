package com.docuhyphen.app.api.model.document

import com.docuhyphen.app.api.model.entity.DocumentVersion
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef

object DocumentVersionCreatorMapper
{
    fun read(version: DocumentVersion): PrincipalRef =
        PrincipalRef(version.createdByPrincipalKind, version.createdByPrincipalId)

    fun recordOn(version: DocumentVersion, creator: PrincipalRef)
    {
        version.createdByPrincipalKind = creator.kind
        version.createdByPrincipalId = creator.id
    }
}
