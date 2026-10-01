package com.docuhyphen.app.api.service.informationrequest.response

import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestStructuredResponseValidationContext
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestStructuredResponseValidationIssue
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestStructuredResponseValidationKind
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject

interface InformationRequestStructuredResponseValidator
{
    val kinds: Set<InformationRequestStructuredResponseValidationKind>

    fun validate(context: InformationRequestStructuredResponseValidationContext):
            List<InformationRequestStructuredResponseValidationIssue>
}

@ApplicationScoped
class InformationRequestStructuredResponseValidationService
{
    @Inject
    lateinit var validatorInstances: Instance<InformationRequestStructuredResponseValidator>

    private val testValidators: List<InformationRequestStructuredResponseValidator>?

    constructor()
    {
        testValidators = null
    }

    internal constructor(validators: List<InformationRequestStructuredResponseValidator>)
    {
        testValidators = validators
    }

    fun validate(context: InformationRequestStructuredResponseValidationContext)
    {
        val issues = duplicatePatchIssues(context) + validators().flatMap { it.validate(context) }
        if (issues.isEmpty()) return

        throw InformationRequestLifecycleException(
            InformationRequestErrorCatalog.STRUCTURED_RESPONSE_VALIDATION_FAILED,
            issues.joinToString("; ") { it.message },
        )
    }

    private fun duplicatePatchIssues(
        context: InformationRequestStructuredResponseValidationContext,
    ): List<InformationRequestStructuredResponseValidationIssue>
    {
        val requirementIssues = context.patches
            .groupBy { it.requirementId }
            .filterValues { it.size > 1 }
            .map { (requirementId, _) ->
                InformationRequestStructuredResponseValidationIssue(
                    kind = InformationRequestStructuredResponseValidationKind.DUPLICATE,
                    requirementId = requirementId,
                    message = "A response patch cannot name the same Requirement more than once",
                )
            }

        val fieldIssues = context.patches.flatMap { patch ->
            patch.fieldValues?.entries
                ?.groupBy { it.fieldContractId }
                ?.filterValues { it.size > 1 }
                ?.map { (fieldContractId, _) ->
                    InformationRequestStructuredResponseValidationIssue(
                        kind = InformationRequestStructuredResponseValidationKind.DUPLICATE,
                        requirementId = patch.requirementId,
                        fieldContractId = fieldContractId,
                        message = "A response Field patch cannot name the same Field more than once",
                    )
                }
                ?: emptyList()
        }

        return requirementIssues + fieldIssues
    }

    private fun validators(): List<InformationRequestStructuredResponseValidator> =
        testValidators ?: validatorInstances.toList()
}
