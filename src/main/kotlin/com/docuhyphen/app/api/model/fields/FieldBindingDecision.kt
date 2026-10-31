package com.docuhyphen.app.api.model.fields

enum class FieldBindingDenial
{
    OUT_OF_AUDIENCE,

    READ_ONLY,
}

sealed interface FieldBindingDecision
{
    data object Allow : FieldBindingDecision

    data class Deny(val reason: FieldBindingDenial) : FieldBindingDecision
}
