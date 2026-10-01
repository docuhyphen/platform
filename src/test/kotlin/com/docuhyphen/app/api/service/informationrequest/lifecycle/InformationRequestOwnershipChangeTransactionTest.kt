package com.docuhyphen.app.api.service.informationrequest.lifecycle

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnershipChangeDecision
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.service.informationrequest.oversight.InformationRequestOperationsPostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestPartyService
import com.docuhyphen.app.api.service.recordpreservation.RecordOwnershipChangePolicy
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(InformationRequestOperationsPostgreSQLResource::class)
class InformationRequestOwnershipChangeTransactionTest
{
    @Inject lateinit var dataSource: DataSource
    @Inject lateinit var partyRepository: InformationRequestPartyRepository
    @Inject lateinit var requestRepository: InformationRequestRepository
    @Inject lateinit var partyService: InformationRequestPartyService
    @Inject lateinit var configuredPolicy: RecordOwnershipChangePolicy

    @Test
    fun `by default a removed member keeps the parties they hold in the organization's requests`()
    {
        val fixture = dataSource.connection.use { SubmissionRuntimeSqlFixture(it) }
        val service = InformationRequestOwnershipChangeService(partyRepository, requestRepository, configuredPolicy, partyService)

        val outcomes = QuarkusTransaction.requiringNew().call { service.memberRemoved(fixture.template.organizationId, fixture.contributorUserId) }

        assertEquals(listOf(RecordOwnershipChangeDecision.RETAIN), outcomes.map { it.decision })
        assertTrue(QuarkusTransaction.requiringNew().call { requireNotNull(partyRepository.findById(fixture.contributorPartyId)).active })
    }

    @Test
    fun `a revoking policy revokes only the removed member's parties in that organization's requests`()
    {
        lateinit var elsewhere: SubmissionRuntimeSqlFixture
        val borrowedPartyId = UUID.randomUUID()
        val fixture = dataSource.connection.use { connection ->
            val home = SubmissionRuntimeSqlFixture(connection)
            elsewhere = SubmissionRuntimeSqlFixture(connection)
            elsewhere.insertPartyForUser(borrowedPartyId, "CONTRIBUTOR", home.contributorUserId)
            home
        }
        val revoking = mock<RecordOwnershipChangePolicy>()
        whenever(revoking.decide(any())).thenReturn(RecordOwnershipChangeDecision.REVOKE)
        val service = InformationRequestOwnershipChangeService(partyRepository, requestRepository, revoking, partyService)

        val outcomes = QuarkusTransaction.requiringNew().call { service.memberRemoved(fixture.template.organizationId, fixture.contributorUserId) }

        assertEquals(listOf(fixture.contributorPartyId), outcomes.map { it.resourceId })
        QuarkusTransaction.requiringNew().run {
            assertFalse(requireNotNull(partyRepository.findById(fixture.contributorPartyId)).active)
            assertTrue(requireNotNull(partyRepository.findById(fixture.attestorPartyId)).active)
            assertTrue(requireNotNull(partyRepository.findById(borrowedPartyId)).active)
            assertEquals(2, requireNotNull(requestRepository.findById(fixture.requestId)).partyRevision)
        }
    }
}
