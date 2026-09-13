package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

enum class BlueprintScope
{
    APP,
    ORG,
    PERSONAL,
}

@Entity
@Serializable
@Table(name = "blueprint_definition")
class BlueprintDefinition
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "name", nullable = false)
    lateinit var name: String

    @Column(name = "scope", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var scope: BlueprintScope = BlueprintScope.PERSONAL

    @Column(name = "organization_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var organizationId: UUID? = null

    @Column(name = "created_by_app_user_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var createdByAppUserId: UUID? = null

    @Column(name = "config_json", nullable = false, columnDefinition = "text")
    lateinit var configJson: String

    @Column(name = "schema_definition_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var schemaDefinitionId: UUID? = null

    /**
     * The exact published Information Request Template Version that future instantiations of this
     * blueprint request information against, or null when it requests none. It names a Version
     * rather than a Template because a request has to keep resolving exactly the configuration it
     * was created against, and a Template keeps changing. Replacing it changes only what is created
     * next; everything already created stays pinned to what it used.
     */
    @Column(name = "information_request_template_version_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var informationRequestTemplateVersionId: UUID? = null

    @Column(name = "summary", nullable = true, length = 512)
    var summary: String? = null

    @Column(name = "description", nullable = true, length = 1024)
    var description: String? = null

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true

    @Column(name = "is_published", nullable = false)
    var isPublished: Boolean = false

    @Column(name = "is_deleted", nullable = false)
    var isDeleted: Boolean = false

    @Column(name = "is_template", nullable = false)
    var isTemplate: Boolean = false

    @Column(name = "source_template_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var sourceTemplateId: UUID? = null

    @Column(name = "general_tags", nullable = false, columnDefinition = "text")
    var generalTags: String = "[]"

    @Column(name = "created_at", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "updated_at", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var updatedAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}
