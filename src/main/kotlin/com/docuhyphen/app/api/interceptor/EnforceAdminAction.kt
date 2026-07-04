package com.docuhyphen.app.api.interceptor

import jakarta.enterprise.util.Nonbinding
import jakarta.interceptor.InterceptorBinding

/**
 * Marks a CDI bean method as requiring an admin action guard check before execution.
 *
 * The [value] must be one of the action keys defined in
 * [com.docuhyphen.app.api.service.auth.AdminActionGuardService.ACTION_LABELS].
 *
 * The interceptor resolves the [com.docuhyphen.app.api.service.auth.AdminApprovalContext]
 * automatically from the method's parameter list (first matching parameter wins).
 * When the method has no such parameter a default context with a null requestId is used.
 *
 * The actor ID is always read from the current [AuthTokenContext].
 */
@InterceptorBinding
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class EnforceAdminAction(
    @get:Nonbinding val value: String = "",
    @get:Nonbinding val requireStepUp: Boolean = true,
)
