package com.docuhyphen.app.api.service.informationrequest.conformance

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.execution.RequestExecutionGrantRepository
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.subscription.SubscriptionOwnerType
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.UUID
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestQuotaUsageTest
{
    @Inject lateinit var dataSource: DataSource
    @Inject lateinit var requestRepository: InformationRequestRepository
    @Inject lateinit var grantRepository: RequestExecutionGrantRepository

    @Test
    fun `an open request commits its evidence allowance and a finished one only what it stores`()
    {
        val runtime = dataSource.connection.use { SubmissionRuntimeSqlFixture(it) }
        val organizationId = runtime.template.organizationId
        dataSource.connection.use { insertGrant(it, runtime.requestId, organizationId, 1_000L) }

        assertEquals(1L, openRequests(organizationId))
        assertEquals(1_000L, committedEvidence(organizationId))

        dataSource.connection.use {
            execute(it, "UPDATE information_request SET state = 'CLOSED', closed_at = now() WHERE id = ?", runtime.requestId)
        }

        assertEquals(0L, openRequests(organizationId))
        assertEquals(3L, committedEvidence(organizationId))
    }

    @Test
    fun `an open request issued without an allowance commits what it stores`()
    {
        val runtime = dataSource.connection.use { SubmissionRuntimeSqlFixture(it) }
        val organizationId = runtime.template.organizationId
        dataSource.connection.use { insertGrant(it, runtime.requestId, organizationId, null) }

        assertEquals(3L, committedEvidence(organizationId))
        assertEquals(0L, committedEvidence(UUID.randomUUID()))
    }

    private fun openRequests(organizationId: UUID): Long =
        QuarkusTransaction.requiringNew().call {
            requestRepository.countOpenForOwner(InformationRequestOwnerType.ORGANIZATION, organizationId)
        }

    private fun committedEvidence(organizationId: UUID): Long =
        QuarkusTransaction.requiringNew().call {
            grantRepository.committedEvidenceBytes(SubscriptionOwnerType.ORGANIZATION, organizationId)
        }

    private fun insertGrant(connection: Connection, requestId: UUID, organizationId: UUID, allowance: Long?)
    {
        execute(
            connection,
            """
            INSERT INTO request_execution_grant
                (id, request_id, owner_type, owner_organization_id, plan_code, subscription_status, enforcement_mode,
                 evidence_byte_allowance, issued_at, created_at)
            VALUES (?, ?, 'ORGANIZATION', ?, 'BUSINESS', 'ACTIVE', 'ENFORCE', ?, now(), now())
            """.trimIndent(),
            UUID.randomUUID(),
            requestId,
            organizationId,
            allowance,
        )
    }
}
