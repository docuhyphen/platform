package com.docuhyphen.app.api.service.informationrequest.oversight

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import org.testcontainers.containers.PostgreSQLContainer
import java.nio.file.Files

private class InformationRequestOperationsPostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<InformationRequestOperationsPostgreSQLContainer>(imageName)

class InformationRequestOperationsPostgreSQLResource : QuarkusTestResourceLifecycleManager
{
    private val postgres = InformationRequestOperationsPostgreSQLContainer("postgres:17")
        .withDatabaseName("docuhyphen_information_request_operations_test")
        .withUsername("docuhyphen")
        .withPassword("docuhyphen")

    override fun start(): Map<String, String>
    {
        postgres.start()
        val root = Files.createTempDirectory("information-request-operations-test")
        return mapOf(
            "quarkus.datasource.jdbc.url" to postgres.jdbcUrl,
            "quarkus.datasource.username" to postgres.username,
            "quarkus.datasource.password" to postgres.password,
            "file.storage.service" to "local",
            "document.version.storage.local.root-directory" to root.toString(),
            "app.secrets.rotation.enabled" to "false",
            "quarkus.kafka.devservices.enabled" to "false",
            "app.domain-event.outbox.dispatch-every" to "off",
            "app.information-request.clock.every" to "off",
            "app.information-request.notice.dispatch-every" to "off",
            "app.record-disposal.every" to "off",
        )
    }

    override fun stop()
    {
        postgres.stop()
    }
}
