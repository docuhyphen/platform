package com.docuhyphen.app.api.service.informationrequest.submission

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.service.command.OverlappingCommandRace
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceMalwareAssessmentService
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestEvidenceScanRaceTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var assessments: InformationRequestEvidenceMalwareAssessmentService

    @Test
    fun `a scan result waits behind a command holding the request so it never lands inside a submission`() {
        val fixture = dataSource.connection.use { connection -> SubmissionRuntimeSqlFixture(connection) }

        val outcome = OverlappingCommandRace(dataSource).whileRowLocked(
            "information_request",
            fixture.requestId,
            listOf { assessments.assess(fixture.evidenceVersionId) },
        )

        assertTrue(outcome.overlapped, "The scan waits for the request a submission holds")
        assertTrue(outcome.results.single().isSuccess, outcome.results.toString())
    }
}
