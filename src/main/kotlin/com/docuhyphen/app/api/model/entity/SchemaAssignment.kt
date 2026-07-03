package com.docuhyphen.app.api.model.entity

import jakarta.persistence.*
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

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

    @Column(name = "assignment_source", nullable = false, length = 24)
    @Enumerated(EnumType.STRING)
    var assignmentSource: SchemaAssignmentSource = SchemaAssignmentSource.MANUAL

    @Column(name = "assigned_by_app_user_id", nullable = true)
    var assignedByAppUserId: UUID? = null

    @Column(name = "assigned_at", nullable = false)
    var assignedAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}
