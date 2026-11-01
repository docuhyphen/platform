package com.docuhyphen.app.api.model.workflow

import kotlinx.serialization.Serializable

@Serializable
data class WorkflowSubjectField(
    val name: String,
    val type: String,
    val description: String? = null,
    val lookupType: String? = null,
)

enum class PredicateErrorCode
{
    BLANK_EXPRESSION,
    INVALID_SYNTAX,
    INVALID_ESCAPE,
    UNKNOWN_FIELD,
    INCOMPATIBLE_OPERATOR,
    INVALID_OPERAND,
    MISSING_SUBJECT_VALUE,
    INVALID_SUBJECT_VALUE,
}

sealed interface PredicateResult
{
    data class Valid(val matches: Boolean) : PredicateResult
    data class Invalid(val code: PredicateErrorCode) : PredicateResult
}

data class ParsedPredicate(
    val fieldName: String,
    val operator: String,
    val operand: String,
    val quoted: Boolean,
)
