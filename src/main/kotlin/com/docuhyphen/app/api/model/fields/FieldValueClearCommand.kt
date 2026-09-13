package com.docuhyphen.app.api.model.fields

import com.docuhyphen.app.api.service.fields.FieldsAccessContext
import com.docuhyphen.app.api.service.fields.FieldsPrecondition
import com.docuhyphen.app.api.service.fields.FieldsResourceRef
import com.docuhyphen.app.api.service.fields.FieldValueSetRef
import java.util.UUID

data class FieldValueClearCommand(
    val resource: FieldsResourceRef,
    val access: FieldsAccessContext,
    val fieldContractIds: Set<UUID>,
    val valueSet: FieldValueSetRef,
    val precondition: FieldsPrecondition,
)
