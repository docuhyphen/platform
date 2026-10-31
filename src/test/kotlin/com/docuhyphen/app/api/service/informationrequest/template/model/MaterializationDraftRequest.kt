package com.docuhyphen.app.api.service.informationrequest.template.model

import com.docuhyphen.app.api.model.fields.FieldsAccessContext
import java.util.*

internal data class MaterializationDraftRequest(
    val requestId: UUID,
    val schemaVersionId: UUID,
    val ownerAccess: FieldsAccessContext,
)
