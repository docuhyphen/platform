package com.docuhyphen.app.api.resource.model

import com.docuhyphen.app.api.serializer.UUIDSerializer
import com.docuhyphen.app.api.service.fields.FieldValueEntry
import kotlinx.serialization.Serializable
import java.util.UUID

/** Request bodies for a resource's Schema Assignment and the typed values held against it. */

/**
 * A sparse change: only the answers it carries are stored, and a field it does not name keeps
 * whatever it already held.
 */
@Serializable
data class SetFieldValuesRequest(
    val values: List<FieldValueEntry> = emptyList(),
)

/**
 * Names the Schema to put in force over a resource. Which published version that means is resolved
 * by the engine rather than named by the caller, so a caller cannot pin a resource to an old one.
 */
@Serializable
data class AssignSchemaRequest(
    @Serializable(with = UUIDSerializer::class)
    val schemaDefinitionId: UUID,
)
