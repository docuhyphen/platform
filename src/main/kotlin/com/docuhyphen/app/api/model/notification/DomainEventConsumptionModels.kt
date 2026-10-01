package com.docuhyphen.app.api.model.notification

enum class DomainEventConsumptionOutcome
{
    APPLIED,
    SKIPPED,
}

data class DomainEventConsumptionResult(
    val outcome: DomainEventConsumptionOutcome,
    val detail: String? = null,
)
{
    companion object
    {
        fun applied(detail: String? = null) =
            DomainEventConsumptionResult(DomainEventConsumptionOutcome.APPLIED, detail)

        fun skipped(detail: String) = DomainEventConsumptionResult(DomainEventConsumptionOutcome.SKIPPED, detail)
    }
}
