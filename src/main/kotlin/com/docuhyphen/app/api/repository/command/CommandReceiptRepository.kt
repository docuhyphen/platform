package com.docuhyphen.app.api.repository.command

import com.docuhyphen.app.api.model.entity.CommandReceipt
import com.docuhyphen.app.api.repository.BaseRepository
import com.docuhyphen.app.api.service.command.CommandReceiptRequest
import com.docuhyphen.app.api.service.command.CommandReceiptStore
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.NoResultException

@ApplicationScoped
class CommandReceiptRepository :
    BaseRepository<CommandReceipt>(CommandReceipt::class.java),
    CommandReceiptStore
{
    override fun findForCommand(request: CommandReceiptRequest): CommandReceipt? =
        try
        {
            entityManager.createQuery(
                """SELECT c FROM CommandReceipt c
                   WHERE c.resourceType = :resourceType
                     AND c.resourceId = :resourceId
                     AND c.operationName = :operationName
                     AND c.actorKind = :actorKind
                     AND c.actorId = :actorId
                     AND c.idempotencyKey = :idempotencyKey""",
                CommandReceipt::class.java,
            )
                .setParameter("resourceType", request.resource.type)
                .setParameter("resourceId", request.resource.id)
                .setParameter("operationName", request.operation)
                .setParameter("actorKind", request.actor.kind)
                .setParameter("actorId", request.actor.id)
                .setParameter("idempotencyKey", request.idempotencyKey)
                .singleResult
        }
        catch (e: NoResultException)
        {
            null
        }

    override fun insert(receipt: CommandReceipt): CommandReceipt = save(receipt)
}
