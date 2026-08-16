package com.docuhyphen.app.api.service.subscription

import com.docuhyphen.app.api.interceptor.EnforceAdminAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlatformSubscriptionTrialServiceContractTest
{
    @Test
    fun `every platform trial mutation requires step up approval`()
    {
        val expectedActions = mapOf(
            "startUserTrial" to "PLATFORM_USER_SUBSCRIPTION_TRIAL_START",
            "extendUserTrial" to "PLATFORM_USER_SUBSCRIPTION_TRIAL_EXTEND",
            "startOrganizationTrial" to "PLATFORM_ORG_SUBSCRIPTION_TRIAL_START",
            "extendOrganizationTrial" to "PLATFORM_ORG_SUBSCRIPTION_TRIAL_EXTEND",
        )

        expectedActions.forEach { (methodName, action) ->
            val annotation = PlatformSubscriptionTrialService::class.java.declaredMethods
                .single { it.name == methodName }
                .getAnnotation(EnforceAdminAction::class.java)
            assertEquals(action, annotation.value)
            assertTrue(annotation.requireStepUp)
        }

        val expectedTransitions = mapOf(
            "endUserTrial" to "PLATFORM_USER_SUBSCRIPTION_TRIAL_END",
            "convertUserTrial" to "PLATFORM_USER_SUBSCRIPTION_TRIAL_CONVERT",
            "endOrganizationTrial" to "PLATFORM_ORG_SUBSCRIPTION_TRIAL_END",
            "convertOrganizationTrial" to "PLATFORM_ORG_SUBSCRIPTION_TRIAL_CONVERT",
        )
        expectedTransitions.forEach { (methodName, action) ->
            val annotation = PlatformSubscriptionTrialTransitionService::class.java.declaredMethods
                .single { it.name == methodName }
                .getAnnotation(EnforceAdminAction::class.java)
            assertEquals(action, annotation.value)
            assertTrue(annotation.requireStepUp)
        }

        val requestDecision = PlatformSubscriptionTrialRequestService::class.java.declaredMethods
            .single { it.name == "decide" }
            .getAnnotation(EnforceAdminAction::class.java)
        assertEquals("PLATFORM_SUBSCRIPTION_TRIAL_REQUEST_DECIDE", requestDecision.value)
        assertTrue(requestDecision.requireStepUp)
    }
}
