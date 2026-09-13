package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/**
 * Names one requested Document that supports the answer to another requirement of the same
 * [InformationRequestTemplateVersion].
 *
 * The relation runs one way: the supported end is never a Document requirement and the supporting
 * end always is, so a chain of supporting evidence cannot close into a cycle. The Version is carried
 * alongside both ends so neither can reach outside the configuration that holds them, and so the
 * set freezes with it.
 */
@Entity
@Table(name = "information_request_template_binding_evidence_link")
class InformationRequestTemplateBindingEvidenceLink
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "template_binding_id", nullable = false)
    lateinit var templateBindingId: UUID

    @Column(name = "supporting_template_binding_id", nullable = false)
    lateinit var supportingTemplateBindingId: UUID

    @Column(name = "template_version_id", nullable = false)
    lateinit var templateVersionId: UUID

    constructor()
}
