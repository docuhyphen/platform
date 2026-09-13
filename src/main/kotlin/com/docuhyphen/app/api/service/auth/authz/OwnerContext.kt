package com.docuhyphen.app.api.service.auth.authz

import java.util.*

/**
 * Identifies who owns a governed resource. Ownership is immutable after creation and
 * does not drift when the creator changes organization memberships.
 *
 * - [Platform]     : owned by the DocuHyphen platform itself (bundled templates, control-plane
 *                    resources). No customer org or user is the owner.
 * - [Organization] : owned by a specific customer organization. The owning org does not change
 *                    merely because a member changes their active organization.
 * - [Personal]     : owned by a specific authenticated user with no organization affiliation
 *                    for this resource. Personal ownership is first-class: authentication,
 *                    Exchange participation, and personal-resource operations must not require
 *                    organization membership.
 *
 * Ownership is one input to an authorization decision. It is not sufficient by itself:
 * Field visibility also evaluates the published Field Contract, Schema Field Binding, caller
 * relationship, and output channel once the Fields implementation begins.
 */
sealed class OwnerContext
{
    data object Platform : OwnerContext()
    data class Organization(val organizationId: UUID) : OwnerContext()
    data class Personal(val userId: UUID) : OwnerContext()
}

/**
 * Identifies who governs reusable configuration (Schema Definitions, Field Definitions,
 * Workflow Definitions, etc.). Each kind names exactly one governing owner.
 *
 * [ScopeReference] is related to but distinct from [OwnerContext]:
 * - An organization-owned Exchange has an [OwnerContext.Organization] but its Field
 *   Schemas are governed by whichever [ScopeReference] published them.
 * - A personally held resource may be governed by the platform configuration scope when the
 *   platform publishes the relevant schema, as well as by its holder's own scope.
 *
 * Do not substitute a nullable [Organization] scope for personal ownership; use [Personal] for
 * that. An absent scope means the governing owner could not be resolved at all, which is not the
 * same statement as a resource one person governs.
 */
sealed class ScopeReference
{
    data object Platform : ScopeReference()
    data class Organization(val organizationId: UUID) : ScopeReference()
    data class Personal(val userId: UUID) : ScopeReference()
}

/**
 * Identifies a governed resource by type and stable ID. Does not encode permission or
 * configuration scope by itself. Authorization-context providers resolve [OwnerContext] and
 * lifecycle state from a [ResourceReference] without loading resource repositories directly.
 *
 * [type] uses [ResourceKind] rather than the runtime [com.docuhyphen.app.api.model.entity.ResourceType]
 * so that the Fields engine can name resource kinds without depending on the Share model's
 * type enum. The two are kept in sync when the resource type registry is
 * expanded to cover every governed resource.
 */
data class ResourceReference(
    val kind: ResourceKind,
    val id: UUID,
)

/**
 * Stable serialized identifiers for every governed resource kind. Add new kinds here when a
 * new resource type becomes governed. Do not repurpose or remove an existing code once it has
 * been persisted or referenced in a webhook payload.
 *
 * The alias EXCHANGE_SESSION is intentionally absent: Exchanges are EXCHANGE.
 */
enum class ResourceKind
{
    EXCHANGE,
    DOCUMENT,
    PRINCIPAL_GROUP,
    DOCUMENT_LIBRARY_ENTRY,
    BLUEPRINT,
    WORKFLOW_DEFINITION,
    SEQUENCE_DEFINITION,
    VARIABLE_DEFINITION,
    COMMUNICATION,
    APPLICATION,
    ORGANIZATION,
    INFORMATION_REQUEST,
    INFORMATION_REQUEST_REQUIREMENT,
}
