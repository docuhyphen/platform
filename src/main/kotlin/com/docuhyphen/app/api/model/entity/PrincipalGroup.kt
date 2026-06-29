package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

enum class PrincipalGroupScope
{
    /** Owned by an organization. Replaces [OrganizationGroup]. */
    ORG,

    /** Owned by a single [AppUser]; used for ad-hoc sharing outside any org. */
    PERSONAL,

    /** Jointly owned by two or more organizations (see [PrincipalGroupCoOwnerOrg]). */
    SHARED_PROJECT,
}

/**
 * Unified group entity. Replaces [OrganizationGroup] (which is kept alongside until V9
 * for backwards compatibility). For ORG-scoped groups the row IDs are reused from the
 * legacy `organization_group` rows so existing FKs (e.g. `exchange.group_id`)
 * keep resolving during the dual-write window.
 */
@Entity
@Serializable
@Table(name = "principal_group")
class PrincipalGroup
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "name", nullable = false)
    lateinit var name: String

    @Column(name = "description", nullable = true, length = 1024)
    var description: String? = null

    @Column(name = "scope", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var scope: PrincipalGroupScope = PrincipalGroupScope.ORG

    @Column(name = "owner_organization_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var ownerOrganizationId: UUID? = null

    @Column(name = "owner_app_user_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var ownerAppUserId: UUID? = null

    @Column(name = "parent_group_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var parentGroupId: UUID? = null

    /**
     * For [PrincipalGroupScope.ORG] only: when true, the group is visible to paired
     * (trusted) organizations as a sharing target. Mirrors the legacy
     * `OrganizationGroup.externallyPublished` field.
     */
    @Column(name = "externally_published", nullable = false)
    var externallyPublished: Boolean = false

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true

    @Column(name = "icon_data", columnDefinition = "TEXT")
    var iconData: String? = null

    @Column(name = "created_date", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var createdDate: Timestamp = Timestamp.from(Instant.now())

    constructor()
}

