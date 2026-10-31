package com.docuhyphen.app.api.service.informationrequest.noauth

import com.docuhyphen.app.api.migration.execute
import java.util.*
import javax.sql.DataSource

internal fun DataSource.revokeAccessSession(sessionId: UUID) {
    connection.use { connection ->
        execute(
            connection,
            "UPDATE request_access_session SET revoked_at = now() WHERE id = ? AND revoked_at IS NULL",
            sessionId,
        )
    }
}
