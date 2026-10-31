package com.docuhyphen.app.api.resource.recordpreservation

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestSignedInCaller
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.recordpreservation.RecordPreservationHoldService
import com.docuhyphen.app.api.service.user.AppUserService
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusMock
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.SQLException
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class LegacyRecordPreservationRefusalHttpTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var appUsers: AppUserService

    @Test
    fun `legacy hold placement maps a database disposal race to a stable conflict`() {
        val user = dataSource.connection.use { connection ->
            val administrator = SubmissionRuntimeSqlFixture(connection).template.userId
            execute(
                connection,
                "INSERT INTO app_role_assignment (id, app_user_id, role_name, granted_at, is_active) VALUES (?, ?, 'APP_ADMIN', now(), true)",
                UUID.randomUUID(),
                administrator
            )
            administrator
        }
        val holds = mock<RecordPreservationHoldService>()
        whenever(holds.place(any())).thenThrow(
            IllegalStateException(
                "Concurrent disposal",
                SQLException("a record under disposal cannot be placed on hold")
            )
        )
        QuarkusMock.installMockForType(holds, RecordPreservationHoldService::class.java)
        val caller = InformationRequestSignedInCaller.signIn(appUsers, user)
        val response = caller.command("legacy-race-${UUID.randomUUID()}")
            .body(
                mapOf(
                    "resourceType" to "EXCHANGE",
                    "resourceId" to UUID.randomUUID().toString(),
                    "reason" to "Preserve the recorded process"
                )
            )
            .post("/platform/audit-legal-holds")
        assertEquals(409, response.statusCode, response.asString())
        assertEquals("RECORD_PRESERVATION_DISPOSAL_IN_PROGRESS", response.jsonPath().getString("reasonCode"))
    }
}
