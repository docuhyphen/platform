package com.docuhyphen.app.api.model.entity

import jakarta.persistence.*
import java.sql.Timestamp
import java.time.Instant
import java.util.*

/**
 * Connects one resource (by [resourceType] + [resourceId]) to exactly one published
 * [SchemaVersion]. A live resource is pinned to an exact version and never silently upgraded.
 * See FIELDS-FEATURE.md "Schema Assignment".
 */
@Entity
@Table(name = "schema_assignment")
class SchemaAssignment
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "resource_type", nullable = false, length = 48)
    lateinit var resourceType: String

    @Column(name = "resource_id", nullable = false)
    lateinit var resourceId: UUID

    @Column(name = "schema_version_id", nullable = false)
    lateinit var schemaVersionId: UUID

    @Column(name = "scope_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var scopeKind: FieldScopeKind = FieldScopeKind.ORGANIZATION

    @Column(name = "scope_org_id", nullable = true)
    var scopeOrgId: UUID? = null

    /** Set when [scopeKind] is PERSONAL; copied from the Schema Definition this assignment applies. */
    @Column(name = "scope_user_id", nullable = true)
    var scopeUserId: UUID? = null

    @Column(name = "assignment_source", nullable = false, length = 24)
    @Enumerated(EnumType.STRING)
    var assignmentSource: SchemaAssignmentSource = SchemaAssignmentSource.MANUAL

    /** Canonical principal that chose the Schema for this resource. */
    @Column(name = "assigned_by_principal_kind", nullable = true, length = 32)
    @Enumerated(EnumType.STRING)
    var assignedByPrincipalKind: PrincipalKind? = null

    @Column(name = "assigned_by_principal_id", nullable = true)
    var assignedByPrincipalId: UUID? = null

    /** Non-secret reference to the access session the assignment was made in. */
    @Column(name = "assigned_by_session_ref", nullable = true, length = 64)
    var assignedBySessionRef: String? = null

    /**
     * Retained alongside the canonical pair and written only for a [PrincipalKind.USER] principal,
     * because this column is a foreign key into the registered-user table.
     */
    @Column(name = "assigned_by_app_user_id", nullable = true)
    var assignedByAppUserId: UUID? = null

    @Column(name = "assigned_at", nullable = false)
    var assignedAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}
