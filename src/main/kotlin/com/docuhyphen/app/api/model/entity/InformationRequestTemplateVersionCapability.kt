package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.model.informationrequest.capability.InformationRequestCapability
import jakarta.persistence.*
import java.util.*

@Entity
@Table(name = "information_request_template_version_capability")
class InformationRequestTemplateVersionCapability
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "template_version_id", nullable = false)
    lateinit var templateVersionId: UUID

    @Column(name = "capability_key", nullable = false, length = 48)
    @Enumerated(EnumType.STRING)
    lateinit var capabilityKey: InformationRequestCapability

    @Column(name = "required_contract_version", nullable = false)
    var requiredContractVersion: Int = 1

    constructor()
}
