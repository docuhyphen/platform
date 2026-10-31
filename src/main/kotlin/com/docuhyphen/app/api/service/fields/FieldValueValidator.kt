package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.FieldContract
import com.docuhyphen.app.api.model.fields.CanonicalFieldValue
import com.docuhyphen.app.api.model.fields.FieldConstraints
import com.docuhyphen.app.api.model.fields.FieldOption
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.serialization.json.JsonElement

@ApplicationScoped
class FieldValueValidator @Inject constructor(
    private val typeRegistry: FieldTypeRegistry,
)
{
    fun canonicalize(contract: FieldContract, input: JsonElement?): CanonicalFieldValue
    {
        val typeContract = typeRegistry.contractFor(contract.valueType)
        val constraints = FieldConstraints.parse(contract.constraintsJson)
        val options = FieldOption.parseList(contract.optionsJson)
        return typeContract.canonicalize(input, constraints, options)
    }

    fun validateContractConfiguration(contract: FieldContract)
    {
        typeRegistry.contractFor(contract.valueType)
        val isNumeric = contract.valueType == com.docuhyphen.app.api.model.entity.FieldValueType.INTEGER ||
                contract.valueType == com.docuhyphen.app.api.model.entity.FieldValueType.DECIMAL
        if (isNumeric)
        {
            FieldConstraints.parse(contract.constraintsJson).scale?.let(CanonicalNumber::assertStorableScale)
        }
        val options = FieldOption.parseList(contract.optionsJson)
        val isSelect = contract.valueType == com.docuhyphen.app.api.model.entity.FieldValueType.SINGLE_SELECT ||
                contract.valueType == com.docuhyphen.app.api.model.entity.FieldValueType.MULTI_SELECT
        if (isSelect)
        {
            if (options.isEmpty())
                throw FieldValidationException("Select fields require at least one option")
            val codes = options.map { it.code }
            if (codes.size != codes.toSet().size)
                throw FieldValidationException("Option codes must be unique")
            if (codes.any { it.isBlank() })
                throw FieldValidationException("Option codes must not be blank")
        }
    }
}
