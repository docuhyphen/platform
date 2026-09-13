package com.docuhyphen.app.api.model.entity

/**
 * Enumerations for the configurable Fields and Business Schema engine.
 * See FIELDS-FEATURE.md. These are persisted as strings; add values only, never repurpose.
 */

/**
 * Governance scope kind that owns Fields configuration. Each kind names exactly one owner: PLATFORM
 * names none, ORGANIZATION names an organization, and PERSONAL names a single user who belongs to no
 * organization for this configuration. Persisted as a string so future kinds (BUSINESS_UNIT, TEAM)
 * extend the model without a schema rewrite.
 *
 * PERSONAL is storable but not yet authorable: the configuration scope model, the subscription
 * owner, and the rollout decision for personal authoring are not in place, so the services refuse a
 * request that asks for it.
 */
enum class FieldScopeKind
{
    PLATFORM,
    ORGANIZATION,
    PERSONAL,
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

/**
 * Whether a Field Value Set holds the answers an assignment gives as itself, or one repetition of a
 * repeatable group within that assignment.
 */
enum class FieldValueSetKind
{
    ROOT,
    OCCURRENCE,
}

/** Where a Field Value originated. Cheap to model early, hard to reconstruct later. */
enum class FieldValueProvenance
{
    USER,

    /** Materialised from the schema binding's configured default when the schema was assigned. */
    SCHEMA_DEFAULT,
    BLUEPRINT_DEFAULT,
    API,
    WORKFLOW_ACTION,
    CALCULATED,
    MIGRATION,
}
