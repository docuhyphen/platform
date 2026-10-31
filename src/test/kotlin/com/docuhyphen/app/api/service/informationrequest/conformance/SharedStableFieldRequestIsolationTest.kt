package com.docuhyphen.app.api.service.informationrequest.conformance

import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.migration.queryStrings
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class SharedStableFieldRequestIsolationTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    private val support by lazy { ConformanceRequestSupport(dataSource, runtime, requestRepository) }
    private val shared by lazy { SharedStableFieldRequestSupport(dataSource, runtime, requestRepository) }

    @Test
    fun `answering and submitting one of two requests on a shared stable Field leaves the other request and the Exchange untouched`() {
        lateinit var exchangeAssignmentId: UUID
        val first = support.fieldRequest(prepare = { connection, prepared ->
            exchangeAssignmentId = shared.assignExchangeField(connection, prepared)
        })
        val second = shared.siblingOf(first)
        val firstServices = runtime.build(first.requestId)
        val secondServices = runtime.build(second.requestId)
        val exchangeBefore = shared.fieldState(exchangeAssignmentId)
        val firstAssignment = shared.assignmentOf(first.requestId)
        val secondAssignment = shared.assignmentOf(second.requestId)
        assertNotEquals(firstAssignment, secondAssignment)

        val firstAnswered = shared.answer(
            firstServices, first.requestId, first.answers.requirementId, first.answers.fieldContractId,
            "First request answer", "answer-first", support.contributor(first),
        )
        val secondAnswered = shared.answer(
            secondServices, second.requestId, second.fieldRequirementId, first.answers.fieldContractId,
            "Second request answer", "answer-second", support.contributor(first),
        )

        assertEquals(
            listOf("First request answer"),
            shared.projectedAnswers(firstAnswered, first.answers.requirementId)
        )
        assertEquals(
            listOf("Second request answer"),
            shared.projectedAnswers(secondAnswered, second.fieldRequirementId)
        )
        assertEquals("First request answer", shared.answerOf(first.requestId))
        dataSource.connection.use { connection ->
            assertEquals(
                setOf("$firstAssignment|First request answer", "$secondAssignment|Second request answer"),
                queryStrings(
                    connection,
                    """
                    SELECT concat_ws('|', value.schema_assignment_id, value.text_value) FROM field_value value
                    WHERE value.field_contract_id = ? AND value.resource_type = 'INFORMATION_REQUEST'
                      AND value.resource_id IN (?, ?)
                    """.trimIndent(),
                    first.answers.fieldContractId,
                    first.requestId,
                    second.requestId,
                ),
                "Each answer is keyed by its own request's assignment",
            )
        }
        val secondBeforeSubmission = shared.fieldState(secondAssignment)

        support.assent(firstServices, first, "assent-first")
        support.submit(firstServices, first, "submit-first")

        assertEquals(
            secondBeforeSubmission,
            shared.fieldState(secondAssignment),
            "Submitting one request leaves the other's answers alone"
        )
        assertEquals(
            exchangeBefore,
            shared.fieldState(exchangeAssignmentId),
            "No request answer ever reaches the Exchange's own answers"
        )
        dataSource.connection.use { connection ->
            assertEquals(
                1,
                queryInt(
                    connection,
                    """
                    SELECT COUNT(*) FROM field_value value
                    JOIN schema_assignment assignment ON assignment.id = value.schema_assignment_id
                    WHERE assignment.resource_type = 'EXCHANGE' AND assignment.resource_id = ?
                    """.trimIndent(),
                    first.runtime.exchangeId,
                ),
                "The Exchange keeps exactly its own answer for the shared Field",
            )
        }
    }
}
