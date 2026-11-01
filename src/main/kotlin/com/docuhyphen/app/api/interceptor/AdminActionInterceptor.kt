package com.docuhyphen.app.api.interceptor

import com.docuhyphen.app.api.service.auth.AdminActionGuardService
import com.docuhyphen.app.api.model.auth.AdminApprovalContext
import jakarta.annotation.Priority
import jakarta.inject.Inject
import jakarta.interceptor.AroundInvoke
import jakarta.interceptor.Interceptor
import jakarta.interceptor.InvocationContext

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
