package com.docuhyphen.app.api.model.entity

import jakarta.persistence.*
import jakarta.persistence.EnumType.STRING
import jakarta.persistence.FetchType.LAZY
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@Entity
@Table(
    name = "identity_provider_link",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["provider", "external_subject_id"])
    ]
)
class IdentityProviderLink
{
    @Id
    var id: UUID = UUID.randomUUID()

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "app_user_id", nullable = false)
    var appUser: AppUser? = null

    @Enumerated(STRING)
    @Column(name = "provider", nullable = false)
    lateinit var provider: IdentityProviderType

    @Column(name = "external_subject_id", nullable = false)
    lateinit var externalSubjectId: String

    @Column(name = "external_email", nullable = false)
    lateinit var externalEmail: String

    @Column(name = "created_date", nullable = false)
    var createdDate: Timestamp = Timestamp.from(Instant.now())

    constructor()
}

