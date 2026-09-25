package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "information_request_template_attestation_policy")
class InformationRequestTemplateAttestationPolicy
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "template_version_id", nullable = false)
    lateinit var templateVersionId: UUID

    @Column(name = "template_binding_id", nullable = false)
    lateinit var templateBindingId: UUID

    @Column(name = "ordering", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var ordering: InformationRequestAttestationOrdering = InformationRequestAttestationOrdering.ANY_ORDER

    @Column(name = "minimum_assent_count", nullable = false)
    var minimumAssentCount: Int = 1

    @Column(name = "minimum_authentication_strength", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var minimumAuthenticationStrength: InformationRequestAuthenticationStrength =
        InformationRequestAuthenticationStrength.VERIFIED_CONTACT

    @Column(name = "validity_hours")
    var validityHours: Int? = null

    @Column(name = "external_signature_reference", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var externalSignatureReference: InformationRequestExternalSignatureReferencePolicy =
        InformationRequestExternalSignatureReferencePolicy.NOT_ACCEPTED

    constructor()
}

@Entity
@Table(name = "information_request_template_attestation_role")
class InformationRequestTemplateAttestationRole
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "template_version_id", nullable = false)
    lateinit var templateVersionId: UUID

    @Column(name = "attestation_policy_id", nullable = false)
    lateinit var attestationPolicyId: UUID

    @Column(name = "role_key", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var roleKey: InformationRequestContributorRole

    @Column(name = "position", nullable = false)
    var position: Int = 1

    constructor()
}
