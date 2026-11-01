package com.docuhyphen.app.api.interceptor

import jakarta.enterprise.util.Nonbinding
import jakarta.interceptor.InterceptorBinding

@InterceptorBinding
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class EnforceAdminAction(
    @get:Nonbinding val value: String = "",
    @get:Nonbinding val requireStepUp: Boolean = true,
)
