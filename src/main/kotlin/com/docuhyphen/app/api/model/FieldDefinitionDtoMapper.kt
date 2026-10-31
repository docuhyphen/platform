package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.FieldContractDto
import com.docuhyphen.app.api.model.dto.FieldDefinitionDto
import com.docuhyphen.app.api.model.dto.FieldTypeInfoDto
import com.docuhyphen.app.api.model.entity.FieldContract
import com.docuhyphen.app.api.model.entity.FieldDefinition
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.fields.FieldConstraints
import com.docuhyphen.app.api.model.fields.FieldOption
import com.docuhyphen.app.api.service.fields.FieldTypeContract

object FieldDefinitionDtoMapper {
    fun toDto(definition: FieldDefinition, contracts: List<FieldContract>): FieldDefinitionDto = FieldDefinitionDto(
        id = definition.id,
        scopeKind = definition.scopeKind,
        scopeOrgId = definition.scopeOrgId,
        namespace = definition.namespace,
        fieldKey = definition.fieldKey,
        status = definition.status,
        contractCount = contracts.size,
        latestContract = contracts.maxByOrNull { it.contractVersion }?.let(::toContractDto),
        createdAt = definition.createdAt,
    )

    fun toContractDto(contract: FieldContract): FieldContractDto = FieldContractDto(
        id = contract.id,
        fieldDefinitionId = contract.fieldDefinitionId,
        contractVersion = contract.contractVersion,
        valueType = contract.valueType,
        typeContractVersion = contract.typeContractVersion,
        label = contract.label,
        description = contract.description,
        helpText = contract.helpText,
        constraints = FieldConstraints.parse(contract.constraintsJson),
        options = FieldOption.parseList(contract.optionsJson),
        dataClassification = contract.dataClassification,
        isSearchable = contract.isSearchable,
        isFilterable = contract.isFilterable,
        isSortable = contract.isSortable,
        isReportable = contract.isReportable,
        createdAt = contract.createdAt,
    )

    fun toTypeInfoDto(type: FieldValueType, contract: FieldTypeContract): FieldTypeInfoDto = FieldTypeInfoDto(
        type = type,
        supportsOptions = type == FieldValueType.SINGLE_SELECT || type == FieldValueType.MULTI_SELECT,
        supportedOperators = contract.supportedOperators.toList(),
    )
}
