package com.docuhyphen.app.api.service.subscription

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.*

class ProductionSubscriptionEnforcementConfigurationTest
{
    @Test
    fun `every environment defaults subscription enforcement to enforce`()
    {
        val properties = Properties()
        val resource = requireNotNull(
            javaClass.classLoader.getResourceAsStream("application.properties"),
        ) { "application.properties is missing" }
        resource.use(properties::load)

        assertEquals(
            "\${APP_SUBSCRIPTION_ENFORCEMENT_MODE:ENFORCE}",
            properties.getProperty("app.subscription.enforcement.mode"),
        )
    }

    @Test
    fun `production defaults subscription enforcement to enforce`()
    {
        val properties = Properties()
        val resource = requireNotNull(
            javaClass.classLoader.getResourceAsStream("application-prod.properties"),
        ) { "application-prod.properties is missing" }
        resource.use(properties::load)

        assertEquals(
            "\${APP_SUBSCRIPTION_ENFORCEMENT_MODE:ENFORCE}",
            properties.getProperty("app.subscription.enforcement.mode"),
        )
    }

    private fun load(resourceName: String): Properties
    {
        val properties = Properties()
        val resource = requireNotNull(
            javaClass.classLoader.getResourceAsStream(resourceName),
        ) { "$resourceName is missing" }
        resource.use(properties::load)
        return properties
    }
}
