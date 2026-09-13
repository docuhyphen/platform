package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.service.fields.FieldOperator
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.LocalDate
import java.util.UUID

/**
 * One term of an [InformationRequestTemplateConditionRule]. A predicate reads exactly one source: a
 * Field's current value by [fieldDefinitionId], or another requirement's current disposition by
 * [sourceRequirementKey]. The scalar literal compared against is stored in the column matching
 * [valueType]; the list literal an `IN` / `NOT_IN` predicate compares against lives in
 * [InformationRequestTemplateConditionPredicateLiteral].
 */
@Entity
@Table(name = "information_request_template_condition_predicate")
class InformationRequestTemplateConditionPredicate
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "condition_rule_id", nullable = false)
    lateinit var conditionRuleId: UUID

    @Column(name = "template_version_id", nullable = false)
    lateinit var templateVersionId: UUID

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0

    @Column(name = "source_requirement_key", nullable = true, length = 128)
    var sourceRequirementKey: String? = null

    @Column(name = "field_definition_id", nullable = true)
    var fieldDefinitionId: UUID? = null

    @Column(name = "value_type", nullable = true, length = 32)
    @Enumerated(EnumType.STRING)
    var valueType: FieldValueType? = null

    @Column(name = "operator", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var operator: FieldOperator

    @Column(name = "expected_disposition", nullable = true, length = 32)
    @Enumerated(EnumType.STRING)
    var expectedDisposition: InformationRequestResponseDisposition? = null

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

    constructor()
}
