package com.docuhyphen.app.api.model.entity

import jakarta.persistence.*
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@Entity
@Table(name = "field_value_set")
class FieldValueSet
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "schema_assignment_id", nullable = false)
    lateinit var schemaAssignmentId: UUID

    @Column(name = "set_kind", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    var setKind: FieldValueSetKind = FieldValueSetKind.ROOT

    @Column(name = "occurrence_path", nullable = true, length = 512)
    var occurrencePath: String? = null

    @Column(name = "revision", nullable = false)
    var revision: Long = 1

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}
