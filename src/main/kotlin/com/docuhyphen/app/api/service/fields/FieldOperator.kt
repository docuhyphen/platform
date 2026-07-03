package com.docuhyphen.app.api.service.fields

/**
 * Type-aware operators a field may support for search and workflow conditions. Reserved for the
 * search/reporting and workflow-integration increments; declared now so each type contract can
 * advertise its supported set from the start.
 */
enum class FieldOperator
{
    EQUALS,
    NOT_EQUALS,
    LESS_THAN,
    LESS_THAN_OR_EQUAL,
    GREATER_THAN,
    GREATER_THAN_OR_EQUAL,
    CONTAINS,
    STARTS_WITH,
    IN,
    NOT_IN,
    IS_EMPTY,
    IS_NOT_EMPTY,
}
