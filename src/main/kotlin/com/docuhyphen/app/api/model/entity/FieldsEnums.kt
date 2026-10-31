package com.docuhyphen.app.api.model.entity

enum class FieldScopeKind
{
    PLATFORM,
    ORGANIZATION,
    PERSONAL,
}

enum class FieldLifecycleStatus
{
    DRAFT,
    PUBLISHED,
    RETIRED,
}

enum class FieldValueType
{
    SHORT_TEXT,
    LONG_TEXT,
    BOOLEAN,
    INTEGER,
    DECIMAL,
    DATE,
    DATE_TIME,
    SINGLE_SELECT,
    MULTI_SELECT,
}

enum class FieldDataClassification
{
    PUBLIC,
    INTERNAL,
    CONFIDENTIAL,
    RESTRICTED,
}

enum class SchemaCompatibility
{
    ADDITIVE,
    COMPATIBLE,
    BREAKING,
}

enum class SchemaAssignmentSource
{
    MANUAL,
    BLUEPRINT,
    API,
    MIGRATION,
}

enum class FieldValueSetKind
{
    ROOT,
    OCCURRENCE,
}

enum class FieldValueProvenance
{
    USER,

    SCHEMA_DEFAULT,
    BLUEPRINT_DEFAULT,
    API,
    WORKFLOW_ACTION,
    CALCULATED,
    MIGRATION,
}
