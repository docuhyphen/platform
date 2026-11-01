package com.docuhyphen.app.api.repository.audit

import com.docuhyphen.app.api.model.entity.StreamHead
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.RequestScoped
import jakarta.persistence.LockModeType

@RequestScoped
class StreamHeadRepository : BaseRepository<StreamHead>(StreamHead::class.java)
{
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

    fun advance(streamHead: StreamHead): StreamHead = update(streamHead)
}
