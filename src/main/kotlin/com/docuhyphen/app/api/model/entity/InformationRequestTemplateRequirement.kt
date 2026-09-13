package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * The stable identity of one thing a Template asks for, held at the
 * [InformationRequestTemplateDefinition] rather than at a Version. A response recorded against this
 * identity still means the same thing after later Versions change how it is asked, so neither the
 * key, the owning definition, nor the kind of thing being asked for may be rewritten. How a given
 * Version asks for it is stated by an [InformationRequestTemplateRequirementBinding].
 */
@Entity
@Table(name = "information_request_template_requirement")
class InformationRequestTemplateRequirement
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "template_definition_id", nullable = false)
    lateinit var templateDefinitionId: UUID

    @Column(name = "requirement_key", nullable = false, length = 128)
    lateinit var requirementKey: String

    @Column(name = "requirement_type", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var requirementType: InformationRequestRequirementType

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}
