package com.docuhyphen.app.api.repository.informationrequest.record

import com.docuhyphen.app.api.model.entity.InformationRequestRecordExport
import com.docuhyphen.app.api.model.entity.RecordOwnerKind
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestExportWindow
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.sql.Timestamp
import java.util.*

@ApplicationScoped
class InformationRequestRecordExportRepository :
    BaseRepository<InformationRequestRecordExport>(InformationRequestRecordExport::class.java)
{
    fun lockOwnerAllowance(ownerKind: RecordOwnerKind, ownerId: UUID) {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtextextended(:allowanceKey, 0))")
            .setParameter("allowanceKey", "information_request_record_export_allowance:$ownerKind:$ownerId")
            .singleResult
    }

    fun windowSince(ownerKind: RecordOwnerKind, ownerId: UUID, since: Timestamp): InformationRequestExportWindow
    {
        val row = entityManager.createQuery(
            """
            SELECT COUNT(export), MIN(export.requestedAt)
            FROM InformationRequestRecordExport export
            WHERE export.ownerKind = :ownerKind
              AND export.ownerId = :ownerId
              AND export.requestedAt >= :since
            """.trimIndent(),
            Array<Any?>::class.java,
        )
            .setParameter("ownerKind", ownerKind)
            .setParameter("ownerId", ownerId)
            .setParameter("since", since)
            .singleResult
        return InformationRequestExportWindow(
            count = (row[0] as Number).toLong(),
            oldestRequestedAt = (row[1] as Timestamp?)?.toInstant(),
        )
    }

    fun findForRequest(requestId: UUID): List<InformationRequestRecordExport> =
        entityManager.createNativeQuery(
            """
            SELECT record_export.*
            FROM information_request_record_export record_export
            WHERE record_export.information_request_id = :request
               OR record_export.id IN (SELECT source.export_id
                                FROM information_request_record_export_source source
                                WHERE source.information_request_id = :request)
            ORDER BY record_export.requested_at, record_export.id
            """.trimIndent(),
            InformationRequestRecordExport::class.java,
        )
            .setParameter("request", requestId)
            .resultList
            .map { it as InformationRequestRecordExport }

    fun insertSource(exportId: UUID, requestId: UUID)
    {
        entityManager.flush()
        entityManager.createNativeQuery(
            "INSERT INTO information_request_record_export_source (export_id, information_request_id) VALUES (:export, :request)",
        )
            .setParameter("export", exportId)
            .setParameter("request", requestId)
            .executeUpdate()
    }

    fun existing(exportIds: Collection<UUID>): Set<UUID> =
        if (exportIds.isEmpty()) emptySet()
        else entityManager.createQuery(
            "SELECT export.id FROM InformationRequestRecordExport export WHERE export.id IN :ids",
            UUID::class.java,
        )
            .setParameter("ids", exportIds.distinct())
            .resultList
            .toSet()

    fun sourcesOf(exportId: UUID): List<UUID> =
        entityManager.createNativeQuery(
            "SELECT information_request_id FROM information_request_record_export_source WHERE export_id = :export ORDER BY information_request_id",
        )
            .setParameter("export", exportId)
            .resultList
            .map { it as UUID }
}
