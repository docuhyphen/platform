package com.docuhyphen.app.api.service.informationrequest.record

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.model.entity.InformationRequestRecordExport
import com.docuhyphen.app.api.model.entity.InformationRequestRecordExportKind
import com.docuhyphen.app.api.model.entity.RecordOwnerKind
import com.docuhyphen.app.api.repository.informationrequest.record.InformationRequestRecordExportRepository
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestRecordExportWindowTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var exports: InformationRequestRecordExportRepository

    @Test
    fun `an owner's day of exports counts its subject exports beside its request exports`() {
        val subjectId = UUID.randomUUID()
        val fixture = dataSource.connection.use { connection ->
            SubmissionRuntimeSqlFixture(connection).also { it.insertSubject(subjectId) }
        }
        val ownerId = fixture.template.organizationId
        QuarkusTransaction.requiringNew().run {
            exports.save(export(ownerId, fixture.template.userId) {
                exportKind = InformationRequestRecordExportKind.REQUEST_RECORD
                informationRequestId = fixture.requestId
            })
            exports.save(export(ownerId, fixture.template.userId) {
                exportKind = InformationRequestRecordExportKind.SUBJECT_RECORD
                subjectIdentityRefId = subjectId
            })
        }

        val window = QuarkusTransaction.requiringNew().call {
            exports.windowSince(
                RecordOwnerKind.ORGANIZATION,
                ownerId,
                Timestamp.from(Instant.now().minus(Duration.ofDays(1)))
            )
        }

        assertEquals(2, window.count)
    }

    private fun export(ownerId: UUID, requesterId: UUID, kind: InformationRequestRecordExport.() -> Unit) =
        InformationRequestRecordExport().apply {
            ownerKind = RecordOwnerKind.ORGANIZATION
            this.ownerId = ownerId
            contentHash = "d".repeat(64)
            storageLocation = "primary"
            requestedByPrincipalId = requesterId
            kind()
        }
}
