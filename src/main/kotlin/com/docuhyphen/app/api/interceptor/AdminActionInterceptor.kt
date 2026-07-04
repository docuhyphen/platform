package com.docuhyphen.app.api.interceptor

import com.docuhyphen.app.api.service.auth.AdminActionGuardService
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import jakarta.annotation.Priority
import jakarta.inject.Inject
import jakarta.interceptor.AroundInvoke
import jakarta.interceptor.Interceptor
import jakarta.interceptor.InvocationContext

/**
 * CDI interceptor that enforces the admin action guard for any method annotated with
 * [EnforceAdminAction].
 *
 * Execution order:
 * 1. Resolve the [EnforceAdminAction] annotation from the intercepted method.
 * 2. Extract the first [AdminApprovalContext] argument from the invocation (null-safe default).
 * 3. Call [AdminActionGuardService.enforce] — throws on step-up failure before the method body runs.
 * 4. Proceed with the original method.
 */
@EnforceAdminAction
@Interceptor
@Priority(Interceptor.Priority.APPLICATION)
class AdminActionInterceptor @Inject constructor(
    private val adminActionGuardService: AdminActionGuardService,
    private val authTokenContext: AuthTokenContext,
)
{
    @AroundInvoke
    fun enforce(ctx: InvocationContext): Any?
    {
        val annotation = ctx.method.getAnnotation(EnforceAdminAction::class.java)
            ?: ctx.target?.javaClass?.getAnnotation(EnforceAdminAction::class.java)
            ?: return ctx.proceed()

        val approvalContext = ctx.parameters
            .filterIsInstance<AdminApprovalContext>()
            .firstOrNull() ?: AdminApprovalContext()

        adminActionGuardService.enforce(
            action = annotation.value,
            actorId = authTokenContext.authToken.appUser?.id,
            context = approvalContext,
            requireStepUp = annotation.requireStepUp,
        )

        return ctx.proceed()
    }
}
