package com.docuhyphen.app.api.model.entity

import jakarta.persistence.*
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@Entity
@Table(name = "field_contract")
class FieldContract
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "field_definition_id", nullable = false)
    lateinit var fieldDefinitionId: UUID

    @Column(name = "contract_version", nullable = false)
    var contractVersion: Int = 1

    @Column(name = "value_type", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var valueType: FieldValueType = FieldValueType.SHORT_TEXT

    @Column(name = "type_contract_version", nullable = false)
    var typeContractVersion: Int = 1

    @Column(name = "label", nullable = false, length = 255)
    lateinit var label: String

    @Column(name = "description", nullable = true, length = 1024)
    var description: String? = null

    @Column(name = "help_text", nullable = true, length = 1024)
    var helpText: String? = null

    @Column(name = "constraints_json", nullable = false, columnDefinition = "text")
    var constraintsJson: String = "{}"

    @Column(name = "options_json", nullable = false, columnDefinition = "text")
    var optionsJson: String = "[]"

    @Column(name = "data_classification", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    var dataClassification: FieldDataClassification = FieldDataClassification.INTERNAL

    @Column(name = "is_searchable", nullable = false)
    var isSearchable: Boolean = false

    @Column(name = "is_filterable", nullable = false)
    var isFilterable: Boolean = false

    @Column(name = "is_sortable", nullable = false)
    var isSortable: Boolean = false

    @Column(name = "is_reportable", nullable = false)
    var isReportable: Boolean = false

    @Column(name = "external_aliases_json", nullable = false, columnDefinition = "text")
    var externalAliasesJson: String = "[]"

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}
