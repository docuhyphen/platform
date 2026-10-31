package com.docuhyphen.app.api.service.informationrequest.acceptedfact

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestAcceptedFact
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.fields.FieldsResourceRef
import com.docuhyphen.app.api.service.fields.FieldValidationException
import com.docuhyphen.app.api.service.fields.FieldValueValidator
import com.docuhyphen.app.api.service.fields.FieldsReadFacade
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.serialization.json.Json

@ApplicationScoped
class InformationRequestFactValueFit @Inject constructor(
    private val fieldsReader: FieldsReadFacade,
    private val validator: FieldValueValidator,
) {
    fun fits(request: InformationRequest, fact: InformationRequestAcceptedFact): Boolean {
        val assignment =
            fieldsReader.assignmentFor(FieldsResourceRef(ResourceType.INFORMATION_REQUEST.name, request.id))
                ?: return false
        val binding = fieldsReader.schemaBindings(assignment.schemaVersionId)
            .firstOrNull { it.fieldDefinitionId == fact.fieldDefinitionId }
            ?: return false
        val contract =
            fieldsReader.contract(binding.fieldContractId)?.takeIf { it.valueType == fact.valueType } ?: return false
        return try {
            validator.canonicalize(contract, Json.parseToJsonElement(fact.canonicalValue))
            true
        } catch (_: FieldValidationException) {
            false
        }
    }
}
