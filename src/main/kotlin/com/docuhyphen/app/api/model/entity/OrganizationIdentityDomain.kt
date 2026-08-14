package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

enum class OrganizationIdentityDomainStatus
{
    PENDING,
    VERIFIED,
}

@Entity
@Table(name = "organization_identity_domain")
class OrganizationIdentityDomain
{
    @Id
    var id: UUID = UUID.randomUUID()

    @ManyToOne
    @JoinColumn(name = "organization_id", nullable = false)
    var organization: Organization? = null

    @Column(name = "domain", nullable = false, length = 253)
    var domain: String = ""

    @Column(name = "verification_token", nullable = false, length = 128)
    var verificationToken: String = ""

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: OrganizationIdentityDomainStatus = OrganizationIdentityDomainStatus.PENDING

    @Column(name = "created_date", nullable = false)
    var createdDate: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "verified_date")
    var verifiedDate: Timestamp? = null

    @Column(name = "created_by")
    var createdBy: UUID? = null

    @Column(name = "verified_by")
    var verifiedBy: UUID? = null
}
