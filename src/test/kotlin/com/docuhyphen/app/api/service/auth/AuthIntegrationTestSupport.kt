package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.service.communication.OtpService
import io.quarkus.arc.Arc
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mockingDetails
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private class AuthPostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<AuthPostgreSQLContainer>(imageName)

private class AuthRedisContainer(imageName: String) :
    GenericContainer<AuthRedisContainer>(DockerImageName.parse(imageName))

private const val REDIS_PORT = 6379

private fun authPostgreSQLContainer(): AuthPostgreSQLContainer = AuthPostgreSQLContainer("postgres:17")
    .withDatabaseName("docuhyphen_auth_test")
    .withUsername("docuhyphen")
    .withPassword("docuhyphen")

private fun authApplicationProperties(postgres: AuthPostgreSQLContainer): Map<String, String> = mapOf(
    "quarkus.datasource.jdbc.url" to postgres.jdbcUrl,
    "quarkus.datasource.username" to postgres.username,
    "quarkus.datasource.password" to postgres.password,
    "file.storage.service" to "local",
    "app.secrets.rotation.enabled" to "false",
    "quarkus.kafka.devservices.enabled" to "false",
    "app.auth.signup.disposable-email.enabled" to "false",
)

class AuthPostgreSQLResource : QuarkusTestResourceLifecycleManager
{
    private val postgres = authPostgreSQLContainer()

    override fun start(): Map<String, String>
    {
        postgres.start()
        return authApplicationProperties(postgres) + ("app.auth.rate-limit.enabled" to "false")
    }

    override fun stop()
    {
        postgres.stop()
    }
}

class AuthAbuseControlResource : QuarkusTestResourceLifecycleManager
{
    private val postgres = authPostgreSQLContainer()
    private val redis = AuthRedisContainer("redis:7-alpine").withExposedPorts(REDIS_PORT)

    override fun start(): Map<String, String>
    {
        postgres.start()
        redis.start()
        return authApplicationProperties(postgres) + mapOf(
            "quarkus.redis.hosts" to "redis://${redis.host}:${redis.getMappedPort(REDIS_PORT)}",
            "app.auth.rate-limit.enabled" to "true",
            "app.auth.rate-limit.sign-up.per-minute" to "4",
            "app.auth.rate-limit.sign-up-completion.per-minute" to "2",
            "app.auth.rate-limit.sign-up.distinct-addresses" to "3",
            "app.auth.sign-up.resend-cooldown-seconds" to "2",
        )
    }

    override fun stop()
    {
        redis.stop()
        postgres.stop()
    }
}

private const val CODE_CHECK_DELAY_MILLIS = 150L

fun slowCodeCheckingOtpService(): OtpService
{
    val otpService = spy(OtpService())
    doAnswer { invocation ->
        Thread.sleep(CODE_CHECK_DELAY_MILLIS)
        invocation.callRealMethod()
    }.whenever(otpService).verifyEmailOtp(any(), any())
    return otpService
}

fun OtpService.checkedCodeCount(): Int =
    mockingDetails(this).invocations.count { it.method.name == "verifyEmailOtp" }

fun runConcurrently(requests: Int, action: () -> Unit): List<Throwable>
{
    val start = CountDownLatch(1)
    val executor = Executors.newFixedThreadPool(requests)
    try
    {
        val results = (1..requests).map {
            executor.submit<Throwable?> {
                start.await()
                val requestContext = Arc.container().requestContext()
                requestContext.activate()
                try
                {
                    runCatching(action).exceptionOrNull()
                }
                finally
                {
                    requestContext.terminate()
                }
            }
        }
        start.countDown()
        return results.mapNotNull { it.get(60, TimeUnit.SECONDS) }
    }
    finally
    {
        executor.shutdownNow()
    }
}
