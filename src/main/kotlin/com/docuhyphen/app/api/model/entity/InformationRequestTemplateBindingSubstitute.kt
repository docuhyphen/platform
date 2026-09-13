package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/**
 * Names one requested Document that may be supplied instead of another requested Document of the
 * same [InformationRequestTemplateVersion].
 *
 * Alternatives are a flat set. Both ends are Document requirements, nothing stands in for itself,
 * and neither end may be chained: a requirement that is already substitute evidence declares no
 * substitutes of its own, and a substitute declares none either. Resolving what may stand in for
 * what therefore never follows a second hop and no cycle is reachable.
 *
 * This is unrelated to [InformationRequestTemplateBindingEvidenceLink], which runs from an answer to
 * the Documents supporting it rather than between two interchangeable Documents. The Version is
 * carried alongside both ends so neither reaches outside the configuration that holds them, and so
 * the set freezes with it.
 */
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
