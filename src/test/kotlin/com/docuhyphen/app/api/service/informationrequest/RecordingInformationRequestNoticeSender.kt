package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestOutboundNotice
import jakarta.annotation.Priority
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Alternative
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

@Alternative
@Priority(1)
@ApplicationScoped
class RecordingInformationRequestNoticeSender : InformationRequestNoticeSender
{
    val sent: MutableList<UUID> = CopyOnWriteArrayList()
    val failuresRemaining = AtomicInteger(0)

    override fun send(notice: InformationRequestOutboundNotice)
    {
        if (failuresRemaining.getAndUpdate { if (it > 0) it - 1 else 0 } > 0) throw IllegalStateException("delivery refused")
        sent += notice.id
    }
}
