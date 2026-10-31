package com.docuhyphen.app.api.model.entity

import jakarta.persistence.*
import java.util.*

@Entity
@Table(name = "information_request_template_binding_disposition")
class InformationRequestTemplateBindingDisposition
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "template_binding_id", nullable = false)
    lateinit var templateBindingId: UUID

    @Column(name = "template_version_id", nullable = false)
    lateinit var templateVersionId: UUID

    @Column(name = "disposition", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var disposition: InformationRequestResponseDisposition

    constructor()
}
