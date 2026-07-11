package com.docuhyphen.app.api.service.workflow

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject

/**
 * Read-only view of the ACTION step handler keys registered in the application.
 *
 * Both the runtime engine and definition validation need to know which
 * `actionHandlerKey` values map to a real [WorkflowActionHandler] bean. The engine builds
 * its own dispatch map from the same beans; this catalog exposes just the key set so
 * validation can reject a definition that references an unregistered handler before it is
 * ever saved, instead of the instance rejecting itself at runtime.
 */
@ApplicationScoped
open class WorkflowActionHandlerCatalog @Inject constructor(
    handlers: Instance<WorkflowActionHandler>,
)
{
    private val registeredKeys: Set<String> = handlers.map { it.key() }.toSet()

    open fun isRegistered(key: String): Boolean = key in registeredKeys

    open fun registeredKeys(): Set<String> = registeredKeys
}
