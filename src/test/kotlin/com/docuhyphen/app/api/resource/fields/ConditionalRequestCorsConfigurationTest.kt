package com.docuhyphen.app.api.resource.fields

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.*

class ConditionalRequestCorsConfigurationTest
{
    private val profiles = listOf(
        "application.properties",
        "application-local.properties",
        "application-staging.properties",
        "application-prod.properties",
    )

    @Test
    fun `every profile allows independent request session credentials`()
    {
        profiles.forEach { profile ->
            assertTrue("x-request-session-token" in listed(profile, "quarkus.http.cors.headers"), profile)
        }
    }

    @Test
    fun `every profile lets a caller state the version it is changing`()
    {
        profiles.forEach { profile ->
            assertTrue(
                "if-match" in listed(profile, "quarkus.http.cors.headers"),
                "$profile must allow the If-Match request header, or no browser client can condition a save",
            )
        }
    }

    @Test
    fun `every profile lets a caller state that it expects nothing to exist yet`() {
        profiles.forEach { profile ->
            assertTrue(
                "if-none-match" in listed(profile, "quarkus.http.cors.headers"),
                "$profile must allow the If-None-Match request header, or no browser client can choose a schema",
            )
        }
    }

    @Test
    fun `every profile lets a client read the validator it has to send back`()
    {
        profiles.forEach { profile ->
            assertTrue(
                "etag" in listed(profile, "quarkus.http.cors.exposed-headers"),
                "$profile must expose the ETag response header, or the version served is unreadable",
            )
        }
    }

    @Test
    fun `every profile allows the verb a canonical sparse update uses`()
    {
        profiles.forEach { profile ->
            assertTrue(
                "patch" in listed(profile, "quarkus.http.cors.methods"),
                "$profile must allow PATCH, which is the verb every sparse update uses",
            )
        }
    }

    private fun listed(profile: String, key: String): List<String>
    {
        val properties = Properties()
        val resource = requireNotNull(javaClass.classLoader.getResourceAsStream(profile)) { "$profile is missing" }
        resource.use(properties::load)
        return properties.getProperty(key).orEmpty()
            .split(',')
            .map { it.trim().lowercase() }
    }
}
