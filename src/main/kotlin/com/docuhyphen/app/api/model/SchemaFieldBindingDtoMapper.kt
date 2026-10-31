package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.SchemaFieldBindingDto
import com.docuhyphen.app.api.model.fields.FieldConstraints
import com.docuhyphen.app.api.model.fields.FieldOption
import com.docuhyphen.app.api.model.fields.ResolvedFieldBinding

object SchemaFieldBindingDtoMapper
{
    fun toDto(resolved: ResolvedFieldBinding): SchemaFieldBindingDto = SchemaFieldBindingDto(
        id = resolved.binding.id,
        fieldContractId = resolved.contract.id,
        fieldDefinitionId = resolved.definition.id,
        namespace = resolved.definition.namespace,
        fieldKey = resolved.definition.fieldKey,
        label = resolved.contract.label,
        valueType = resolved.contract.valueType,
        displayOrder = resolved.binding.displayOrder,
        section = resolved.binding.section,
        isRequired = resolved.binding.isRequired,
        isReadOnly = resolved.binding.isReadOnly,
        defaultValueJson = resolved.binding.defaultValueJson,
        visibility = resolved.binding.visibility,
        description = resolved.contract.description,
        helpText = resolved.contract.helpText,
        constraints = FieldConstraints.parse(resolved.contract.constraintsJson),
        options = FieldOption.parseList(resolved.contract.optionsJson),
    )
}
