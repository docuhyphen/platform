package com.docuhyphen.app.api.model.entity

import jakarta.persistence.*
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * The stable identity of a configurable business concept for a resource type (for example
 * `acme:customer-case`). Live mutable field rules belong to a [SchemaVersion], not here.
 * See FIELDS-FEATURE.md "Schema Definition".
 */
@Entity
@Table(name = "schema_definition")
class SchemaDefinition
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "scope_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var scopeKind: FieldScopeKind = FieldScopeKind.ORGANIZATION

    @Column(name = "scope_org_id", nullable = true)
    var scopeOrgId: UUID? = null

    @Column(name = "namespace", nullable = false, length = 128)
    lateinit var namespace: String

    @Column(name = "schema_key", nullable = false, length = 128)
    lateinit var schemaKey: String

    @Column(name = "display_name", nullable = false, length = 255)
    lateinit var displayName: String

    @Column(name = "description", nullable = true, length = 1024)
    var description: String? = null

    /** Target resource type code; only EXCHANGE is supported in the first release. */
    @Column(name = "target_resource_type", nullable = false, length = 48)
    var targetResourceType: String = "EXCHANGE"

    @Column(name = "status", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    var status: FieldLifecycleStatus = FieldLifecycleStatus.DRAFT

    @Column(name = "created_by_app_user_id", nullable = true)
    var createdByAppUserId: UUID? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}
