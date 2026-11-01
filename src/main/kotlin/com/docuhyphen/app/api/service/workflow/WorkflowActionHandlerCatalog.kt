package com.docuhyphen.app.api.service.workflow

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject

@ApplicationScoped
open class WorkflowActionHandlerCatalog @Inject constructor(
    handlers: Instance<WorkflowActionHandler>,
)
{
    private val registeredKeys: Set<String> = handlers.map { it.key() }.toSet()

    open fun isRegistered(key: String): Boolean = key in registeredKeys

    open fun registeredKeys(): Set<String> = registeredKeys
}
