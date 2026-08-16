package com.docuhyphen.app.api.repository.audit

import com.docuhyphen.app.api.repository.BaseRepository

import com.docuhyphen.app.api.model.entity.StreamHead
import jakarta.enterprise.context.RequestScoped
import jakarta.persistence.LockModeType

@RequestScoped
class StreamHeadRepository : BaseRepository<StreamHead>(StreamHead::class.java)
{
    /**
     * Ensures a `stream_head` row exists for [streamId] (idempotent insert-if-absent), then
     * returns it under a `PESSIMISTIC_WRITE` row lock held for the rest of the caller's
     * transaction. Two concurrent [com.docuhyphen.app.api.service.audit.LedgerProcessor]
     * appenders for the same stream therefore serialize on this row: the second caller blocks
     * until the first commits (advancing [StreamHead.lastSequence]/[StreamHead.lastHash]) or
     * rolls back, so the chain can never fork or reorder.
     */
    fun lockOrCreate(streamId: String): StreamHead
    {
        entityManager.createNativeQuery(
            "INSERT INTO stream_head (stream_id, last_sequence, last_hash, updated_at) " +
                "VALUES (:streamId, 0, NULL, now()) ON CONFLICT (stream_id) DO NOTHING",
        )
            .setParameter("streamId", streamId)
            .executeUpdate()

        return entityManager.find(StreamHead::class.java, streamId, LockModeType.PESSIMISTIC_WRITE)
    }

    /** Persists the advanced [lastSequence]/[lastHash] before the caller's transaction commits. */
    fun advance(streamHead: StreamHead): StreamHead = update(streamHead)
}
