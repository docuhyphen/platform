package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.sql.Timestamp
import java.time.Instant

@Entity
@Table(name = "stream_head")
class StreamHead
{
    @Id
    @Column(name = "stream_id", length = 128)
    lateinit var streamId: String

    @Column(name = "last_sequence", nullable = false)
    var lastSequence: Long = 0

    @Column(name = "last_hash", length = 128)
    var lastHash: String? = null

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Timestamp = Timestamp.from(Instant.now())
}
