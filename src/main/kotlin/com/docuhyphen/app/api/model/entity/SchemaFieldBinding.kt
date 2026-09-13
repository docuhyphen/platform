package com.docuhyphen.app.api.model.entity

import jakarta.persistence.*
import java.util.*

/**
 * Places a [FieldContract] into a [SchemaVersion]. Contextual behaviour (order, requiredness,
 * visibility, static default) belongs here, not on the global [FieldDefinition].
 * See FIELDS-FEATURE.md "Schema Field Binding".
 */
@Entity
@Table(name = "schema_field_binding")
class SchemaFieldBinding
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "schema_version_id", nullable = false)
    lateinit var schemaVersionId: UUID

    @Column(name = "field_contract_id", nullable = false)
    lateinit var fieldContractId: UUID

    /**
     * Stable identity of the bound field, carried alongside the contract so a version can hold at
     * most one binding per field however many contract versions that field has.
     */
    @Column(name = "field_definition_id", nullable = false)
    lateinit var fieldDefinitionId: UUID

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0

    @Column(name = "section", nullable = true, length = 128)
    var section: String? = null

    @Column(name = "is_required", nullable = false)
    var isRequired: Boolean = false

    @Column(name = "is_read_only", nullable = false)
    var isReadOnly: Boolean = false

    /** Canonical JSON of the static default value, or null when there is no default. */
    @Column(name = "default_value_json", nullable = true, columnDefinition = "text")
    var defaultValueJson: String? = null

    @Column(name = "visibility", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    var visibility: FieldDataClassification = FieldDataClassification.INTERNAL

    constructor()
}
