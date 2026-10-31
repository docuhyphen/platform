package com.docuhyphen.app.api.service.fields

class SchemaAssignmentConflictException(
    override val message: String,
    val reasonCode: String,
) : IllegalStateException(message) {
    companion object {
        const val SCHEMA_ALREADY_ASSIGNED = "FIELDS_SCHEMA_ALREADY_ASSIGNED"
    }
}
