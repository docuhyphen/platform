package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.service.informationrequest.InformationRequestParentSnapshot
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

@ApplicationScoped
class ExchangeLifecycleStateService @Inject constructor(private val repository: ExchangeRepository)
{
    fun snapshot(id: UUID): InformationRequestParentSnapshot? = repository.findById(id)?.let {
        InformationRequestParentSnapshot(it.status, it.isDeleted)
    }
}
