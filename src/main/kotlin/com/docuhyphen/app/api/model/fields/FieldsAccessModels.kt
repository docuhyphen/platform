package com.docuhyphen.app.api.model.fields

import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import java.util.*

data class FieldsResourceRef(
    val resourceType: String,
    val resourceId: UUID,
)

sealed interface FieldValueSetRef {
    data object Root : FieldValueSetRef

    data class Occurrence(val occurrencePath: String) : FieldValueSetRef
}

enum class FieldValueOperation {
    READ,
    WRITE,
}

enum class SchemaAssignmentOperation {
    ASSIGN,
    UNASSIGN,
}

data class FieldsAccessContext(
    val principal: PrincipalRef,
    val authorization: AuthorizationContext,
)

data class FieldValueReadCommand(
    val resource: FieldsResourceRef,
    val access: FieldsAccessContext,
    val valueSet: FieldValueSetRef = FieldValueSetRef.Root,
) {
    val operation: FieldValueOperation get() = FieldValueOperation.READ
}
