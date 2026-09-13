package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/**
 * One answer an [InformationRequestTemplateRequirementBinding] permits a respondent to give. A
 * binding permits each disposition at most once, and never
 * [InformationRequestResponseDisposition.NOT_ANSWERED], which is the state a requirement starts in
 * rather than a way of resolving it.
 *
 * The Version is carried alongside the binding so this set freezes with the configuration that
 * holds it.
 */
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
