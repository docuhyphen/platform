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
@Table(name = "organization_group")
class OrganizationGroup {

    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true

    @Column(name = "created_date", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var createdDate: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "name", nullable = false)
    lateinit var name: String

    // When true, this group is visible to paired (trusted) organizations when they
    // pick recipients for a new sharing session. Individual users in the org are
    // never enumerated to outside orgs, they can only be reached via a published group.
    @Column(name = "externally_published", nullable = false)
    var externallyPublished: Boolean = false

//    @Column(name = "is_deleted", nullable = false)
//    var isDeleted: Boolean = true

    @OneToMany(
        mappedBy = "organizationGroup",
        cascade = [CascadeType.ALL], fetch = FetchType.LAZY,
        orphanRemoval =  true)
    var members: MutableList<OrganizationGroupMember> = mutableListOf()

    constructor()
}

