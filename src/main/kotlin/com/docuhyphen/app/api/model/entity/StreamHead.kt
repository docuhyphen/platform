package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.sql.Timestamp
import java.time.Instant

/**
 * Per-stream sequence/hash cursor used by
 * [com.docuhyphen.app.api.service.audit.LedgerProcessor] to atomically extend
 * `audit_ledger_event`. A stream is one owner scope + time partition (default
 * `<organizationId-or-"platform">:<UTC yyyy-MM>`; see [LedgerProcessor.resolveStreamId] - the
 * exact partition policy is a Prerequisite Decision still unanswered by compliance/legal, so this
 * is a documented placeholder, not a final policy).
 *
 * [com.docuhyphen.app.api.repository.audit.StreamHeadRepository.lockOrCreate] takes a
 * `PESSIMISTIC_WRITE` lock on this row before reading [lastSequence]/[lastHash], so two
 * concurrent appenders to the same stream serialize on this row and cannot fork or reorder the
 * chain.
 */
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
