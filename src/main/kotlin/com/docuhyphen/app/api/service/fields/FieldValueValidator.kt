package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.FieldContract
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.serialization.json.JsonElement

/**
 * Validates and canonicalizes a raw field-value input against a persisted [FieldContract].
 * Layer 1 (type shape) and layer 2 (contract constraints) of the validation model. Binding-level
 * requiredness (layer 3) and lifecycle/authorization layers are enforced by the value service.
 * See FIELDS-FEATURE.md "Validation Model".
 */
@ApplicationScoped
class FieldValueValidator @Inject constructor(
    private val typeRegistry: FieldTypeRegistry,
)
{
    /**
     * @throws FieldValidationException if [input] is malformed or violates the contract. The
     *   returned value's [CanonicalFieldValue.isEmpty] is true for a cleared/absent value.
     */
    fun canonicalize(contract: FieldContract, input: JsonElement?): CanonicalFieldValue
    {
        val typeContract = typeRegistry.contractFor(contract.valueType)
        val constraints = FieldConstraints.parse(contract.constraintsJson)
        val options = FieldOption.parseList(contract.optionsJson)
        return typeContract.canonicalize(input, constraints, options)
    }

    /** Validates that a contract's own configuration (options for select types) is coherent. */
    fun validateContractConfiguration(contract: FieldContract)
    {
        typeRegistry.contractFor(contract.valueType) // fail closed on unknown type
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
