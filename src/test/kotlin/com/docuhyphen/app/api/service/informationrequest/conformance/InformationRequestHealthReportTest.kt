package com.docuhyphen.app.api.service.informationrequest.conformance

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.informationrequest.oversight.InformationRequestHealthIndicatorKey
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.oversight.InformationRequestHealthService
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestHealthReportTest
{
    @Inject lateinit var dataSource: DataSource
    @Inject lateinit var healthService: InformationRequestHealthService

    @Test
    fun `the report counts an issued request without a grant and a request event stuck in the outbox`()
    {
        val before = counts()
        dataSource.connection.use { connection ->
            val runtime = SubmissionRuntimeSqlFixture(connection)
            val eventId = UUID.randomUUID()
            execute(
                connection,
                """
                INSERT INTO workflow_event_outbox
                    (id, event_id, idempotency_key, event_type, owner_kind, owner_id, organization_id, ordering_key,
                     envelope_json, status, attempt_count, created_at, next_attempt_at)
                VALUES (?, ?, ?, 'information_request.request.issue', 'ORGANIZATION', ?, ?, ?, '{}', 'PENDING', 0,
                        now() - interval '1 hour', now() - interval '1 hour')
                """.trimIndent(),
                UUID.randomUUID(),
                eventId,
                "information_request:$eventId",
                runtime.template.organizationId,
                runtime.template.organizationId,
                "information_request:${runtime.requestId}",
            )
        }

        val after = counts()

        assertEquals(1, after.getValue(InformationRequestHealthIndicatorKey.REQUESTS_WITHOUT_EXECUTION_GRANT) -
            before.getValue(InformationRequestHealthIndicatorKey.REQUESTS_WITHOUT_EXECUTION_GRANT))
        assertEquals(1, after.getValue(InformationRequestHealthIndicatorKey.EVENT_DELIVERY_BACKLOG) -
            before.getValue(InformationRequestHealthIndicatorKey.EVENT_DELIVERY_BACKLOG))
        assertEquals(InformationRequestHealthIndicatorKey.entries.toSet(), after.keys)
    }

    private fun counts(): Map<InformationRequestHealthIndicatorKey, Long> =
        QuarkusTransaction.requiringNew().call { healthService.report().indicators.associate { it.key to it.count } }
}
