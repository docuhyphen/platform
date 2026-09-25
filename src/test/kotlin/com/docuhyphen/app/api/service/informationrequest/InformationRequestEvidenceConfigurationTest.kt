package com.docuhyphen.app.api.service.informationrequest

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.util.Properties

class InformationRequestEvidenceConfigurationTest
{
    @Test
    fun `evidence is collected by default without requiring a malware scanner`()
    {
        val base = properties("application.properties")

        assertEquals("true", resolvedDefault(base, UPLOAD_ENABLED))
        assertEquals("false", resolvedDefault(base, MALWARE_SCAN_REQUIRED))
        assertEquals("none", resolvedDefault(base, MALWARE_SCANNER))
    }

    @Test
    fun `no deployment profile turns evidence collection off or requires a scanner it does not have`()
    {
        listOf("application-prod.properties", "application-staging.properties", "application-local.properties").forEach { profile ->
            val values = properties(profile)

            values.getProperty(UPLOAD_ENABLED)?.let { assertEquals("true", resolved(it), "$profile $UPLOAD_ENABLED") }
            values.getProperty(MALWARE_SCAN_REQUIRED)?.let { assertEquals("false", resolved(it), "$profile $MALWARE_SCAN_REQUIRED") }
        }
    }

    private fun properties(name: String): Properties
    {
        val stream = InformationRequestEvidenceConfigurationTest::class.java.classLoader.getResourceAsStream(name)
        assertNotNull(stream, "$name is on the classpath")
        return Properties().apply { stream!!.use(::load) }
    }

    private fun resolvedDefault(values: Properties, key: String): String?
    {
        val value = values.getProperty(key)
        assertNotNull(value, "$key is declared")
        return resolved(value!!)
    }

    private fun resolved(value: String): String =
        Regex("""^\$\{[A-Z0-9_]+:(.*)}$""").find(value.trim())?.groupValues?.get(1) ?: value.trim()

    private companion object
    {
        const val UPLOAD_ENABLED = "app.information-request.evidence.upload.enabled"
        const val MALWARE_SCAN_REQUIRED = "app.information-request.evidence.malware-scan.required"
        const val MALWARE_SCANNER = "app.information-request.evidence.malware-scanner"
    }
}
