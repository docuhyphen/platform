package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.SchemaAssignmentSource
import com.docuhyphen.app.api.serializer.UUIDSerializer
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.util.UUID

/**
 * What every Fields command states: which resource it addresses, who is asking, what it intends to
 * do, and, for a mutation, which state of the stored data it believes it is changing.
 *
 * Nothing here is resolved from the request the command arrived on. The surface that received the
 * request resolves the caller once and passes it in, so the same engine serves a registered caller
 * and a recipient-bound caller without either surface reaching into the other's authentication.
 */

/** The resource a Fields command addresses, named by its Fields resource type and identity. */
data class FieldsResourceRef(
    val resourceType: String,
    val resourceId: UUID,
)

/**
 * Which set of answers under the resource's assignment a value command addresses: the answers the
 * resource gives as itself, or one repetition of a repeatable group named by its stable path.
 */
sealed interface FieldValueSetRef
{
    data object Root : FieldValueSetRef

    data class Occurrence(val occurrencePath: String) : FieldValueSetRef
}

/**
 * One answer, addressed to the field contract it answers. [value] is the canonical JSON form of that
 * field's type: text and option codes as strings, a Boolean as a JSON Boolean, a date as
 * `YYYY-MM-DD`, a date and time as an offset date-time, a multi-select as an array of option codes,
 * and a cleared answer as JSON null or an empty string.
 *
 * A number is carried as a JSON string of digits rather than as a JSON number, in a request and in a
 * response alike, because a JSON number is parsed as binary floating point by most clients and a
 * wide decimal loses digits on the way through. A JSON number is still accepted on the way in and is
 * read exactly as written.
 */
@Serializable
data class FieldValueEntry(
    @Serializable(with = UUIDSerializer::class)
    val fieldContractId: UUID,
    val value: JsonElement,
)

/** What a value command, and each per-binding decision it needs, intends. */
enum class FieldValueOperation
{
    READ,
    WRITE,
}

/** What a Schema-assignment command intends for the resource's Schema. */
enum class SchemaAssignmentOperation
{
    ASSIGN,
    UNASSIGN,
}

/**
 * The caller a command acts for. [principal] is the only authorization identity; [authorization]
 * carries the surrounding facts a decision needs, including the non-secret session reference the
 * engine records as provenance.
 */
data class FieldsAccessContext(
    val principal: PrincipalRef,
    val authorization: AuthorizationContext,
)

/** A projection of the answers one set currently holds. */
data class FieldValueReadCommand(
    val resource: FieldsResourceRef,
    val access: FieldsAccessContext,
    val valueSet: FieldValueSetRef = FieldValueSetRef.Root,
)
{
    val operation: FieldValueOperation get() = FieldValueOperation.READ
}

/**
 * A sparse change to the answers one set holds. Each entry names the binding it answers, and
 * [precondition] names the state of the set the caller read before deciding on those answers.
 */
data class FieldValueWriteCommand(
    val resource: FieldsResourceRef,
    val access: FieldsAccessContext,
    val entries: List<FieldValueEntry>,
    val valueSet: FieldValueSetRef = FieldValueSetRef.Root,
    val precondition: FieldsPrecondition = FieldsPrecondition.Unconditioned,
)
{
    val operation: FieldValueOperation get() = FieldValueOperation.WRITE
}

/**
 * A change to which Schema governs a resource. [schemaDefinitionId] names the Schema for an
 * assignment and is absent for a removal, and [precondition] names the assignment the caller read.
 */
data class SchemaAssignmentCommand(
    val resource: FieldsResourceRef,
    val access: FieldsAccessContext,
    val operation: SchemaAssignmentOperation,
    val schemaDefinitionId: UUID? = null,
    val source: SchemaAssignmentSource = SchemaAssignmentSource.MANUAL,
    val precondition: FieldsPrecondition = FieldsPrecondition.Unconditioned,
)

/**
 * Assigns one exact published Schema Version to a resource. This is for domains that already froze
 * the Version as part of their own configuration and must not silently advance to a later Version.
 */
data class PublishedSchemaAssignmentCommand(
    val resource: FieldsResourceRef,
    val access: FieldsAccessContext,
    val schemaVersionId: UUID,
    val source: SchemaAssignmentSource = SchemaAssignmentSource.API,
    val precondition: FieldsPrecondition = FieldsPrecondition.Unconditioned,
)

data class BlueprintFieldDefaultEntry(
    val fieldDefinitionId: UUID,
    val value: JsonElement,
)

data class BlueprintFieldDefaultsCommand(
    val resource: FieldsResourceRef,
    val access: FieldsAccessContext,
    val defaults: List<BlueprintFieldDefaultEntry>,
)
