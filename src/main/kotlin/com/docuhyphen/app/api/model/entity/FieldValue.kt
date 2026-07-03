package com.docuhyphen.app.api.model.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * A typed value belonging to a [SchemaAssignment] and a [SchemaFieldBinding] / [FieldContract].
 * The scalar column matching [valueType] is populated; SINGLE_SELECT / MULTI_SELECT canonical
 * option codes live in [FieldValueSelection]. Values remain readable after retirement.
 * See FIELDS-FEATURE.md "Field Value".
 */
@Entity
@Table(name = "field_value")
class FieldValue
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "schema_assignment_id", nullable = false)
    lateinit var schemaAssignmentId: UUID

    @Column(name = "schema_field_binding_id", nullable = true)
    var schemaFieldBindingId: UUID? = null

    @Column(name = "field_contract_id", nullable = false)
    lateinit var fieldContractId: UUID

    @Column(name = "resource_type", nullable = false, length = 48)
    lateinit var resourceType: String

    @Column(name = "resource_id", nullable = false)
    lateinit var resourceId: UUID

    @Column(name = "value_type", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var valueType: FieldValueType = FieldValueType.SHORT_TEXT

    @Column(name = "text_value", nullable = true, columnDefinition = "text")
    var textValue: String? = null

    @Column(name = "number_value", nullable = true, precision = 38, scale = 10)
    var numberValue: BigDecimal? = null

    @Column(name = "bool_value", nullable = true)
    var boolValue: Boolean? = null

    @Column(name = "date_value", nullable = true)
    var dateValue: LocalDate? = null

    @Column(name = "datetime_value", nullable = true)
    var datetimeValue: LocalDateTime? = null

    @Column(name = "provenance", nullable = false, length = 24)
    @Enumerated(EnumType.STRING)
    var provenance: FieldValueProvenance = FieldValueProvenance.USER

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "updated_by_app_user_id", nullable = true)
    var updatedByAppUserId: UUID? = null

    constructor()
}
