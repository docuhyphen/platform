package com.docuhyphen.app.api.model.entity

import jakarta.persistence.*
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * An immutable published contract of a [SchemaDefinition]. Publishing a changed draft creates a
 * new version; it never rewrites an existing published version. Field bindings live in
 * [SchemaFieldBinding]. See FIELDS-FEATURE.md "Schema Version".
 */
@Entity
@Table(name = "schema_version")
class SchemaVersion
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "schema_definition_id", nullable = false)
    lateinit var schemaDefinitionId: UUID

    @Column(name = "version_number", nullable = false)
    var versionNumber: Int = 1

    @Column(name = "status", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    var status: FieldLifecycleStatus = FieldLifecycleStatus.DRAFT

    @Column(name = "compatibility", nullable = true, length = 16)
    @Enumerated(EnumType.STRING)
    var compatibility: SchemaCompatibility? = null

    /** JSON array of schema-level validation rules (reserved; empty in first release). */
    @Column(name = "schema_rules_json", nullable = false, columnDefinition = "text")
    var schemaRulesJson: String = "[]"

    @Column(name = "published_at", nullable = true)
    var publishedAt: Timestamp? = null

    @Column(name = "published_by_app_user_id", nullable = true)
    var publishedByAppUserId: UUID? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}
