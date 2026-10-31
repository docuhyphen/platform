package com.docuhyphen.app.api.service.informationrequest.response

import com.docuhyphen.app.api.model.informationrequest.InformationRequestItemProblem
import com.docuhyphen.app.api.model.informationrequest.response.*
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestItemRefusalException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject
import java.util.*

interface InformationRequestStructuredResponseValidator
{
    val stages: Set<InformationRequestStructuredResponseValidationStage>

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

    fun appliesTo(stage: InformationRequestStructuredResponseValidationStage): Boolean =
        validators().any { stage in it.stages }

    fun evaluate(context: InformationRequestStructuredResponseValidationContext):
            List<InformationRequestStructuredResponseValidationIssue> =
        validators().filter { context.stage in it.stages }.flatMap { it.validate(context) }

    fun requireDistinctPatchTargets(patches: List<InformationRequestResponsePatch>)
    {
        val issues = duplicatePatchIssues(patches)
        if (issues.isEmpty()) return
        throw refusal(issues.map { problemOf(it) }, undisclosedProblemCount = 0)
    }

    fun validate(
        context: InformationRequestStructuredResponseValidationContext,
        disclosed: (UUID) -> Boolean = { true },
    ) {
        val issues = evaluate(context)
        if (issues.isEmpty()) return

        val (shown, withheld) = issues.partition { issue -> issue.requirementId?.let(disclosed) ?: true }
        throw refusal(shown.map { problemOf(it) }, withheld.size)
    }

    private fun refusal(problems: List<InformationRequestItemProblem>, undisclosedProblemCount: Int) =
        InformationRequestItemRefusalException(
            reasonCode = InformationRequestErrorCatalog.STRUCTURED_RESPONSE_VALIDATION_FAILED,
            message = "The response did not pass structured validation",
            problems = problems,
            undisclosedProblemCount = undisclosedProblemCount,
        )

    private fun problemOf(issue: InformationRequestStructuredResponseValidationIssue) =
        InformationRequestItemProblem(
            code = InformationRequestErrorCatalog.STRUCTURED_RESPONSE_VALIDATION_FAILED,
            message = issue.message,
            requirementId = issue.requirementId,
            occurrencePath = issue.occurrencePath,
            fieldContractId = issue.fieldContractId,
            validationKind = issue.kind,
        )

    private fun duplicatePatchIssues(
        patches: List<InformationRequestResponsePatch>,
    ): List<InformationRequestStructuredResponseValidationIssue>
    {
        val requirementIssues = patches
            .groupBy { it.requirementId }
            .filterValues { it.size > 1 }
            .map { (requirementId, _) ->
                InformationRequestStructuredResponseValidationIssue(
                    kind = InformationRequestStructuredResponseValidationKind.DUPLICATE,
                    requirementId = requirementId,
                    message = "A response patch cannot name the same Requirement more than once",
                )
            }

        val fieldIssues = patches.flatMap { patch ->
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
