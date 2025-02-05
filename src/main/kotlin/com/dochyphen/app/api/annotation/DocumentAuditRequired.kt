package com.dochyphen.app.api.annotation

import jakarta.interceptor.InterceptorBinding
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.FUNCTION

@InterceptorBinding
@Target(FUNCTION, AnnotationTarget.CLASS)
@Retention(RUNTIME)
annotation class DocumentAuditRequired