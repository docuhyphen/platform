package com.docuhyphen.app.api.model.document

import com.docuhyphen.app.api.model.entity.DocumentVersion
import com.docuhyphen.app.api.model.identity.PrincipalDisplay

data class DocumentVersionView(
    val version: DocumentVersion,
    val creator: PrincipalDisplay,
)
