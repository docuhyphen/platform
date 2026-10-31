package com.docuhyphen.app.api.model.entity

import jakarta.persistence.*
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@Entity
@Table(name = "information_request_group_occurrence")
class InformationRequestGroupOccurrence
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "source_template_group_id", nullable = false)
    lateinit var sourceTemplateGroupId: UUID

    @Column(name = "parent_occurrence_id", nullable = true)
    var parentOccurrenceId: UUID? = null

    @Column(name = "occurrence_index", nullable = false)
    var occurrenceIndex: Int = 0

    @Column(name = "occurrence_path", nullable = false, length = 512)
    lateinit var occurrencePath: String

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "removed_at")
    var removedAt: Timestamp? = null

    @Column(name = "removed_by_principal_kind", length = 32)
    @Enumerated(EnumType.STRING)
    var removedByPrincipalKind: PrincipalKind? = null

    @Column(name = "removed_by_principal_id")
    var removedByPrincipalId: UUID? = null

    constructor()
}
