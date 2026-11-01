package com.docuhyphen.app.api.service.informationrequest.access

import com.docuhyphen.app.api.exception.StepUpRequiredException
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.conformance.ConformanceRequestSupport
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.security.ForbiddenException
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestProtectedRequirementDisclosureTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requests: InformationRequestRepository

    @Inject
    lateinit var disclosure: InformationRequestRequirementDisclosure

    @Test
    fun `verification is offered only when the real authorizer would admit the caller afterwards`() {
        val support = ConformanceRequestSupport(dataSource, runtime, requests)
        val prepared = support.fieldRequest(beforePublish = { connection, configured, _ ->
            execute(
                connection,
                "UPDATE information_request_template_requirement_binding SET confidentiality_compartment_key = ? WHERE id IN (?, ?)",
                "restricted-response",
                configured.attestationBindingId,
                configured.documentBindingId,
            )
        })
        val access = RequestAccessContext(
            PrincipalRef.user(prepared.runtime.contributorUserId),
            AuthorizationContext(mfaSatisfied = false, sessionRef = "protected-response-session"),
        )

        QuarkusTransaction.requiringNew().run {
            val hidden = assertThrows<ForbiddenException> {
                disclosure.requireContent(access, prepared.requestId, prepared.runtime.attestationRequirementId)
            }
            val unknown = assertThrows<ForbiddenException> {
                disclosure.requireContent(access, prepared.requestId, UUID.randomUUID())
            }
            assertEquals(unknown.message, hidden.message)
            assertThrows<StepUpRequiredException> {
                disclosure.requireContent(access, prepared.requestId, prepared.runtime.documentRequirementId)
            }
        }
    }
}
