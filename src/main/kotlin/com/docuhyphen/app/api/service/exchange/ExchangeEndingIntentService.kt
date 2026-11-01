package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.ExchangeEndingIntent
import com.docuhyphen.app.api.repository.exchange.ExchangeEndingIntentRepository
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.util.*

@ApplicationScoped
class ExchangeEndingIntentService @Inject constructor(
    private val intentRepository: ExchangeEndingIntentRepository,
) {
    @Transactional(Transactional.TxType.MANDATORY)
    fun record(
        exchangeId: UUID,
        workflowInstanceId: UUID,
        cancelRemainingInformationRequests: Boolean,
        requestedBy: PrincipalRef,
    ): ExchangeEndingIntent {
        intentRepository.findForExchange(exchangeId).forEach(intentRepository::delete)
        return intentRepository.save(
            ExchangeEndingIntent().apply {
                this.exchangeId = exchangeId
                this.workflowInstanceId = workflowInstanceId
                this.cancelRemainingInformationRequests = cancelRemainingInformationRequests
                requestedByPrincipalKind = requestedBy.kind
                requestedByPrincipalId = requestedBy.id
            },
        )
    }

    fun pendingFor(exchangeId: UUID, workflowInstanceId: UUID?): ExchangeEndingIntent? =
        workflowInstanceId
            ?.let(intentRepository::findForWorkflowInstance)
            ?.takeIf { it.exchangeId == exchangeId }

    @Transactional(Transactional.TxType.MANDATORY)
    fun consume(intent: ExchangeEndingIntent) {
        intentRepository.delete(intent)
    }
}
