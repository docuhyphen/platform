package com.docuhyphen.app.api.service.informationrequest.condition

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.informationrequest.condition.InformationRequestConditionEvaluationState
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestDocumentConditionSourceTransactionTest
{
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var conditions: InformationRequestConditionEvaluationService

    @Test
    fun `a Document with no stated answer is not empty once its own file completes it`()
    {
        val runtime = requestReading(SUPPORTING_RECORD, "IS_NOT_EMPTY")
        stateAnswer(runtime, "NOT_ANSWERED")

        assertEquals(InformationRequestConditionEvaluationState.TRUE, stateOf(runtime))
    }

    @Test
    fun `a Document with no stated answer counts as provided once its own file completes it`()
    {
        val runtime = requestReading(SUPPORTING_RECORD, "EQUALS", expectedDisposition = "PROVIDED")
        stateAnswer(runtime, "NOT_ANSWERED")

        assertEquals(InformationRequestConditionEvaluationState.TRUE, stateOf(runtime))
    }

    @Test
    fun `a Document with neither a file nor a stated answer stays empty`()
    {
        val runtime = requestReading(OPTIONAL_RECORD, "IS_NOT_EMPTY")

        assertEquals(InformationRequestConditionEvaluationState.FALSE, stateOf(runtime))
    }

    @Test
    fun `an answer the respondent stated for a Document wins over its files`()
    {
        val runtime = requestReading(SUPPORTING_RECORD, "EQUALS", expectedDisposition = "PROVIDED")
        stateAnswer(runtime, "NOT_APPLICABLE")

        assertEquals(InformationRequestConditionEvaluationState.FALSE, stateOf(runtime))
    }

    private fun requestReading(
        sourceRequirementKey: String,
        operator: String,
        expectedDisposition: String? = null,
    ): SubmissionRuntimeSqlFixture =
        dataSource.connection.use { connection ->
            SubmissionRuntimeSqlFixture(
                connection,
                optionalRecord = true,
                beforePublish = { configured ->
                    val ruleId = UUID.randomUUID()
                    execute(
                        connection,
                        """
                        INSERT INTO information_request_template_condition_rule
                            (id, template_version_id, rule_key, expression_version, hidden_data_policy)
                        VALUES (?, ?, ?, 1, 'RETAIN_SECURELY')
                        """.trimIndent(),
                        ruleId,
                        configured.template.versionId,
                        RULE_KEY,
                    )
                    execute(
                        connection,
                        """
                        INSERT INTO information_request_template_condition_predicate
                            (id, condition_rule_id, template_version_id, display_order, source_requirement_key,
                             operator, expected_disposition)
                        VALUES (?, ?, ?, 0, ?, ?, ?)
                        """.trimIndent(),
                        UUID.randomUUID(),
                        ruleId,
                        configured.template.versionId,
                        sourceRequirementKey,
                        operator,
                        expectedDisposition,
                    )
                },
            )
        }

    private fun stateAnswer(runtime: SubmissionRuntimeSqlFixture, disposition: String)
    {
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE information_request_response SET disposition = ? WHERE information_request_requirement_id = ?",
                disposition,
                runtime.documentRequirementId,
            )
        }
    }

    private fun stateOf(runtime: SubmissionRuntimeSqlFixture): InformationRequestConditionEvaluationState =
        QuarkusTransaction.requiringNew().call {
            conditions.evaluate(runtime.requestId).single { it.ruleKey == RULE_KEY }.state
        }

    private companion object
    {
        const val RULE_KEY = "when-record-given"
        const val SUPPORTING_RECORD = "supporting-record"
        const val OPTIONAL_RECORD = "optional-record"
    }
}
