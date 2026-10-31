package com.docuhyphen.app.api.model.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.*

@Entity
@Table(name = "field_value_revision")
class FieldValueRevision
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "field_value_id", nullable = false)
    lateinit var fieldValueId: UUID

    @Column(name = "field_value_set_id", nullable = false)
    lateinit var fieldValueSetId: UUID

    @Column(name = "schema_assignment_id", nullable = false)
    lateinit var schemaAssignmentId: UUID

    @Column(name = "schema_field_binding_id", nullable = true)
    var schemaFieldBindingId: UUID? = null

    @Column(name = "field_contract_id", nullable = false)
    lateinit var fieldContractId: UUID

    @Column(name = "revision_number", nullable = false)
    var revisionNumber: Int = 1

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
    var datetimeValue: Timestamp? = null

    @Column(name = "datetime_offset_minutes", nullable = true)
    var datetimeOffsetMinutes: Int? = null

    @Column(name = "is_cleared", nullable = false)
    var isCleared: Boolean = false

    @Column(name = "provenance", nullable = false, length = 24)
    @Enumerated(EnumType.STRING)
    var provenance: FieldValueProvenance = FieldValueProvenance.USER

    @Column(name = "recorded_by_principal_kind", nullable = true, length = 32)
    @Enumerated(EnumType.STRING)
    var recordedByPrincipalKind: PrincipalKind? = null

    @Column(name = "recorded_by_principal_id", nullable = true)
    var recordedByPrincipalId: UUID? = null

    @Column(name = "recorded_by_session_ref", nullable = true, length = 64)
    var recordedBySessionRef: String? = null

    @Column(name = "recorded_at", nullable = false)
    var recordedAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}
