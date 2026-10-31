package com.docuhyphen.app.api.model.entity

import jakarta.persistence.*
import java.sql.Timestamp
import java.time.Instant
import java.util.*

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
