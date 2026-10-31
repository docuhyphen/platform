package com.docuhyphen.app.api.service.informationrequest.conformance

import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.migration.queryStrings
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class SharedStableFieldRequestCoexistenceTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    private val support by lazy { ConformanceRequestSupport(dataSource, runtime, requestRepository) }
    private val shared by lazy { SharedStableFieldRequestSupport(dataSource, runtime, requestRepository) }

    @Test
    fun `two requests from one Template Version keep their own assignment, answers, revisions and package for one stable Field`() {
        val first = support.fieldRequest()
        val second = shared.siblingOf(first)
        val firstServices = runtime.build(first.requestId)
        val secondServices = runtime.build(second.requestId)

        shared.answer(
            firstServices, first.requestId, first.answers.requirementId, first.answers.fieldContractId,
            "A value", "answer-first", support.contributor(first),
        )
        shared.answer(
            secondServices, second.requestId, second.fieldRequirementId, first.answers.fieldContractId,
            "B value", "answer-second", support.contributor(first),
        )

        dataSource.connection.use { connection ->
            assertEquals(
                setOf(first.requestId.toString(), second.requestId.toString()),
                queryStrings(
                    connection,
                    """
                    SELECT resource_id::text FROM schema_assignment
                    WHERE resource_type = 'INFORMATION_REQUEST' AND schema_version_id = ?
                    """.trimIndent(),
                    first.answers.schemaVersionId,
                ),
                "Each request has its own assignment pinned to the one Schema Version its Template names",
            )
            listOf(first.requestId, second.requestId).forEach { requestId ->
                assertEquals(
                    1,
                    queryInt(
                        connection,
                        """
                        SELECT COUNT(*) FROM field_value_set value_set
                        JOIN schema_assignment assignment ON assignment.id = value_set.schema_assignment_id
                        WHERE assignment.resource_id = ? AND value_set.set_kind = 'ROOT'
                        """.trimIndent(),
                        requestId,
                    ),
                )
            }
        }
        assertEquals(
            "A value",
            shared.answerOf(first.requestId),
            "The first answer survives the second request's write"
        )
        assertEquals("B value", shared.answerOf(second.requestId))
        assertEquals(setOf("A value"), shared.revisionTextsOf(first.requestId))
        assertEquals(setOf("B value"), shared.revisionTextsOf(second.requestId))

        support.assent(firstServices, first, "assent-first")
        val firstPackage = support.submit(firstServices, first, "submit-first")
        shared.assent(secondServices, second, support.attestor(first), "assent-second")
        val secondPackage = shared.submit(secondServices, second, support.contributor(first), "submit-second")

        assertEquals(InformationRequestState.CLOSED, firstPackage.request.state)
        assertEquals(InformationRequestState.CLOSED, secondPackage.request.state)
        dataSource.connection.use { connection ->
            listOf(first.requestId to "A value", second.requestId to "B value").forEach { (requestId, text) ->
                assertEquals(
                    setOf("$requestId|$text"),
                    queryStrings(
                        connection,
                        """
                        SELECT concat_ws('|', assignment.resource_id, revision.text_value)
                        FROM information_request_submission_item item
                        JOIN field_value_revision revision ON revision.id = item.field_value_revision_id
                        JOIN schema_assignment assignment ON assignment.id = revision.schema_assignment_id
                        WHERE item.information_request_id = ?
                        """.trimIndent(),
                        requestId,
                    ),
                    "A package's Field item points only at its own request's revision",
                )
            }
            assertEquals(
                0,
                queryInt(
                    connection,
                    "SELECT COUNT(*) FROM schema_assignment WHERE resource_type = 'EXCHANGE' AND resource_id = ?",
                    first.runtime.exchangeId,
                ),
                "Answering requests never gives their Exchange a Schema Assignment of its own",
            )
        }
    }
}
