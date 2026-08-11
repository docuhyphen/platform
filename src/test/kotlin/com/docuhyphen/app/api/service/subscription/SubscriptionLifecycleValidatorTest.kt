package com.docuhyphen.app.api.service.subscription

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant

class SubscriptionLifecycleValidatorTest
{
    private val validator = SubscriptionLifecycleValidator()
    private val start = Instant.parse("2026-08-01T00:00:00Z")
    private val end = Instant.parse("2026-09-01T00:00:00Z")

    @Test
    fun `owner types accept only their product plans`()
    {
        assertThrows(IllegalArgumentException::class.java) {
            validator.validate(SubscriptionOwnerType.USER, update(PlanCode.BUSINESS))
        }
        assertThrows(IllegalArgumentException::class.java) {
            validator.validate(SubscriptionOwnerType.ORGANIZATION, update(PlanCode.PERSONAL))
        }
    }

    @Test
    fun `trial past due and cancellation dates are deterministic`()
    {
        assertThrows(IllegalArgumentException::class.java) {
            validator.validate(
                SubscriptionOwnerType.USER,
                update(PlanCode.PERSONAL, status = SubscriptionStatus.TRIALING, periodEnd = null),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            validator.validate(
                SubscriptionOwnerType.USER,
                update(PlanCode.PERSONAL, status = SubscriptionStatus.PAST_DUE, graceEnd = null),
            )
        }
        assertDoesNotThrow {
            validator.validate(
                SubscriptionOwnerType.USER,
                update(
                    PlanCode.PERSONAL,
                    status = SubscriptionStatus.PAST_DUE,
                    graceEnd = end.plusSeconds(3600),
                ),
            )
        }
        assertDoesNotThrow {
            validator.validate(
                SubscriptionOwnerType.USER,
                update(PlanCode.PERSONAL, status = SubscriptionStatus.CANCELED),
            )
        }
    }

    @Test
    fun `every administrative mutation requires an audit reason`()
    {
        assertThrows(IllegalArgumentException::class.java) {
            validator.validate(
                SubscriptionOwnerType.USER,
                update(PlanCode.FREE).copy(changeReason = " "),
            )
        }
    }

    private fun update(
        planCode: PlanCode,
        status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
        periodEnd: Instant? = end,
        graceEnd: Instant? = null,
    ): SubscriptionLifecycleUpdate = SubscriptionLifecycleUpdate(
        planCode = planCode,
        status = status,
        billingFrequency = BillingFrequency.MONTHLY,
        currentPeriodStart = periodEnd?.let { start },
        currentPeriodEnd = periodEnd,
        gracePeriodEnd = graceEnd,
        changeReason = "Support correction approved",
    )
}
