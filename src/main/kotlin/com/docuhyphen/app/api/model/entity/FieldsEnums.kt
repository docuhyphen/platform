package com.docuhyphen.app.api.model.entity

/**
 * Enumerations for the configurable Fields and Business Schema engine.
 * See FIELDS-FEATURE.md. These are persisted as strings; add values only, never repurpose.
 */

/**
 * Governance scope kind that owns Fields configuration. The first release resolves PLATFORM and
 * ORGANIZATION only. Persisted as a string so future kinds (BUSINESS_UNIT, TEAM) extend the model
 * without a schema rewrite.
 */
enum class FieldScopeKind
{
    PLATFORM,
    ORGANIZATION,
}

/** Draft/publish/retire lifecycle shared by Field Definitions, Schema Definitions, and Schema Versions. */
enum class FieldLifecycleStatus
{
    DRAFT,
    PUBLISHED,
    RETIRED,
}

/** Registered value types available in the first release. Unknown codes must fail closed. */
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

/** Sensitivity classification of a field. Classification never grants access by itself. */
enum class FieldDataClassification
{
    PUBLIC,
    INTERNAL,
    CONFIDENTIAL,
    RESTRICTED,
}

/** Compatibility classification recorded when a new Schema Version is published. */
enum class SchemaCompatibility
{
    ADDITIVE,
    COMPATIBLE,
    BREAKING,
}

/** How a Schema Assignment came to exist. */
enum class SchemaAssignmentSource
{
    MANUAL,
    BLUEPRINT,
    API,
    MIGRATION,
}

/** Where a Field Value originated. Cheap to model early, hard to reconstruct later. */
enum class FieldValueProvenance
{
    USER,
    BLUEPRINT_DEFAULT,
    API,
    WORKFLOW_ACTION,
    CALCULATED,
    MIGRATION,
}
