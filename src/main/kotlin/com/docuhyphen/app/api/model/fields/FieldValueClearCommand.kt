package com.docuhyphen.app.api.model.fields

import java.util.*

data class FieldValueClearCommand(
    val resource: FieldsResourceRef,
    val access: FieldsAccessContext,
    val fieldContractIds: Set<UUID>,
    val valueSet: FieldValueSetRef,
    val precondition: FieldsPrecondition,
)
