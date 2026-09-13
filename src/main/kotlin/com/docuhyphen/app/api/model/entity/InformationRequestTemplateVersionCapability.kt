package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.service.informationrequest.InformationRequestCapability
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/**
 * One runtime capability an [InformationRequestTemplateVersion] needs somebody to supply, at the
 * contract version the Version was frozen against.
 *
 * Which capabilities a Version needs follows from what it configures, so publication refuses a set
 * that disagrees with the configuration in either direction. Whether an installed runtime can serve
 * the recorded contract versions is a separate question, answered when a request is created against
 * the Version rather than when the Version freezes.
 */
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
