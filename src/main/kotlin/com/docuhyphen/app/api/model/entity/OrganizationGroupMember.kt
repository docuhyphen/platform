package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@Entity
@Serializable
@Table(name = "organization_group_member")
class OrganizationGroupMember
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true

    @Column(name = "created_date", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var createdDate: Timestamp = Timestamp.from(Instant.now())

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_group_id")
    var organizationGroup: OrganizationGroup? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_user_id")
    var appUser: AppUser? = null

    @OneToOne(mappedBy = "organizationGroupMember", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var permissions: OrganizationGroupMemberPermission? = null

    constructor()
}