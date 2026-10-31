package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.*

@Entity
@Table(name = "information_request_template_binding_substitute")
class InformationRequestTemplateBindingSubstitute
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "template_binding_id", nullable = false)
    lateinit var templateBindingId: UUID

    @Column(name = "substitute_template_binding_id", nullable = false)
    lateinit var substituteTemplateBindingId: UUID

    @Column(name = "template_version_id", nullable = false)
    lateinit var templateVersionId: UUID

    constructor()
}
