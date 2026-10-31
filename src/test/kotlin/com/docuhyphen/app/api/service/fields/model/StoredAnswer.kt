package com.docuhyphen.app.api.service.fields.model

import com.docuhyphen.app.api.model.entity.FieldValueProvenance
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef

data class StoredAnswer(
    val text: String,
    val principal: PrincipalRef,
    val sessionRef: String? = null,
    val provenance: FieldValueProvenance = FieldValueProvenance.USER,
)
